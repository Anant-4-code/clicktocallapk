package com.callbridge.app

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Must match server crypto.js PEPPER (callbridge-shared-key-v1). */
object SecretDeriver {

    private const val PEPPER = "callbridge-shared-key-v1"

    fun derive(agentId: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(PEPPER.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val hash = mac.doFinal(agentId.lowercase().toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
