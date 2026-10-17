package com.escosis.v2463alpe

import android.content.Context
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream

object PreloadPusher {

    private const val TAG = "PreloadPusher"
    private const val CHUNK = 16000

    @JvmStatic
    fun push(context: Context, onLog: (String) -> Unit): Boolean {
        return try {
            onLog("授予 WRITE_SECURE_SETTINGS...")
            try {
                AdbClient.shell(context,
                    "pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS")
            } catch (e: Exception) {
                Log.w(TAG, "授予 WRITE_SECURE_SETTINGS 失败", e)
            }

            onLog("读取 assets/preload.so...")
            val data = try {
                val is_ = context.assets.open("preload.so")
                val bos = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var n = is_.read(buf)
                while (n > 0) {
                    bos.write(buf, 0, n)
                    n = is_.read(buf)
                }
                is_.close()
                bos.toByteArray()
            } catch (e: Exception) {
                onLog("assets/preload.so 不存在")
                Log.e(TAG, "assets 读取失败", e)
                return false
            }

            onLog("preload.so: ${data.size} bytes")

            onLog("清理旧文件...")
            AdbClient.shell(context, "rm -f /data/local/tmp/preload.so /data/local/tmp/preload.b64")

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
            AdbClient.shell(context, "base64 -d /data/local/tmp/preload.b64 > /data/local/tmp/preload.so")
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