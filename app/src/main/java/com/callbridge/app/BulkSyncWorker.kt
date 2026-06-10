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

class BulkSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val TAG = "BulkSync"
    private val MAX_FILES = 150
    private val RAILWAY_URL = "https://spectacular-possibility-production-c0cf.up.railway.app"

    private val audioExtensions = setOf(
        "mp3", "aac", "m4a", "amr", "wav", "opus", "ogg", "3gp", "mp4"
    )

    private val rootPaths = listOf(
        "/storage/emulated/0/Recordings/Record/Call",               // Vivo confirmed
        "/storage/emulated/0/Recordings/Call",                      // Samsung
        "/storage/emulated/0/Recordings/Call/Call recording",       // Samsung subfolder variant
        "/storage/emulated/0/MIUI/sound_recorder/call_rec",         // Xiaomi
        "/storage/emulated/0/CallRecordings",                       // OnePlus
        "/storage/emulated/0/PhoneRecord",                          // Huawei
        "/storage/emulated/0/call_recordings"                       // fallback
    )

    override suspend fun doWork(): Result {
        val syncedPrefs = applicationContext.getSharedPreferences("synced_files", Context.MODE_PRIVATE)
        val agentId = applicationContext.getSharedPreferences("callbridge", Context.MODE_PRIVATE)
            .getString("agentId", "Unknown") ?: "Unknown"

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val alreadyOnDrive = fetchDriveFileNames(client, agentId)
        Log.i(TAG, "Drive already has ${alreadyOnDrive.size} files for $agentId")

        if (alreadyOnDrive.isNotEmpty()) {
            val editor = syncedPrefs.edit()
            alreadyOnDrive.forEach { name -> editor.putBoolean(name, true) }
            editor.apply()
        }

        fun collectFiles(dir: File): List<File> {
            if (!dir.exists()) return emptyList()
            val result = mutableListOf<File>()
            dir.listFiles()?.forEach { f ->
                if (f.isDirectory) result.addAll(collectFiles(f))
                else if (f.extension.lowercase() in audioExtensions) result.add(f)
            }
            return result
        }

        val allLocalFiles = rootPaths.flatMap { collectFiles(File(it)) }

        val filesToUpload = allLocalFiles
            .filter { !syncedPrefs.getBoolean(it.name, false) }
            .filter { !alreadyOnDrive.contains(it.name) }
            .sortedByDescending { it.lastModified() }
            .take(MAX_FILES)

        Log.i(TAG, "Total local: ${allLocalFiles.size}, uploading: ${filesToUpload.size}")

        if (filesToUpload.isEmpty()) {
            Log.i(TAG, "Nothing to upload")
            return Result.success()
        }

        var uploaded = 0
        var failed = 0

        for (file in filesToUpload) {
            try {
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
                    else                 -> "audio/octet-stream"
                }

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("audio", file.name,
                        file.asRequestBody(mimeType.toMediaType()))
                    .addFormDataPart("agentId", agentId)
                    .addFormDataPart("fileName", file.name)
                    .addFormDataPart("duration", (file.length() / 16000).toString())
                    .addFormDataPart("timestamp", file.lastModified().toString())
                    .build()

                val response = client.newCall(
                    Request.Builder().url("$RAILWAY_URL/upload-recording").post(requestBody).build()
                ).execute()

                if (response.isSuccessful) {
                    syncedPrefs.edit().putBoolean(file.name, true).apply()
                    uploaded++
                    Log.i(TAG, "Uploaded ($uploaded/${filesToUpload.size}): ${file.name}")
                } else {
                    failed++
                    Log.e(TAG, "Failed ${file.name}: ${response.code}")
                }
                response.close()
            } catch (e: Exception) {
                failed++
                Log.e(TAG, "Error ${file.name}: ${e.message}")
            }
        }

        Log.i(TAG, "Done: $uploaded uploaded, $failed failed")
        return if (failed == 0) Result.success() else Result.retry()
    }

    private fun fetchDriveFileNames(client: OkHttpClient, agentId: String): Set<String> {
        return try {
            val response = client.newCall(
                Request.Builder()
                    .url("$RAILWAY_URL/list-files?agentId=${android.net.Uri.encode(agentId)}")
                    .get()
                    .build()
            ).execute()
            if (!response.isSuccessful) return emptySet()
            val body = response.body?.string() ?: return emptySet()
            response.close()
            val arr = org.json.JSONArray(body)
            val names = mutableSetOf<String>()
            for (i in 0 until arr.length()) names.add(arr.getString(i))
            names
        } catch (e: Exception) {
            Log.w(TAG, "fetchDriveFileNames failed: ${e.message}")
            emptySet()
        }
    }
}
