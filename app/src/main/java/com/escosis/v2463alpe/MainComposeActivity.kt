package com.escosis.v2463alpe

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.escosis.v2463alpe.ui.AppNavigation
import com.escosis.v2463alpe.ui.AppStateHolder
import com.escosis.v2463alpe.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        var keepSplash = true
        splashScreen.setKeepOnScreenCondition { keepSplash }

        AdbPairingHelper.init(this)
        enableEdgeToEdge()

        setContent {
            AppTheme {
                val isInitDone by AppStateHolder.isInitDone.collectAsState()
                keepSplash = !isInitDone

                var pendingUpdate by remember { mutableStateOf<UpdateInfo?>(null) }

                LaunchedEffect(Unit) {
                    if (!Prefs.isAutoCheckUpdate(this@MainComposeActivity)) return@LaunchedEffect
                    val current = UpdateChecker.getCurrentVersion(this@MainComposeActivity)
                    val info = withContext(Dispatchers.IO) { UpdateChecker.fetchLatest() }
                        ?: return@LaunchedEffect
                    if (UpdateChecker.isNewer(info.version, current)) {
                        pendingUpdate = info
                    }
                }

                pendingUpdate?.let { info ->
                    UpdateDialog(
                        info = info,
                        currentVersion = UpdateChecker.getCurrentVersion(this@MainComposeActivity),
                        onDismiss = { pendingUpdate = null },
                        onOpenReleasePage = {
                            pendingUpdate = null
                            val url = info.releaseUrl.ifBlank { info.downloadUrl }
                            if (url.isNotBlank()) openUrlPreferringBrowser(url)
                        },
                    )
                }

                AppNavigation()
            }
        }
    }

    private fun openUrlPreferringBrowser(url: String) {
        if (url.isBlank()) return
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val candidates = packageManager.queryIntentActivities(intent, 0)
            val browser = candidates.firstOrNull {
                val pkg = it.activityInfo.packageName
                pkg != "com.github.android" &&
                        !pkg.contains("github", ignoreCase = true)
            }
            if (browser != null) {
                intent.setPackage(browser.activityInfo.packageName)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("MainComposeActivity", "打开链接失败", e)
            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            } catch (_: Exception) {}
        }
    }
}