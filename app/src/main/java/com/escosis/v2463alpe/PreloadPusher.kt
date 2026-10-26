package com.escosis.v2463alpe

import android.content.Context
import android.os.Build
import android.util.Base64
import android.util.Log
import java.io.File

data class PreloadEntry(
    val dirName: String,
    val fileName: String,
    val model: String,
) {
    val key: String get() = "$dirName/$fileName"
}

object PreloadPusher {

    private const val TAG = "PreloadPusher"
    private const val CHUNK = 16000
    const val MAX_CUSTOM_SIZE = 20 * 1024 * 1024 // 20MB

    const val MODE_AUTO = "auto"
    const val MODE_CUSTOM = "custom"
    const val MODE_VERSION = "version"

    data class AutoDetectResult(
        val entry: PreloadEntry,
        val isDeviceMatch: Boolean,
    )

    @JvmStatic
    fun getCurrentKernelVersion(): String {
        return try {
            System.getProperty("os.version")?.trim().orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    @JvmStatic
    fun getCurrentModel(): String {
        return try {
            (Build.MODEL ?: "").trim()
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * 枚举 assets 下所有 `<内核版本>/<型号>.so` 条目。
     * 目录名必须等于内核版本，文件名必须是 `xxx.so`。
     */
    @JvmStatic
    fun listAssetEntries(context: Context): List<PreloadEntry> {
        return try {
            val dirs = context.assets.list("") ?: return emptyList()
            val result = mutableListOf<PreloadEntry>()
            for (dir in dirs) {
                val files = try {
                    context.assets.list(dir).orEmpty()
                } catch (_: Exception) {
                    continue
                }
                for (f in files) {
                    if (!f.endsWith(".so", ignoreCase = true)) continue
                    try {
                        val sub = context.assets.list("$dir/$f")
                        if (sub != null && sub.isNotEmpty()) continue
                    } catch (_: Exception) {
                        continue
                    }
                    val model = f.substring(0, f.length - 3)
                    if (model.isBlank()) continue
                    result.add(PreloadEntry(dir, f, model))
                }
            }
            result.sortedWith(
                compareByDescending<PreloadEntry> { it.dirName }
                    .thenBy { it.model }
            )
        } catch (e: Exception) {
            Log.e(TAG, "枚举 assets 失败", e)
            emptyList()
        }
    }

    /**
     * 自动检测：
     * 1. 内核版本必须完全相等
     * 2. 内核相等的条目里优先找当前设备型号（ro.product.model）
     * 3. 找不到则回退到同内核的第一个其他型号，isDeviceMatch = false
     * 4. 内核完全没匹配则返回 null
     */
    @JvmStatic
    fun detectBestEntry(context: Context): AutoDetectResult? {
        val kv = getCurrentKernelVersion()
        if (kv.isEmpty()) return null
        val model = getCurrentModel()
        val all = listAssetEntries(context)
        val sameKernel = all.filter { it.dirName == kv }
        if (sameKernel.isEmpty()) return null

        val deviceMatch = sameKernel.firstOrNull {
            it.model.equals(model, ignoreCase = true)
        }
        if (deviceMatch != null) {
            return AutoDetectResult(deviceMatch, true)
        }
        return AutoDetectResult(sameKernel.first(), false)
    }

    @JvmStatic
    fun getCustomPreloadFile(context: Context): File {
        val deContext = context.createDeviceProtectedStorageContext()
        return File(deContext.filesDir, "custom_preload.so")
    }

    @JvmStatic
    fun saveCustomPreload(
        context: Context,
        bytes: ByteArray,
        displayName: String,
    ): Boolean {
        if (bytes.isEmpty() || bytes.size > MAX_CUSTOM_SIZE) return false
        return try {
            getCustomPreloadFile(context).writeBytes(bytes)
            Prefs.setCustomPreloadName(context, displayName)
            Prefs.setPreloadMode(context, MODE_CUSTOM)
            true
        } catch (e: Exception) {
            Log.e(TAG, "保存自定义 preload 失败", e)
            false
        }
    }

    @JvmStatic
    fun clearCustomPreload(context: Context) {
        try {
            val f = getCustomPreloadFile(context)
            if (f.exists()) f.delete()
        } catch (_: Exception) {}
        Prefs.setCustomPreloadName(context, "")
        if (Prefs.getPreloadMode(context) == MODE_CUSTOM) {
            Prefs.setPreloadMode(context, MODE_AUTO)
        }
    }

    private fun loadData(context: Context, onLog: (String) -> Unit): Pair<ByteArray, String>? {
        val mode = Prefs.getPreloadMode(context)

        if (mode == MODE_CUSTOM) {
            val r = loadCustom(context, onLog)
            if (r != null) return r
            onLog("自定义 preload.so 不可用，回退自动检测")
        }

        if (mode == MODE_VERSION) {
            val key = Prefs.getPreloadVersion(context)
            val entry = parseKeyToEntry(key)
            if (entry != null) {
                val r = loadFromAssetsEntry(context, entry, onLog)
                if (r != null) return r
                onLog("内置条目 $key 不存在，回退自动检测")
            } else if (key.isNotBlank()) {
                onLog("内置条目格式错误: $key，回退自动检测")
            } else {
                onLog("未指定内置条目，回退自动检测")
            }
        }

        // 自动检测
        val detected = detectBestEntry(context)
        if (detected == null) {
            val kv = getCurrentKernelVersion()
            val model = getCurrentModel()
            val all = listAssetEntries(context)
            val kernels = all.map { it.dirName }.distinct().sorted()
            onLog("自动检测失败：当前内核 ${kv.ifBlank { "未知" }}，设备 ${model.ifBlank { "未知" }}")
            if (kernels.isEmpty()) {
                onLog("assets 中没有任何 preload 条目")
            } else {
                onLog("assets 中现有内核: ${kernels.joinToString(", ")}")
            }
            return null
        }

        if (detected.isDeviceMatch) {
            onLog("自动检测：设备+内核精确匹配 ${detected.entry.model}@${detected.entry.dirName}")
        } else {
            onLog(
                "自动检测：当前设备 ${getCurrentModel()} 无专属 payload，" +
                        "回退到其他型号 ${detected.entry.model}@${detected.entry.dirName}"
            )
        }
        return loadFromAssetsEntry(context, detected.entry, onLog)
    }

    private fun parseKeyToEntry(key: String): PreloadEntry? {
        if (key.isBlank()) return null
        val slash = key.indexOf('/')
        if (slash <= 0 || slash >= key.length - 1) return null
        val dir = key.substring(0, slash)
        val file = key.substring(slash + 1)
        if (!file.endsWith(".so", ignoreCase = true)) return null
        val model = file.substring(0, file.length - 3)
        if (model.isBlank()) return null
        return PreloadEntry(dir, file, model)
    }

    private fun loadCustom(context: Context, onLog: (String) -> Unit): Pair<ByteArray, String>? {
        val f = getCustomPreloadFile(context)
        if (!f.exists() || f.length() == 0L) {
            onLog("自定义 preload.so 不存在")
            return null
        }
        return try {
            val bytes = f.readBytes()
            val name = Prefs.getCustomPreloadName(context).ifBlank { "custom.so" }
            bytes to "自定义（$name）"
        } catch (e: Exception) {
            onLog("读取自定义 preload.so 失败: ${e.message}")
            null
        }
    }

    private fun loadFromAssetsEntry(
        context: Context,
        entry: PreloadEntry,
        onLog: (String) -> Unit,
    ): Pair<ByteArray, String>? {
        val path = "${entry.dirName}/${entry.fileName}"
        return try {
            val bytes = context.assets.open(path).use { it.readBytes() }
            bytes to "内置（${entry.model}@${entry.dirName}）"
        } catch (e: Exception) {
            onLog("读取 assets/$path 失败: ${e.message}")
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