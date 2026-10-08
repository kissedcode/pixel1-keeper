package com.kissedcode.pixel1keeper.device

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.os.StatFs

/** Состояние устройства: батарея, место, сеть, выданные доступы. */
class DeviceHealth(private val context: Context) {
    data class Battery(val tempC: Int, val plugged: Boolean, val level: Int)

    fun battery(): Battery {
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temp = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10
        val plugged = (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        return Battery(temp, plugged, level)
    }

    fun freeBytes(): Long = StatFs(Environment.getExternalStorageDirectory().path).availableBytes

    fun onUnmeteredWifi(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    fun hasUsageAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION")
        val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun ignoringBatteryOptimizations(): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    fun googlePhotosInstalled(): Boolean =
        runCatching { context.packageManager.getApplicationInfo(GOOGLE_PHOTOS, 0) }.isSuccess

    companion object {
        const val GOOGLE_PHOTOS = "com.google.android.apps.photos"
        const val HOT_C = 42
        const val COOL_C = 38
        fun gb(bytes: Long): String = String.format(java.util.Locale.US, "%.1f", bytes / 1_073_741_824.0)
    }
}
