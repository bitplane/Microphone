package net.bitplane.android.microphone

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground service that pipes the microphone straight to the audio output.
 *
 * It has to be a foreground service of type "microphone": since Android 9 a
 * background app can't read the mic, and since Android 8 background services
 * are killed shortly after the app leaves the screen.
 */
class MicrophoneService : Service() {

    @Volatile
    private var running = false
    private var audioThread: Thread? = null
    private var noisyReceiverRegistered = false

    // Set when a chosen device or the connected devices change; the audio loop picks it up.
    @Volatile
    private var rerouteNeeded = true
    private var communicationModeOn = false
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) { rerouteNeeded = true }
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) { rerouteNeeded = true }
    }
    private var selectionWatchers: List<Job> = emptyList()
    private val scope = MainScope()

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                Log.d(TAG, "Audio output unplugged, stopping")
                stopSelf()
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.d(TAG, "Stop requested")
            stopSelf()
            return START_NOT_STICKY
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "No RECORD_AUDIO permission, not starting")
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    0
                }
            )
        } catch (e: Exception) {
            // Android 14+ refuses microphone FGS starts unless the app is in the foreground.
            Log.e(TAG, "Couldn't enter foreground", e)
            stopSelf()
            return START_NOT_STICKY
        }

        if (!running) {
            running = true
            _active.value = true
            registerNoisyReceiver()
            AudioDevices.load(this)
            audioManager.registerAudioDeviceCallback(deviceCallback, null)
            selectionWatchers = listOf(AudioDevices.input, AudioDevices.output).map { choice ->
                scope.launch { choice.selected.collect { rerouteNeeded = true } }
            }
            audioThread = Thread(::audioLoop, "MicrophoneLoop").also { it.start() }
        }

        // A microphone service can't be restarted from the background, so don't ask to be.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Stopping mic service")
        running = false
        audioThread?.join(1000)
        audioThread = null
        selectionWatchers.forEach { it.cancel() }
        scope.cancel()
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
        if (noisyReceiverRegistered) {
            unregisterReceiver(noisyReceiver)
            noisyReceiverRegistered = false
        }
        _active.value = false
        super.onDestroy()
    }

    private fun registerNoisyReceiver() {
        ContextCompat.registerReceiver(
            this,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        noisyReceiverRegistered = true
    }

    private fun audioLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

        val config = pickConfig()
        if (config == null) {
            Log.e(TAG, "No supported audio config")
            stopSelfOnMainThread()
            return
        }
        val rate = config.sampleRate
        val burst = config.burstFrames

        var input: AudioRecord? = null
        var output: AudioTrack? = null
        try {
            input = createAudioRecord(rate, config.inBufferBytes)
            // Room to grow into: the input may hand over its audio in lumps bigger than the
            // output's minimum, so the track's capacity has to cover both.
            output = createAudioTrack(rate, maxOf(config.outBufferBytes, config.inBufferBytes) * 2)

            if (input == null || output.state != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "Audio devices failed to initialise")
                return
            }

            // Start with the smallest playback queue and only grow it when playback runs dry.
            val tunable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
            val capacity = if (tunable) output.bufferCapacityInFrames else 0
            var queueFrames = 0
            if (tunable) queueFrames = output.setBufferSizeInFrames(2 * burst)
            var underruns = 0
            Log.d(TAG, "Audio at $rate Hz, burst $burst frames, queue $queueFrames of $capacity frames")

            route(input, output)
            input.startRecording()
            output.play()
            Log.d(TAG, "Entered record loop")

            val buffer = ShortArray(burst)
            while (running) {
                if (rerouteNeeded) route(input, output)
                val read = input.read(buffer, 0, buffer.size)
                if (read < 0) {
                    Log.e(TAG, "AudioRecord.read failed: $read")
                    break
                }
                if (read == 0) continue

                if (tunable) {
                    // Whatever doesn't fit in the queue is dropped, so latency can't build up.
                    output.write(buffer, 0, read, AudioTrack.WRITE_NON_BLOCKING)
                    val now = output.underrunCount
                    if (now > underruns) {
                        underruns = now
                        if (queueFrames + burst <= capacity) {
                            queueFrames = output.setBufferSizeInFrames(queueFrames + burst)
                            Log.d(TAG, "Underrun, queue now $queueFrames frames")
                        }
                    }
                } else {
                    output.write(buffer, 0, read)
                }
            }
            Log.d(TAG, "Record loop finished")
        } catch (e: Exception) {
            Log.e(TAG, "Error while recording, aborting", e)
        } finally {
            input?.runCatching { stop() }
            input?.release()
            output?.runCatching { stop() }
            output?.release()
            setCommunicationMode(null)
            // If the loop died on its own (not via onDestroy), take the service down with it.
            if (running) stopSelfOnMainThread()
        }
    }

    private class AudioConfig(
        val sampleRate: Int,
        val burstFrames: Int,
        val inBufferBytes: Int,
        val outBufferBytes: Int,
    )

    /**
     * Uses the output's native rate and burst size, which is what Android needs to put
     * us on its fast path. Falls back to common rates on phones that don't report them.
     */
    private fun pickConfig(): AudioConfig? {
        val nativeRate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
        val burst = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
            ?.toIntOrNull()?.takeIf { it > 0 } ?: DEFAULT_BURST
        for (rate in listOfNotNull(nativeRate, 48000, 44100).distinct()) {
            val inBytes = AudioRecord.getMinBufferSize(rate, CHANNEL_IN, ENCODING)
            val outBytes = AudioTrack.getMinBufferSize(rate, CHANNEL_OUT, ENCODING)
            if (inBytes > 0 && outBytes > 0) return AudioConfig(rate, burst, inBytes, outBytes)
        }
        return null
    }

    /** Points recording and playback at the chosen devices, or lets Android pick if they aren't connected. */
    private fun route(input: AudioRecord, output: AudioTrack) {
        rerouteNeeded = false
        val inDevice = AudioDevices.input.current(this)
        val outDevice = AudioDevices.output.current(this)
        Log.d(TAG, "Routing ${describe(inDevice)} -> ${describe(outDevice)}")
        setCommunicationMode(inDevice?.takeIf(AudioDevices::needsCommunicationMode))
        input.preferredDevice = inDevice
        output.preferredDevice = outDevice
    }

    private fun describe(device: AudioDeviceInfo?) =
        device?.let { "${it.productName} (type ${it.type})" } ?: "default"

    /**
     * Bluetooth headset mics are only open during "calls", so switch the phone into
     * communication mode for them, and back to normal for everything else.
     */
    @Suppress("DEPRECATION")
    private fun setCommunicationMode(bluetoothMic: AudioDeviceInfo?) {
        if (bluetoothMic != null) {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.setCommunicationDevice(bluetoothMic)
            } else {
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
            }
            communicationModeOn = true
        } else if (communicationModeOn) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                audioManager.isBluetoothScoOn = false
                audioManager.stopBluetoothSco()
            }
            audioManager.mode = AudioManager.MODE_NORMAL
            communicationModeOn = false
        }
    }

    private fun stopSelfOnMainThread() {
        ContextCompat.getMainExecutor(this).execute { stopSelf() }
    }

    /**
     * Prefers the input meant for live performance, which skips the processing that adds
     * delay. Not every phone can open every source, so falls back until one works.
     */
    // Permission is checked in onStartCommand before the loop starts.
    @Suppress("MissingPermission")
    private fun createAudioRecord(rate: Int, bufferBytes: Int): AudioRecord? {
        val sources = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaRecorder.AudioSource.VOICE_PERFORMANCE)
            add(MediaRecorder.AudioSource.MIC)
        }
        for (source in sources) {
            val record = runCatching {
                AudioRecord.Builder()
                    .setAudioSource(source)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(ENCODING)
                            .setChannelMask(CHANNEL_IN)
                            .setSampleRate(rate)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferBytes)
                    .build()
            }.getOrNull()
            if (record?.state == AudioRecord.STATE_INITIALIZED) {
                Log.d(TAG, "Recording from source $source")
                return record
            }
            record?.release()
        }
        return null
    }

    private fun createAudioTrack(rate: Int, bufferSize: Int): AudioTrack {
        val builder = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(ENCODING)
                    .setChannelMask(CHANNEL_OUT)
                    .setSampleRate(rate)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        }
        return builder.build()
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }

        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, MicrophoneService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic_notification)
            .setContentTitle(getString(R.string.mic_active))
            .setContentText(getString(R.string.cancel_mic))
            .setContentIntent(stopIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val TAG = "Microphone"
        private const val ACTION_STOP = "net.bitplane.android.microphone.STOP"
        private const val CHANNEL_ID = "microphone_channel_id"
        private const val NOTIFICATION_ID = 1

        // Frames per read and write when the phone doesn't say what its fast path uses.
        private const val DEFAULT_BURST = 256
        // Mono is the only input channel config every device is guaranteed to support.
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

        private val _active = MutableStateFlow(false)

        /** Whether the mic is currently live. Process-scoped, so it can't go stale after a kill. */
        val active: StateFlow<Boolean> = _active.asStateFlow()

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, MicrophoneService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MicrophoneService::class.java))
        }
    }
}
