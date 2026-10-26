package com.escosis.v2463alpe

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.escosis.v2463alpe.ui.AppStateHolder
import com.escosis.v2463alpe.ui.theme.AppTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LockScreenActivity : ComponentActivity() {

    private val logs = mutableStateListOf<String>()
    private var busy by mutableStateOf(false)
    private var waitingForUnlock by mutableStateOf(false)

    private var mode: String = BootReceiver.MODE_STEP1
    private lateinit var screenOffReceiver: BroadcastReceiver
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        mode = intent.getStringExtra(BootReceiver.EXTRA_MODE) ?: BootReceiver.MODE_STEP1
        val isStep2 = BootReceiver.MODE_STEP2 == mode

        val isTest = intent.getBooleanExtra("EXTRA_IS_TEST", false)

        if (!Prefs.isAppEnabled(this) && !isTest) {
            Log.d(TAG, "应用已禁用，直接 finish")
            finish()
            return
        }

        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (!isTest && !isStep2 && km != null && !km.isKeyguardLocked) {
            Log.d(TAG, "非锁屏且非第二步，直接 finish")
            finish()
            return
        }

        screenOffReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (Intent.ACTION_SCREEN_OFF == intent?.action
                    && waitingForUnlock
                    && BootReceiver.MODE_STEP2 == mode
                ) {
                    Log.d(TAG, "用户重新锁屏，1 秒后自动重试")
                    mainHandler.postDelayed({
                        if (!isFinishing && waitingForUnlock) {
                            waitingForUnlock = false
                            appendLog("用户已返回，自动重试")
                            performConnectAndEscalate()
                        }
                    }, 1000)
                }
            }
        }
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))

        setContent {
            AppTheme {
                LockScreenContent(
                    logs = logs,
                    busy = busy,
                    isStep2 = isStep2,
                    onConnectEscalate = { performConnectAndEscalate() },
                    onUnlock = { performUnlock() },
                )
            }
        }

        appendLog("模式: " + if (isStep2) "第二步" else "第一步")
        appendLog(if (isStep2) "将自动开始第二步" else "点击「连接并提权」开始")

        if (isStep2 && !isTest) {
            mainHandler.postDelayed({ performConnectAndEscalate() }, 500)
        }
    }

    override fun onResume() {
        super.onResume()
        if (BootReceiver.MODE_STEP2 == mode) return

        val isTest = intent.getBooleanExtra("EXTRA_IS_TEST", false)
        if (isTest) return

        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (km == null || km.isKeyguardLocked) return

        Log.d(TAG, "onResume: 疑似未锁屏，800ms 后二次确认")
        mainHandler.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            val km2 = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            if (km2 != null && !km2.isKeyguardLocked) {
                Log.d(TAG, "onResume: 确认未锁屏，finish")
                finish()
            } else {
                Log.d(TAG, "onResume: 二次确认仍锁定，保持界面")
            }
        }, 800)
    }

    private fun appendLog(msg: String) {
        mainHandler.post {
            val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            logs.add("[$time] $msg")
            if (logs.size > 200) {
                repeat(logs.size - 200) { logs.removeAt(0) }
            }
        }
    }

    private fun performUnlock() {
        if (busy) return
        val isStep2 = BootReceiver.MODE_STEP2 == mode
        if (!isStep2) {
            finish()
        } else {
            appendLog("请解锁，并允许无线调试（勾选“始终允许”）")
        }
        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (km != null && km.isKeyguardLocked) {
            km.requestDismissKeyguard(this, null)
        }
    }

    private fun performConnectAndEscalate() {
        if (busy) return
        Thread { runConnectAndEscalate() }.start()
    }

    private fun runConnectAndEscalate() {
        busy = true
        val isStep2 = BootReceiver.MODE_STEP2 == mode

        try {
            appendLog("elapsedRealtime=" + SystemClock.elapsedRealtime() + "ms")
            appendLog("adb_wifi_enabled=" + android.provider.Settings.Global.getInt(
                contentResolver, "adb_wifi_enabled", -1))
            enableWirelessDebug()

            appendLog("正在连接无线调试...")
            val mdnsFailed = booleanArrayOf(false)
            val ok = AdbPairingHelper.autoConnect(this) { msg ->
                appendLog(msg)
                if (msg.contains("mDNS 未发现")) mdnsFailed[0] = true
            }

            if (!ok) {
                if (mdnsFailed[0]) {
                    if (isStep2) {
                        appendLog("【重要】mDNS 失败：可能是当前 Wi-Fi 未授权无线调试")
                        appendLog("【重要】请解锁，并允许无线调试（勾选“始终允许”），然后重试")
                        appendLog("【重要】锁屏返回此界面将自动重试")
                        waitingForUnlock = true
                    } else {
                        appendLog("【重要】mDNS 失败：可能是当前 Wi-Fi 未授权无线调试")
                        appendLog("【重要】请解锁，并允许无线调试（勾选“始终允许”）")
                        appendLog("【重要】然后重启手机重新尝试")
                    }
                } else {
                    appendLog("连接失败，可重试")
                }
                busy = false
                return
            }

            appendLog("无线调试已连接")

            if (isStep2) {
                runStep2AfterConnected()
                return
            }

            appendLog("执行第一步提权...")
            AdbPairingHelper.escalate(
                this,
                { msg -> appendLog(msg) },
                { success ->
                    if (success) {
                        appendLog("第一阶段成功但设备未自动重启，复用当前连接直接进入第二阶段")
                        mode = BootReceiver.MODE_STEP2
                        Thread { runStep2AfterConnected() }.start()
                    } else {
                        appendLog("第一阶段失败，设备即将重启")
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "异常", e)
            appendLog("异常: " + e.message)
            busy = false
        }
    }

    private fun runStep2AfterConnected() {
        try {
            val pkgName = Prefs.getKsuPackage(this)
            val serial = "127.0.0.1:" + AdbPairingHelper.getConnectedPort()

            appendLog("目标管理器包名: $pkgName")
            appendLog("当前设备: $serial")
            appendLog("正在通过 find 定位 libksud.so ...")

            val innerCmd = "export ANDROID_SERIAL=$serial; " +
                    "echo \"Target: $pkgName\"; " +
                    "so_path=\$(find /data/app -name libksud.so 2>/dev/null | grep \"$pkgName\" | head -n 1); " +
                    "if [ -z \"\$so_path\" ]; then echo 'SO_NOT_FOUND'; exit 1; fi; " +
                    "echo \"Found SO: \$so_path\"; " +
                    "chmod 755 \"\$so_path\" 2>&1 || { echo 'CHMOD_FAILED'; exit 1; }; " +
                    "\"\$so_path\" late-load --allow-shell --package-name $pkgName 2>&1; " +
                    "echo 'LATE_EXIT:'\$?"

            val safeInnerCmd = innerCmd.replace("'", "'\\''")

            val suPaths = listOf(
                "su",
                "/data/local/tmp/su",
                "/apex/com.android.virt/bin/su"
            )
            var lateLoadOk = false
            for ((idx, suPath) in suPaths.withIndex()) {
                val cmd = "$suPath -c '$safeInnerCmd'"
                appendLog("执行 late-load（尝试 $suPath）...")

                val sb = StringBuilder()
                val doneLatch = CountDownLatch(1)
                AdbClient.shellStream(this, cmd, serial) { line ->
                    appendLog(line)
                    sb.append(line).append('\n')
                    if (line.startsWith("[结束]")) {
                        doneLatch.countDown()
                    }
                }

                val finished = doneLatch.await(20, TimeUnit.SECONDS)
                if (!finished) {
                    appendLog("late-load 超时 20s")
                } else {
                    appendLog("late-load 命令已结束")
                }

                val out = sb.toString()
                val daemonRefused = out.contains("su: connect daemon: Connection refused")
                if (daemonRefused) {
                    if (idx < suPaths.lastIndex) {
                        appendLog("$suPath 不可用，改用 ${suPaths[idx + 1]} 重试")
                        continue
                    }
                    appendLog("所有 su 路径均不可用")
                    appendLog("late-load 失败，停止第二步")
                    busy = false
                    return
                }

                val soNotFound = out.contains("SO_NOT_FOUND")
                val chmodFailed = out.contains("CHMOD_FAILED")
                val lateExit = Regex("LATE_EXIT:(\\d+)").find(out)
                    ?.groupValues?.get(1)?.toIntOrNull()
                if (soNotFound || chmodFailed || (lateExit != null && lateExit != 0)) {
                    val reason = when {
                        soNotFound -> "SO_NOT_FOUND"
                        chmodFailed -> "CHMOD_FAILED"
                        else -> "LATE_EXIT=$lateExit"
                    }
                    appendLog("late-load 失败（$reason），停止第二步")
                    busy = false
                    return
                }
                lateLoadOk = true
                break
            }

            if (!lateLoadOk) {
                appendLog("late-load 未成功，停止第二步")
                busy = false
                return
            }

            appendLog("等待 /system/bin/su 就绪...")
            val realSu = "/system/bin/su"
            var suReady = false
            for (i in 0 until 30) {
                val idOut = AdbClient.shellWithSerial(this, serial, "$realSu -c id")
                if (idOut.contains("uid=0")) {
                    suReady = true
                    appendLog("su 已就绪 (" + (i * 500) + "ms)")
                    break
                }
                Thread.sleep(500)
            }

            if (!suReady) {
                appendLog("su 未就绪，第二步失败")
                busy = false
                return
            }

            appendLog("修复 AVF su...")
            val wrapper = "#!/system/bin/sh\nexec $realSu \"\$@\"\n"
            val b64 = Base64.encodeToString(wrapper.toByteArray(), Base64.NO_WRAP)
            val fixCmd = "$realSu -c \"rm -f /apex/com.android.virt/bin/su" +
                    " && echo '$b64' | base64 -d > /apex/com.android.virt/bin/su" +
                    " && chmod 755 /apex/com.android.virt/bin/su" +
                    " && echo FIX_OK\""
            val fixOut = AdbClient.shellWithSerial(this, serial, fixCmd)
            appendLog("fix: " + fixOut.trim())

            val verifyOut = AdbClient.shellWithSerial(this, serial, "su -c id")
            appendLog("verify: " + verifyOut.trim())

            if (!verifyOut.contains("uid=0")) {
                appendLog("验证失败，未记录 Root 状态，第二步失败")
                busy = false
                return
            }

            Prefs.setHasRoot(this, true)
            Prefs.setRootChecked(this, true)
            Prefs.setActiveKsuPackage(this, pkgName)
            appendLog("状态已更新：已 Root")

            try {
                AppStateHolder.refreshNow(this)
            } catch (_: Exception) {}

            if (Prefs.isAutoSoftReboot(this)) {
                val softRebootFlag = getSoftRebootFlagFile()
                var flagWritten = false
                try {
                    softRebootFlag.writeText("1")
                    flagWritten = true
                    appendLog("已写入软重启标志")
                } catch (e: Exception) {
                    Log.e(TAG, "写软重启标志失败", e)
                    appendLog("写软重启标志失败: " + e.message)
                }

                appendLog("执行 ksud soft-reboot...")
                try {
                    val rebootOut = AdbClient.shellWithSerial(
                        this, serial, "su -c 'ksud soft-reboot'"
                    )
                    appendLog("soft-reboot: " + rebootOut.trim())
                } catch (e: Exception) {
                    Log.e(TAG, "soft-reboot 异常", e)
                    appendLog("soft-reboot 异常: " + e.message)
                    if (flagWritten) {
                        try {
                            softRebootFlag.delete()
                            appendLog("软重启命令异常，已删除标志")
                        } catch (_: Exception) {}
                    }
                }
                appendLog("软重启已触发，跳过无线调试清理")
                try {
                    AppStateHolder.refreshNow(this)
                } catch (_: Exception) {}
                mainHandler.post { finish() }
                return
            }

            appendLog("正在关闭无线调试...")
            try {
                android.provider.Settings.Global.putInt(contentResolver, "adb_wifi_enabled", 0)
                AdbServerManager.killExistingServer(this)
            } catch (e: Exception) {
                Log.e(TAG, "关闭无线调试失败", e)
            }
            appendLog("无线调试已关闭")

            Thread.sleep(1000)
            appendLog("第二步完成，界面即将关闭")
            Thread.sleep(1000)
            mainHandler.post { finish() }
        } catch (e: Exception) {
            Log.e(TAG, "Step2 异常", e)
            appendLog("第二步异常: " + e.message)
            busy = false
        }
    }

    private fun enableWirelessDebug() {
        try {
            if (checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                val cur = android.provider.Settings.Global.getInt(
                    contentResolver, "adb_wifi_enabled", 0)
                if (cur == 0) {
                    android.provider.Settings.Global.putInt(
                        contentResolver, "adb_wifi_enabled", 1)
                    appendLog("已开启无线调试")
                    Thread.sleep(1500)
                }
            }
        } catch (e: Exception) {
            appendLog("开无线调试失败: " + e.message)
        }
    }

    private fun getSoftRebootFlagFile(): File {
        val deContext = createDeviceProtectedStorageContext()
        return File(deContext.filesDir, "soft_reboot_pending.flag")
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(screenOffReceiver) } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "LockScreenActivity"
    }
}

@Composable
private fun LockScreenContent(
    logs: List<String>,
    busy: Boolean,
    isStep2: Boolean,
    onConnectEscalate: () -> Unit,
    onUnlock: () -> Unit,
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(logs.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "日志",
                        color = MiuixTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    SelectionContainer {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .verticalScroll(scrollState),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            logs.forEach { line ->
                                Text(
                                    text = line,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { if (!busy) onConnectEscalate() },
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isStep2) "连接并提权（第二步）" else "连接并提权（第一步）",
                        color = if (busy) MiuixTheme.colorScheme.onSurfaceVariantSummary
                        else MiuixTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { if (!busy) onUnlock() },
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "解锁",
                        color = if (busy) MiuixTheme.colorScheme.onSurfaceVariantSummary
                        else MiuixTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}