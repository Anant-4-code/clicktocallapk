package com.callbridge.app

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity

class AgentLoginActivity : AppCompatActivity() {

    private val agentNames = listOf(
        "Sonali", "Jayshri", "Manjusha", "Nikita",
        "Anant", "Dhanashree", "Kalyani", "Komal",
        "sujata", "seema", "vaishnavi", "Mansi"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If already logged in, skip this screen
        val prefs = getSharedPreferences("callbridge", MODE_PRIVATE)
        val savedAgent = prefs.getString("agentId", null)
        if (savedAgent != null) {
            startMainActivity()
            return
        }

        setContentView(R.layout.activity_agent_login)

        val spinner = findViewById<Spinner>(R.id.agent_spinner)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, agentNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        findViewById<Button>(R.id.btn_start).setOnClickListener {
            val selected = spinner.selectedItem.toString()
            prefs.edit().putString("agentId", selected).apply()
            startMainActivity()
        }
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
