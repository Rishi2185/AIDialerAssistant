package com.aicallshield.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.util.Log
import androidx.core.app.NotificationCompat
import com.aicallshield.AICallShieldApp
import com.aicallshield.R
import com.aicallshield.util.Constants
import java.io.ByteArrayOutputStream

/**
 * Foreground service for audio capture and processing during call screening.
 *
 * Captures microphone audio, encodes it, and provides it to the WebSocket
 * client for real-time transcription.
 *
 * Note: Due to Android restrictions, capturing the remote caller's audio
 * directly requires the app to be the default dialer. This service
 * captures the device microphone as a prototype implementation.
 */
class AudioProcessingService : Service() {

    companion object {
        private const val TAG = "AudioProcessing"
        const val ACTION_START = "com.aicallshield.action.START_AUDIO"
        const val ACTION_STOP = "com.aicallshield.action.STOP_AUDIO"
        const val EXTRA_CALL_ID = "call_id"

        @Volatile
        var isRecording: Boolean = false
            private set

        // Callback for delivering audio chunks
        var audioChunkListener: ((ByteArray) -> Unit)? = null
    }

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var callId: String? = null

    private val sampleRate = Constants.AUDIO_SAMPLE_RATE
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioEncoding = AudioFormat.ENCODING_PCM_16BIT

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                callId = intent.getStringExtra(EXTRA_CALL_ID)
                startForegroundService()
                startRecording()
            }
            ACTION_STOP -> {
                stopRecording()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundService() {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Constants.SCREENING_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(Constants.SCREENING_NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, AICallShieldApp.CHANNEL_SCREENING)
            .setContentTitle(getString(R.string.screening_notification_title))
            .setContentText(getString(R.string.screening_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun startRecording() {
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioEncoding)

        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid buffer size: $bufferSize")
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                channelConfig,
                audioEncoding,
                bufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                return
            }

            audioRecord?.startRecording()
            isRecording = true

            Log.i(TAG, "Audio recording started for call: $callId")

            recordingThread = Thread {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)

                val buffer = ByteArray(bufferSize)
                val audioChunkBuffer = ByteArrayOutputStream()
                var bytesCollected = 0
                val chunkSize = sampleRate * 2 // 1 second of audio for lower latency

                while (isRecording) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0

                    if (bytesRead > 0) {
                        audioChunkBuffer.write(buffer, 0, bytesRead)
                        bytesCollected += bytesRead

                        // Send chunk every ~1 second
                        if (bytesCollected >= chunkSize) {
                            val chunk = audioChunkBuffer.toByteArray()
                            audioChunkListener?.invoke(chunk)
                            audioChunkBuffer.reset()
                            bytesCollected = 0
                        }
                    }
                }
            }
            recordingThread?.start()

        } catch (e: SecurityException) {
            Log.e(TAG, "Audio recording permission denied", e)
        } catch (e: Exception) {
            Log.e(TAG, "Audio recording failed", e)
        }
    }

    private fun stopRecording() {
        isRecording = false

        try {
            recordingThread?.join(1000)
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            recordingThread = null
            Log.i(TAG, "Audio recording stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
    }
}
