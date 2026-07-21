package com.pianokids.ui.practice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pianokids.ui.components.PianoKeyboard
import com.pianokids.ui.theme.Correct
import com.pianokids.ui.theme.Primary
import com.pianokids.ui.theme.Secondary
import com.pianokids.ui.theme.Tertiary
import com.pianokids.ui.theme.Warning
import com.pianokids.ui.theme.Wrong

/**
 * 练琴界面。
 *
 * 曲目选择 → 练琴模式（简化五线谱 + 实时高亮反馈）→ 得分。
 *
 * 顶部三个按钮：「我的乐谱库」「自定义创建」「拍照识谱」。
 */
@Composable
fun PracticeScreen(
    onOpenLibrary: () -> Unit = {},
    onCreateNew: () -> Unit = {},
    onScan: () -> Unit = {},
    viewModel: PracticeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val customSongs by viewModel.customSongs.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.snackbar.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (uiState.selectedSong == null) {
                SongListScreen(
                    builtinSongs = viewModel.builtinSongs,
                    customSongs = customSongs,
                    onSelect = viewModel::selectSong,
                    onOpenLibrary = onOpenLibrary,
                    onCreateNew = onCreateNew,
                    onScan = onScan,
                )
            } else if (uiState.finished) {
                ScoreScreen(
                    score = uiState.score,
                    correct = uiState.correctCount,
                    total = uiState.totalCount,
                    onBackToList = viewModel::backToSongList,
                )
            } else {
                PracticeModeScreen(
                    uiState = uiState,
                    onBack = viewModel::backToSongList,
                )
            }
        }
    }
}

// ============== 曲目选择 ==============

@Composable
private fun SongListScreen(
    builtinSongs: List<SongItem>,
    customSongs: List<SongItem>,
    onSelect: (SongItem) -> Unit,
    onOpenLibrary: () -> Unit,
    onCreateNew: () -> Unit,
    onScan: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "选择一首曲目",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )

        // 顶部入口：乐谱库 / 自定义创建 / 拍照识谱
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EntryCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.LibraryMusic,
                label = "我的乐谱",
                color = Secondary,
                onClick = onOpenLibrary,
            )
            EntryCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Edit,
                label = "自定义",
                color = Primary,
                onClick = onCreateNew,
            )
            EntryCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.CameraAlt,
                label = "拍照识谱",
                color = Tertiary,
                onClick = onScan,
            )
        }

        // 内置曲目
        SectionHeader("🎵 内置曲目")
        builtinSongs.forEach { song ->
            SongCard(song = song, accentColor = Primary, onSelect = onSelect)
        }

        // 自定义曲目
        if (customSongs.isNotEmpty()) {
            SectionHeader("✏️ 我的乐谱")
            customSongs.forEach { song ->
                SongCard(song = song, accentColor = Tertiary, onSelect = onSelect)
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
            ) {
                Text(
                    text = "还没有自定义乐谱\n点击上方「自定义」创建，或用「拍照识谱」识别",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun EntryCard(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SongCard(
    song: SongItem,
    accentColor: Color,
    onSelect: (SongItem) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(song) },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = accentColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = song.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
            Text(
                text = "▶",
                color = Color.White,
                fontSize = 28.sp,
            )
        }
    }
}

// ============== 练琴模式 ==============

@Composable
private fun PracticeModeScreen(
    uiState: PracticeUiState,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 顶部：返回 + 进度
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = "小星星",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "${uiState.currentIndex + 1} / ${uiState.totalCount}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 简化五线谱
        Staff(
            notes = uiState.notes,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        // 底部键盘高亮当前应弹
        val currentNote = uiState.notes.getOrNull(uiState.currentIndex)
        val keyboardNotes = uiState.notes
            .filter { it.status == NoteStatus.CURRENT || it.status == NoteStatus.CORRECT || it.status == NoteStatus.WRONG }
            .associate { it.midi to (it.status.toColor() ?: Color.Gray) }
        PianoKeyboard(
            modifier = Modifier.fillMaxWidth(),
            startMidi = 60,
            endMidi = 72,
            highlightedNotes = keyboardNotes.keys,
            noteColors = keyboardNotes,
            keyHeight = 100.dp,
        )
    }
}

// ============== 五线谱 ==============

/**
 * MIDI -> 音阶位置（自然音阶：C=0, D=1, E=2, F=3, G=4, A=5, B=6）
 */
private fun midiToDiatonic(midi: Int): Int {
    val octave = midi / 12 - 1
    val degree = when (midi % 12) {
        0, 1 -> 0   // C
        2, 3 -> 1   // D
        4 -> 2      // E
        5, 6 -> 3   // F
        7, 8 -> 4   // G
        9, 10 -> 5  // A
        11 -> 6     // B
        else -> 0
    }
    return octave * 7 + degree
}

/**
 * 简化五线谱：5 条横线 + 椭圆音符。
 */
@Composable
private fun Staff(
    notes: List<PracticeNote>,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(scrollState),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
        ) {
            drawStaff(notes, lineColor)
        }
    }
}

/**
 * 在 Canvas 上绘制五线谱与音符。
 */
private fun DrawScope.drawStaff(notes: List<PracticeNote>, lineColor: Color) {
    val canvasW = size.width
    val canvasH = size.height
    // 五线谱参数
    val staffCenterY = canvasH / 2f
    val lineGap = 20f
    val halfGap = lineGap / 2f
    // 五线谱 5 条线，从下到上
    val bottomLineY = staffCenterY + lineGap * 2
    // E4 的 diatonic = 4*7 + 2 = 30（底线的位置）
    val bottomLineDiatonic = 30

    // 每个音符的水平间距
    val noteSize = 28f
    val startX = 60f
    val noteSpacing = 48f
    val totalWidth = startX + notes.size * noteSpacing + 40f
    val drawWidth = maxOf(totalWidth, canvasW)

    // 1. 绘制 5 条线
    for (i in 0..4) {
        val y = bottomLineY - i * lineGap
        drawLine(
            color = lineColor,
            start = Offset(20f, y),
            end = Offset(drawWidth - 20f, y),
            strokeWidth = 2f,
        )
    }

    // 2. 绘制高音谱号占位（用文字代替太复杂，画一个简单标记）
    drawLine(
        color = lineColor.copy(alpha = 0.5f),
        start = Offset(20f, bottomLineY - lineGap * 4),
        end = Offset(20f, bottomLineY),
        strokeWidth = 4f,
    )

    // 3. 绘制音符
    notes.forEachIndexed { index, note ->
        val x = startX + index * noteSpacing
        val diatonic = midiToDiatonic(note.midi)
        // Y 位置：diatonic 越大（音越高），Y 越小（越靠上）
        val y = bottomLineY - (diatonic - bottomLineDiatonic) * halfGap

        // 加线（ledger lines）：当音符在五线谱外时
        if (y > bottomLineY + halfGap) {
            // 低于底线，画加线
            var ledgerY = bottomLineY + lineGap
            while (ledgerY <= y + halfGap) {
                drawLine(
                    color = lineColor,
                    start = Offset(x - noteSize / 2 - 4, ledgerY),
                    end = Offset(x + noteSize / 2 + 4, ledgerY),
                    strokeWidth = 2f,
                )
                ledgerY += lineGap
            }
        } else if (y < bottomLineY - lineGap * 4 - halfGap) {
            // 高于顶线，画加线
            val topLineY = bottomLineY - lineGap * 4
            var ledgerY = topLineY - lineGap
            while (ledgerY >= y - halfGap) {
                drawLine(
                    color = lineColor,
                    start = Offset(x - noteSize / 2 - 4, ledgerY),
                    end = Offset(x + noteSize / 2 + 4, ledgerY),
                    strokeWidth = 2f,
                )
                ledgerY -= lineGap
            }
        }

        // 音符颜色
        val noteColor = when (note.status) {
            NoteStatus.PENDING -> Color(0xFF757575)
            NoteStatus.CURRENT -> Warning
            NoteStatus.CORRECT -> Correct
            NoteStatus.WRONG -> Wrong
        }

        // 椭圆音符
        drawOval(
            color = noteColor,
            topLeft = Offset(x - noteSize / 2, y - noteSize / 3),
            size = Size(noteSize, noteSize * 0.66f),
        )
        // 音符杆
        drawLine(
            color = noteColor,
            start = Offset(x + noteSize / 2, y),
            end = Offset(x + noteSize / 2, y - 30f),
            strokeWidth = 2f,
        )

        // 当前音符标记
        if (note.status == NoteStatus.CURRENT) {
            drawCircle(
                color = Warning.copy(alpha = 0.3f),
                radius = noteSize,
                center = Offset(x, y),
            )
        }
    }
}

// ============== 得分界面 ==============

@Composable
private fun ScoreScreen(
    score: Float,
    correct: Int,
    total: Int,
    onBackToList: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "🎉", fontSize = 72.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "完成啦！",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "${(score * 100).toInt()}%",
            style = MaterialTheme.typography.displayLarge,
            color = if (score >= 0.8f) Correct else if (score >= 0.5f) Warning else Wrong,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "弹对 $correct / $total 个音",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onBackToList,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
        ) {
            Text("再练一次", style = MaterialTheme.typography.titleMedium, color = Color.White)
        }
    }
}
