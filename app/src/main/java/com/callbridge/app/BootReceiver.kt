package com.callbridge.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the ntfy listener after phone reboot. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        ServiceStarter.startListener(context)
    }
}
