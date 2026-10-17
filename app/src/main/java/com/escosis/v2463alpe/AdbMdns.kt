package com.escosis.v2463alpe

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.IOException
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.util.concurrent.Executors

@RequiresApi(Build.VERSION_CODES.R)
class AdbMdns(
    context: Context,
    private val serviceType: String,
    private val onFound: (Int) -> Unit,
    private val maxRefreshCount: Int = 100
) {
    companion object {
        const val TLS_CONNECT = "_adb-tls-connect._tcp"
        const val TLS_PAIRING = "_adb-tls-pairing._tcp"
        private const val TAG = "AdbMdns"
        private const val CHECK_INTERVAL = 1000L
        private const val INITIAL_DELAY = 200L
        private const val RESTART_DELAY = 50L
        private const val RETRY_DELAY = 50L
        private const val MAX_START_RETRY = 10
    }

    private val appContext = context.applicationContext
    private val nsdManager: NsdManager = appContext.getSystemService(NsdManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var executor = Executors.newSingleThreadExecutor()

    @Volatile private var registered = false
    @Volatile private var running = false
    @Volatile private var pendingRestart = false
    @Volatile private var startFailedRetryCount = 0
    @Volatile private var refreshCount = 0
    @Volatile private var serviceFound = false
    @Volatile private var resolving = false
    private var serviceName: String? = null
    private var listener: DiscoveryListener? = null

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            if (serviceFound) return
            if (refreshCount >= maxRefreshCount) {
                Log.v(TAG, "刷新次数已达上限")
                stop()
                return
            }
            if (registered && !pendingRestart && !serviceFound && !resolving) {
                refreshCount++
                Log.v(TAG, "刷新服务发现 (第${refreshCount}次)")
                pendingRestart = true
                try {
                    listener?.let { nsdManager.stopServiceDiscovery(it) }
                } catch (e: Exception) {
                    pendingRestart = false
                }
            }
            mainHandler.postDelayed(this, CHECK_INTERVAL)
        }
    }

    fun start() {
        if (running) return
        running = true
        startFailedRetryCount = 0
        refreshCount = 0
        serviceFound = false
        resolving = false
        if (executor.isShutdown) executor = Executors.newSingleThreadExecutor()
        startDiscoveryInternal()
        mainHandler.postDelayed(refreshRunnable, INITIAL_DELAY)
    }

    private fun startDiscoveryInternal() {
        if (!running || registered) return
        try {
            listener = DiscoveryListener(this)
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            Log.v(TAG, "启动服务发现: $serviceType")
        } catch (e: Exception) {
            Log.e(TAG, "启动服务发现失败", e)
            scheduleRetryStart()
        }
    }

    private fun scheduleRetryStart() {
        if (!running) return
        if (startFailedRetryCount >= MAX_START_RETRY) return
        startFailedRetryCount++
        mainHandler.postDelayed({
            if (running && !registered) startDiscoveryInternal()
        }, RETRY_DELAY)
    }

    fun stop() {
        if (!running) return
        running = false
        mainHandler.removeCallbacks(refreshRunnable)
        if (registered) {
            try { listener?.let { nsdManager.stopServiceDiscovery(it) } } catch (_: Exception) {}
        }
        listener = null
        registered = false
        pendingRestart = false
    }

    fun destroy() {
        stop()
        executor.shutdown()
    }

    private fun onDiscoveryStart() { registered = true }

    private fun onDiscoveryStop() {
        registered = false
        if (pendingRestart && running) {
            pendingRestart = false
            mainHandler.postDelayed({
                if (running && !registered) startDiscoveryInternal()
            }, RESTART_DELAY)
        }
    }

    @Suppress("DEPRECATION")
    private fun onServiceFound(info: NsdServiceInfo) {
        resolving = true
        nsdManager.resolveService(info, ResolveListener(this))
    }

    @Suppress("DEPRECATION")
    private fun onServiceResolved(resolved: NsdServiceInfo) {
        if (!running) return
        executor.execute {
            try {
                val isLocal = NetworkInterface.getNetworkInterfaces().asSequence().any { ni ->
                    ni.inetAddresses.asSequence().any { addr ->
                        resolved.host.hostAddress == addr.hostAddress
                    }
                }
                if (!isLocal || !running) return@execute

                val portAvailable = isPortAvailable(resolved.port)
                Log.v(TAG, "isLocal=$isLocal, portAvailable=$portAvailable, port=${resolved.port}")
                if (portAvailable && running) {
                    mainHandler.post {
                        if (running && !serviceFound) {
                            serviceFound = true
                            mainHandler.removeCallbacks(refreshRunnable)
                            serviceName = resolved.serviceName
                            Log.i(TAG, "已发现服务，端口=${resolved.port}")
                            onFound(resolved.port)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "服务验证失败", e)
            }
        }
    }

    private fun isPortAvailable(port: Int) = try {
        ServerSocket().use {
            it.bind(InetSocketAddress("127.0.0.1", port), 1)
            false
        }
    } catch (_: IOException) {
        true
    }

    internal class DiscoveryListener(private val m: AdbMdns) : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {
            Log.v(TAG, "发现已开始: $serviceType")
            m.startFailedRetryCount = 0
            m.onDiscoveryStart()
        }
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "开始发现失败: $serviceType, code=$errorCode")
            m.registered = false
            m.scheduleRetryStart()
        }
        override fun onDiscoveryStopped(serviceType: String) {
            Log.v(TAG, "发现已停止: $serviceType")
            m.onDiscoveryStop()
        }
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "停止发现失败: $serviceType, code=$errorCode")
            m.registered = false
            m.pendingRestart = false
            if (m.running) m.mainHandler.postDelayed({
                if (m.running && !m.registered) m.startDiscoveryInternal()
            }, RESTART_DELAY)
        }
        override fun onServiceFound(info: NsdServiceInfo) {
            Log.v(TAG, "发现服务: ${info.serviceName}")
            m.onServiceFound(info)
        }
        override fun onServiceLost(info: NsdServiceInfo) {
            Log.v(TAG, "服务丢失: ${info.serviceName}")
        }
    }

    internal class ResolveListener(private val m: AdbMdns) : NsdManager.ResolveListener {
        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
            Log.e(TAG, "解析服务失败: ${info.serviceName}, code=$errorCode")
            m.resolving = false
        }
        override fun onServiceResolved(info: NsdServiceInfo) {
            Log.v(TAG, "解析服务成功: ${info.serviceName}, port=${info.port}")
            m.resolving = false
            m.onServiceResolved(info)
        }
    }
}