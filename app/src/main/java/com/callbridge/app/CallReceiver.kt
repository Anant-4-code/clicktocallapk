package com.callbridge.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

class CallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.e("CallBridge_DEBUG", "=== RECEIVER FIRED === state: ${intent.getStringExtra(TelephonyManager.EXTRA_STATE)}")
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        if (state == TelephonyManager.EXTRA_STATE_RINGING && !incomingNumber.isNullOrEmpty()) {
            context.getSharedPreferences("callbridge", Context.MODE_PRIVATE)
                .edit().putString("lastCallNumber", incomingNumber).apply()
        }

        when (state) {
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (isOppo()) {
                    // OPPO stores recordings in protected system folder; record ourselves.
                    Log.d("CallBridge_DEBUG", "OPPO detected - using MediaRecorder fallback")
                    CallRecordingService.startRecording(context)
                } else {
                    Log.d("CallBridge_DEBUG", "Call connected - built-in recorder active")
                }
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (isOppo()) {
                    CallRecordingService.stopAndUpload(context)
                } else {
                    Log.d("CallBridge_DEBUG", "Call ended - watcher will detect new file")
                }
            }
        }
    }

    private fun isOppo(): Boolean {
        val brand = android.os.Build.BRAND.lowercase()
        val manufacturer = android.os.Build.MANUFACTURER.lowercase()
        return brand.contains("oppo") || manufacturer.contains("oppo") ||
            brand.contains("realme") || manufacturer.contains("realme")
    }
}
