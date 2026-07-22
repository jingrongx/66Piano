package com.pianokids.ui.util

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 响应式布局工具集。
 *
 * 基于 [LocalConfiguration] 的屏幕宽度（dp）判断窗口尺寸类别，
 * 与 Material3 WindowSizeClass 保持一致的判定阈值，但无需额外依赖。
 *
 * - [WindowClass.COMPACT]：< 600dp（手机竖屏）
 * - [WindowClass.MEDIUM]：600–840dp（手机横屏 / 小平板竖屏）
 * - [WindowClass.EXPANDED]：≥ 840dp（平板横屏）
 */
enum class WindowClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

/**
 * 判断当前窗口尺寸类别。
 *
 * 使用方式：
 * ```
 * val windowClass = rememberWindowClass()
 * if (windowClass == WindowClass.COMPACT) { /* 单列 */ }
 * ```
 */
@Composable
fun rememberWindowClass(): WindowClass {
    val width = LocalConfiguration.current.screenWidthDp.dp
    return when {
        width >= 840.dp -> WindowClass.EXPANDED
        width >= 600.dp -> WindowClass.MEDIUM
        else -> WindowClass.COMPACT
    }
}

/**
 * 判断当前是否横屏（宽 > 高）。
 */
@Composable
fun rememberIsLandscape(): Boolean {
    val cfg = LocalConfiguration.current
    return cfg.screenWidthDp > cfg.screenHeightDp
}

/**
 * 推荐的网格列数：根据窗口宽度自适应。
 *
 * - COMPACT：2 列
 * - MEDIUM：3 列
 * - EXPANDED：4 列
 */
@Composable
fun rememberAdaptiveGridColumns(): Int = when (rememberWindowClass()) {
    WindowClass.COMPACT -> 2
    WindowClass.MEDIUM -> 3
    WindowClass.EXPANDED -> 4
}

/**
 * 推荐的卡片列表列数（用于卡片列表横向分栏）：
 * - COMPACT：1 列
 * - MEDIUM / EXPANDED：2 列
 */
@Composable
fun rememberListColumns(): Int = when (rememberWindowClass()) {
    WindowClass.COMPACT -> 1
    WindowClass.MEDIUM -> 2
    WindowClass.EXPANDED -> 2
}

/**
 * 推荐的内容横向内边距：横屏宽屏下增加边距避免内容过宽。
 */
@Stable
fun adaptiveHorizontalPadding(windowClass: WindowClass): Dp = when (windowClass) {
    WindowClass.COMPACT -> 16.dp
    WindowClass.MEDIUM -> 24.dp
    WindowClass.EXPANDED -> 32.dp
}

/**
 * 在横屏 + MEDIUM/EXPANDED 时将一列内容拆成左右两列，
 * 竖屏或 COMPACT 时保持单列。
 *
 * @param left 左列内容
 * @param right 右列内容
 * @param modifier 整体 Modifier
 * @param spacing 列间距
 */
@Composable
fun AdaptiveTwoColumns(
    modifier: Modifier = Modifier,
    spacing: Dp = 16.dp,
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
) {
    val windowClass = rememberWindowClass()
    if (windowClass == WindowClass.COMPACT) {
        // 单列：只渲染左列，调用方需自行将 right 内容追加到主 Column 中
        // 此 helper 在 COMPACT 模式下不常用，调用方应优先使用 rememberListColumns 判断
        left()
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.weight(1f),
            ) { left() }
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.weight(1f),
            ) { right() }
        }
    }
}
