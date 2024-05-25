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
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.nio.ByteBuffer

class MicrophoneService : Service(), OnSharedPreferenceChangeListener {
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
        Log.d(APP_TAG, "Creating mic service")

        // notification service
        mNotificationManager = NotificationManagerCompat.from(applicationContext)
        mBroadcastReceiver = MicrophoneReceiver()

        // create input and output streams
        mInBufferSize = AudioRecord.getMinBufferSize(
            mSampleRate,
            AudioFormat.CHANNEL_CONFIGURATION_STEREO,
            mFormat
        )
        val mOutBufferSize = AudioTrack.getMinBufferSize(
            mSampleRate,
            AudioFormat.CHANNEL_CONFIGURATION_STEREO,
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
                AudioFormat.CHANNEL_CONFIGURATION_STEREO,
                mFormat,
                mInBufferSize
            )
        }
        mAudioOutput = AudioTrack(
            AudioManager.STREAM_MUSIC,
            mSampleRate,
            AudioFormat.CHANNEL_CONFIGURATION_STEREO,
            mFormat,
            mOutBufferSize,
            AudioTrack.MODE_STREAM
        )

        // listen for preference changes
        mSharedPreferences = getSharedPreferences(APP_TAG, MODE_PRIVATE)
        mSharedPreferences.registerOnSharedPreferenceChangeListener(this)
        mActive = mSharedPreferences.getBoolean("active", false)

        if (mActive) record()
    }

    override fun onDestroy() {
        Log.d(APP_TAG, "Stopping mic service")

        val e = mSharedPreferences.edit()
        e.putBoolean("active", false)
        e.apply()

        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        mAudioInput!!.release()
        mAudioOutput!!.release()
    }

    @Deprecated("Deprecated in Java")
    override fun onStart(intent: Intent, startId: Int) {
        super.onStart(intent, startId)
        Log.d(APP_TAG, "Service sent intent")

        // if this is a stop request, cancel the recording
        if (intent.action != null) {
            if (intent.action == "net.bitplane.android.microphone.STOP") {
                Log.d(APP_TAG, "Cancelling recording via notification click")
                val e = mSharedPreferences.edit()
                e.putBoolean("active", false)
                e.apply()
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        // intercept the preference change.

        if (key != null && key != "active") return

        val bActive = sharedPreferences.getBoolean("active", false)

        Log.d(APP_TAG, "Mic state changing (from " + mActive + " to " + bActive + ")")

        if (bActive != mActive) {
            mActive = bActive

            if (mActive) record()

            if (!mActive) mNotificationManager.cancel(0)
        }
    }

    private fun record() {
        val t: Thread = object : Thread() {
            override fun run() {
                val cancelIntent = Intent()
                cancelIntent.setAction("net.bitplane.android.microphone.STOP")
                cancelIntent.setData(Uri.parse("null://null"))
                cancelIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                val pendingCancelIntent = PendingIntent.getService(
                    applicationContext,
                    0,
                    cancelIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )

                val builder =
                    NotificationCompat.Builder(applicationContext, "microphone_channel_id")
                    .setSmallIcon(R.drawable.ic_mic_notification)
                    .setContentTitle(getString(R.string.mic_active))
                    .setContentText(getString(R.string.cancel_mic))
                    .setWhen(System.currentTimeMillis())
                    .setContentIntent(pendingCancelIntent)
                    .setAutoCancel(true)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel =
                        NotificationChannel(
                            "microphone_channel_id",
                            "Microphone",
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
                    // allow the
                    registerReceiver(
                        mBroadcastReceiver,
                        IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                    )
                    Log.d(APP_TAG, "Entered record loop")
                    recordLoop()
                    Log.d(APP_TAG, "Record loop finished")
                }
            }

            private fun recordLoop() {
                if (mAudioOutput!!.state != AudioTrack.STATE_INITIALIZED || mAudioInput!!.state != AudioTrack.STATE_INITIALIZED) {
                    Log.d(APP_TAG, "Can't start. Race condition?")
                } else {
                    try {
                        try {
                            mAudioOutput!!.play()
                        } catch (e: Exception) {
                            Log.e(APP_TAG, "Failed to start playback")
                            return
                        }
                        try {
                            mAudioInput!!.startRecording()
                        } catch (e: Exception) {
                            Log.e(APP_TAG, "Failed to start recording")
                            mAudioOutput!!.stop()
                            return
                        }

                        try {
                            val bytes = ByteBuffer.allocateDirect(mInBufferSize)
                            var o = 0
                            val b = ByteArray(mInBufferSize)
                            while (mActive) {
                                o = mAudioInput!!.read(bytes, mInBufferSize)
                                bytes[b]
                                bytes.rewind()
                                mAudioOutput!!.write(b, 0, o)
                            }

                            Log.d(APP_TAG, "Finished recording")
                        } catch (e: Exception) {
                            Log.d(APP_TAG, "Error while recording, aborting.")
                        }

                        try {
                            mAudioOutput!!.stop()
                        } catch (e: Exception) {
                            Log.e(APP_TAG, "Can't stop playback")
                            mAudioInput!!.stop()
                            return
                        }
                        try {
                            mAudioInput!!.stop()
                        } catch (e: Exception) {
                            Log.e(APP_TAG, "Can't stop recording")
                            return
                        }
                    } catch (e: Exception) {
                        Log.d(APP_TAG, "Error somewhere in record loop.")
                    }
                }
                // cancel notification and receiver
                mNotificationManager.cancel(0)
                try {
                    unregisterReceiver(mBroadcastReceiver)
                } catch (e: IllegalArgumentException) {
                    Log.e(APP_TAG, "Receiver wasn't registered: $e")
                }
            }
        }

        t.start()
    }

    private class MicrophoneReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action != null && action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                val prefs = context.getSharedPreferences(APP_TAG, MODE_PRIVATE)
                val e = prefs.edit()
                e.putBoolean("active", false)
                e.apply()
            }
        }
    }

    companion object {
        private const val APP_TAG = "Microphone"
    }
}
