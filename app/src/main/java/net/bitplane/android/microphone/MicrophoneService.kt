package net.bitplane.android.microphone

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import java.nio.ByteBuffer

class MicrophoneService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val mSampleRate = 44100
    private val mFormat = AudioFormat.ENCODING_PCM_16BIT
    private var mActive = false

    private lateinit var mSharedPreferences: SharedPreferences
    private var mAudioOutput: AudioTrack? = null
    private var mAudioInput: AudioRecord? = null
    private var mInBufferSize = 0
    private lateinit var mNotificationManager: NotificationManagerCompat
    private lateinit var mBroadcastReceiver: MicrophoneReceiver

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        Log.d(AppPreferences.APP_TAG, "Creating mic service")

        // notification service
        mNotificationManager = NotificationManagerCompat.from(applicationContext)
        mBroadcastReceiver = MicrophoneReceiver()

        // create input and output streams
        mInBufferSize = AudioRecord.getMinBufferSize(
            mSampleRate,
            AudioFormat.CHANNEL_IN_STEREO,
            mFormat
        )
        val mOutBufferSize = AudioTrack.getMinBufferSize(
            mSampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            mFormat
        )
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            mAudioInput = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                mSampleRate,
                AudioFormat.CHANNEL_IN_STEREO,
                mFormat,
                mInBufferSize
            )
        }
        mAudioOutput = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(mFormat)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .setSampleRate(mSampleRate)
                    .build()
            )
            .setBufferSizeInBytes(mOutBufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        // listen for preference changes
        mSharedPreferences = AppPreferences.prefs(this)
        mSharedPreferences.registerOnSharedPreferenceChangeListener(this)
        mActive = AppPreferences.isActive(mSharedPreferences)

        if (mActive) record()
    }

    override fun onDestroy() {
        Log.d(AppPreferences.APP_TAG, "Stopping mic service")

        AppPreferences.setActive(mSharedPreferences, false)

        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        mAudioInput?.release()
        mAudioOutput?.release()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(AppPreferences.APP_TAG, "Service sent intent")

        // if this is a stop request, cancel the recording
        if (intent?.action != null) {
            if (intent.action == ACTION_STOP) {
                Log.d(AppPreferences.APP_TAG, "Cancelling recording via notification click")
                AppPreferences.setActive(mSharedPreferences, false)
            }
        }

        return START_STICKY
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        // intercept the preference change.
        if (key != null && key != AppPreferences.KEY_ACTIVE) return
        val bActive = sharedPreferences.getBoolean(AppPreferences.KEY_ACTIVE, false)
        Log.d(AppPreferences.APP_TAG, "Mic state changing (from $mActive to $bActive)")

        if (bActive != mActive) {
            mActive = bActive
            if (mActive) record()
            if (!mActive) mNotificationManager.cancel(0)
        }
    }

    private fun record() {
        val t: Thread = object : Thread() {
            override fun run() {
                val cancelIntent = Intent(applicationContext, MicrophoneService::class.java).apply {
                    action = ACTION_STOP
                    data = "null://null".toUri()
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                val pendingCancelIntent = PendingIntent.getService(
                    applicationContext,
                    0,
                    cancelIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )

                val builder =
                    NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_mic_notification)
                        .setContentTitle(getString(R.string.mic_active))
                        .setContentText(getString(R.string.cancel_mic))
                        .setWhen(System.currentTimeMillis())
                        .setContentIntent(pendingCancelIntent)
                        .setAutoCancel(true)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel =
                        NotificationChannel(
                            CHANNEL_ID,
                            AppPreferences.APP_TAG,
                            NotificationManager.IMPORTANCE_DEFAULT
                        )
                    mNotificationManager.createNotificationChannel(channel)
                }

                if (ActivityCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    mNotificationManager.notify(0, builder.build())
                }

                if (ActivityCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    registerReceiver(
                        mBroadcastReceiver,
                        IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                    )
                    Log.d(AppPreferences.APP_TAG, "Entered record loop")
                    recordLoop()
                    Log.d(AppPreferences.APP_TAG, "Record loop finished")
                }
            }

            private fun recordLoop() {
                val audioOutput = mAudioOutput
                val audioInput = mAudioInput

                if (audioOutput == null || audioInput == null) {
                    Log.e(AppPreferences.APP_TAG, "Audio components are not ready")
                    return
                }

                if (audioOutput.state != AudioTrack.STATE_INITIALIZED || audioInput.state != AudioRecord.STATE_INITIALIZED) {
                    Log.d(AppPreferences.APP_TAG, "Can't start. Race condition?")
                    return
                }

                val directBuffer = ByteBuffer.allocateDirect(mInBufferSize)
                val byteArray = ByteArray(mInBufferSize)

                try {
                    audioOutput.play()
                    audioInput.startRecording()

                    while (mActive) {
                        val read = audioInput.read(directBuffer, mInBufferSize)
                        directBuffer[byteArray]
                        directBuffer.rewind()
                        audioOutput.write(byteArray, 0, read)
                    }

                    Log.d(AppPreferences.APP_TAG, "Finished recording")
                } catch (e: IllegalStateException) {
                    Log.e(AppPreferences.APP_TAG, "Failed during audio start/stop", e)
                } catch (e: Exception) {
                    Log.e(AppPreferences.APP_TAG, "Error while recording, aborting.", e)
                } finally {
                    try {
                        if (audioOutput.playState == AudioTrack.PLAYSTATE_PLAYING) {
                            audioOutput.stop()
                        }
                    } catch (e: IllegalStateException) {
                        Log.e(AppPreferences.APP_TAG, "Can't stop playback", e)
                    }
                    try {
                        if (audioInput.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                            audioInput.stop()
                        }
                    } catch (e: IllegalStateException) {
                        Log.e(AppPreferences.APP_TAG, "Can't stop recording", e)
                    }
                }

                // cancel notification and receiver
                mNotificationManager.cancel(0)
                try {
                    unregisterReceiver(mBroadcastReceiver)
                } catch (e: IllegalArgumentException) {
                    Log.e(AppPreferences.APP_TAG, "Receiver wasn't registered: $e")
                }
            }
        }

        t.start()
    }

    private class MicrophoneReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action != null && action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                AppPreferences.setActive(context, false)
            }
        }
    }

    companion object {
        private const val ACTION_STOP = "net.bitplane.android.microphone.STOP"
        private const val CHANNEL_ID = "microphone_channel_id"
    }
}
