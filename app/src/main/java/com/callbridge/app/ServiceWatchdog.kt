package com.callbridge.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log

object ServiceWatchdog {

    private const val TAG = "ServiceWatchdog"
    private const val REQUEST_CODE = 9001
    private const val INTERVAL_MS = 60 * 1000L // 1 minute — fast enough for Vivo/Oppo kill

    fun schedule(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, WatchdogReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAt = SystemClock.elapsedRealtime() + INTERVAL_MS

        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    // Android 12+ — check permission before setExact
                    if (alarm.canScheduleExactAlarms()) {
                        alarm.setExactAndAllowWhileIdle(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending
                        )
                    } else {
                        // Fall back to inexact — still fires within a few minutes
                        alarm.setAndAllowWhileIdle(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending
                        )
                    }
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    alarm.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending
                    )
                }
                else -> {
                    alarm.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending)
                }
            }
            Log.d(TAG, "Watchdog scheduled in ${INTERVAL_MS/1000}s")
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm denied — using inexact fallback")
            alarm.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending)
        } catch (e: Exception) {
            Log.e(TAG, "Alarm scheduling failed: ${e.message}")
        }
    }
}
