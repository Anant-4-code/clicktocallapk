package com.callbridge.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Restarts the ntfy listener after phone reboot. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        ServiceStarter.startListener(context)

        // Start CallMonitorService for call recording
        val serviceIntent = Intent(context, CallMonitorService::class.java)
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
