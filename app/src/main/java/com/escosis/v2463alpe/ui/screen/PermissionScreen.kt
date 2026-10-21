package com.escosis.v2463alpe.ui.screen

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.escosis.v2463alpe.BootReceiver
import com.escosis.v2463alpe.LockScreenActivity
import com.escosis.v2463alpe.Prefs
import com.escosis.v2463alpe.R
import com.escosis.v2463alpe.ksuDisplayName
import com.escosis.v2463alpe.ui.AppStateHolder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val BUILTIN_KSU_PACKAGES = setOf(
    "me.weishu.kernelsu",
    "vctsrt.cntgtj.uqfwgg",
    "com.sukisu.ultra",
    "com.resukisu.resukisu",
)

@Composable
fun PermissionScreen(onNavigateToPairing: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val status by AppStateHolder.status.collectAsState()

    var directRequestInProgress by remember { mutableStateOf(false) }
    var showGrantDialog by remember { mutableStateOf(false) }
    var showWirelessDialog by remember { mutableStateOf(false) }
    var showKsuManagerDialog by remember { mutableStateOf(false) }
    var stepIndex by remember { mutableIntStateOf(-1) }
    var waitingForResume by remember { mutableStateOf(false) }
    var isAutoRequest by remember { mutableStateOf(false) }
    var pendingWirelessGuide by remember { mutableStateOf(false) }
    var requestGeneration by remember { mutableIntStateOf(0) }
    var resumeJob by remember { mutableStateOf<Job?>(null) }

    val steps = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= 37) add("localNetwork")
            add("overlay")
            add("battery")
            add("oem")
        }
    }

    val localNetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { stepIndex++ }

    fun doOpenAppDetails() {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
        )
    }

    fun doOpenKsuManager() {
        val pkg = Prefs.getKsuPackage(context)
        if (pkg.isBlank()) {
            Toast.makeText(context, "请先在设置中配置管理器包名", Toast.LENGTH_SHORT).show()
            return
        }
        if (pkg !in BUILTIN_KSU_PACKAGES) {
            Toast.makeText(
                context,
                "自定义包名无法直接打开，请手动从桌面启动管理器",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            Toast.makeText(
                context,
                "未检测到已安装的 ${ksuDisplayName(pkg)}",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun startRequest(auto: Boolean) {
        if (directRequestInProgress) return
        Prefs.setFirstLaunchPending(context, false)
        directRequestInProgress = true
        waitingForResume = false
        pendingWirelessGuide = false
        isAutoRequest = auto

        val s = AppStateHolder.status.value
        val start = steps.indexOfFirst { step ->
            when (step) {
                "localNetwork" -> !s.hasLocalNetwork
                "overlay"      -> !s.hasOverlay
                "battery"      -> !s.hasBatteryOpt
                "oem"          -> true
                else           -> true
            }
        }
        stepIndex = if (start < 0) steps.size else start
        requestGeneration++
    }

    LaunchedEffect(requestGeneration, stepIndex) {
        val idx = stepIndex
        if (idx < 0) return@LaunchedEffect
        if (idx >= steps.size) {
            directRequestInProgress = false
            waitingForResume = false
            return@LaunchedEffect
        }
        when (steps[idx]) {
            "localNetwork" -> {
                localNetLauncher.launch("android.permission.ACCESS_LOCAL_NETWORK")
            }
            "overlay" -> {
                waitingForResume = true
                try {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                } catch (e: Exception) {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                }
            }
            "battery" -> {
                waitingForResume = true
                try {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                } catch (e: Exception) {
                    waitingForResume = false
                    stepIndex++
                }
            }
            "oem" -> {
                showGrantDialog = true
                directRequestInProgress = false
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            when {
                waitingForResume -> {
                    waitingForResume = false
                    resumeJob?.cancel()
                    resumeJob = scope.launch {
                        delay(500)
                        stepIndex++
                    }
                }
                pendingWirelessGuide -> {
                    pendingWirelessGuide = false
                    resumeJob?.cancel()
                    resumeJob = scope.launch {
                        delay(500)
                        if (!AppStateHolder.status.value.hasWriteSecure) {
                            showWirelessDialog = true
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        delay(600)
        if (Prefs.isFirstLaunchPending(context)) {
            startRequest(auto = true)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "get_methods") {
            Column {
                Text(
                    text = "获取方式",
                    style = MiuixTheme.textStyles.title3,
                    color = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "直接申请",
                        summary = "申请：悬浮窗、忽略电池优化、自启动、锁屏显示、后台弹出界面",
                        startAction = {
                            Icon(
                                painterResource(R.drawable.ic_settings),
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp).size(24.dp),
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        },
                        onClick = { startRequest(auto = false) },
                    )
                    ArrowPreference(
                        title = "无线调试",
                        summary = "申请 WRITE_SECURE_SETTINGS，并始终允许通过当前网络进行无线调试",
                        startAction = {
                            Icon(
                                painterResource(R.drawable.ic_wifi),
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp).size(24.dp),
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        },
                        onClick = { showWirelessDialog = true },
                    )
                    ArrowPreference(
                        title = "管理器授权",
                        summary = "提权后在管理器中授予 ROOT ，并启用传统 SU 命令支持",
                        startAction = {
                            Icon(
                                painterResource(R.drawable.ic_tag),
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp).size(24.dp),
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        },
                        onClick = { showKsuManagerDialog = true },
                    )
                }
            }
        }

        item(key = "status_list") {
            Column {
                Text(
                    text = "状态检查",
                    style = MiuixTheme.textStyles.title3,
                    color = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        PermissionItem(
                            name = "悬浮窗",
                            summary = "用于在锁屏和后台弹出界面",
                            ok = status.hasOverlay,
                            loading = status.isLoading
                        )
                        PermissionItem(
                            name = "忽略电池优化",
                            summary = "防止服务被系统休眠",
                            ok = status.hasBatteryOpt,
                            loading = status.isLoading
                        )
                        PermissionItem(
                            name = "本地网络",
                            summary = "用于 mDNS 自动发现无线调试",
                            ok = status.hasLocalNetwork,
                            loading = status.isLoading
                        )
                        PermissionItem(
                            name = "WRITE_SECURE_SETTINGS",
                            summary = "用于自动开关无线调试",
                            ok = status.hasWriteSecure,
                            loading = status.isLoading
                        )
                        ManualItem(
                            name = "自启动",
                            summary = "防止应用被系统清理",
                        )
                        ManualItem(
                            name = "锁屏显示",
                            summary = "在锁屏界面弹出提权面板",
                        )
                        ManualItem(
                            name = "后台弹出界面",
                            summary = "在后台状态拉起提权面板",
                        )
                        ManualItem(
                            name = "始终允许无线调试",
                            summary = "用于自动连接无线调试（由于对话框的显示需解锁）",
                        )
                        PermissionItem(
                            name = "ROOT",
                            summary = "用于显示提权状态，阻止提权完成执行软重启后的锁屏提权面板显示",
                            ok = status.hasRoot,
                            loading = status.isLoading
                        )
                        ManualItem(
                            name = "管理器传统 SU 命令支持",
                            summary = "用于检测提权状态以及修复假权限",
                        )
                    }
                }
            }
        }

        item(key = "test_lock_screen") {
            Column {
                Text(
                    text = "测试",
                    style = MiuixTheme.textStyles.title3,
                    color = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "测试锁屏界面",
                        startAction = {
                            Icon(
                                painterResource(R.drawable.ic_lock),
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp).size(24.dp),
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        },
                        onClick = {
                            val intent = Intent(context, LockScreenActivity::class.java).apply {
                                putExtra("EXTRA_IS_TEST", true)
                                putExtra(BootReceiver.EXTRA_MODE, BootReceiver.MODE_STEP1)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        },
                    )
                }
            }
        }
    }

    if (showGrantDialog) {
        AlertDialog(
            onDismissRequest = { showGrantDialog = false },
            containerColor = MiuixTheme.colorScheme.surface,
            titleContentColor = MiuixTheme.colorScheme.onSurface,
            textContentColor = MiuixTheme.colorScheme.onSurface,
            title = { Text("授予 OEM 权限",fontSize = 20.sp,) },
            text = {
                Text(
                    "将前往设置中的应用详情页，请点击「查看所有权限」，并开启以下项：\n\n" +
                            "· 自启动\n" +
                            "· 锁屏显示\n" +
                            "· 后台弹出界面\n\n" +
                            "请注意，这三项权限为厂商自定义权限，应用无法直接检测其是否成功获取，请确保已获取后再重启执行提权。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showGrantDialog = false
                    if (isAutoRequest) {
                        pendingWirelessGuide = true
                    }
                    doOpenAppDetails()
                }) {
                    Text("前往设置", color = MiuixTheme.colorScheme.primary)
                }
            },
        )
    }

    if (showWirelessDialog) {
        AlertDialog(
            onDismissRequest = { showWirelessDialog = false },
            containerColor = MiuixTheme.colorScheme.surface,
            titleContentColor = MiuixTheme.colorScheme.onSurface,
            textContentColor = MiuixTheme.colorScheme.onSurface,
            title = { Text("无线调试授权",fontSize = 20.sp,) },
            text = {
                Text(
                    "WRITE_SECURE_SETTINGS 是系统级权限，无法通过普通权限申请授予，" +
                            "只能借助无线调试（ADB）方式完成。\n\n" +
                            "即将前往配对页面，请在配对与连接后，点击「执行指令」授予该权限，同时推送提权必需的 preload.so。\n\n" +
                    "在开启无线调试时请务必勾选「始终允许」。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showWirelessDialog = false
                    onNavigateToPairing()
                }) {
                    Text("前往配对", color = MiuixTheme.colorScheme.primary)
                }
            },
        )
    }

    if (showKsuManagerDialog) {
        val pkg = Prefs.getKsuPackage(context)
        val name = if (pkg.isBlank()) "未知管理器" else ksuDisplayName(pkg)
        val isBuiltin = pkg in BUILTIN_KSU_PACKAGES
        AlertDialog(
            onDismissRequest = { showKsuManagerDialog = false },
            containerColor = MiuixTheme.colorScheme.surface,
            titleContentColor = MiuixTheme.colorScheme.onSurface,
            textContentColor = MiuixTheme.colorScheme.onSurface,
            title = { Text("管理器授权",fontSize = 20.sp,) },
            text = {
                Text(
                    if (!isBuiltin) {
                        "当前为自定义包名（$pkg），无法直接打开管理器。\n\n" +
                                "请在提权后手动启动该管理器，并为本应用授予 ROOT 权限，" +
                                "并确保已启用「传统 SU 命令支持」。"
                    } else {
                        "即将打开 $name。\n\n" +
                                "请在提权后于该管理器中为本应用授予 ROOT 权限，" +
                                "并确保已启用「传统 SU 命令支持」。"
                    }
                    + "\n\nROOT 权限有无不影响提权流程；传统 SU 命令支持在管理器中应当是默认启用的，无需配置，若提权后发现禁用则请启用后重启重新提权。"
                )
            },
            confirmButton = {
                if (isBuiltin) {
                    TextButton(onClick = {
                        showKsuManagerDialog = false
                        doOpenKsuManager()
                    }) {
                        Text("打开管理器", color = MiuixTheme.colorScheme.primary)
                    }
                } else {
                    TextButton(onClick = { showKsuManagerDialog = false }) {
                        Text("知道了", color = MiuixTheme.colorScheme.primary)
                    }
                }
            },
        )
    }
}

@Composable
private fun PermissionItem(
    name: String,
    summary: String,
    ok: Boolean,
    loading: Boolean
) {
    val tint = when {
        loading -> MiuixTheme.colorScheme.onSurfaceVariantSummary
        ok -> if (MiuixTheme.isDynamicColor) MiuixTheme.colorScheme.primary
        else Color(0xFF4ADE80)
        else -> if (MiuixTheme.isDynamicColor) MiuixTheme.colorScheme.error
        else Color(0xFFEF4444)
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (loading) {
                Text(text = "…", color = tint, fontSize = 16.sp)
            } else {
                Icon(
                    painter = painterResource(
                        id = if (ok) R.drawable.ic_check else R.drawable.ic_close
                    ),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Column {
            Text(name, color = MiuixTheme.colorScheme.onSurface, fontSize = 15.sp)
            Text(
                summary,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ManualItem(
    name: String,
    summary: String
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_priority_high),
                contentDescription = null,
                tint = Color(0xFFF59E0B),
                modifier = Modifier.size(20.dp),
            )
        }
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = MiuixTheme.colorScheme.onSurface, fontSize = 15.sp)
                Text(
                    "  需确认",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp
                )
            }
            Text(
                summary,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp
            )
        }
    }
}