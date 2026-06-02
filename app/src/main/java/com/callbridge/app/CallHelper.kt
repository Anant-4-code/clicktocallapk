package com.callbridge.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat

object CallHelper {

    private const val TAG = "CallHelper"

    fun placeCall(context: Context, number: String) {
        val valid = PhoneNumbers.rejectIfInvalid(number, "dial") ?: return
        val formats = numberFormats(valid)
        if (formats.isEmpty()) {
            Log.w(TAG, "Invalid number: $number")
            return
        }

        val canCall = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val simReady = isSimReady(context)

        if (canCall && simReady) {
            for (format in formats) {
                val uri = Uri.parse("tel:$format")
                if (placeCallViaTelecom(context, uri)) {
                    Log.d(TAG, "Telecom placed call: $format")
                    return
                }
                if (placeCallViaIntent(context, uri)) {
                    Log.d(TAG, "Intent placed call: $format")
                    return
                }
            }
        } else {
            Log.w(TAG, "Skip auto-call: permission=$canCall simReady=$simReady")
        }

        // Fallback: open dialer with best local format (user taps green button once)
        val dialUri = Uri.parse("tel:${formats.first()}")
        context.startActivity(
            Intent(Intent.ACTION_DIAL, dialUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )

        val msg = when {
            !simReady -> "No SIM / no mobile signal — connect to cellular network, then tap Call"
            !canCall -> "Allow Phone permission in CallBridge settings"
            else -> "Tap the green Call button to dial"
        }
        Toast.makeText(context.applicationContext, msg, Toast.LENGTH_LONG).show()
    }

    private fun placeCallViaTelecom(context: Context, uri: Uri): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        return try {
            val telecom = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            telecom.placeCall(uri, Bundle())
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Telecom SecurityException: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Telecom failed: ${e.message}")
            false
        }
    }

    private fun placeCallViaIntent(context: Context, uri: Uri): Boolean {
        return try {
            context.startActivity(
                Intent(Intent.ACTION_CALL, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "ACTION_CALL failed: ${e.message}")
            false
        }
    }

    private fun isSimReady(context: Context): Boolean {
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Cannot check — assume OK
            return true
        }
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            tm.simState == TelephonyManager.SIM_STATE_READY
        } catch (e: Exception) {
            true
        }
    }

    /** Try multiple formats — some Xiaomi ROMs reject +91 on auto-call. */
    private fun numberFormats(raw: String): List<String> {
        val digits = raw.replace(Regex("[^0-9+]"), "")
        if (digits.isEmpty()) return emptyList()

        val formats = linkedSetOf<String>()

        when {
            digits.startsWith("+") -> {
                formats.add(digits)
                val withoutPlus = digits.removePrefix("+")
                if (withoutPlus.startsWith("91") && withoutPlus.length >= 12) {
                    formats.add(withoutPlus.substring(2)) // 10-digit local
                    formats.add(withoutPlus)             // 91xxxxxxxxxx
                }
            }
            digits.length == 10 && digits.first() in '6'..'9' -> {
                formats.add(digits)           // local — often works best on Indian phones
                formats.add("+91$digits")
                formats.add("91$digits")
            }
            digits.startsWith("91") && digits.length >= 12 -> {
                formats.add(digits.substring(2))
                formats.add("+$digits")
                formats.add(digits)
            }
            else -> formats.add(digits)
        }

        return formats.toList()
    }
}
