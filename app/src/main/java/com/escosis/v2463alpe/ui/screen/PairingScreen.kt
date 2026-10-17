package com.escosis.v2463alpe.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.escosis.v2463alpe.AdbPairingHelper
import com.escosis.v2463alpe.PreloadPusher
import com.escosis.v2463alpe.R
import com.escosis.v2463alpe.ui.AppStateHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

private val BlockParentScroll = object : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        return available
    }
}

private const val BUSY_PAIR = "pair"
private const val BUSY_DISCONNECT = "disconnect"
private const val BUSY_CONNECT = "connect"
private const val BUSY_PUSH = "push"

@Composable
fun PairingScreen() {
    val context = LocalContext.current
    val status by AppStateHolder.status.collectAsState()
    val scope = rememberCoroutineScope()

    val logs = remember { mutableStateListOf<String>() }
    var busyAction by remember { mutableStateOf<String?>(null) }

    var showPairForm by remember { mutableStateOf(false) }
    var inputHost by remember { mutableStateOf("") }
    var inputPort by remember { mutableStateOf("") }
    var inputCode by remember { mutableStateOf("") }
    var showSplitDialog by remember { mutableStateOf(false) }
    var showCmdDialog by remember { mutableStateOf(false) }

    fun appendLog(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val line = "[$time] $msg"
        scope.launch(Dispatchers.Main) {
            logs.add(line)
            while (logs.size > 200) logs.removeAt(0)
        }
    }

    LaunchedEffect(Unit) {
        if (logs.isEmpty()) appendLog("就绪")
        if (inputHost.isEmpty()) {
            inputHost = getLocalIpAddress() ?: "127.0.0.1"
        }
    }

    val logListState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isEmpty()) return@LaunchedEffect
        val info = logListState.layoutInfo
        val total = info.totalItemsCount
        if (total == 0) return@LaunchedEffect
        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
        if (lastVisible >= total - 3) {
            logListState.animateScrollToItem(total - 1)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        flingBehavior = ScrollableDefaults.flingBehavior(),
    ) {
        item(key = "pair_section") {
            Column {
                SectionTitle("无线调试配对")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 0.dp)
                        ) {
                            Text(
                                text = when {
                                    status.isLoading -> "状态：检测中..."
                                    status.isConnected -> "状态：已连接 :${status.connectedPort}"
                                    status.isPaired -> "状态：已配对（未连接）"
                                    else -> "状态：未配对"
                                },
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            if (!status.isLoading && !status.wifiConnected) {
                                Text(
                                    "提示：请先连接 Wi-Fi",
                                    modifier = Modifier.padding(top = 4.dp),
                                    color = MiuixTheme.colorScheme.error,
                                )
                            }
                        }

                        if (showPairForm) {
                            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                                MiuixField(inputHost, { inputHost = it }, "IP 地址")
                                Spacer(Modifier.height(8.dp))
                                MiuixField(
                                    inputPort,
                                    { inputPort = it.filter { c -> c.isDigit() } },
                                    "配对端口",
                                    KeyboardType.Number,
                                )
                                Spacer(Modifier.height(8.dp))
                                MiuixField(
                                    inputCode,
                                    { inputCode = it.filter { c -> c.isDigit() }.take(6) },
                                    "6 位配对码",
                                    KeyboardType.NumberPassword,
                                )
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Button(
                                        onClick = {
                                            if (busyAction != null) return@Button
                                            val port = inputPort.toIntOrNull()
                                            if (inputHost.isBlank() || port == null || inputCode.length != 6) {
                                                appendLog("请填写完整信息（端口数字，配对码 6 位）")
                                                return@Button
                                            }
                                            busyAction = BUSY_PAIR
                                            appendLog("开始配对 $inputHost:$port...")
                                            scope.launch {
                                                AdbPairingHelper.pair(context, inputHost, port, inputCode)
                                                var paired = false
                                                for (i in 0 until 20) {
                                                    delay(500)
                                                    if (AdbPairingHelper.isPaired()) {
                                                        paired = true
                                                        break
                                                    }
                                                }
                                                if (paired) {
                                                    appendLog("配对成功")
                                                    AppStateHolder.refreshNow(context)
                                                    showPairForm = false
                                                    inputPort = ""
                                                    inputCode = ""
                                                } else {
                                                    appendLog("配对超时或失败")
                                                }
                                                busyAction = null
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        enabled = busyAction == null,
                                    ) { Text(if (busyAction == BUSY_PAIR) "配对中..." else "配对") }
                                    Button(
                                        onClick = {
                                            showPairForm = false
                                            inputPort = ""
                                            inputCode = ""
                                        },
                                        modifier = Modifier.weight(1f),
                                        enabled = busyAction == null,
                                    ) { Text("取消") }
                                }
                                Spacer(Modifier.height(12.dp))
                            }
                        } else {
                            when {
                                status.isConnected -> {
                                    val selfBusy = busyAction == BUSY_DISCONNECT
                                    ArrowPreference(
                                        title = if (selfBusy) "处理中..." else "断开连接",
                                        summary = "关闭无线调试并断开当前连接",
                                        modifier = Modifier.alpha(
                                            if (busyAction != null && !selfBusy) 0.4f else 1f
                                        ),
                                        startAction = {
                                            Icon(
                                                painterResource(R.drawable.ic_close),
                                                contentDescription = null,
                                                modifier = Modifier.padding(end = 6.dp).size(24.dp),
                                                tint = MiuixTheme.colorScheme.onSurface,
                                            )
                                        },
                                        onClick = {
                                            if (busyAction != null) return@ArrowPreference
                                            busyAction = BUSY_DISCONNECT
                                            appendLog("断开连接并关闭无线调试...")
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    AdbPairingHelper.disconnect(context)
                                                }
                                                delay(500)
                                                appendLog("已断开")
                                                AppStateHolder.refreshNow(context)
                                                busyAction = null
                                            }
                                        },
                                    )
                                }

                                status.isPaired -> {
                                    val selfBusy = busyAction == BUSY_CONNECT
                                    ArrowPreference(
                                        title = if (selfBusy) "连接中..." else "一键连接",
                                        summary = "自动连接已配对的无线调试端口",
                                        modifier = Modifier.alpha(
                                            if ((busyAction != null && !selfBusy) || !status.wifiConnected) 0.4f else 1f
                                        ),
                                        startAction = {
                                            Icon(
                                                painterResource(R.drawable.ic_link),
                                                contentDescription = null,
                                                modifier = Modifier.padding(end = 6.dp).size(24.dp),
                                                tint = MiuixTheme.colorScheme.onSurface,
                                            )
                                        },
                                        onClick = {
                                            if (busyAction != null || !status.wifiConnected) return@ArrowPreference
                                            busyAction = BUSY_CONNECT
                                            appendLog("开始一键连接...")
                                            scope.launch {
                                                val ok = withContext(Dispatchers.IO) {
                                                    AdbPairingHelper.autoConnect(context) { msg ->
                                                        appendLog(msg)
                                                    }
                                                }
                                                appendLog(if (ok) "连接成功" else "连接失败")
                                                AppStateHolder.refreshNow(context)
                                                busyAction = null
                                            }
                                        },
                                    )
                                    ArrowPreference(
                                        title = "重新配对",
                                        summary = "重新输入配对端口与配对码",
                                        modifier = Modifier.alpha(
                                            if (busyAction != null) 0.4f else 1f
                                        ),
                                        startAction = {
                                            Icon(
                                                painterResource(R.drawable.ic_replay),
                                                contentDescription = null,
                                                modifier = Modifier.padding(end = 6.dp).size(24.dp),
                                                tint = MiuixTheme.colorScheme.onSurface,
                                            )
                                        },
                                        onClick = {
                                            if (busyAction != null) return@ArrowPreference
                                            showSplitDialog = true
                                        },
                                    )
                                }

                                else -> {
                                    val blocked = busyAction != null || !status.wifiConnected
                                    ArrowPreference(
                                        title = "开始配对",
                                        summary = "输入配对端口与 6 位配对码完成配对",
                                        modifier = Modifier.alpha(
                                            if (blocked) 0.4f else 1f
                                        ),
                                        startAction = {
                                            Icon(
                                                painterResource(R.drawable.ic_wifi),
                                                contentDescription = null,
                                                modifier = Modifier.padding(end = 6.dp).size(24.dp),
                                                tint = MiuixTheme.colorScheme.onSurface,
                                            )
                                        },
                                        onClick = {
                                            if (blocked) return@ArrowPreference
                                            showSplitDialog = true
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "push_section") {
            Column {
                SectionTitle("授权并推送")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 0.dp)
                        ) {
                            val pushReady = status.preloadPushed && status.hasWriteSecure

                            Text(
                                text = when {
                                    status.isLoading -> "状态：检测中..."
                                    pushReady -> "状态：已完成"
                                    !status.hasWriteSecure && !status.preloadPushed -> "状态：未授权且未推送"
                                    !status.hasWriteSecure -> "状态：未授权"
                                    else -> "状态：未推送"
                                },
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        val pushBusy = busyAction == BUSY_PUSH
                        ArrowPreference(
                            title = if (pushBusy) "处理中..." else "执行指令",
                            summary = "授予 WRITE_SECURE_SETTINGS 并推送 preload.so",
                            modifier = Modifier.alpha(
                                if ((busyAction != null && !pushBusy) || !status.isConnected) 0.4f else 1f
                            ),
                            startAction = {
                                Icon(
                                    painterResource(R.drawable.ic_play_arrow),
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 6.dp).size(24.dp),
                                    tint = MiuixTheme.colorScheme.onSurface,
                                )
                            },
                            onClick = {
                                if (busyAction != null) return@ArrowPreference
                                if (!status.isConnected) {
                                    Toast.makeText(context, "请先配对并连接无线调试后再执行指令", Toast.LENGTH_SHORT).show()
                                    return@ArrowPreference
                                }
                                busyAction = BUSY_PUSH
                                appendLog("开始授权并推送...")
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) {
                                        PreloadPusher.push(context) { msg ->
                                            appendLog(msg)
                                        }
                                    }
                                    appendLog(if (ok) "执行完成" else "执行失败")
                                    AppStateHolder.refreshNow(context)
                                    busyAction = null
                                }
                            },
                        )
                        ArrowPreference(
                            title = "查看指令",
                            modifier = Modifier.alpha(
                                if (busyAction != null) 0.4f else 1f
                            ),
                            startAction = {
                                Icon(
                                    painterResource(R.drawable.ic_code),
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 6.dp).size(24.dp),
                                    tint = MiuixTheme.colorScheme.onSurface,
                                )
                            },
                            onClick = {
                                if (busyAction != null) return@ArrowPreference
                                showCmdDialog = true
                            },
                        )
                    }
                }
            }
        }

        item(key = "log_section") {
            Column {
                SectionTitle("日志")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (logs.isEmpty()) {
                            Text(
                                "（无）",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                        } else {
                            SelectionContainer {
                                LazyColumn(
                                    state = logListState,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(240.dp)
                                        .nestedScroll(BlockParentScroll),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    items(logs) { line ->
                                        Text(
                                            text = line,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = MiuixTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSplitDialog) {
        AlertDialog(
            onDismissRequest = { showSplitDialog = false },
            containerColor = MiuixTheme.colorScheme.surface,
            titleContentColor = MiuixTheme.colorScheme.onSurface,
            textContentColor = MiuixTheme.colorScheme.onSurface,
            title = { Text("提示") },
            text = {
                Text(
                    "请开启「开发者设置」中的「无线调试」，在询问是否允许无线调试时勾选「始终允许」，然后利用分屏或小窗使本应用与设置同屏以进行配对"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSplitDialog = false
                    showPairForm = true
                    if (inputHost.isEmpty()) {
                        inputHost = getLocalIpAddress() ?: "127.0.0.1"
                    }
                }) {
                    Text("开始配对", color = MiuixTheme.colorScheme.primary)
                }
            },
        )
    }

    if (showCmdDialog) {
        val cmdGrant = "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
        val cmdPush = "adb push preload.so /data/local/tmp/preload.so && adb shell chmod 755 /data/local/tmp/preload.so"

        AlertDialog(
            onDismissRequest = { showCmdDialog = false },
            containerColor = MiuixTheme.colorScheme.surface,
            titleContentColor = MiuixTheme.colorScheme.onSurface,
            textContentColor = MiuixTheme.colorScheme.onSurface,
            title = { Text("查看指令") },
            text = {
                Column {
                    Text("1. 授予 WRITE_SECURE_SETTINGS", color = MiuixTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    SelectionContainer {
                        Text(
                            cmdGrant,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("2. 推送 preload.so", color = MiuixTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    SelectionContainer {
                        Text(
                            cmdPush,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCmdDialog = false }) {
                    Text("关闭", color = MiuixTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    val clip = context.getSystemService(ClipboardManager::class.java)
                    val text = "$cmdGrant\n$cmdPush"
                    val ok = try {
                        clip?.setPrimaryClip(ClipData.newPlainText("adb", text))
                        clip != null
                    } catch (e: Exception) {
                        false
                    }
                    Toast.makeText(
                        context,
                        if (ok) "已复制到剪贴板" else "复制失败",
                        Toast.LENGTH_SHORT
                    ).show()
                }) {
                    Text("复制全部", color = MiuixTheme.colorScheme.primary)
                }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.title3,
        color = MiuixTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
    )
}

@Composable
private fun MiuixField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = TextStyle(
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = 16.sp,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MiuixTheme.colorScheme.onSurface,
            unfocusedTextColor = MiuixTheme.colorScheme.onSurface,
            disabledTextColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            focusedBorderColor = MiuixTheme.colorScheme.primary,
            unfocusedBorderColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            focusedLabelColor = MiuixTheme.colorScheme.primary,
            unfocusedLabelColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            cursorColor = MiuixTheme.colorScheme.primary,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
        ),
    )
}

private fun getLocalIpAddress(): String? {
    return try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        for (iface in Collections.list(interfaces)) {
            if (iface.isLoopback || !iface.isUp) continue
            for (addr in Collections.list(iface.inetAddresses)) {
                if (addr.isLoopbackAddress) continue
                if (addr !is Inet4Address) continue
                val ip = addr.hostAddress ?: continue
                if (ip.startsWith("192.168.") || ip.startsWith("10.")
                    || ip.startsWith("172.")) {
                    return ip
                }
            }
        }
        null
    } catch (e: Exception) {
        null
    }
}