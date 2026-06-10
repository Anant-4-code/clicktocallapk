package com.callbridge.app

object AppStrings {

    enum class Lang(val code: String) {
        EN("en"), MR("mr")
    }

    data class Pack(
        val setupTitle: String,
        val setupSubtitle: String,
        val nameHint: String,
        val registerBtn: String,
        val registering: String,
        val enterName: String,
        val readyTitle: String,
        val statusInfo: (String) -> String,
        val btnPower: String,
        val btnAutostart: String,
        val btnBattery: String,
        val btnReregister: String,
        val btnSync: String,
        val syncing: String,
        val syncOk: String,
        val langEnglish: String,
        val langMarathi: String,
        val errorPrefix: String,
    )

    private val en = Pack(
        setupTitle = "CallBridge Setup",
        setupSubtitle = "One-time setup. Then close the app — sheet calls will auto-dial.",
        nameHint = "Your name (e.g. anant)",
        registerBtn = "Register & enable auto-dial",
        registering = "Registering…",
        enterName = "Please enter your name",
        readyTitle = "CallBridge ready",
        statusInfo = { agent ->
            """
            Agent: $agent

            Tap buttons below once. Then press Home.

            1. Power settings → ON background activity
            2. Autostart → enable CallBridge
            3. Battery → Unrestricted

            Encrypted calls: re-register after server update.

            ↓ Scroll down for Re-register ↓
            """.trimIndent()
        },
        btnPower = "① Power settings",
        btnAutostart = "② Autostart",
        btnBattery = "③ Battery — Unrestricted",
        btnReregister = "↻ RE-REGISTER (reset device)",
        btnSync = "Sync with server",
        syncing = "Syncing…",
        syncOk = "Synced with server",
        langEnglish = "English",
        langMarathi = "मराठी",
        errorPrefix = "Error: ",
    )

    private val mr = Pack(
        setupTitle = "CallBridge सेटअप",
        setupSubtitle = "एकदाच सेटअप करा. मग अॅप बंद करा — शीटवरून कॉल आपोआप होतील.",
        nameHint = "तुमचे नाव (उदा. anant)",
        registerBtn = "नोंदणी करा आणि ऑटो-डायल सुरू करा",
        registering = "नोंदणी होत आहे…",
        enterName = "कृपया तुमचे नाव लिहा",
        readyTitle = "CallBridge तयार",
        statusInfo = { agent ->
            """
            एजंट: $agent

            खालील बटणे एकदा दाबा. मग Home दाबा.

            1. Power settings → background ON करा
            2. Autostart → CallBridge ON
            3. Battery → Unrestricted

            एनक्रिप्शनसाठी: सर्व्हर अपडेट नंतर Re-register करा.

            ↓ Re-register खाली स्क्रोल करा ↓
            """.trimIndent()
        },
        btnPower = "① Power settings",
        btnAutostart = "② Autostart",
        btnBattery = "③ Battery — Unrestricted",
        btnReregister = "↻ पुन्हा नोंदणी (Re-register)",
        btnSync = "सर्व्हरशी जोडा",
        syncing = "जोडत आहे…",
        syncOk = "सर्व्हरशी जोडले",
        langEnglish = "English",
        langMarathi = "मराठी",
        errorPrefix = "त्रुटी: ",
    )

    fun pack(lang: Lang): Pack = if (lang == Lang.MR) mr else en

    fun fromCode(code: String?): Lang =
        if (code == "mr") Lang.MR else Lang.EN
}
