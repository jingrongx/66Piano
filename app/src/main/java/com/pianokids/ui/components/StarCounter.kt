package com.pianokids.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pianokids.ui.theme.Secondary

/**
 * 星星计数器：显示星星数 + ⭐ 图标。
 *
 * 当数字增长时图标会弹一下，提供积极的视觉反馈。
 *
 * @param count 当前星星数
 * @param animateOnIncrease 数字增长时是否触发弹跳动画
 * @param tint 星星图标颜色，默认使用主题次色（阳光黄）
 * @param fontSize 数字字体大小
 */
@Composable
fun StarCounter(
    count: Int,
    modifier: Modifier = Modifier,
    animateOnIncrease: Boolean = true,
    tint: Color = Secondary,
    fontSize: TextUnit = 20.sp,
) {
    var previousCount by remember { mutableStateOf(count) }
    val scaleAnim = remember { Animatable(1f) }

    LaunchedEffect(count) {
        if (animateOnIncrease && count > previousCount) {
            // 弹跳序列：先放大到 1.4，再回弹到 1.0
            scaleAnim.animateTo(
                targetValue = 1.4f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            )
            scaleAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            )
        }
        previousCount = count
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = count.toString(),
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = "星星",
            tint = tint,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(fontSize.value.dp + 4.dp)
                .scale(scaleAnim.value),
        )
    }
}
