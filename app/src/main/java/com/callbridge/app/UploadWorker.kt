package com.callbridge.app

import android.content.Context
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
            file.delete() // Clean up local file after successful upload
            Result.success()
        } catch (e: Exception) {
            Result.retry() // Will retry with exponential backoff
        }
    }

    private fun uploadToRailway(
        file: File,
        agentId: String,
        duration: Long,
        timestamp: Long
    ) {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS) // Large file upload
            .readTimeout(30, TimeUnit.SECONDS)
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
            .url("https://spectacular-possibility-production-c0cf.up.railway.app/upload-recording")
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Upload failed: ${response.code}")
        }
    }
}
