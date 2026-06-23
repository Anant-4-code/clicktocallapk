package com.callbridge.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Restarts the ntfy listener after phone reboot. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val validActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.LOCKED_BOOT_COMPLETED",
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )
        if (intent.action !in validActions) return
        ServiceStarter.startListener(context)

        // Start CallMonitorService for call recording
        val serviceIntent = Intent(context, CallMonitorService::class.java)
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
