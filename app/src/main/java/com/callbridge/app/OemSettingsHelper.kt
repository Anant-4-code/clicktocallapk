package com.callbridge.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

/** Opens battery / background / autostart screens on common OEM phones. */
object OemSettingsHelper {

    private const val TAG = "OemSettings"

    fun openPowerSettings(context: Context) {
        val pkg = context.packageName
        val intents = listOf(
            // Xiaomi / Redmi / POCO — app power details
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity"
                )
                putExtra("extra_pkgname", pkg)
            },
            Intent().apply {
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.powercenter.powerui.PowerSettings"
                )
                putExtra("package_name", pkg)
            },
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$pkg")
            }
        )
        if (!tryIntents(context, intents)) {
            openAppDetails(context)
        }
    }

    fun openAutostartSettings(context: Context) {
        val intents = listOf(
            Intent().apply {
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            },
            Intent().apply {
                setClassName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.startupapp.StartupAppListActivity"
                )
            },
            Intent().apply {
                setClassName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )
            },
            Intent().apply {
                setClassName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
            },
            Intent().apply {
                setClassName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.battery.ui.BatteryActivity"
                )
            }
        )
        if (!tryIntents(context, intents)) {
            openAppDetails(context)
        }
    }

    fun openBatteryOptimization(context: Context) {
        val pkg = context.packageName
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$pkg")
                    }
                )
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "Battery optimization intent failed", e)
        }
        openAppDetails(context)
    }

    private fun openAppDetails(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    private fun tryIntents(context: Context, intents: List<Intent>): Boolean {
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    return true
                }
            } catch (e: Exception) {
                Log.d(TAG, "Intent failed: ${e.message}")
            }
        }
        return false
    }
}
