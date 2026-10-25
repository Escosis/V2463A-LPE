package com.escosis.v2463alpe

import android.content.Context
import android.util.Base64
import android.util.Log
import java.io.File

object PreloadPusher {

    private const val TAG = "PreloadPusher"
    private const val CHUNK = 16000
    const val MAX_CUSTOM_SIZE = 20 * 1024 * 1024 // 20MB

    /** 自定义 preload 保存位置（DE 存储，锁屏提权可用） */
    @JvmStatic
    fun getCustomPreloadFile(context: Context): File {
        val deContext = context.createDeviceProtectedStorageContext()
        return File(deContext.filesDir, "custom_preload.so")
    }

    /** 保存用户选择的 preload 字节。成功返回 true。 */
    @JvmStatic
    fun saveCustomPreload(
        context: Context,
        bytes: ByteArray,
        displayName: String,
    ): Boolean {
        if (bytes.isEmpty() || bytes.size > MAX_CUSTOM_SIZE) return false
        return try {
            getCustomPreloadFile(context).writeBytes(bytes)
            Prefs.setCustomPreload(context, true)
            Prefs.setCustomPreloadName(context, displayName)
            true
        } catch (e: Exception) {
            Log.e(TAG, "保存自定义 preload 失败", e)
            false
        }
    }

    /** 清除自定义 preload，回退默认 */
    @JvmStatic
    fun clearCustomPreload(context: Context) {
        try {
            val f = getCustomPreloadFile(context)
            if (f.exists()) f.delete()
        } catch (_: Exception) {}
        Prefs.setCustomPreload(context, false)
        Prefs.setCustomPreloadName(context, "")
    }

    private fun loadData(
        context: Context,
        onLog: (String) -> Unit,
    ): Pair<ByteArray, String>? {
        if (Prefs.isCustomPreload(context)) {
            val f = getCustomPreloadFile(context)
            if (f.exists() && f.length() > 0) {
                try {
                    val bytes = f.readBytes()
                    val name = Prefs.getCustomPreloadName(context).ifBlank { "custom.so" }
                    return bytes to "自定义（$name）"
                } catch (e: Exception) {
                    onLog("读取自定义 preload.so 失败: ${e.message}，回退默认")
                }
            } else {
                onLog("自定义 preload.so 不存在，回退默认")
            }
        }
        return try {
            val bytes = context.assets.open("preload.so").use { it.readBytes() }
            bytes to "默认（assets）"
        } catch (e: Exception) {
            onLog("assets/preload.so 不存在")
            null
        }
    }

    @JvmStatic
    fun push(context: Context, onLog: (String) -> Unit): Boolean {
        return try {
            onLog("授予 WRITE_SECURE_SETTINGS...")
            try {
                AdbClient.shell(
                    context,
                    "pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
                )
            } catch (e: Exception) {
                Log.w(TAG, "授予 WRITE_SECURE_SETTINGS 失败", e)
            }

            onLog("读取 preload.so...")
            val (data, sourceLabel) = loadData(context, onLog) ?: return false
            onLog("来源: $sourceLabel")
            onLog("preload.so: ${data.size} bytes")

            onLog("清理旧文件...")
            AdbClient.shell(
                context,
                "rm -f /data/local/tmp/preload.so /data/local/tmp/preload.b64"
            )

            onLog("base64 编码...")
            val b64 = Base64.encodeToString(data, Base64.NO_WRAP)

            val total = (b64.length + CHUNK - 1) / CHUNK
            onLog("分 $total 块推送...")
            for (i in 0 until total) {
                val start = i * CHUNK
                val end = minOf(start + CHUNK, b64.length)
                val chunk = b64.substring(start, end)
                AdbClient.shell(context, "echo '$chunk' >> /data/local/tmp/preload.b64")
                if (i % 5 == 0 || i == total - 1) {
                    onLog("进度 ${i + 1}/$total")
                }
            }

            onLog("解码 + 权限...")
            AdbClient.shell(
                context,
                "base64 -d /data/local/tmp/preload.b64 > /data/local/tmp/preload.so"
            )
            AdbClient.shell(context, "rm -f /data/local/tmp/preload.b64")
            AdbClient.shell(context, "chmod 755 /data/local/tmp/preload.so")

            onLog("验证...")
            val check = AdbClient.shell(context, "ls -la /data/local/tmp/preload.so")
            val ok = check.contains("preload.so")
            if (ok) {
                Prefs.setPreloadPushed(context, true)
                onLog("推送成功")
            } else {
                onLog("验证失败: ${check.trim()}")
            }
            ok
        } catch (e: Exception) {
            Log.e(TAG, "推送异常", e)
            onLog("异常: ${e.message}")
            false
        }
    }
}