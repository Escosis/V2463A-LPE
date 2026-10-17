package com.escosis.v2463alpe.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.escosis.v2463alpe.Prefs
import com.escosis.v2463alpe.R
import com.escosis.v2463alpe.ui.theme.LocalSetThemeMode
import com.escosis.v2463alpe.ui.theme.LocalThemeMode
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val flavorNames = listOf("KernelSU", "KernelSU Next", "SukiSU-Ultra", "自定义")
private val flavorPackages = listOf(
    "me.weishu.kernelsu",
    "vctsrt.cntgtj.uqfwgg",
    "com.sukisu.ultra"
)

@Composable
fun SettingsScreen(
    onNavigateToAbout: () -> Unit,
    onOverlayExpandedChange: (Boolean) -> Unit = {},
) {
    val ctx = LocalContext.current
    val themeMode = LocalThemeMode.current
    val setThemeMode = LocalSetThemeMode.current
    var currentPkg by remember { mutableStateOf(Prefs.getKsuPackage(ctx)) }
    var selectedIndex by remember {
        val idx = flavorPackages.indexOf(currentPkg)
        mutableIntStateOf(if (idx >= 0) idx else 3)
    }

    var themeExpanded by remember { mutableStateOf(false) }
    var ksuExpanded by remember { mutableStateOf(false) }
    val anyExpanded = themeExpanded || ksuExpanded

    SideEffect { onOverlayExpandedChange(anyExpanded) }
    DisposableEffect(Unit) {
        onDispose { onOverlayExpandedChange(false) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "theme") {
            Card(modifier = Modifier.fillMaxWidth()) {
                OverlayDropdownPreference(
                    title = "主题",
                    items = listOf("跟随系统", "浅色", "深色"),
                    startAction = {
                        Icon(
                            painter = painterResource(R.drawable.ic_palette),
                            contentDescription = "主题",
                            modifier = Modifier.padding(end = 6.dp).size(24.dp),
                            tint = MiuixTheme.colorScheme.onSurface
                        )
                    },
                    selectedIndex = themeMode,
                    onExpandedChange = { themeExpanded = it },
                    onSelectedIndexChange = { newIndex ->
                        setThemeMode(newIndex)
                    }
                )
            }
        }

        item(key = "ksu_package") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(bottom = if (selectedIndex == 3) 16.dp else 0.dp)) {
                    OverlayDropdownPreference(
                        title = "管理器分支",
                        items = flavorNames,
                        startAction = {
                            Icon(
                                painter = painterResource(R.drawable.ic_tag),
                                contentDescription = "管理器包名",
                                modifier = Modifier.padding(end = 6.dp).size(24.dp),
                                tint = MiuixTheme.colorScheme.onSurface
                            )
                        },
                        selectedIndex = selectedIndex,
                        onExpandedChange = { ksuExpanded = it },
                        onSelectedIndexChange = { newIndex ->
                            selectedIndex = newIndex
                            if (newIndex < 3) {
                                currentPkg = flavorPackages[newIndex]
                                Prefs.setKsuPackage(ctx, currentPkg)
                            } else {
                                if (currentPkg in flavorPackages) {
                                    currentPkg = ""
                                    Prefs.setKsuPackage(ctx, "")
                                }
                            }
                        }
                    )

                    AnimatedVisibility(
                        visible = selectedIndex == 3,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        OutlinedTextField(
                            value = currentPkg,
                            onValueChange = {
                                currentPkg = it.trim()
                                Prefs.setKsuPackage(ctx, currentPkg)
                            },
                            label = { Text("自定义包名") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            textStyle = TextStyle(
                                color = MiuixTheme.colorScheme.onSurface,
                                fontSize = 14.sp
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MiuixTheme.colorScheme.onSurface,
                                unfocusedTextColor = MiuixTheme.colorScheme.onSurface,
                                focusedBorderColor = MiuixTheme.colorScheme.primary,
                                unfocusedBorderColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                focusedLabelColor = MiuixTheme.colorScheme.primary,
                                unfocusedLabelColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                cursorColor = MiuixTheme.colorScheme.primary,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            )
                        )
                    }
                }
            }
        }

        item(key = "about_entry") {
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = "关于",
                    startAction = {
                        Icon(
                            painter = painterResource(R.drawable.ic_info),
                            contentDescription = "关于",
                            modifier = Modifier.padding(end = 6.dp).size(24.dp),
                            tint = MiuixTheme.colorScheme.onSurface
                        )
                    },
                    onClick = onNavigateToAbout
                )
            }
        }
    }
}