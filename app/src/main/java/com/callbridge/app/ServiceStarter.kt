package com.callbridge.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object ServiceStarter {

    private const val TAG = "ServiceStarter"

    fun startListener(context: Context) {
        val topic = context.getSharedPreferences("callbridge", Context.MODE_PRIVATE)
            .getString("ntfy_topic", null)
            ?.takeIf { it.isNotEmpty() } ?: return

        try {
            val intent = Intent(context, NtfyListenerService::class.java).apply {
                putExtra("ntfy_topic", topic)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            ServiceWatchdog.schedule(context)
            Log.d(TAG, "Listener start requested")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listener", e)
        }
    }

    fun launchCallTrampoline(context: Context, number: String) {
        val intent = Intent(context, CallTrampolineActivity::class.java).apply {
            putExtra(CallTrampolineActivity.EXTRA_NUMBER, number)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
