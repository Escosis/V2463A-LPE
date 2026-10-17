package com.escosis.v2463alpe.ui.screen

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.escosis.v2463alpe.R
import com.escosis.v2463alpe.ui.AppStateHolder
import com.escosis.v2463alpe.ui.AppStatus
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.escosis.v2463alpe.ksuDisplayName

@Composable
fun HomeScreen() {
    val status by AppStateHolder.status.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "status") { StatusCard(status) }
        item(key = "info") { InfoCard(status) }
    }
}

@Composable
private fun StatusCard(status: AppStatus) {
    val ready = status.allReady
    val isRoot = status.hasRoot
    val isDark = isSystemInDarkTheme()

    val title = when {
        status.isLoading -> "检测中..."
        !ready -> "未就绪"
        isRoot -> "已提权"
        else -> "已就绪"
    }
    val bottomLeftText = when {
        status.isLoading -> ""
        !ready -> "缺少: ${status.missingLabel}"
        isRoot -> "管理器：${ksuDisplayName(status.activeKsuPackage)}"
        else -> "请再次确认 OEM 权限是否获取，\n然后重启执行提权"
    }

    val cardColor = when {
        MiuixTheme.isDynamicColor && ready -> MiuixTheme.colorScheme.secondaryContainer
        MiuixTheme.isDynamicColor && !ready -> MiuixTheme.colorScheme.errorContainer
        ready && isDark -> Color(0xFF1A3825)
        ready && !isDark -> Color(0xFFDFFAE4)
        !ready && isDark -> Color(0xFF3D1F1F)
        else -> Color(0xFFFFE8E0)
    }
    val iconTint = when {
        MiuixTheme.isDynamicColor && ready -> MiuixTheme.colorScheme.primary
        MiuixTheme.isDynamicColor -> MiuixTheme.colorScheme.error
        ready -> Color(0xFF4ADE80)
        else -> Color(0xFFEF4444)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = cardColor),
    ) {
        Box(modifier = Modifier.height(120.dp).fillMaxWidth()) {
            Box(
                modifier = Modifier.fillMaxSize().offset(27.dp, 31.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Icon(
                    painter = painterResource(
                        id = if (ready) R.drawable.ic_check_circle_outline
                        else R.drawable.ic_error_outline
                    ),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(110.dp),
                )
            }
            if (bottomLeftText.isNotEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(16.dp, 10.dp),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    Text(bottomLeftText, fontSize = 14.sp)
                }
            }
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp, 14.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                Column {
                    Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text("版本 ${getFullVersion()}", fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun InfoCard(status: AppStatus) {
    val ctx = LocalContext.current
    val appVersion = remember {
        try {
            val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            val name = info.versionName ?: "?"
            val code = info.longVersionCode
            "$name ($code)"
        } catch (e: Exception) { "?" }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            InfoRow(R.drawable.ic_tag, "应用版本", appVersion)
            InfoRow(R.drawable.ic_phone_android, "安卓版本", Build.VERSION.RELEASE)
            InfoRow(R.drawable.ic_developer_board, "内核版本", System.getProperty("os.version") ?: "?")
            InfoRow(R.drawable.ic_info, "设备型号", Build.MODEL)
            InfoRow(
                R.drawable.ic_security, "权限状态",
                when {
                    status.isLoading -> "..."
                    status.allReady -> "全部已授予"
                    else -> "缺少: ${status.missingLabel}"
                },
                bottomPadding = 12.dp,
            )
            InfoRow(
                R.drawable.ic_wifi, "无线调试",
                when {
                    status.isLoading -> "..."
                    status.isConnected -> "已连接 :${status.connectedPort}"
                    status.isPaired -> "已配对，未连接"
                    else -> "未配对"
                },
                bottomPadding = 0.dp,
            )
        }
    }
}

@Composable
private fun InfoRow(
    iconRes: Int,
    title: String,
    content: String,
    bottomPadding: Dp = 12.dp,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = bottomPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = title,
            modifier = Modifier.padding(end = 12.dp).size(24.dp),
            tint = MiuixTheme.colorScheme.onSurface,
        )
        Column {
            Text(
                text = title,
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = content,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun getFullVersion(): String {
    val ctx = LocalContext.current
    return remember(ctx) {
        try {
            val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            val name = info.versionName ?: "?"
            val code = info.longVersionCode
            "$name ($code)"
        } catch (e: Exception) {
            "?"
        }
    }
}