package com.escosis.v2463alpe.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.escosis.v2463alpe.AdbPairingHelper

data class AppStatus(
    val isLoading: Boolean = true,
    val hasOverlay: Boolean = false,
    val hasBatteryOpt: Boolean = false,
    val hasWriteSecure: Boolean = false,
    val hasLocalNetwork: Boolean = false,
    val wifiConnected: Boolean = false,
    val isPaired: Boolean = false,
    val isConnected: Boolean = false,
    val connectedPort: Int = -1,
    val preloadPushed: Boolean = false,
    val hasRoot: Boolean = false,
    val activeKsuPackage: String = "",
) {
    val allReady: Boolean
        get() = !isLoading &&
                hasOverlay && hasBatteryOpt &&
                hasWriteSecure && hasLocalNetwork &&
                isPaired && preloadPushed

    val missingLabel: String
        get() = when {
            !hasOverlay -> "悬浮窗"
            !hasBatteryOpt -> "电池优化"
            !hasWriteSecure -> "WRITE_SECURE_SETTINGS"
            !hasLocalNetwork -> "本地网络访问"
            !isPaired -> "无线调试配对"
            !preloadPushed -> "preload.so"
            else -> ""
        }
}

fun checkStatus(
    context: Context,
    hasRoot: Boolean,
    cachedConnected: Boolean,
    cachedPort: Int,
    cachedPreload: Boolean,
    activeKsuPackage: String,
): AppStatus {
    val overlay = Settings.canDrawOverlays(context)

    val battery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.isIgnoringBatteryOptimizations(context.packageName)
    } else true

    val writeSecure = ContextCompat.checkSelfPermission(
        context, "android.permission.WRITE_SECURE_SETTINGS"
    ) == PackageManager.PERMISSION_GRANTED

    // ACCESS_LOCAL_NETWORK：Android 17 (API 37) 起才需要运行时申请，以下版本隐式授予
    val localNetwork = if (Build.VERSION.SDK_INT >= 37) {
        ContextCompat.checkSelfPermission(
            context, "android.permission.ACCESS_LOCAL_NETWORK"
        ) == PackageManager.PERMISSION_GRANTED
    } else true

    val wifi = AdbPairingHelper.isWifiConnected(context)
    val paired = AdbPairingHelper.isPaired()

    return AppStatus(
        isLoading = false,
        hasOverlay = overlay,
        hasBatteryOpt = battery,
        hasWriteSecure = writeSecure,
        hasLocalNetwork = localNetwork,
        wifiConnected = wifi,
        isPaired = paired,
        isConnected = cachedConnected,
        connectedPort = cachedPort,
        preloadPushed = cachedPreload,
        hasRoot = hasRoot,
        activeKsuPackage = activeKsuPackage,
    )
}