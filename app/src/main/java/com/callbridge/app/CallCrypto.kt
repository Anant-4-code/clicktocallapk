package com.callbridge.app

import android.util.Base64
import android.util.Log
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CallCrypto {

    private const val TAG = "CallCrypto"
    const val PREFIX = "enc:v1:"
    private const val IV_LEN = 12
    private const val TAG_LEN = 16

    fun isEncrypted(value: String): Boolean {
        val t = value.trim()
        return t.startsWith(PREFIX) || t.startsWith("enc:")
    }

    /**
     * @return decrypted phone string, or null if decrypt failed (never returns garbage digits)
     */
    fun decryptOrNull(payload: String, secretHex: String): String? {
        val trimmed = payload.trim()
        if (!isEncrypted(trimmed)) return null

        if (secretHex.isBlank()) {
            Log.e(TAG, "Missing agent_secret")
            return null
        }

        return try {
            val b64 = trimmed.removePrefix(PREFIX).replace(" ", "+")
            val packed = Base64.decode(
                b64,
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            )
            if (packed.size <= IV_LEN + TAG_LEN) return null

            val iv = packed.copyOfRange(0, IV_LEN)
            val authTag = packed.copyOfRange(IV_LEN, IV_LEN + TAG_LEN)
            val ciphertext = packed.copyOfRange(IV_LEN + TAG_LEN, packed.size)

            val combined = ByteArray(ciphertext.size + authTag.size)
            System.arraycopy(ciphertext, 0, combined, 0, ciphertext.size)
            System.arraycopy(authTag, 0, combined, ciphertext.size, authTag.size)

            val key = SecretKeySpec(hexToBytes(secretHex), "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))

            val plain = String(cipher.doFinal(combined), Charsets.UTF_8).trim()
            if (plain.isEmpty()) null else plain
        } catch (e: Exception) {
            Log.e(TAG, "Decrypt failed (re-register app): ${e.message}")
            null
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val out = ByteArray(hex.length / 2)
        var i = 0
        while (i < hex.length) {
            out[i / 2] = ((Character.digit(hex[i], 16) shl 4) +
                Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return out
    }
}
