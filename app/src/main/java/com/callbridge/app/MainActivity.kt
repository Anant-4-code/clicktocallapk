package com.callbridge.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID

const val WEBHOOK_SERVER = "https://redirectorhook-production.up.railway.app"

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CallBridge"
        private const val REQ_PERMISSIONS = 1001
    }

    private val prefs by lazy { getSharedPreferences("callbridge", MODE_PRIVATE) }
    private val client = OkHttpClient()
    private var pendingNtfyTopic: String? = null

    private fun lang(): AppStrings.Lang = AppStrings.fromCode(prefs.getString("lang", "en"))
    private fun s() = AppStrings.pack(lang())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (prefs.getString("agent_id", null) != null) {
            showStatusScreen()
        } else {
            showPairingScreen()
        }
    }

    override fun onResume() {
        super.onResume()
        syncRegistrationWithServer()
        requestBatteryExemptionIfNeeded()
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISSIONS) {
            val topic = pendingNtfyTopic
            pendingNtfyTopic = null
            if (topic != null) {
                launchNtfyService(topic)
                moveTaskToBack(true)
            }
        }
    }

    private fun addLanguageBar(parent: LinearLayout) {
        val strings = s()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, 0, 0, 24)
        }
        val enBtn = Button(this).apply {
            text = strings.langEnglish
            setOnClickListener { setLanguage(AppStrings.Lang.EN) }
        }
        val mrBtn = Button(this).apply {
            text = strings.langMarathi
            setOnClickListener { setLanguage(AppStrings.Lang.MR) }
        }
        row.addView(enBtn)
        row.addView(mrBtn)
        parent.addView(row)
    }

    private fun setLanguage(lang: AppStrings.Lang) {
        prefs.edit().putString("lang", lang.code).apply()
        recreate()
    }

    private fun confirmReregister(onConfirm: () -> Unit) {
        val msg = if (lang() == AppStrings.Lang.MR) {
            "हे नाव आणि एनक्रिप्शन की पुन्हा सेट करेल. पुढे चालवायचे?"
        } else {
            "This resets your name and encryption key. Continue?"
        }
        AlertDialog.Builder(this)
            .setTitle(s().btnReregister)
            .setMessage(msg)
            .setPositiveButton(if (lang() == AppStrings.Lang.MR) "होय" else "Yes") { _, _ -> onConfirm() }
            .setNegativeButton(if (lang() == AppStrings.Lang.MR) "रद्द" else "Cancel", null)
            .show()
    }

    private fun showPairingScreen() {
        val strings = s()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 80, 48, 48)
        }

        addLanguageBar(layout)

        layout.addView(TextView(this).apply {
            text = strings.setupTitle
            textSize = 24f
        })
        layout.addView(TextView(this).apply {
            text = strings.setupSubtitle
            textSize = 14f
            setPadding(0, 8, 0, 32)
        })

        val agentInput = EditText(this).apply {
            hint = strings.nameHint
            textSize = 16f
        }
        val registerBtn = Button(this).apply { text = strings.registerBtn }
        val statusText = TextView(this).apply { textSize = 13f; setPadding(0, 20, 0, 0) }

        registerBtn.setOnClickListener {
            val agentId = agentInput.text.toString().trim().lowercase()
            if (agentId.isEmpty()) {
                statusText.text = strings.enterName
                return@setOnClickListener
            }
            registerBtn.isEnabled = false
            statusText.text = strings.registering
            val ntfyTopic = "callbridge-$agentId-${UUID.randomUUID().toString().take(6)}"

            CoroutineScope(Dispatchers.IO).launch {
                val result = registerWithServer(agentId, ntfyTopic)
                withContext(Dispatchers.Main) {
                    if (result.success) {
                        prefs.edit()
                            .putString("agent_id", agentId)
                            .putString("ntfy_topic", ntfyTopic)
                            .putString("agent_secret", result.agentSecret)
                            .apply()
                        ensurePermissionsAndStart(ntfyTopic)
                        showStatusScreen()
                    } else {
                        statusText.text = strings.errorPrefix + result.error
                        registerBtn.isEnabled = true
                    }
                }
            }
        }

        layout.addView(agentInput)
        layout.addView(registerBtn)
        layout.addView(statusText)
        setContentView(ScrollView(this).apply { addView(layout) })
    }

    private fun showStatusScreen() {
        val strings = s()
        val agentId = prefs.getString("agent_id", "unknown")!!

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 80, 48, 48)
        }

        addLanguageBar(layout)

        layout.addView(TextView(this).apply {
            text = strings.readyTitle
            textSize = 24f
        })

        // Re-register at TOP — easy to find
        layout.addView(Button(this).apply {
            text = strings.btnReregister
            setBackgroundColor(Color.parseColor("#B00020"))
            setTextColor(Color.WHITE)
            setPadding(24, 28, 24, 28)
            setOnClickListener {
                confirmReregister {
                    prefs.edit().clear().apply()
                    stopService(Intent(this@MainActivity, NtfyListenerService::class.java))
                    recreate()
                }
            }
        })

        val syncBtn = Button(this).apply { text = strings.btnSync }
        val statusLine = TextView(this).apply { textSize = 13f; setPadding(0, 8, 0, 0) }
        syncBtn.setOnClickListener {
            syncBtn.isEnabled = false
            statusLine.text = strings.syncing
            CoroutineScope(Dispatchers.IO).launch {
                val agent = prefs.getString("agent_id", null)
                val topic = prefs.getString("ntfy_topic", null)
                val result = if (agent != null && topic != null) {
                    registerWithServer(agent, topic)
                } else {
                    RegisterResult(false, "Not registered")
                }
                withContext(Dispatchers.Main) {
                    syncBtn.isEnabled = true
                    if (result.success) {
                        if (result.agentSecret.isNotEmpty()) {
                            prefs.edit().putString("agent_secret", result.agentSecret).apply()
                        }
                        statusLine.text = strings.syncOk
                        prefs.getString("ntfy_topic", null)?.let { launchNtfyService(it) }
                    } else {
                        statusLine.text = strings.errorPrefix + result.error
                    }
                }
            }
        }
        layout.addView(syncBtn)
        layout.addView(statusLine)

        val ntfyTopic = prefs.getString("ntfy_topic", "—")!!
        layout.addView(TextView(this).apply {
            text = strings.statusInfo(agentId) + "\n\nTopic: $ntfyTopic"
            textSize = 14f
            setLineSpacing(0f, 1.4f)
            setPadding(0, 24, 0, 24)
        })

        layout.addView(Button(this).apply {
            text = if (lang() == AppStrings.Lang.MR) "चाचणी — 9999999999 कॉल" else "Test call (9999999999)"
            setOnClickListener {
                CoroutineScope(Dispatchers.IO).launch {
                    testCallFromServer(agentId)
                }
            }
        })

        layout.addView(Button(this).apply {
            text = strings.btnPower
            setOnClickListener { OemSettingsHelper.openPowerSettings(this@MainActivity) }
        })
        layout.addView(Button(this).apply {
            text = strings.btnAutostart
            setOnClickListener { OemSettingsHelper.openAutostartSettings(this@MainActivity) }
        })
        layout.addView(Button(this).apply {
            text = strings.btnBattery
            setOnClickListener { OemSettingsHelper.openBatteryOptimization(this@MainActivity) }
        })

        setContentView(ScrollView(this).apply { addView(layout) })

        prefs.getString("ntfy_topic", "")?.takeIf { it.isNotEmpty() }?.let {
            ensurePermissionsAndStart(it)
        }
        syncRegistrationWithServer()
        requestBatteryExemptionIfNeeded()
    }

    private fun ensurePermissionsAndStart(ntfyTopic: String) {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.CALL_PHONE)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.READ_PHONE_STATE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isNotEmpty()) {
            pendingNtfyTopic = ntfyTopic
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQ_PERMISSIONS)
            return
        }
        launchNtfyService(ntfyTopic)
    }

    private fun launchNtfyService(ntfyTopic: String) {
        prefs.edit().putString("ntfy_topic", ntfyTopic).apply()
        ServiceStarter.startListener(this)
    }

    private fun requestBatteryExemptionIfNeeded() {
        if (prefs.getBoolean("asked_battery", false)) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            prefs.edit().putBoolean("asked_battery", true).apply()
            OemSettingsHelper.openBatteryOptimization(this)
        }
    }

    private data class RegisterResult(
        val success: Boolean,
        val error: String = "",
        val agentSecret: String = ""
    )

    private fun syncRegistrationWithServer() {
        val agentId = prefs.getString("agent_id", null) ?: return
        val ntfyTopic = prefs.getString("ntfy_topic", null) ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val result = registerWithServer(agentId, ntfyTopic)
            if (result.success && result.agentSecret.isNotEmpty()) {
                prefs.edit().putString("agent_secret", result.agentSecret).apply()
            }
        }
    }

    private fun testCallFromServer(agentId: String) {
        try {
            val json = JSONObject().apply {
                put("agentId", agentId)
                put("number", "9999999999")
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            client.newCall(
                Request.Builder()
                    .url("$WEBHOOK_SERVER/call")
                    .post(body)
                    .build()
            ).execute().close()
        } catch (e: Exception) {
            Log.e(TAG, "Test call failed", e)
        }
    }

    private fun registerWithServer(agentId: String, ntfyTopic: String): RegisterResult {
        return try {
            val json = JSONObject().apply {
                put("agentId", agentId)
                put("ntfyTopic", ntfyTopic)
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val response = client.newCall(
                Request.Builder()
                    .url("$WEBHOOK_SERVER/register")
                    .post(body)
                    .build()
            ).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val body = JSONObject(responseBody)
                val secret = body.optString("agentSecret", "")
                if (secret.isEmpty()) {
                    return RegisterResult(
                        success = false,
                        error = "Server has no encryption key — redeploy server.js"
                    )
                }
                RegisterResult(success = true, agentSecret = secret)
            } else {
                val err = try {
                    JSONObject(responseBody).getString("error")
                } catch (_: Exception) {
                    "Server error ${response.code}"
                }
                RegisterResult(success = false, error = err)
            }
        } catch (e: Exception) {
            RegisterResult(success = false, error = "Cannot reach server: ${e.message}")
        }
    }
}
