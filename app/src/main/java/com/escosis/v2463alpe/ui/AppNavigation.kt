package com.escosis.v2463alpe.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.escosis.v2463alpe.Prefs
import com.escosis.v2463alpe.R
import com.escosis.v2463alpe.ui.screen.AboutScreen
import com.escosis.v2463alpe.ui.screen.HomeScreen
import com.escosis.v2463alpe.ui.screen.PairingScreen
import com.escosis.v2463alpe.ui.screen.PermissionScreen
import com.escosis.v2463alpe.ui.screen.SettingsScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import kotlinx.coroutines.flow.first

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()

    var selectedPage by remember { mutableIntStateOf(0) }
    var showAbout by remember { mutableStateOf(false) }
    var isOverlayExpanded by remember { mutableStateOf(false) }

    val tabs: List<Triple<String, String, Int>> = remember {
        listOf(
            Triple("首页", "V2463A LPE", R.drawable.ic_home),
            Triple("权限", "权限", R.drawable.ic_security),
            Triple("配对", "配对", R.drawable.ic_link),
            Triple("设置", "设置", R.drawable.ic_settings),
        )
    }

    LaunchedEffect(Unit) {
        AppStateHolder.start(context)

        if (!Prefs.isFirstLaunchDone(context)) {
            Prefs.setFirstLaunchDone(context, true)
            Prefs.setFirstLaunchPending(context, true)
            AppStateHolder.isInitDone.first { it }
            delay(400)
            selectedPage = 1
            pagerState.animateScrollToPage(1)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage to pagerState.isScrollInProgress }
            .collect { (page, scrolling) ->
                if (!scrolling) selectedPage = page
            }
    }

    val title by remember {
        derivedStateOf { tabs[pagerState.currentPage].second }
    }

    val animationJob = remember { arrayOfNulls<Job>(1) }

    val onTabClick: (Int) -> Unit = { index ->
        animationJob[0]?.cancel()
        selectedPage = index
        animationJob[0] = scope.launch {
            val pageSize = pagerState.layoutInfo.pageSize.toFloat()
            val pageSpacing = pagerState.layoutInfo.pageSpacing.toFloat()
            if (pageSize <= 0f) {
                pagerState.scrollToPage(index)
                return@launch
            }

            val currentPosition = pagerState.currentPage + pagerState.currentPageOffsetFraction
            val delta = index - currentPosition
            if (kotlin.math.abs(delta) < 0.001f) return@launch

            val scrollPixels = delta * (pageSize + pageSpacing)
            val duration = (kotlin.math.abs(delta) * 150f).coerceAtLeast(200f).toInt()

            pagerState.animateScrollBy(
                value = scrollPixels,
                animationSpec = tween<Float>(
                    durationMillis = duration,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }

    val overlayExpandedOnSettings =
        isOverlayExpanded && pagerState.currentPage == 3

    val navigationEventState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(
        state = navigationEventState,
        isBackEnabled = (showAbout || pagerState.currentPage != 0) && !overlayExpandedOnSettings,
        onBackCompleted = {
            if (showAbout) showAbout = false else onTabClick(0)
        },
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = { TopAppBar(title = title) },
            bottomBar = {
                NavigationBar {
                    tabs.forEachIndexed { index, (label, _, iconRes) ->
                        NavigationBarItem(
                            selected = selectedPage == index,
                            onClick = { onTabClick(index) },
                            icon = ImageVector.vectorResource(id = iconRes),
                            label = label,
                        )
                    }
                }
            },
        ) { paddingValues ->
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 3,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) { page ->
                when (page) {
                    0 -> HomeScreen()
                    1 -> PermissionScreen(
                        onNavigateToPairing = { onTabClick(2) }
                    )
                    2 -> PairingScreen()
                    3 -> SettingsScreen(
                        onNavigateToAbout = { showAbout = true },
                        onOverlayExpandedChange = { isOverlayExpanded = it },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showAbout,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        ) {
            AboutScreen(onBack = { showAbout = false })
        }
    }
}