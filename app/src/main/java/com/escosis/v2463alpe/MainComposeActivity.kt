package com.escosis.v2463alpe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.escosis.v2463alpe.ui.AppNavigation
import com.escosis.v2463alpe.ui.AppStateHolder
import com.escosis.v2463alpe.ui.theme.AppTheme

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

                AppNavigation()
            }
        }
    }
}