package com.pianokids.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pianokids.ui.theme.Correct
import com.pianokids.ui.theme.PianoBlackKey
import com.pianokids.ui.theme.PianoWhiteKey
import com.pianokids.ui.theme.PianoWhiteKeyBorder
import com.pianokids.ui.theme.Warning
import com.pianokids.ui.theme.Wrong

/**
 * 钢琴键盘高亮颜色。
 */
enum class KeyHighlightColor(val color: Color) {
    /** 默认高亮：主色橙 */
    ACCENT(Color(0xFFFF8A65)),

    /** 正确：绿 */
    CORRECT(Correct),

    /** 当前应弹：黄 */
    CURRENT(Warning),

    /** 错误：红 */
    WRONG(Wrong),
}

// ============== 音名工具 ==============

private val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

/** MIDI 音符号 -> 音名（如 60 -> "C4"） */
fun midiToNoteName(midi: Int): String {
    val octave = midi / 12 - 1
    val name = NOTE_NAMES[midi % 12]
    return "$name$octave"
}

/** 判断某 MIDI 音是否为黑键 */
private fun isBlackKey(midi: Int): Boolean {
    val pc = midi % 12
    return pc == 1 || pc == 3 || pc == 6 || pc == 8 || pc == 10
}

/**
 * 一个白键的描述：MIDI 音符号 + 该白键在白键序列中的索引。
 */
private data class WhiteKey(val midi: Int, val indexInWhites: Int)

/**
 * 一个黑键的描述：MIDI 音符号 + 它左侧紧邻的白键索引（用于定位）。
 */
private data class BlackKey(val midi: Int, val leftWhiteIndex: Int)

/**
 * 计算从 [startMidi] 到 [endMidi]（含）范围内的所有白键与黑键。
 */
private fun buildKeys(startMidi: Int, endMidi: Int): Pair<List<WhiteKey>, List<BlackKey>> {
    val whites = mutableListOf<WhiteKey>()
    val blacks = mutableListOf<BlackKey>()
    var whiteIndex = 0
    for (midi in startMidi..endMidi) {
        if (isBlackKey(midi)) {
            blacks.add(BlackKey(midi, whiteIndex - 1))
        } else {
            whites.add(WhiteKey(midi, whiteIndex))
            whiteIndex++
        }
    }
    return whites to blacks
}

/**
 * 可复用的钢琴键盘 composable。
 *
 * 显示 2 个八度（C4~C6），白键黑键比例正确。
 * 支持高亮指定的 MIDI 音符并指定高亮颜色。
 * P1 仅用于展示，不可点击。
 *
 * @param startMidi 起始 MIDI 音符号（默认 C4=60）
 * @param endMidi 结束 MIDI 音符号（含，默认 C6=84）
 * @param highlightedNotes 需要高亮的 MIDI 音符集合
 * @param highlightColor 统一高亮颜色；若 [noteColors] 未指定某键则用此色
 * @param noteColors 每个 MIDI 音符单独指定的高亮颜色；优先级高于 [highlightColor]
 * @param showNoteLabels 是否在白键上显示音名
 * @param keyHeight 键盘高度
 */
@Composable
fun PianoKeyboard(
    modifier: Modifier = Modifier,
    startMidi: Int = 60,
    endMidi: Int = 84,
    highlightedNotes: Set<Int> = emptySet(),
    highlightColor: Color? = null,
    noteColors: Map<Int, Color> = emptyMap(),
    showNoteLabels: Boolean = true,
    keyHeight: Dp = 160.dp,
) {
    val (whites, blacks) = buildKeys(startMidi, endMidi)
    if (whites.isEmpty()) return

    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(keyHeight)) {
        val totalWidthPx = constraints.maxWidth.toFloat()
        val whiteCount = whites.size
        val whiteWidthPx = if (whiteCount > 0) totalWidthPx / whiteCount else totalWidthPx
        val blackWidthPx = whiteWidthPx * 0.6f
        val density = LocalDensity.current

        // 1. 白键层
        Row(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        ) {
            whites.forEach { wk ->
                val color = noteColors[wk.midi] ?: highlightColor
                val isHighlighted = wk.midi in highlightedNotes
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 1.dp)
                        .clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                        .background(if (isHighlighted && color != null) color else PianoWhiteKey)
                        .border(1.dp, PianoWhiteKeyBorder, RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (showNoteLabels) {
                        Text(
                            text = midiToNoteName(wk.midi),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isHighlighted && color != null) Color.White else Color(0xFF757575),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                }
            }
        }

        // 2. 黑键层：用绝对偏移定位，覆盖在白键之上
        blacks.forEach { bk ->
            val color = noteColors[bk.midi] ?: highlightColor
            val isHighlighted = bk.midi in highlightedNotes
            // 黑键中心 = 左侧白键右边界 = (leftWhiteIndex + 1) * whiteWidth
            val centerX = (bk.leftWhiteIndex + 1) * whiteWidthPx
            val leftPx = centerX - blackWidthPx / 2f
            val leftDp = with(density) { leftPx.toDp() }
            val blackWidthDp = with(density) { blackWidthPx.toDp() }
            Box(
                modifier = Modifier
                    .padding(start = leftDp)
                    .width(blackWidthDp)
                    .fillMaxHeight(0.62f)
                    .padding(horizontal = 1.dp)
                    .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                    .background(if (isHighlighted && color != null) color else PianoBlackKey)
                    .align(Alignment.TopStart),
            )
        }
    }
}
