package com.escosis.v2463alpe

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.Socket
import java.util.concurrent.TimeUnit

object AdbServerManager {

    private const val TAG = "AdbServerManager"
    private const val ADB_PORT = 5037
    private const val MAX_WAIT_MS = 5000L
    private const val PAIRING_NAME = "V2463A-LPE "

    @Volatile
    private var serverProcess: Process? = null

    @Volatile
    private var serverReady = false

    private fun findAdbBinary(context: Context): String? {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val nativeAdb = File(nativeDir, "libadb.so")
        Log.d(TAG, "nativeLibraryDir = $nativeDir")
        Log.d(TAG, "libadb.so exists = ${nativeAdb.exists()}")

        if (nativeAdb.exists()) {
            File(nativeDir).listFiles()?.forEach { f ->
                Log.d(TAG, "  lib: ${f.name} (${f.length()} bytes)")
            }
            return nativeAdb.absolutePath
        }

        val filesAdb = File(context.filesDir, "adb")
        if (filesAdb.exists()) return filesAdb.absolutePath
        return null
    }

    private fun prepareExternalStorage(context: Context): String {
        val deContext = context.createDeviceProtectedStorageContext()
        val extDir = File(deContext.filesDir, "ext_storage")
        if (!extDir.exists()) extDir.mkdirs()
        File(extDir, ".android").mkdirs()
        return extDir.absolutePath
    }

    @JvmStatic
    fun ensureAdbServer(context: Context): Boolean {
        synchronized(this) {
            if (serverProcess?.isAlive == true && serverReady) return true

            if (isPortReady()) {
                serverReady = true
                Log.d(TAG, "ADB Server 已在 5037 端口运行，直接复用")
                return true
            }

            val adbPath = findAdbBinary(context)
            if (adbPath == null) {
                Log.e(TAG, "未找到 ADB 二进制")
                return false
            }

            prepareExternalStorage(context)

            Log.d(TAG, "启动 ADB Server: $adbPath")
            val started = spawnAdbServer(context, adbPath)
            if (!started) {
                Log.e(TAG, "spawnAdbServer 失败")
                return false
            }

            val ready = waitForPortReady(MAX_WAIT_MS)
            serverReady = ready
            Log.d(TAG, "ADB Server 就绪: $ready")
            return ready
        }
    }

    private fun spawnAdbServer(context: Context, adbPath: String): Boolean {
        val extPath = prepareExternalStorage(context)

        try {
            val pb = ProcessBuilder(adbPath, "server", "nodaemon")
            pb.environment()["EXTERNAL_STORAGE"] = extPath
            pb.environment()["HOME"] = extPath
            pb.environment()["ANDROID_SDK_HOME"] = extPath
            pb.environment()["LOGNAME"] = PAIRING_NAME
            pb.environment()["HOSTNAME"] = ""
            pb.environment()["ADB_TRACE"] = "1"
            pb.environment()["TMPDIR"] = context.cacheDir.absolutePath
            pb.environment()["ADB_MDNS_AUTO_CONNECT"] = "adb-tls-connect"

            val process = pb.redirectErrorStream(true).start()
            serverProcess = process
            drainAndLogOutput(process, "direct")

            try { Thread.sleep(500) } catch (_: InterruptedException) {}
            if (process.isAlive) {
                Log.d(TAG, "直接 exec 成功")
                return true
            }
            Log.w(TAG, "直接 exec 后进程立刻退出，转 linker64 回退")
            serverProcess = null
        } catch (e: Exception) {
            Log.w(TAG, "直接 exec 失败: ${e.message}，尝试 linker64 回退")
        }

        return try {
            val linker = "/system/bin/linker64"
            val libPath = context.applicationInfo.nativeLibraryDir + ":/system/lib64:/vendor/lib64"
            val pb = ProcessBuilder(linker, adbPath, "server", "nodaemon")
            pb.environment()["LD_LIBRARY_PATH"] = libPath
            pb.environment()["EXTERNAL_STORAGE"] = extPath
            pb.environment()["HOME"] = extPath
            pb.environment()["ANDROID_SDK_HOME"] = extPath
            pb.environment()["LOGNAME"] = PAIRING_NAME
            pb.environment()["HOSTNAME"] = ""
            pb.environment()["TMPDIR"] = context.cacheDir.absolutePath
            pb.environment()["ADB_MDNS_AUTO_CONNECT"] = "adb-tls-connect"

            val process = pb.redirectErrorStream(true).start()
            serverProcess = process
            drainAndLogOutput(process, "linker64")
            try { Thread.sleep(500) } catch (_: InterruptedException) {}
            if (process.isAlive) {
                Log.d(TAG, "linker64 回退成功")
                true
            } else {
                Log.e(TAG, "linker64 回退后进程也退出了")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "linker64 回退也失败", e)
            false
        }
    }

    private fun drainAndLogOutput(process: Process, tagSuffix: String) {
        Thread {
            try {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line: String?
                    while (true) {
                        line = reader.readLine() ?: break
                        Log.d(TAG, "[$tagSuffix] $line")
                    }
                }
                val exit = process.waitFor()
                Log.w(TAG, "[$tagSuffix] adb server 已退出，exitCode=$exit")
            } catch (_: Exception) {
            }
        }.apply { isDaemon = true }.start()
    }

    private fun isPortReady(): Boolean {
        return try {
            Socket("127.0.0.1", ADB_PORT).use { socket ->
                socket.soTimeout = 1000
                val req = "000chost:version"
                socket.getOutputStream().write(req.toByteArray())
                socket.getOutputStream().flush()
                val buf = ByteArray(4)
                val n = socket.getInputStream().read(buf)
                n == 4 && String(buf) == "OKAY"
            }
        } catch (_: Exception) {
            false
        }
    }

    /** 快速探测 5037 是否就绪 */
    @JvmStatic
    fun isServerReadyFast(): Boolean = isPortReady()

    private fun waitForPortReady(timeoutMs: Long): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (isPortReady()) return true
            try { Thread.sleep(200) } catch (_: InterruptedException) {}
        }
        return false
    }

    @JvmStatic
    fun resetServer(context: Context): Boolean {
        synchronized(this) {
            Log.w(TAG, "重置 ADB Server")
            serverProcess?.destroy()
            serverProcess = null
            serverReady = false
            try { Thread.sleep(500) } catch (_: InterruptedException) {}
            return ensureAdbServer(context)
        }
    }

    @JvmStatic
    fun killExistingServer(context: Context) {
        synchronized(this) {
            Log.d(TAG, "killExistingServer: 开始清理")

            if (!isPortReady()) {
                Log.d(TAG, "5037 无 server，跳过 kill")
                try { serverProcess?.destroy() } catch (_: Exception) {}
                serverProcess = null
                serverReady = false
                return
            }

            val extPath = try {
                prepareExternalStorage(context)
            } catch (e: Exception) {
                ""
            }

            try {
                val adbPath = File(context.applicationInfo.nativeLibraryDir, "libadb.so").absolutePath
                if (File(adbPath).exists()) {
                    val pb = ProcessBuilder(adbPath, "kill-server")
                    if (extPath.isNotEmpty()) {
                        pb.environment()["EXTERNAL_STORAGE"] = extPath
                        pb.environment()["HOME"] = extPath
                        pb.environment()["ANDROID_SDK_HOME"] = extPath
                    }
                    pb.environment()["LOGNAME"] = PAIRING_NAME
                    pb.environment()["HOSTNAME"] = ""
                    pb.redirectErrorStream(true)
                    val process = pb.start()
                    val finished = process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
                    if (!finished) {
                        Log.w(TAG, "adb kill-server 超时，强制销毁")
                        process.destroy()
                    }
                    Log.d(TAG, "adb kill-server 完成")
                }
            } catch (e: Exception) {
                Log.w(TAG, "adb kill-server 失败: ${e.message}")
            }

            try {
                serverProcess?.destroy()
                serverProcess = null
            } catch (_: Exception) {}

            serverReady = false
            Thread.sleep(300)
            Log.d(TAG, "killExistingServer: 完成")
        }
    }

    @JvmStatic
    fun ensureAdbServerWithRetry(context: Context): Boolean {
        if (ensureAdbServer(context)) return true
        Log.w(TAG, "首次 ensureAdbServer 失败，重置后重试")
        resetServer(context)
        return ensureAdbServer(context)
    }
}