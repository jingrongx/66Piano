package com.pianokids.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pianokids.ui.home.HomeScreen
import com.pianokids.ui.learn.LearnScreen
import com.pianokids.ui.learn.LessonScreen
import com.pianokids.ui.pet.PetScreen
import com.pianokids.ui.practice.PracticeScreen
import com.pianokids.ui.tuner.TunerScreen

// ============== 路由 ==============

object Routes {
    const val HOME = "home"
    const val LEARN = "learn"
    const val TUNER = "tuner"
    const val PRACTICE = "practice"
    const val PET = "pet"
    const val LESSON = "lesson/{lessonId}"

    fun lesson(lessonId: String) = "lesson/$lessonId"
}

/**
 * 底部导航 Tab 项。
 */
private data class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val BOTTOM_TABS = listOf(
    BottomTab(Routes.HOME, "首页", Icons.Filled.Home),
    BottomTab(Routes.LEARN, "学习", Icons.Filled.School),
    BottomTab(Routes.TUNER, "调音", Icons.Filled.Tune),
    BottomTab(Routes.PRACTICE, "练琴", Icons.Filled.MusicNote),
    BottomTab(Routes.PET, "豆豆", Icons.Filled.Pets),
)

/**
 * 钢琴学院顶级 Composable。
 *
 * 设置 Scaffold + 底部导航栏（5 个 Tab），使用 Navigation Compose。
 * 启动时申请麦克风权限。
 */
@Composable
fun PianoKidsApp() {
    val navController = rememberNavController()
    val context = LocalContext.current

    // 麦克风权限申请
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ ->
        // 无论授权与否都不阻塞 UI；未授权时音频相关页面会优雅降级
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // 在课程教学页隐藏底部导航栏
    val showBottomBar = currentRoute in setOf(
        Routes.HOME,
        Routes.LEARN,
        Routes.TUNER,
        Routes.PRACTICE,
        Routes.PET,
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                PianoKidsBottomBar(
                    currentRoute = currentRoute,
                    navController = navController,
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onNavigateLearn = {
                        navController.navigate(Routes.LEARN) {
                            popUpTo(navController.graph.findStartDestination().id)
                            launchSingleTop = true
                        }
                    },
                    onNavigatePractice = {
                        navController.navigate(Routes.PRACTICE) {
                            popUpTo(navController.graph.findStartDestination().id)
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Routes.LEARN) {
                LearnScreen(
                    onLessonClick = { lessonId ->
                        navController.navigate(Routes.lesson(lessonId))
                    },
                )
            }
            composable(Routes.TUNER) {
                TunerScreen()
            }
            composable(Routes.PRACTICE) {
                PracticeScreen()
            }
            composable(Routes.PET) {
                PetScreen()
            }
            composable(
                route = Routes.LESSON,
                arguments = listOf(
                    navArgument("lessonId") { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val lessonId = backStackEntry.arguments?.getString("lessonId") ?: "lesson_1"
                LessonScreen(
                    lessonId = lessonId,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

/**
 * 底部导航栏。
 */
@Composable
private fun PianoKidsBottomBar(
    currentRoute: String?,
    navController: NavHostController,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        BOTTOM_TABS.forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                    )
                },
                label = {
                    Text(text = tab.label)
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
