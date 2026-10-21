package com.escosis.v2463alpe.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.escosis.v2463alpe.Prefs
import com.escosis.v2463alpe.R
import com.escosis.v2463alpe.UpdateChecker
import com.escosis.v2463alpe.UpdateDialog
import com.escosis.v2463alpe.UpdateInfo
import com.escosis.v2463alpe.ui.theme.LocalSetThemeMode
import com.escosis.v2463alpe.ui.theme.LocalThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val flavorNames = listOf("KernelSU", "KernelSU Next", "SukiSU-Ultra", "BakaSU", "自定义")
private val flavorPackages = listOf(
    "me.weishu.kernelsu",
    "vctsrt.cntgtj.uqfwgg",
    "com.sukisu.ultra",
    "com.resukisu.resukisu"
)

@Composable
fun SettingsScreen(
    onNavigateToAbout: () -> Unit,
    onOverlayExpandedChange: (Boolean) -> Unit = {},
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val themeMode = LocalThemeMode.current
    val setThemeMode = LocalSetThemeMode.current
    var currentPkg by remember { mutableStateOf(Prefs.getKsuPackage(ctx)) }
    var selectedIndex by remember {
        val idx = flavorPackages.indexOf(currentPkg)
        mutableIntStateOf(if (idx >= 0) idx else 4)
    }

    var themeExpanded by remember { mutableStateOf(false) }
    var ksuExpanded by remember { mutableStateOf(false) }
    val anyExpanded = themeExpanded || ksuExpanded

    var checkingUpdate by remember { mutableStateOf(false) }
    var updateResultMsg by remember { mutableStateOf<String?>(null) }
    var pendingUpdate by remember { mutableStateOf<UpdateInfo?>(null) }

    SideEffect { onOverlayExpandedChange(anyExpanded) }
    DisposableEffect(Unit) {
        onDispose { onOverlayExpandedChange(false) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "app_config") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    var enabled by remember { mutableStateOf(Prefs.isAppEnabled(ctx)) }
                    SwitchPreference(
                        title = "应用启用",
                        summary = "关闭后开机不显示锁屏提权面板",
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            Prefs.setAppEnabled(ctx, it)
                        },
                        startAction = {
                            Icon(
                                painter = painterResource(R.drawable.ic_apps),
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp).size(24.dp),
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        },
                    )

                    OverlayDropdownPreference(
                        title = "管理器分支",
                        summary = "选择用于提权持久化的管理器分支",
                        items = flavorNames,
                        startAction = {
                            Icon(
                                painter = painterResource(R.drawable.ic_tag),
                                contentDescription = "管理器包名",
                                modifier = Modifier.padding(end = 6.dp).size(24.dp),
                                tint = MiuixTheme.colorScheme.onSurface
                            )
                        },
                        selectedIndex = selectedIndex,
                        onExpandedChange = { ksuExpanded = it },
                        onSelectedIndexChange = { newIndex ->
                            selectedIndex = newIndex
                            if (newIndex < 4) {
                                currentPkg = flavorPackages[newIndex]
                                Prefs.setKsuPackage(ctx, currentPkg)
                            } else {
                                if (currentPkg in flavorPackages) {
                                    currentPkg = ""
                                    Prefs.setKsuPackage(ctx, "")
                                }
                            }
                        }
                    )

                    AnimatedVisibility(
                        visible = selectedIndex == 4,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        OutlinedTextField(
                            value = currentPkg,
                            onValueChange = {
                                currentPkg = it.trim()
                                Prefs.setKsuPackage(ctx, currentPkg)
                            },
                            label = { Text("自定义包名") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            textStyle = TextStyle(
                                color = MiuixTheme.colorScheme.onSurface,
                                fontSize = 14.sp
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MiuixTheme.colorScheme.onSurface,
                                unfocusedTextColor = MiuixTheme.colorScheme.onSurface,
                                focusedBorderColor = MiuixTheme.colorScheme.primary,
                                unfocusedBorderColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                focusedLabelColor = MiuixTheme.colorScheme.primary,
                                unfocusedLabelColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                cursorColor = MiuixTheme.colorScheme.primary,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            )
                        )
                    }

                    var autoSoftReboot by remember { mutableStateOf(Prefs.isAutoSoftReboot(ctx)) }
                    SwitchPreference(
                        title = "提权完成自动软重启",
                        summary = "修复假权限后自动执行 ksud soft-reboot 以加载模块",
                        checked = autoSoftReboot,
                        onCheckedChange = {
                            autoSoftReboot = it
                            Prefs.setAutoSoftReboot(ctx, it)
                        },
                        startAction = {
                            Icon(
                                painter = painterResource(R.drawable.ic_round_power_settings_new),
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp).size(24.dp),
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        },
                    )
                }
            }
        }

        item(key = "theme") {
            Card(modifier = Modifier.fillMaxWidth()) {
                OverlayDropdownPreference(
                    title = "主题",
                    summary = "选择应用外观",
                    items = listOf("跟随系统", "浅色", "深色"),
                    startAction = {
                        Icon(
                            painter = painterResource(R.drawable.ic_palette),
                            contentDescription = "主题",
                            modifier = Modifier.padding(end = 6.dp).size(24.dp),
                            tint = MiuixTheme.colorScheme.onSurface
                        )
                    },
                    selectedIndex = themeMode,
                    onExpandedChange = { themeExpanded = it },
                    onSelectedIndexChange = { newIndex ->
                        setThemeMode(newIndex)
                    }
                )
            }
        }

        item(key = "update") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    var autoCheck by remember { mutableStateOf(Prefs.isAutoCheckUpdate(ctx)) }
                    SwitchPreference(
                        title = "自动检查更新",
                        summary = "应用启动时自动检查新版本",
                        checked = autoCheck,
                        onCheckedChange = {
                            autoCheck = it
                            Prefs.setAutoCheckUpdate(ctx, it)
                        },
                        startAction = {
                            Icon(
                                painter = painterResource(R.drawable.ic_round_autorenew),
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp).size(24.dp),
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        },
                    )

                    ArrowPreference(
                        title = "检查更新",
                        summary = updateResultMsg
                            ?: "当前版本 ${UpdateChecker.getCurrentVersion(ctx)}",
                        startAction = {
                            Icon(
                                painter = painterResource(R.drawable.ic_round_system_update_alt),
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp).size(24.dp),
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        },
                        onClick = {
                            if (checkingUpdate) return@ArrowPreference
                            checkingUpdate = true
                            updateResultMsg = "检查中..."
                            scope.launch {
                                val current = UpdateChecker.getCurrentVersion(ctx)
                                val info = withContext(Dispatchers.IO) {
                                    UpdateChecker.fetchLatest()
                                }
                                checkingUpdate = false
                                when {
                                    info == null -> {
                                        updateResultMsg = "检查失败，请检查网络"
                                    }
                                    UpdateChecker.isNewer(info.version, current) -> {
                                        updateResultMsg = "发现新版本 ${info.tagName}"
                                        pendingUpdate = info
                                    }
                                    else -> {
                                        updateResultMsg = "已是最新（$current）"
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }

        item(key = "about_entry") {
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = "关于",
                    startAction = {
                        Icon(
                            painter = painterResource(R.drawable.ic_round_info),
                            contentDescription = "关于",
                            modifier = Modifier.padding(end = 6.dp).size(24.dp),
                            tint = MiuixTheme.colorScheme.onSurface
                        )
                    },
                    onClick = onNavigateToAbout
                )
            }
        }
    }

    pendingUpdate?.let { info ->
        UpdateDialog(
            info = info,
            currentVersion = UpdateChecker.getCurrentVersion(ctx),
            onDismiss = { pendingUpdate = null },
            onOpenReleasePage = {
                pendingUpdate = null
                val url = info.releaseUrl.ifBlank { info.downloadUrl }
                if (url.isBlank()) {
                    Toast.makeText(ctx, "无可用链接", Toast.LENGTH_SHORT).show()
                } else {
                    openUrlPreferringBrowser(ctx, url)
                }
            },
        )
    }
}

private fun openUrlPreferringBrowser(ctx: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val candidates = ctx.packageManager.queryIntentActivities(intent, 0)
        val browser = candidates.firstOrNull {
            val pkg = it.activityInfo.packageName
            pkg != "com.github.android" &&
                    !pkg.contains("github", ignoreCase = true)
        }
        if (browser != null) {
            intent.setPackage(browser.activityInfo.packageName)
        }
        ctx.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(ctx, "打开链接失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}