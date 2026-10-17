package com.escosis.v2463alpe.ui

import android.content.Context
import com.escosis.v2463alpe.AdbClient
import com.escosis.v2463alpe.AdbPairingHelper
import com.escosis.v2463alpe.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

object AppStateHolder {

    private const val REFRESH_INTERVAL_MS = 3000L
    private const val SLOW_REFRESH_EVERY = 3

    private val _status = MutableStateFlow(AppStatus(isLoading = true))
    val status: StateFlow<AppStatus> = _status.asStateFlow()

    private var started = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var cachedConnected = false
    private var cachedPort = -1
    private var cachedPreload = false

    private val _isInitDone = MutableStateFlow(false)
    val isInitDone: StateFlow<Boolean> = _isInitDone.asStateFlow()

    fun start(context: Context) {
        if (started) return
        started = true

        val appContext = context.applicationContext

        scope.launch {
            val initialRoot = checkRootSync()
            Prefs.setHasRoot(appContext, initialRoot)
            Prefs.setRootChecked(appContext, true)

            val activeKsuPackage = Prefs.getActiveKsuPackage(appContext)

            var tick = 0
            while (true) {
                val hasRoot = Prefs.getHasRoot(appContext)

                val doSlow = (tick % SLOW_REFRESH_EVERY == 0)

                if (doSlow) {
                    try {
                        cachedConnected = com.escosis.v2463alpe.AdbPairingHelper.getConnectedHost() != null &&
                                com.escosis.v2463alpe.AdbPairingHelper.checkConnected(appContext)
                        cachedPort = com.escosis.v2463alpe.AdbPairingHelper.getConnectedPort()
                    } catch (e: Exception) {
                        cachedConnected = false
                        cachedPort = -1
                    }

                    if (cachedConnected) {
                        cachedPreload = probePreload(appContext)
                    } else {
                        cachedPreload = Prefs.getPreloadPushed(appContext)
                    }
                }

                val s = checkStatus(appContext, hasRoot, cachedConnected, cachedPort, cachedPreload, activeKsuPackage)
                _status.value = s

                if (!_isInitDone.value) {
                    _isInitDone.value = true
                }

                tick++
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    fun refreshRoot(context: Context): Boolean {
        val r = checkRootSync()
        Prefs.setHasRoot(context, r)
        Prefs.setRootChecked(context, true)
        return r
    }

    private fun checkRootSync(): Boolean {
        return try {
            val pb = ProcessBuilder("su", "-c", "id").redirectErrorStream(true)
            val p = pb.start()
            val out = p.inputStream.bufferedReader().readText()
            if (!p.waitFor(3, TimeUnit.SECONDS)) {
                p.destroy()
                return false
            }
            out.contains("uid=0")
        } catch (e: Exception) {
            false
        }
    }

    fun refreshNow(context: Context) {
        scope.launch {
            val appContext = context.applicationContext
            val hasRoot = Prefs.getHasRoot(appContext)

            try {
                cachedConnected = AdbPairingHelper.getConnectedHost() != null &&
                        AdbPairingHelper.checkConnected(appContext)
                cachedPort = AdbPairingHelper.getConnectedPort()
            } catch (e: Exception) {
                cachedConnected = false
                cachedPort = -1
            }

            if (cachedConnected) {
                cachedPreload = probePreload(appContext)
            } else {
                cachedPreload = Prefs.getPreloadPushed(appContext)
            }

            val activeKsuPackage = Prefs.getActiveKsuPackage(appContext)

            val s = checkStatus(appContext, hasRoot, cachedConnected, cachedPort, cachedPreload, activeKsuPackage)
            _status.value = s
        }
    }

    private fun probePreload(appContext: Context): Boolean {
        val fallback = Prefs.getPreloadPushed(appContext)
        return try {
            val out = AdbClient.shell(appContext, "ls /data/local/tmp/preload.so")
            val trimmed = out.trim()
            when {
                trimmed.contains("preload.so") -> {
                    Prefs.setPreloadPushed(appContext, true)
                    true
                }
                trimmed.contains("No such file") ||
                        trimmed.contains("not found") -> {
                    Prefs.setPreloadPushed(appContext, false)
                    false
                }
                else -> {
                    fallback
                }
            }
        } catch (e: Exception) {
            fallback
        }
    }
}