package com.callbridge.app

import android.util.Log

object PhoneNumbers {

    private const val TAG = "PhoneNumbers"

    /** Valid: 10-digit Indian mobile, or +91 / international E.164 (max 15 digits). */
    private fun isIndianMobileLocal(local: String): Boolean =
        local.length == 10 && local.first() in '6'..'9'

    fun isValid(number: String): Boolean {
        val d = number.replace(Regex("[^0-9+]"), "")
        if (d.isEmpty()) return false

        if (d.startsWith("+91") && d.length == 13) {
            return isIndianMobileLocal(d.removePrefix("+91"))
        }
        if (d.length == 12 && d.startsWith("91")) {
            return isIndianMobileLocal(d.removePrefix("91"))
        }
        if (d.length == 11 && d.startsWith("0")) {
            return isIndianMobileLocal(d.drop(1))
        }
        if (d.length == 10 && d.first() in '6'..'9') return true
        if (d.startsWith("+") && d.length in 11..15) return true

        return false
    }

    fun normalize(number: String): String {
        val d = number.replace(Regex("[^0-9+]"), "")
        if (d.startsWith("+")) {
            if (d.startsWith("+91") && d.length == 13) return d
            return d
        }
        if (d.length == 12 && d.startsWith("91")) {
            val local = d.removePrefix("91")
            if (isIndianMobileLocal(local)) return "+91$local"
        }
        if (d.length == 11 && d.startsWith("0")) {
            val local = d.drop(1)
            if (isIndianMobileLocal(local)) return "+91$local"
        }
        if (d.length == 10 && d.first() in '6'..'9') return "+91$d"
        return d
    }

    fun rejectIfInvalid(number: String, context: String): String? {
        if (!isValid(number)) {
            Log.e(TAG, "Invalid number ($context): length=${number.length}")
            return null
        }
        return normalize(number)
    }
}
