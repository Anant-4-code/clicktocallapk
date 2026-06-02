package com.callbridge.app

import android.app.Application

/** Starts the listener when the process starts (app not required in foreground). */
class CallBridgeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val topic = getSharedPreferences("callbridge", MODE_PRIVATE)
            .getString("ntfy_topic", null)
            ?.takeIf { it.isNotEmpty() } ?: return
        ServiceStarter.startListener(this)
    }
}
