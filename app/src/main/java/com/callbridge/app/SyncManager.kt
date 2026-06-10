package com.callbridge.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

object SyncManager {

    private const val TAG = "SyncManager"
    private const val RAILWAY_URL =
        "https://spectacular-possibility-production-c0cf.up.railway.app/upload-recording"
    private const val PREFS_SYNCED = "synced_files"

    data class SyncResult(val total: Int, val uploaded: Int, val failed: Int)

    suspend fun syncAll(context: Context): SyncResult = withContext(Dispatchers.IO) {
        val agentId = context.getSharedPreferences("callbridge", Context.MODE_PRIVATE)
            .getString("agentId", "Unknown") ?: "Unknown"

        val syncedPrefs = context.getSharedPreferences(PREFS_SYNCED, Context.MODE_PRIVATE)

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        val audioExtensions = setOf(
            // Lossy
            "mp3", "aac", "m4a", "ogg", "opus", "wma", "amr", "3gp", "mp2",
            // Lossless
            "wav", "flac", "alac", "aiff", "ape",
            // Telephony
            "gsm", "vox", "au",
            // Containers
            "mp4"
        )
        val recordings = context.filesDir.listFiles { f ->
            f.extension.lowercase() in audioExtensions && !syncedPrefs.getBoolean(f.name, false)
        } ?: emptyArray()

        Log.d(TAG, "Found ${recordings.size} unsynced recording(s) for $agentId")

        var uploaded = 0
        var failed = 0

        for (file in recordings) {
            try {
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("agentId", agentId)
                    .addFormDataPart("duration", estimateDuration(file).toString())
                    .addFormDataPart("timestamp", file.lastModified().toString())
                    .addFormDataPart(
                        "audio", file.name,
                        file.asRequestBody(getMimeType(file.extension).toMediaType())
                    )
                    .build()

                val request = Request.Builder()
                    .url(RAILWAY_URL)
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    syncedPrefs.edit().putBoolean(file.name, true).apply()
                    uploaded++
                    Log.d(TAG, "Synced: ${file.name}")
                } else {
                    Log.e(TAG, "Server rejected ${file.name}: ${response.code}")
                    failed++
                }
                response.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing ${file.name}: ${e.message}")
                failed++
            }
        }

        SyncResult(total = recordings.size, uploaded = uploaded, failed = failed)
    }

    // ~16KB/sec at 128kbps AAC
    private fun estimateDuration(file: File): Long = file.length() / 16000

    private fun getMimeType(extension: String): String = when (extension.lowercase()) {
        "mp3", "mp2"        -> "audio/mpeg"
        "aac"               -> "audio/aac"
        "m4a", "mp4", "alac" -> "audio/mp4"
        "ogg"               -> "audio/ogg"
        "opus"              -> "audio/opus"
        "wma"               -> "audio/x-ms-wma"
        "amr"               -> "audio/amr"
        "3gp"               -> "audio/3gpp"
        "wav"               -> "audio/wav"
        "flac"              -> "audio/flac"
        "aiff"              -> "audio/aiff"
        "ape"               -> "audio/x-ape"
        "gsm"               -> "audio/gsm"
        "vox"               -> "audio/vox"
        "au"                -> "audio/basic"
        else                -> "audio/octet-stream"
    }
}
