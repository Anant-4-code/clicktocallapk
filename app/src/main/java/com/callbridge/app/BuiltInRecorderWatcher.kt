package com.callbridge.app

import android.content.Context
import android.os.Build
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File

class BuiltInRecorderWatcher(private val context: Context) {

    private val TAG = "BuiltInWatcher"
    private val handler = Handler(Looper.getMainLooper())
    private val observers = mutableListOf<FileObserver>()

    private val rootPaths = listOf(
        "/storage/emulated/0/Recordings/Record/Call",   // Vivo FuntouchOS — CONFIRMED
        "/storage/emulated/0/Recordings/Record",        // Vivo root
        "/storage/emulated/0/Recordings",               // Samsung / Realme / generic
        "/storage/emulated/0/Record",                   // Vivo older versions
        "/storage/emulated/0/ColorOS",                  // OPPO ColorOS
        "/storage/emulated/0/MIUI/sound_recorder",      // Xiaomi MIUI
        "/storage/emulated/0/MIUI/sound_recorder/call_rec",
        "/storage/emulated/0/CallRecordings",           // OnePlus
        "/storage/emulated/0/PhoneRecord",              // Huawei
        "/storage/emulated/0/call_recordings",          // Generic
        "/storage/emulated/0/Music/CallRecordings"      // Samsung variant
    )

    private val audioExtensions = setOf(
        "mp3", "aac", "m4a", "amr", "wav", "opus", "ogg", "3gp", "mp4"
    )

    fun start() {
        stop()
        for (path in rootPaths) {
            val dir = File(path)
            if (dir.exists()) watchDirRecursive(dir)
            else scheduleRetryWatch(dir)
        }
        Log.i(TAG, "Watcher started on ${observers.size} path(s)")
    }

    private fun watchDirRecursive(dir: File) {
        if (!dir.exists() || !dir.isDirectory) return
        watchDir(dir)
        dir.listFiles()?.filter { it.isDirectory }?.forEach { watchDirRecursive(it) }
    }

    @Suppress("DEPRECATION")
    private fun watchDir(dir: File) {
        Log.i(TAG, "Watching: ${dir.absolutePath}")

        val observer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ — use File-based constructor (path-based deprecated)
            object : FileObserver(dir, CLOSE_WRITE or MOVED_TO or CREATE) {
                override fun onEvent(event: Int, fileName: String?) = handleEvent(dir, event, fileName)
            }
        } else {
            object : FileObserver(dir.absolutePath, CLOSE_WRITE or MOVED_TO or CREATE) {
                override fun onEvent(event: Int, fileName: String?) = handleEvent(dir, event, fileName)
            }
        }

        observer.startWatching()
        observers.add(observer)
    }

    private fun handleEvent(dir: File, event: Int, fileName: String?) {
        if (fileName == null) return
        val file = File(dir, fileName)
        val eventType = event and FileObserver.ALL_EVENTS

        if (eventType == FileObserver.CREATE && file.isDirectory) {
            Log.i(TAG, "New subfolder: ${file.absolutePath}")
            handler.postDelayed({ watchDirRecursive(file) }, 500)
            return
        }

        val ext = fileName.substringAfterLast(".", "").lowercase()
        if (ext !in audioExtensions) return

        Log.i(TAG, "Recording detected: $fileName (${file.length()} bytes)")

        // Vivo writes files slowly — wait 8s then check, retry up to 3 times
        scheduleUploadWithRetry(file, attemptsLeft = 3)
    }

    private fun scheduleUploadWithRetry(file: File, attemptsLeft: Int, delayMs: Long = 8000) {
        handler.postDelayed({
            when {
                !file.exists() -> {
                    Log.w(TAG, "File disappeared: ${file.name}")
                }
                file.length() < 5000 -> {
                    if (attemptsLeft > 1) {
                        Log.w(TAG, "File too small (${file.length()}b), retrying: ${file.name}")
                        scheduleUploadWithRetry(file, attemptsLeft - 1, 5000)
                    } else {
                        Log.w(TAG, "Giving up on small file: ${file.name}")
                    }
                }
                else -> {
                    val syncedPrefs = context.getSharedPreferences("synced_files", Context.MODE_PRIVATE)
                    if (!syncedPrefs.getBoolean(file.name, false)) {
                        syncedPrefs.edit().putBoolean(file.name, true).apply()
                        val duration = file.length() / 16000
                        CallRecordingService.enqueueUpload(context, file, duration)
                        Log.i(TAG, "Queued: ${file.name} (${file.length()} bytes)")
                    } else {
                        Log.d(TAG, "Already queued: ${file.name}")
                    }
                }
            }
        }, delayMs)
    }

    // Keep retrying non-existent paths every 30s — Vivo creates folders lazily
    private fun scheduleRetryWatch(dir: File) {
        handler.postDelayed({
            if (dir.exists()) {
                Log.i(TAG, "Path appeared: ${dir.absolutePath}")
                watchDirRecursive(dir)
            } else {
                scheduleRetryWatch(dir)
            }
        }, 30_000)
    }

    fun stop() {
        observers.forEach { it.stopWatching() }
        observers.clear()
        handler.removeCallbacksAndMessages(null)
        Log.i(TAG, "Watcher stopped")
    }

    fun scanExisting() {
        val request = androidx.work.OneTimeWorkRequestBuilder<BulkSyncWorker>()
            .setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .build()
            )
            .build()
        androidx.work.WorkManager.getInstance(context)
            .enqueueUniqueWork("bulk_sync",
                androidx.work.ExistingWorkPolicy.KEEP, request)
        Log.i(TAG, "Bulk sync queued")
    }
}
