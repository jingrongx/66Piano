package com.pianokids.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pianokids.ui.challenge.ChallengeScreen
import com.pianokids.ui.home.HomeScreen
import com.pianokids.ui.learn.LearnScreen
import com.pianokids.ui.learn.LessonScreen
import com.pianokids.ui.library.LibraryScreen
import com.pianokids.ui.library.PieceEditorScreen
import com.pianokids.ui.parent.ParentScreen
import com.pianokids.ui.pet.CosmeticShopScreen
import com.pianokids.ui.pet.PetScreen
import com.pianokids.ui.practice.PracticeScreen
import com.pianokids.ui.scan.ScanScreen
import com.pianokids.ui.tuner.TunerScreen
import com.pianokids.ui.update.UpdateScreen

// ============== 路由 ==============

object Routes {
    const val HOME = "home"
    const val LEARN = "learn"
    const val TUNER = "tuner"
    const val PRACTICE = "practice"
    const val PET = "pet"
    const val LESSON = "lesson/{lessonId}"
    const val LIBRARY = "library"
    const val PIECE_EDITOR = "piece_editor/{pieceId}"
    const val SCAN = "scan"
    const val COSMETIC_SHOP = "cosmetic_shop"
    const val PARENT = "parent"
    const val CHALLENGE = "challenge"
    const val UPDATE = "update"

    fun lesson(lessonId: String) = "lesson/$lessonId"
    fun pieceEditor(pieceId: Long) = "piece_editor/$pieceId"
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
 *
 * **权限**：由 [com.pianokids.MainActivity] 在启动时强制申请，这里不再重复申请。
 */
@Composable
fun PianoKidsApp() {
    val navController = rememberNavController()

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
                    onNavigateParent = {
                        navController.navigate(Routes.PARENT)
                    },
                    onNavigateChallenge = {
                        navController.navigate(Routes.CHALLENGE)
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
                PracticeScreen(
                    onOpenLibrary = { navController.navigate(Routes.LIBRARY) },
                    onCreateNew = { navController.navigate(Routes.pieceEditor(-1L)) },
                    onScan = { navController.navigate(Routes.SCAN) },
                )
            }
            composable(Routes.PET) {
                PetScreen(
                    onNavigateToShop = {
                        navController.navigate(Routes.COSMETIC_SHOP)
                    },
                )
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
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onBack = { navController.popBackStack() },
                    onCreateNew = { navController.navigate(Routes.pieceEditor(-1L)) },
                    onScan = { navController.navigate(Routes.SCAN) },
                    onEditPiece = { pieceId ->
                        navController.navigate(Routes.pieceEditor(pieceId))
                    },
                )
            }
            composable(
                route = Routes.PIECE_EDITOR,
                arguments = listOf(
                    navArgument("pieceId") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) { backStackEntry ->
                val pieceId = backStackEntry.arguments?.getLong("pieceId") ?: -1L
                PieceEditorScreen(
                    pieceId = pieceId,
                    onBack = { navController.popBackStack() },
                    onSaved = { _ ->
                        // 保存成功后回到乐谱库
                        navController.popBackStack(Routes.LIBRARY, inclusive = false)
                    },
                )
            }
            composable(Routes.SCAN) {
                ScanScreen(
                    onBack = { navController.popBackStack() },
                    onEditSequence = {
                        // 拍照识谱识别完成后，进入编辑器新建乐谱
                        navController.navigate(Routes.pieceEditor(-1L))
                    },
                )
            }
            composable(Routes.COSMETIC_SHOP) {
                CosmeticShopScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.PARENT) {
                ParentScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToUpdate = {
                        navController.navigate(Routes.UPDATE)
                    },
                )
            }
            composable(Routes.CHALLENGE) {
                ChallengeScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.UPDATE) {
                UpdateScreen(
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
