package com.puraa.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

/** Runtime permissions the relay needs: SMS receipt, plus notifications on 13+. */
fun relayRuntimePermissions(): Array<String> {
    val perms = mutableListOf(Manifest.permission.RECEIVE_SMS)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        perms += Manifest.permission.POST_NOTIFICATIONS
    }
    return perms.toTypedArray()
}

/** The subset of [relayRuntimePermissions] not yet granted. */
fun missingRelayPermissions(context: Context): List<String> =
    relayRuntimePermissions().filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }

/** Ask to be exempt from battery optimisation so sends stay prompt when idle. */
fun requestBatteryExemption(context: Context) {
    val pm = ContextCompat.getSystemService(context, PowerManager::class.java) ?: return
    if (pm.isIgnoringBatteryOptimizations(context.packageName)) return
    if (context !is Activity) return
    runCatching {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = "package:${context.packageName}".toUri()
        }
        context.startActivity(intent)
    }
}
