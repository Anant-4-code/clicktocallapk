package com.callbridge.app

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class NtfyListenerService : Service() {

    companion object {
        const val CHANNEL_ID = "callbridge_silent"
        const val NOTIF_ID = 1
        const val TAG = "NtfyListener"

        @Volatile
        var isRunning = false
    }

    private var listenerJob: Job? = null
    private var ntfyTopic: String = ""
    private var wakeLock: PowerManager.WakeLock? = null

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createSilentChannel()
        acquirePersistentWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ntfyTopic = intent?.getStringExtra("ntfy_topic")
            ?: getSharedPreferences("callbridge", MODE_PRIVATE).getString("ntfy_topic", "") ?: ""

        if (ntfyTopic.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            startForeground(NOTIF_ID, buildSilentNotification())
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed: ${e.message}", e)
            stopSelf()
            return START_NOT_STICKY
        }

        ServiceWatchdog.schedule(this)

        startListening()

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // User swiped app away — keep listening
        Log.d(TAG, "Task removed — restarting listener")
        ServiceStarter.startListener(applicationContext)
        super.onTaskRemoved(rootIntent)
    }

    private fun acquirePersistentWakeLock() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CallBridge::Listener").apply {
                setReferenceCounted(false)
                // 12-hour timeout — Vivo/Oppo ignore indefinite locks but respect timed ones
                acquire(12 * 60 * 60 * 1000L)
            }
            Log.d(TAG, "WakeLock acquired")
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock unavailable: ${e.message}")
        }
    }

    private fun startListening() {
        listenerJob?.cancel()
        listenerJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    listenToStream()
                } catch (e: Exception) {
                    Log.e(TAG, "Stream error: ${e.message}. Reconnecting in 5s…")
                    delay(5_000)
                }
            }
        }
    }

    private fun ntfyBaseUrl(): String {
        val base = getSharedPreferences("callbridge", MODE_PRIVATE)
            .getString("ntfy_base_url", "https://ntfy-production-1e24.up.railway.app")
            ?.trim()
            ?.trimEnd('/')
            .orEmpty()
        return base.ifEmpty { "https://ntfy-production-1e24.up.railway.app" }
    }

    private fun listenToStream() {
        val agentId = getSharedPreferences("callbridge", MODE_PRIVATE)
            .getString("agent_id", "")?.lowercase() ?: ""

        val topicUrl = if (agentId.isNotEmpty()) {
            "${ntfyBaseUrl()}/$ntfyTopic,callbridge-$agentId/json"
        } else {
            "${ntfyBaseUrl()}/$ntfyTopic/json"
        }

        val request = Request.Builder()
            .url(topicUrl)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("ntfy returned ${response.code}")
            }

            val source = response.body?.source() ?: throw Exception("Empty body")
            val reader = BufferedReader(InputStreamReader(source.inputStream()))

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let { processNtfyMessage(it) }
            }
        }
    }

    private fun processNtfyMessage(rawLine: String) {
        if (rawLine.isBlank()) return

        try {
            val json = JSONObject(rawLine)
            if (json.optString("event", "message") != "message") return

            val topic = json.optString("topic", "")
            val agentId = getSharedPreferences("callbridge", MODE_PRIVATE)
                .getString("agent_id", "")?.lowercase() ?: ""

            if (agentId.isNotEmpty() && topic == "callbridge-$agentId") {
                // Analysis results topic notification
                val title = json.optString("title", "Call Report")
                val message = json.optString("message", "")
                val priorityStr = json.optString("priority", "default")
                showAnalysisNotification(title, message, priorityStr)
                return
            }

            val number = resolvePhoneNumber(json.optString("message", ""))
            if (number == null) return

            Log.d(TAG, "Call request OK")
            ServiceStarter.launchCallTrampoline(this, number)
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}")
        }
    }

    private fun showAnalysisNotification(title: String, message: String, priorityStr: String) {
        val channelId = "callbridge_alerts"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "CallBridge Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Analysis reports and alerts"
                enableLights(true)
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }

        val priority = if (priorityStr == "high" || priorityStr == "5" || priorityStr == "4" || priorityStr == "urgent") {
            NotificationCompat.PRIORITY_MAX
        } else {
            NotificationCompat.PRIORITY_HIGH
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(priority)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val notifId = (System.currentTimeMillis() % 100000).toInt() + 100
        nm.notify(notifId, notification)
    }

    private fun resolvePhoneNumber(messageText: String): String? {
        val trimmed = messageText.trim()
        if (trimmed.isEmpty()) return null

        // Never dial random digits from encrypted blobs
        if (CallCrypto.isEncrypted(trimmed)) {
            val agentId = getSharedPreferences("callbridge", MODE_PRIVATE)
                .getString("agent_id", null)
            if (agentId.isNullOrBlank()) {
                notifyReRegister()
                return null
            }
            val secret = SecretDeriver.derive(agentId)
            val decrypted = CallCrypto.decryptOrNull(trimmed, secret)
            if (decrypted == null) {
                notifyReRegister()
                return null
            }
            return PhoneNumbers.rejectIfInvalid(decrypted, "decrypted")
                ?: run {
                    notifyReRegister()
                    null
                }
        }

        val legacy = extractLegacyPlainNumber(trimmed) ?: return null
        return PhoneNumbers.rejectIfInvalid(legacy, "legacy")
    }

    private fun notifyReRegister() {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                applicationContext,
                "CallBridge: tap RE-REGISTER in app (encryption key mismatch)",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /** Plain numeric or JSON only — never parse enc:v1: strings. */
    private fun extractLegacyPlainNumber(messageText: String): String? {
        if (CallCrypto.isEncrypted(messageText)) return null

        if (messageText.startsWith("{")) {
            return try {
                val payload = JSONObject(messageText)
                payload.optString("number", "")
                    .ifEmpty { payload.optString("message", "") }
                    .trim()
                    .takeIf { it.isNotEmpty() }
            } catch (_: Exception) {
                null
            }
        }

        val digits = messageText.replace(Regex("[^0-9+]"), "")
        return digits.takeIf { it.length in 7..15 }
    }

    private fun createSilentChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "CallBridge background",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildSilentNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(" ")
            .setContentText(" ")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setOngoing(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        listenerJob?.cancel()
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) { }
        wakeLock = null

        // System killed us — try to come back
        ServiceStarter.startListener(applicationContext)
        super.onDestroy()
    }
}
