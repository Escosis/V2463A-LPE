package com.escosis.v2463alpe

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class UpdateInfo(
    val tagName: String,
    val version: String,
    val downloadUrl: String,
    val releaseUrl: String,
)

object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val API_URL = "https://api.github.com/repos/Escosis/V2463A-LPE/releases/latest"
    private const val TIMEOUT_MS = 15_000

    fun getCurrentVersion(context: Context): String {
        return try {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName ?: "0"
        } catch (e: Exception) {
            Log.w(TAG, "读取版本号失败", e)
            "0"
        }
    }

    fun fetchLatest(): UpdateInfo? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(API_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "V2463A-LPE-UpdateChecker")
            }
            if (conn.responseCode != 200) {
                Log.w(TAG, "GitHub API 返回 ${conn.responseCode}")
                return null
            }
            val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            val json = JSONObject(body)
            val tagName = json.optString("tag_name", "")
            if (tagName.isEmpty()) return null
            val releaseUrl = json.optString("html_url", "")

            var apkUrl = ""
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    val name = a.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = a.optString("browser_download_url", "")
                        if (apkUrl.isNotEmpty()) break
                    }
                }
            }

            UpdateInfo(
                tagName = tagName,
                version = tagName.removePrefix("v"),
                downloadUrl = apkUrl,
                releaseUrl = releaseUrl,
            )
        } catch (e: Exception) {
            Log.e(TAG, "检查更新失败", e)
            null
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    fun isNewer(remoteVersion: String, currentVersion: String): Boolean =
        compareVersions(remoteVersion, currentVersion) > 0

    fun compareVersions(a: String, b: String): Int {
        val pa = a.removePrefix("v").split(".")
        val pb = b.removePrefix("v").split(".")
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrNull(i)?.toIntOrNull() ?: 0
            val y = pb.getOrNull(i)?.toIntOrNull() ?: 0
            if (x != y) return if (x > y) 1 else -1
        }
        return 0
    }
}

@Composable
fun UpdateDialog(
    info: UpdateInfo,
    currentVersion: String,
    onDismiss: () -> Unit,
    onOpenReleasePage: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MiuixTheme.colorScheme.surface,
        titleContentColor = MiuixTheme.colorScheme.onSurface,
        textContentColor = MiuixTheme.colorScheme.onSurface,
        title = {
            Text(
                "发现新版本",
                fontSize = 20.sp,
            )
        },
        text = {
            Column {
                Text("当前版本：$currentVersion")
                Spacer(Modifier.height(4.dp))
                Text("最新版本：${info.version}")
                Spacer(Modifier.height(8.dp))
                Text("建议打开 Release 页面查看更新内容并下载 APK。")
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenReleasePage) {
                Text("打开 Release 页", color = MiuixTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("稍后", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        },
    )
}