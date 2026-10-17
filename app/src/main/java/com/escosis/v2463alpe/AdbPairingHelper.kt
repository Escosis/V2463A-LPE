package com.escosis.v2463alpe

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object AdbPairingHelper {

    private const val TAG = "AdbPairingHelper"

    @Volatile private var appContext: Context? = null
    @Volatile private var pairingState = false
    @Volatile private var connectedHostState: String? = null
    @Volatile private var connectedPortState = -1
    @Volatile private var autoConnectingState = false

    @JvmStatic
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun getKnownHostsFile(): File? {
        val ctx = appContext ?: return null
        return try {
            val deContext = ctx.createDeviceProtectedStorageContext()
            File(deContext.filesDir, "ext_storage/.android/adb_known_hosts.pb")
        } catch (e: Exception) {
            null
        }
    }

    @JvmStatic
    fun isPaired(): Boolean {
        val f = getKnownHostsFile() ?: return false
        return f.exists() && f.length() > 0
    }

    @JvmStatic fun isPairing(): Boolean = pairingState
    @JvmStatic fun isAutoConnecting(): Boolean = autoConnectingState
    @JvmStatic fun getConnectedHost(): String? = connectedHostState
    @JvmStatic fun getConnectedPort(): Int = connectedPortState

    @JvmStatic
    fun isWifiConnected(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (e: Exception) {
            false
        }
    }

    @JvmStatic
    fun pair(context: Context, host: String, port: Int, code: String) {
        if (pairingState) return
        pairingState = true

        CoroutineScope(Dispatchers.IO).launch {
            try {
                AdbServerManager.killExistingServer(context)
                Thread.sleep(300)

                val serverOk = AdbServerManager.ensureAdbServerWithRetry(context)
                if (!serverOk) {
                    Log.e(TAG, "ADB Server 启动失败")
                    return@launch
                }

                val output = AdbClient.pair(context, host, port, code)
                val success = output.contains("Successfully paired", ignoreCase = true)
                        || output.trimEnd().endsWith("Success")

                if (success) {
                    Log.d(TAG, "配对成功（密钥文件已生成）")
                } else {
                    Log.w(TAG, "配对失败: $output")
                }
            } finally {
                pairingState = false
            }
        }
    }

    @JvmStatic
    fun connect(context: Context, host: String, port: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            val output = AdbClient.connect(context, host, port)
            if (output.contains("connected", ignoreCase = true)
                || output.contains("already connected", ignoreCase = true)) {
                connectedHostState = host
                connectedPortState = port
                Log.d(TAG, "连接成功: $host:$port")
            } else {
                Log.w(TAG, "连接失败: $output")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @JvmStatic
    fun autoConnect(context: Context, onLog: ((String) -> Unit)? = null): Boolean {
        if (autoConnectingState) {
            onLog?.invoke("[跳过] 正在连接中")
            return false
        }
        autoConnectingState = true

        fun log(s: String) {
            Log.d(TAG, s)
            onLog?.invoke(s)
        }

        return try {
            val t0 = System.currentTimeMillis()
            val WIFI_TIMEOUT = 10_000L
            val MDNS_TOTAL = 30_000L
            val PUT_INT_INTERVAL = 3_000L

            log("[1/4] 检查 Wi-Fi...")
            val wifiDeadline = t0 + WIFI_TIMEOUT
            var wifiOk = isWifiConnected(context)
            while (!wifiOk && System.currentTimeMillis() < wifiDeadline) {
                try { Thread.sleep(300) } catch (_: InterruptedException) { break }
                wifiOk = isWifiConnected(context)
            }
            if (!wifiOk) {
                log("[失败] Wi-Fi 未连接（10s 超时）")
                return false
            }
            log("[1/4] Wi-Fi 已连接（" + (System.currentTimeMillis() - t0) + "ms）")

            if (!AdbServerManager.isServerReadyFast()) {
                AdbServerManager.killExistingServer(context)
                val serverOk = AdbServerManager.ensureAdbServerWithRetry(context)
                if (!serverOk) {
                    log("[失败] ADB Server 启动失败")
                    return false
                }
            }
            log("[2/4] ADB Server 就绪")

            log("[3/4] 等待系统 adbd 广播...")
            val mdnsStart = System.currentTimeMillis()
            var foundPort = -1
            var lastPutInt = 0L
            var round = 0

            while (System.currentTimeMillis() - mdnsStart < MDNS_TOTAL) {
                round++
                val elapsed = (System.currentTimeMillis() - mdnsStart) / 1000
                log("探测 #" + round + "（已等 " + elapsed + "s）")

                if (System.currentTimeMillis() - lastPutInt > PUT_INT_INTERVAL) {
                    try {
                        android.provider.Settings.Global.putInt(
                            context.getContentResolver(), "adb_wifi_enabled", 1)
                        lastPutInt = System.currentTimeMillis()
                    } catch (e: Exception) {
                        Log.w(TAG, "putInt 失败", e)
                    }
                }

                val latch = CountDownLatch(1)
                val portHolder = intArrayOf(-1)
                val mdns = AdbMdns(
                    context = context,
                    serviceType = AdbMdns.TLS_CONNECT,
                    onFound = { port ->
                        portHolder[0] = port
                        latch.countDown()
                    }
                )
                android.os.Handler(android.os.Looper.getMainLooper()).post { mdns.start() }

                val found = latch.await(2500, TimeUnit.MILLISECONDS)
                android.os.Handler(android.os.Looper.getMainLooper()).post { mdns.destroy() }

                if (found && portHolder[0] > 0) {
                    foundPort = portHolder[0]
                    break
                }

                try { Thread.sleep(500) } catch (_: InterruptedException) { break }
            }

            if (foundPort <= 0) {
                log("[失败] 30s 未发现 mDNS 广播")
                return false
            }
            log("[3/4] mDNS 发现端口: " + foundPort
                    + "（" + (System.currentTimeMillis() - mdnsStart) + "ms）")

            AdbClient.connect(context, "127.0.0.1", foundPort)
            try { Thread.sleep(400) } catch (_: InterruptedException) {}

            var connected = false
            for (i in 0 until 4) {
                val devices = AdbClient.devices(context)
                if (devices.lines().any { it.contains("\tdevice") && !it.contains("offline") }) {
                    connected = true
                    break
                }
                AdbClient.connect(context, "127.0.0.1", foundPort)
                try { Thread.sleep(400) } catch (_: InterruptedException) {}
            }

            if (connected) {
                connectedHostState = "127.0.0.1"
                connectedPortState = foundPort
                log("[4/4] 连接成功 127.0.0.1:" + foundPort
                        + "（" + (System.currentTimeMillis() - t0) + "ms）")
                true
            } else {
                log("[失败] connect 未成功")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "autoConnect 异常", e)
            onLog?.invoke("[异常] ${e.message}")
            false
        } finally {
            autoConnectingState = false
        }
    }

    @JvmStatic
    fun escalate(context: Context, onLog: ((String) -> Unit)? = null) {
        fun log(s: String) {
            Log.d(TAG, s)
            onLog?.invoke(s)
        }

        if (connectedHostState == null || connectedPortState <= 0) {
            log("[失败] 未连接，请先执行一键连接")
            return
        }

        val flagFile = getStage1FlagFile(context)
        val serial = "127.0.0.1:$connectedPortState"

        val cmd = "LD_PRELOAD=/data/local/tmp/preload.so /system/bin/id"
        log("执行: $cmd")
        log("目标设备: $serial")

        val stage1Done = AtomicBoolean(false)

        AdbClient.shellStream(context, cmd, serial) { line ->
            log(line)

            if (stage1Done.get()) return@shellStream

            if (line.contains("root cred patched") ||
                line.contains("embedded su daemon ready")) {
                if (!stage1Done.compareAndSet(false, true)) return@shellStream
                try {
                    flagFile.writeText("1")
                    log("[标志] 已写入 ${flagFile.absolutePath}")
                } catch (e: Exception) {
                    Log.e(TAG, "写标志失败", e)
                    log("[标志] 写入失败: ${e.message}")
                }
                log("[成功] 第一阶段提权完成，等待自动重启")
                return@shellStream
            }

            if (line.contains("cfi miss")) {
                if (!stage1Done.compareAndSet(false, true)) return@shellStream
                log("[失败] 首次 cfi miss，重启设备重新尝试")
                Thread {
                    Thread.sleep(1500)
                    try {
                        val out = AdbClient.runAdb(context, null, "-s", serial, "shell", "reboot")
                        log("[重启] 返回: " + out.trim().ifBlank { "(空)" })
                    } catch (e: Exception) {
                        Log.e(TAG, "重启失败", e)
                        log("[重启] 异常: " + e.message)
                    }
                }.start()
                return@shellStream
            }
        }
    }

    @JvmStatic
    fun getStage1FlagFile(context: Context): File {
        val deContext = context.createDeviceProtectedStorageContext()
        return File(deContext.filesDir, "escalate_stage1_done.flag")
    }

    @JvmStatic
    fun shell(context: Context, command: String): String {
        return AdbClient.shell(context, command)
    }

    @JvmStatic
    fun checkConnected(context: Context): Boolean {
        val devices = AdbClient.devices(context)
        for (line in devices.lines()) {
            if (line.contains("\tdevice") && !line.contains("offline")) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun disconnect(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val port = connectedPortState
                if (port > 0) {
                    try {
                        AdbClient.runAdb(context, null,
                            "-s", "127.0.0.1:$port",
                            "disconnect", "127.0.0.1:$port")
                    } catch (_: Exception) {}
                }

                AdbServerManager.killExistingServer(context)

                try {
                    android.provider.Settings.Global.putInt(
                        context.contentResolver, "adb_wifi_enabled", 0)
                } catch (e: Exception) {
                    Log.w(TAG, "关闭无线调试失败", e)
                }

                connectedHostState = null
                connectedPortState = -1
            } catch (e: Exception) {
                Log.e(TAG, "断开失败", e)
            }
        }
    }
}