package com.escosis.v2463alpe

import android.content.Context
import android.content.SharedPreferences

fun ksuDisplayName(pkg: String): String = when (pkg) {
    "me.weishu.kernelsu"   -> "KernelSU"
    "vctsrt.cntgtj.uqfwgg" -> "KernelSU Next"
    "com.sukisu.ultra"     -> "SukiSU-Ultra"
    "com.resukisu.resukisu" -> "BakaSU"
    ""                     -> "未知"
    else                   -> pkg
}

/**
 * 统一使用 DE 存储的 SharedPreferences，
 * 保证锁屏状态下也能读写。
 */
object Prefs {
    private const val NAME = "app_prefs"
    private const val KEY_THEME = "theme_mode"          // 0=跟随系统 1=浅色 2=深色
    private const val KEY_ROOT_CHECKED = "root_checked"
    private const val KEY_HAS_ROOT = "has_root"
    private const val KEY_PRELOAD_PUSHED = "preload_pushed"
    private const val KEY_FIRST_LAUNCH_DONE = "first_launch_done"
    private const val KEY_FIRST_LAUNCH_PENDING = "first_launch_pending"
    private const val KEY_KSU_PACKAGE = "ksu_package"
    private const val KEY_ACTIVE_KSU_PKG = "active_ksu_pkg"
    private const val KEY_APP_ENABLED = "app_enabled"
    private const val KEY_AUTO_SOFT_REBOOT = "auto_soft_reboot"
    private const val KEY_AUTO_CHECK_UPDATE = "auto_check_update"
    private const val KEY_PRELOAD_MODE = "preload_mode"       // "auto" | "custom" | "version"
    private const val KEY_PRELOAD_VERSION = "preload_version"
    private const val KEY_CUSTOM_PRELOAD_NAME = "custom_preload_name"

    fun getPreloadMode(context: Context): String =
        sp(context).getString(KEY_PRELOAD_MODE, "auto") ?: "auto"

    fun setPreloadMode(context: Context, mode: String) =
        sp(context).edit().putString(KEY_PRELOAD_MODE, mode).apply()

    fun getPreloadVersion(context: Context): String =
        sp(context).getString(KEY_PRELOAD_VERSION, "") ?: ""

    fun setPreloadVersion(context: Context, v: String) =
        sp(context).edit().putString(KEY_PRELOAD_VERSION, v).apply()

    fun getCustomPreloadName(context: Context): String =
        sp(context).getString(KEY_CUSTOM_PRELOAD_NAME, "") ?: ""

    fun setCustomPreloadName(context: Context, name: String) =
        sp(context).edit().putString(KEY_CUSTOM_PRELOAD_NAME, name).apply()

    fun isAutoCheckUpdate(context: Context): Boolean =
        sp(context).getBoolean(KEY_AUTO_CHECK_UPDATE, true)

    fun setAutoCheckUpdate(context: Context, v: Boolean) =
        sp(context).edit().putBoolean(KEY_AUTO_CHECK_UPDATE, v).apply()
    fun isAutoSoftReboot(context: Context): Boolean =
        sp(context).getBoolean(KEY_AUTO_SOFT_REBOOT, false)

    fun setAutoSoftReboot(context: Context, v: Boolean) =
        sp(context).edit().putBoolean(KEY_AUTO_SOFT_REBOOT, v).apply()

    fun isAppEnabled(context: Context): Boolean =
        sp(context).getBoolean(KEY_APP_ENABLED, true)

    fun setAppEnabled(context: Context, v: Boolean) =
        sp(context).edit().putBoolean(KEY_APP_ENABLED, v).apply()

    fun getActiveKsuPackage(context: Context): String =
        sp(context).getString(KEY_ACTIVE_KSU_PKG, "") ?: ""

    fun setActiveKsuPackage(context: Context, pkg: String) {
        sp(context).edit().putString(KEY_ACTIVE_KSU_PKG, pkg).apply()
    }

    fun getKsuPackage(context: Context): String =
        sp(context).getString(KEY_KSU_PACKAGE, "me.weishu.kernelsu") ?: "me.weishu.kernelsu"

    fun setKsuPackage(context: Context, pkg: String) =
        sp(context).edit().putString(KEY_KSU_PACKAGE, pkg).apply()

    private fun sp(context: Context): SharedPreferences =
        context.createDeviceProtectedStorageContext()
            .getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun getThemeMode(context: Context): Int = sp(context).getInt(KEY_THEME, 0)
    fun setThemeMode(context: Context, mode: Int) =
        sp(context).edit().putInt(KEY_THEME, mode).apply()

    fun isRootChecked(context: Context): Boolean = sp(context).getBoolean(KEY_ROOT_CHECKED, false)
    fun setRootChecked(context: Context, v: Boolean) =
        sp(context).edit().putBoolean(KEY_ROOT_CHECKED, v).apply()

    fun getHasRoot(context: Context): Boolean = sp(context).getBoolean(KEY_HAS_ROOT, false)
    fun setHasRoot(context: Context, v: Boolean) =
        sp(context).edit().putBoolean(KEY_HAS_ROOT, v).apply()

    fun getPreloadPushed(context: Context): Boolean = sp(context).getBoolean(KEY_PRELOAD_PUSHED, false)
    fun setPreloadPushed(context: Context, v: Boolean) =
        sp(context).edit().putBoolean(KEY_PRELOAD_PUSHED, v).apply()

    fun isFirstLaunchDone(context: Context): Boolean =
        sp(context).getBoolean(KEY_FIRST_LAUNCH_DONE, false)

    fun setFirstLaunchDone(context: Context, v: Boolean) =
        sp(context).edit().putBoolean(KEY_FIRST_LAUNCH_DONE, v).apply()

    fun isFirstLaunchPending(context: Context): Boolean =
        sp(context).getBoolean(KEY_FIRST_LAUNCH_PENDING, false)

    fun setFirstLaunchPending(context: Context, v: Boolean) =
        sp(context).edit().putBoolean(KEY_FIRST_LAUNCH_PENDING, v).apply()
}