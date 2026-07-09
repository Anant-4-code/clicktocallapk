package com.callbridge.app

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class UploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val filePath = inputData.getString("filePath") ?: return Result.failure()
        val agentId = inputData.getString("agentId") ?: return Result.failure()
        val duration = inputData.getLong("duration", 0)
        val timestamp = inputData.getLong("timestamp", System.currentTimeMillis())

        val file = File(filePath)
        if (!file.exists()) return Result.failure()

        return try {
            uploadToRailway(file, agentId, duration, timestamp)
            applicationContext
                .getSharedPreferences("synced_files", Context.MODE_PRIVATE)
                .edit().putBoolean(file.name, true).apply()
            Result.success()
        } catch (e: Exception) {
            Log.e("UploadWorker", "Upload failed for ${file.name}: ${e.message}")
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }

    private fun uploadToRailway(
        file: File,
        agentId: String,
        duration: Long,
        timestamp: Long
    ) {
        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(300, TimeUnit.SECONDS)  // 5 min — Vivo files can be large on slow networks
            .readTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        val ext = file.extension.lowercase()
        val mimeType = when (ext) {
            "mp3", "mp2"         -> "audio/mpeg"
            "aac"                -> "audio/aac"
            "m4a", "mp4", "alac" -> "audio/mp4"
            "ogg"                -> "audio/ogg"
            "opus"               -> "audio/opus"
            "amr"                -> "audio/amr"
            "3gp"                -> "audio/3gpp"
            "wav"                -> "audio/wav"
            "flac"               -> "audio/flac"
            else                 -> "audio/octet-stream"
        }

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "audio",
                file.name,                               // actual filename with phone number
                file.asRequestBody(mimeType.toMediaType())
            )
            .addFormDataPart("agentId", agentId)
            .addFormDataPart("fileName", file.name)      // explicit filename field for server
            .addFormDataPart("duration", duration.toString())
            .addFormDataPart("timestamp", timestamp.toString())
            .build()

        val request = Request.Builder()
            .url("https://callbridgeserver-production.up.railway.app/upload-recording")
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Upload failed: ${response.code}")
        }
    }
}
