package com.callbridge.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class WatchdogReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        ServiceWatchdog.schedule(context)

        if (!NtfyListenerService.isRunning) {
            Log.d("CallBridge", "Watchdog: listener dead — restarting")
            ServiceStarter.startListener(context)
        }
    }
}
