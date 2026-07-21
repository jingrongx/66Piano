package com.pianokids.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pianokids.music.Note
import com.pianokids.music.NoteDuration
import com.pianokids.ui.theme.Correct
import com.pianokids.ui.theme.Primary
import com.pianokids.ui.theme.Secondary
import com.pianokids.ui.theme.Warning

/**
 * 自定义乐谱编辑器界面。
 *
 * 顶部：标题输入 + BPM
 * 中部：水平横向滚动的音符列表，每个音符卡片显示音名+时值
 *      可上下调音高（半音）、循环切换时值、切换休止符、删除
 * 底部：添加音符按钮 + 保存按钮
 *
 * @param pieceId 已有乐谱的 id，新建时为 -1
 * @param onBack 返回
 * @param onSaved 保存成功后回调，参数为 pieceId
 */
@Composable
fun PieceEditorScreen(
    pieceId: Long = -1L,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    viewModel: PieceEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 初始化
    LaunchedEffect(pieceId) {
        if (pieceId > 0) {
            viewModel.loadPiece(pieceId)
        } else if (uiState.notes.isEmpty()) {
            viewModel.startNew()
        }
    }

    // 保存事件
    LaunchedEffect(uiState.savedId) {
        uiState.savedId?.let {
            snackbarHostState.showSnackbar("保存成功")
            viewModel.consumeSavedEvent()
            onSaved(it)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 顶部：返回 + 标题
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
                    text = if (uiState.pieceId == null) "新建乐谱" else "编辑乐谱",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
            }

            // 标题输入
            OutlinedTextField(
                value = uiState.title,
                onValueChange = viewModel::updateTitle,
                label = { Text("曲名") },
                placeholder = { Text("给乐谱起个名字") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            // BPM
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "速度",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = { viewModel.updateTempo(uiState.tempo - 10) }) {
                    Text("-10", fontSize = 14.sp)
                }
                Text(
                    text = "${uiState.tempo}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                IconButton(onClick = { viewModel.updateTempo(uiState.tempo + 10) }) {
                    Text("+10", fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "BPM",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 提示
            Text(
                text = "点击音符卡片可切换时值；↑↓ 调整音高；休止符表示静音",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 音符列表（横向滚动）
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(uiState.notes, key = { i, _ -> i }) { index, note ->
                    NoteCard(
                        note = note,
                        onChangePitchUp = { viewModel.changeNotePitch(index, +1) },
                        onChangePitchDown = { viewModel.changeNotePitch(index, -1) },
                        onChangePitchOctaveUp = { viewModel.changeNotePitch(index, +12) },
                        onChangePitchOctaveDown = { viewModel.changeNotePitch(index, -12) },
                        onCycleDuration = { viewModel.cycleNoteDuration(index) },
                        onToggleRest = { viewModel.toggleRest(index) },
                        onRemove = { viewModel.removeNote(index) },
                    )
                }
            }

            // 添加音符按钮
            Button(
                onClick = viewModel::addNote,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Secondary),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(4.dp))
                Text("添加音符", color = Color.White)
            }

            // 保存按钮
            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "保存乐谱",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun NoteCard(
    note: EditableNote,
    onChangePitchUp: () -> Unit,
    onChangePitchDown: () -> Unit,
    onChangePitchOctaveUp: () -> Unit,
    onChangePitchOctaveDown: () -> Unit,
    onCycleDuration: () -> Unit,
    onToggleRest: () -> Unit,
    onRemove: () -> Unit,
) {
    val isRest = note.midi == Note.REST_MIDI
    val noteName = if (isRest) "休止" else midiToName(note.midi)
    val octave = if (isRest) "" else "${note.midi / 12 - 1}"

    Card(
        modifier = Modifier
            .width(120.dp)
            .height(280.dp)
            .clickable { onCycleDuration() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isRest) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // 顶部删除 + 休止切换
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onToggleRest, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = if (isRest) Icons.Filled.MusicNote else Icons.Filled.Pause,
                        contentDescription = "休止符切换",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // 音名
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isRest) Color(0xFFBDBDBD) else Primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = noteName,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isRest) Color.White else Primary,
                    )
                    if (octave.isNotEmpty()) {
                        Text(
                            text = octave,
                            fontSize = 14.sp,
                            color = if (isRest) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 时值
            Text(
                text = durationLabel(note.beats),
                style = MaterialTheme.typography.bodySmall,
                color = Warning,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 升降调
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onChangePitchUp, modifier = Modifier.size(28.dp)) {
                        Text("+1", fontSize = 12.sp, color = Primary)
                    }
                    IconButton(onClick = onChangePitchOctaveUp, modifier = Modifier.size(28.dp)) {
                        Text("+12", fontSize = 12.sp, color = Correct)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onChangePitchDown, modifier = Modifier.size(28.dp)) {
                        Text("-1", fontSize = 12.sp, color = Primary)
                    }
                    IconButton(onClick = onChangePitchOctaveDown, modifier = Modifier.size(28.dp)) {
                        Text("-12", fontSize = 12.sp, color = Correct)
                    }
                }
            }
        }
    }
}

private val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

private fun midiToName(midi: Int): String {
    val octave = midi / 12 - 1
    return NOTE_NAMES[midi % 12] + octave
}

private fun durationLabel(beats: Double): String = when (beats) {
    NoteDuration.WHOLE -> "全音符 ♩"
    NoteDuration.HALF -> "二分音符"
    NoteDuration.QUARTER -> "四分 ♩"
    NoteDuration.EIGHTH -> "八分 ♪"
    NoteDuration.SIXTEENTH -> "十六分"
    else -> beats.toString()
}
