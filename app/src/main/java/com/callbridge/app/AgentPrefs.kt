package com.callbridge.app

import android.content.Context

object AgentPrefs {
    fun getId(context: Context): String {
        return context.getSharedPreferences("callbridge", Context.MODE_PRIVATE)
            .getString("agentId", "Unknown") ?: "Unknown"
    }

    fun clear(context: Context) {
        context.getSharedPreferences("callbridge", Context.MODE_PRIVATE)
            .edit().remove("agentId").apply()
    }
}
