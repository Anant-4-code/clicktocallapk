package com.callbridge.app

import android.content.Context
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File

class BuiltInRecorderWatcher(private val context: Context) {

    private val TAG = "BuiltInWatcher"
    private val handler = Handler(Looper.getMainLooper())
    private val observers = mutableListOf<FileObserver>()

    // Root paths to watch; existing and newly-created subfolders are watched too.
    private val rootPaths = listOf(
        "/storage/emulated/0/Recordings",           // Samsung / Realme / generic
        "/storage/emulated/0/ColorOS",              // OPPO ColorOS root
        "/storage/emulated/0/Recordings/Record",    // Vivo FuntouchOS (confirmed)
        "/storage/emulated/0/Record",               // Vivo older versions
        "/storage/emulated/0/MIUI/sound_recorder",  // Xiaomi MIUI root
        "/storage/emulated/0/CallRecordings",       // OnePlus OxygenOS
        "/storage/emulated/0/PhoneRecord",          // Huawei
        "/storage/emulated/0/call_recordings",      // Generic fallback
        "/storage/emulated/0/Music/CallRecordings"  // Some Samsung variants
    )

    private val audioExtensions = setOf(
        "mp3", "aac", "m4a", "amr", "wav", "opus", "ogg", "3gp", "mp4"
    )

    fun start() {
        stop()

        for (path in rootPaths) {
            val dir = File(path)
            if (dir.exists()) watchDirRecursive(dir)
            watchForNewSubfolders(dir)
        }

        Log.i(TAG, "Watcher started. Monitoring ${observers.size} path(s)")
    }

    private fun watchDirRecursive(dir: File) {
        if (!dir.exists() || !dir.isDirectory) return

        watchDir(dir)
        dir.listFiles()?.filter { it.isDirectory }?.forEach { watchDirRecursive(it) }
    }

    private fun watchDir(dir: File) {
        Log.i(TAG, "Watching: ${dir.absolutePath}")

        val observer = object : FileObserver(
            dir.absolutePath,
            FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO or FileObserver.CREATE
        ) {
            override fun onEvent(event: Int, fileName: String?) {
                if (fileName == null) return

                val file = File(dir, fileName)
                val eventType = event and FileObserver.ALL_EVENTS

                if (eventType == FileObserver.CREATE && file.isDirectory) {
                    Log.i(TAG, "New subfolder detected, watching: ${file.absolutePath}")
                    handler.postDelayed({ watchDirRecursive(file) }, 500)
                    return
                }

                val ext = fileName.substringAfterLast(".", "").lowercase()
                if (ext !in audioExtensions) return

                Log.i(TAG, "New recording detected: $fileName in ${dir.absolutePath}")

                handler.postDelayed({
                    if (file.exists() && file.length() > 5000) {
                        val syncedPrefs = context.getSharedPreferences("synced_files", Context.MODE_PRIVATE)
                        if (!syncedPrefs.getBoolean(file.name, false)) {
                            syncedPrefs.edit().putBoolean(file.name, true).apply()
                            val duration = file.length() / 16000
                            CallRecordingService.enqueueUpload(context, file, duration)
                            Log.i(TAG, "Queued for upload: ${file.name} (${file.length()} bytes)")
                        } else {
                            Log.d(TAG, "Already queued, skipping: ${file.name}")
                        }
                    } else {
                        Log.w(TAG, "File too small or missing after delay: ${file.name}")
                    }
                }, 3000)
            }
        }

        observer.startWatching()
        observers.add(observer)
    }

    private fun watchForNewSubfolders(dir: File) {
        if (dir.exists()) return

        Log.d(TAG, "Path not found yet, will retry: ${dir.absolutePath}")
        handler.postDelayed({
            if (dir.exists()) {
                Log.i(TAG, "Path appeared, now watching: ${dir.absolutePath}")
                watchDirRecursive(dir)
            } else {
                watchForNewSubfolders(dir)
            }
        }, 10_000)
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
            .enqueueUniqueWork(
                "bulk_sync",
                androidx.work.ExistingWorkPolicy.KEEP,
                request
            )

        Log.i(TAG, "Queued bulk sync worker")
    }
}
