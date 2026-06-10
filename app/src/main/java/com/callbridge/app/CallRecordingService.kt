package com.callbridge.app

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import android.media.MediaRecorder

object CallRecordingService {

    private const val TAG = "CallRecording"
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTime: Long = 0L

    fun startRecording(context: Context) {
        if (recorder != null) return

        val timestamp = SimpleDateFormat("ddMMMyyyy_HHmm", Locale.getDefault()).format(Date())
        val phoneNumber = context.getSharedPreferences("callbridge", Context.MODE_PRIVATE)
            .getString("lastCallNumber", "unknown") ?: "unknown"
        val sanitized = phoneNumber.replace(Regex("[^0-9+]"), "")
        val fileName = "${sanitized}_${timestamp}.m4a"
        outputFile = File(context.filesDir, fileName)
        startTime = System.currentTimeMillis()

        val sources = listOf(
            MediaRecorder.AudioSource.VOICE_CALL,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC
        )

        for (source in sources) {
            val sourceName = when (source) {
                MediaRecorder.AudioSource.VOICE_CALL -> "VOICE_CALL"
                MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "VOICE_COMMUNICATION"
                MediaRecorder.AudioSource.MIC -> "MIC"
                else -> "UNKNOWN"
            }
            Log.d(TAG, "Trying audio source: $sourceName")

            val mr = MediaRecorder()  // ← create a FRESH instance each attempt
            try {
                mr.setAudioSource(source)
                mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                mr.setAudioSamplingRate(44100)
                mr.setAudioEncodingBitRate(128000)
                mr.setOutputFile(outputFile!!.absolutePath)
                mr.prepare()
                mr.start()
                recorder = mr  // only assign if fully started
                Log.i(TAG, "SUCCESS: Recording started with $sourceName")
                return
            } catch (e: Exception) {
                Log.w(TAG, "FAILED: $sourceName - ${e.message}")
                mr.release()  // release THIS instance, not the shared var
            }
        }
        Log.e(TAG, "CRITICAL: All audio sources failed - cannot record call")
        // all sources failed — recorder stays null, no crash
    }

    fun stopAndUpload(context: Context) {
        recorder?.apply {
            try { stop() } catch (e: Exception) { /* ignore if call too short */ }
            release()
        }
        recorder = null

        val duration = (System.currentTimeMillis() - startTime) / 1000

        outputFile?.let { file ->
            if (file.exists() && file.length() > 1000) {
                // File is valid — queue upload
                enqueueUpload(context, file, duration)
            }
        }
        outputFile = null
    }

    fun enqueueUpload(context: Context, file: File, duration: Long) {
        val data = workDataOf(
            "filePath" to file.absolutePath,
            "duration" to duration,
            "agentId" to AgentPrefs.getId(context),
            "timestamp" to System.currentTimeMillis()
        )

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(data)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30, TimeUnit.SECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }
}
