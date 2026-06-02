package com.callbridge.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

/**
 * Brief invisible screen so the dialer can start when the app is closed.
 * Android blocks raw call intents from a killed UI; this activity is allowed.
 */
class CallTrampolineActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val number = intent.getStringExtra(EXTRA_NUMBER)?.trim().orEmpty()
        if (number.isEmpty()) {
            finish()
            return
        }

        // Tiny delay helps some devices connect to the mobile network before dialing
        Handler(Looper.getMainLooper()).postDelayed({
            CallHelper.placeCall(this, number)
            finish()
        }, 600)
    }

    companion object {
        const val EXTRA_NUMBER = "number"
    }
}
