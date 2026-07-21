package com.pianokids.ui.tuner

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pianokids.audio.PitchResult
import com.pianokids.ui.theme.Correct
import com.pianokids.ui.theme.OnSurface
import com.pianokids.ui.theme.Surface
import com.pianokids.ui.theme.Warning
import com.pianokids.ui.theme.Wrong
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 调音器界面。
 *
 * 大圆形仪表盘，指针随 centsOff 摆动（-50~+50），
 * 中央显示音名，颜色反馈：|cents|<5 绿色，5-15 黄色，>15 红色。
 */
@Composable
fun TunerScreen(
    viewModel: TunerViewModel = hiltViewModel(),
) {
    val pitch by viewModel.pitch.collectAsStateWithLifecycle()

    // 进入页面时启动监听服务
    LaunchedEffect(Unit) {
        viewModel.startListening()
    }

    val centsOff = pitch?.note?.centsOff ?: 0f
    val noteName = pitch?.note?.noteName ?: "--"
    val freq = pitch?.freq ?: 0f
    val isSilent = pitch == null || pitch!!.freq <= 0f

    // 根据偏差选择颜色
    val feedbackColor = when {
        isSilent -> MaterialTheme.colorScheme.onSurfaceVariant
        kotlin.math.abs(centsOff) < 5f -> Correct
        kotlin.math.abs(centsOff) < 15f -> Warning
        else -> Wrong
    }

    val feedbackText = when {
        isSilent -> "弹一个音，看看准不准"
        kotlin.math.abs(centsOff) < 5f -> "完美！"
        kotlin.math.abs(centsOff) < 15f -> "再调一点点"
        else -> "差得有点多哦"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        // 顶部提示
        Text(
            text = if (isSilent) "弹一个音，看看准不准" else feedbackText,
            style = MaterialTheme.typography.headlineSmall,
            color = feedbackColor,
            fontWeight = FontWeight.Bold,
        )

        // 大圆形仪表盘
        TunerGauge(
            centsOff = if (isSilent) 0f else centsOff,
            noteName = noteName,
            color = feedbackColor,
            modifier = Modifier.size(300.dp),
        )

        // 底部频率
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isSilent) "等待演奏…" else "${freq.toInt()} Hz",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            if (!isSilent) {
                Text(
                    text = "音分偏差：${"%+.1f".format(centsOff)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = feedbackColor,
                )
            }
        }
    }
}

/**
 * 调音器仪表盘：指针随 [centsOff] 摆动。
 *
 * -50 音分指针朝左，0 朝上，+50 朝右。
 */
@Composable
private fun TunerGauge(
    centsOff: Float,
    noteName: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    // 平滑动画
    val animatedCents by animateFloatAsState(
        targetValue = centsOff,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 200f),
        label = "needle",
    )

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(Surface, MaterialTheme.colorScheme.surfaceVariant),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawGaugeArc(animatedCents, color)
            drawTickMarks()
            drawNeedle(animatedCents, color)
        }
        // 中央音名
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = noteName,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurface,
            )
            Text(
                text = if (kotlin.math.abs(centsOff) < 0.5f) "♪" else "",
                fontSize = 24.sp,
                color = color,
            )
        }
    }
}

/**
 * 绘制仪表盘弧形（半圆，从左下到右下）。
 */
private fun DrawScope.drawGaugeArc(cents: Float, color: Color) {
    val w = size.width
    val h = size.height
    val center = Offset(w / 2f, h / 2f)
    val radius = minOf(w, h) / 2f * 0.85f

    // 背景弧（半圆，顶部开口朝上）
    val sweepAngle = 180f
    drawArc(
        color = Color.LightGray.copy(alpha = 0.3f),
        startAngle = 180f,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 12f, cap = StrokeCap.Round),
    )

    // 彩色弧（从中央向两侧延伸，反映偏差）
    val clampedCents = cents.coerceIn(-50f, 50f)
    val angleFromTop = (clampedCents / 50f) * 90f // -90..90
    // 0 度在正上方（Canvas 中 270°）
    val needleAngleDeg = 270f + angleFromTop
    // 绿色中心区域（-15..15 度即 -30%..30%）
    drawArc(
        color = Correct.copy(alpha = 0.4f),
        startAngle = 270f - 27f,
        sweepAngle = 54f,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 12f, cap = StrokeCap.Round),
    )
}

/**
 * 绘制刻度线。
 */
private fun DrawScope.drawTickMarks() {
    val w = size.width
    val h = size.height
    val center = Offset(w / 2f, h / 2f)
    val radius = minOf(w, h) / 2f * 0.85f

    // 每 10 音分一个刻度，从 -50 到 +50
    for (cents in -50..50 step 10) {
        val angleDeg = (cents / 50f) * 90f // -90..90
        val angleRad = (angleDeg - 90f) * (PI / 180f).toFloat() // Canvas 角度系
        val outer = Offset(
            x = center.x + radius * cos(angleRad),
            y = center.y + radius * sin(angleRad),
        )
        val tickLen = if (cents == 0) 24f else 14f
        val inner = Offset(
            x = center.x + (radius - tickLen) * cos(angleRad),
            y = center.y + (radius - tickLen) * sin(angleRad),
        )
        drawLine(
            color = if (cents == 0) Correct else Color.Gray,
            start = inner,
            end = outer,
            strokeWidth = if (cents == 0) 5f else 3f,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * 绘制指针。
 */
private fun DrawScope.drawNeedle(cents: Float, color: Color) {
    val w = size.width
    val h = size.height
    val center = Offset(w / 2f, h / 2f)
    val radius = minOf(w, h) / 2f * 0.78f

    val clampedCents = cents.coerceIn(-50f, 50f)
    val angleDeg = (clampedCents / 50f) * 90f // -90..90，0=朝上

    rotate(degrees = angleDeg, pivot = center) {
        // 指针主体：从中心向上
        drawLine(
            color = color,
            start = center,
            end = Offset(center.x, center.y - radius),
            strokeWidth = 6f,
            cap = StrokeCap.Round,
        )
        // 中心圆
        drawCircle(
            color = color,
            radius = 14f,
            center = center,
        )
        drawCircle(
            color = Color.White,
            radius = 6f,
            center = center,
        )
    }
}
