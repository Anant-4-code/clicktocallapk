package com.callbridge.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.telephony.TelephonyManager

class CallMonitorService : Service() {

    private val callReceiver = CallReceiver()
    private lateinit var builtInWatcher: BuiltInRecorderWatcher

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, buildNotification())

        val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        registerReceiver(callReceiver, filter)

        // Watch built-in recorder folders (Samsung, OPPO, Vivo, Xiaomi, etc.)
        builtInWatcher = BuiltInRecorderWatcher(this)
        builtInWatcher.start()
        Thread {
            builtInWatcher.scanExisting()
        }.start()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(callReceiver)
        builtInWatcher.stop()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val channelId = "callbridge_monitor"
        val channel = NotificationChannel(
            channelId, "Call Monitor", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        return Notification.Builder(this, channelId)
            .setContentTitle("CallBridge Active")
            .setContentText("Monitoring calls...")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .build()
    }
}
