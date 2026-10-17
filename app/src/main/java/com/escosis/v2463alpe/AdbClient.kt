package com.escosis.v2463alpe

import android.content.Context
import android.util.Log
import java.io.File

object AdbClient {

    private const val TAG = "AdbClient"
    private const val PAIRING_NAME = "V2463A-LPE "

    @JvmStatic
    fun pair(context: Context, host: String, port: Int, code: String): String {
        return runAdb(context, code + "\n", "pair", "$host:$port")
    }

    @JvmStatic
    fun connect(context: Context, host: String, port: Int): String {
        return runAdb(context, null, "connect", "$host:$port")
    }

    @JvmStatic
    fun shell(context: Context, command: String): String {
        return runAdb(context, null, "shell", command)
    }

    @JvmStatic
    fun devices(context: Context): String {
        return runAdb(context, null, "devices")
    }

    @JvmStatic
    fun runAdb(context: Context, stdin: String?, vararg args: String): String {
        val adbPath = File(context.applicationInfo.nativeLibraryDir, "libadb.so").absolutePath
        if (!File(adbPath).exists()) {
            Log.e(TAG, "adb 二进制不存在: $adbPath")
            return ""
        }

        val deContext = context.createDeviceProtectedStorageContext()
        val extDir = File(deContext.filesDir, "ext_storage")
        if (!extDir.exists()) extDir.mkdirs()
        File(extDir, ".android").mkdirs()

        return try {
            val pb = ProcessBuilder(adbPath, *args)
            pb.environment()["EXTERNAL_STORAGE"] = extDir.absolutePath
            pb.environment()["HOME"] = extDir.absolutePath
            pb.environment()["ANDROID_SDK_HOME"] = extDir.absolutePath
            pb.environment()["LOGNAME"] = PAIRING_NAME
            pb.environment()["HOSTNAME"] = ""
            pb.redirectErrorStream(true)

            val process = pb.start()

            if (stdin != null) {
                try {
                    process.outputStream.write(stdin.toByteArray())
                    process.outputStream.flush()
                    process.outputStream.close()
                } catch (e: Exception) {
                    Log.w(TAG, "写入 stdin 失败: ${e.message}")
                }
            }

            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exit = process.waitFor()

            Log.d(TAG, "adb ${args.joinToString(" ")} → exit=$exit")
            Log.d(TAG, "output: $output")
            output
        } catch (e: Exception) {
            Log.e(TAG, "执行 adb 失败", e)
            ""
        }
    }

    /**
     * 流式执行 shell 命令：每读到一行立即回调。
     * @param serial 指定目标设备，例如 "127.0.0.1:37655"。null 时不指定。
     *               用 -s 指定，避免 server 里有多台 transport 时报 more than one device。
     */
    @JvmStatic
    fun shellStream(
        context: Context,
        command: String,
        serial: String?,
        onLine: (String) -> Unit
    ) {
        val adbPath = File(context.applicationInfo.nativeLibraryDir, "libadb.so").absolutePath
        if (!File(adbPath).exists()) {
            Log.e(TAG, "adb 二进制不存在: $adbPath")
            onLine("[错误] adb 二进制不存在")
            return
        }

        val deContext = context.createDeviceProtectedStorageContext()
        val extDir = File(deContext.filesDir, "ext_storage")
        if (!extDir.exists()) extDir.mkdirs()
        File(extDir, ".android").mkdirs()

        try {
            val cmdList = mutableListOf(adbPath)
            if (!serial.isNullOrBlank()) {
                cmdList.add("-s")
                cmdList.add(serial)
            }
            cmdList.add("shell")
            cmdList.add(command)

            val pb = ProcessBuilder(cmdList)
            pb.environment()["EXTERNAL_STORAGE"] = extDir.absolutePath
            pb.environment()["HOME"] = extDir.absolutePath
            pb.environment()["ANDROID_SDK_HOME"] = extDir.absolutePath
            pb.environment()["LOGNAME"] = PAIRING_NAME
            pb.environment()["HOSTNAME"] = ""
            pb.redirectErrorStream(true)

            val process = pb.start()

            Thread {
                try {
                    process.inputStream.bufferedReader().use { reader ->
                        var line: String?
                        while (true) {
                            line = reader.readLine() ?: break
                            onLine(line)
                        }
                    }
                    val exit = process.waitFor()
                    onLine("[结束] exitCode=$exit")
                } catch (e: Exception) {
                    onLine("[读取终止] ${e.message}")
                }
            }.apply { isDaemon = true }.start()

        } catch (e: Exception) {
            Log.e(TAG, "shellStream 启动失败", e)
            onLine("[异常] ${e.message}")
        }
    }

    @JvmStatic
    fun shellWithSerial(context: Context, serial: String, command: String): String {
        if (serial.isNullOrBlank()) return shell(context, command)
        return runAdb(context, null, "-s", serial, "shell", command)
    }
}