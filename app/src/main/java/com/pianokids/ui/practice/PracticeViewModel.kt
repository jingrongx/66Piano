package com.pianokids.ui.practice

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pianokids.audio.AudioCapture
import com.pianokids.audio.PianoListeningService
import com.pianokids.audio.PitchDetector
import com.pianokids.audio.PitchResult
import com.pianokids.data.repo.ProgressRepository
import androidx.compose.ui.graphics.Color
import com.pianokids.ui.theme.Correct
import com.pianokids.ui.theme.Warning
import com.pianokids.ui.theme.Wrong
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 练习曲目的一个音符。
 *
 * @property midi MIDI 音符号
 * @property name 音名（如 "C4"）
 * @property status 该音符的演奏状态
 */
data class PracticeNote(
    val midi: Int,
    val name: String,
    val status: NoteStatus,
)

enum class NoteStatus {
    /** 未演奏 */
    PENDING,
    /** 当前应弹 */
    CURRENT,
    /** 弹对 */
    CORRECT,
    /** 弹错 */
    WRONG,
}

/**
 * 练习界面 UI 状态。
 */
data class PracticeUiState(
    val selectedSong: String? = null,
    val notes: List<PracticeNote> = emptyList(),
    val currentIndex: Int = 0,
    val score: Float = 0f,
    val finished: Boolean = false,
    val correctCount: Int = 0,
    val totalCount: Int = 0,
)

/**
 * 可选曲目信息。
 */
data class SongItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val notes: List<Int>, // MIDI 音符序列
)

/**
 * 练琴界面 ViewModel。
 *
 * 曲目选择（P1 仅"小星星"），选中后进入练琴模式，
 * 实时检测孩子弹的音并高亮反馈。
 */
@HiltViewModel
class PracticeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pitchDetector: PitchDetector,
    private val audioCapture: AudioCapture,
    private val progressRepository: ProgressRepository,
) : ViewModel() {

    companion object {
        /** 小星星旋律（C 大调）：C C G G A A G | F F E E D D C */
        private val TWINKLE_NOTES = listOf(60, 60, 67, 67, 69, 69, 67, 65, 65, 64, 64, 62, 62, 60)

        private val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

        fun midiToName(midi: Int): String {
            val octave = midi / 12 - 1
            return NOTE_NAMES[midi % 12] + octave
        }
    }

    /** 可选曲目列表 */
    val songs: List<SongItem> = listOf(
        SongItem(
            id = "twinkle",
            title = "小星星",
            subtitle = "C 大调 · 入门",
            notes = TWINKLE_NOTES,
        ),
    )

    private val _uiState = MutableStateFlow(PracticeUiState())
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()

    private val _snackbar = MutableSharedFlow<String>()
    val snackbar: SharedFlow<String> = _snackbar.asSharedFlow()

    /** 标记是否正在处理一个音符（防重复） */
    private var handling = false

    init {
        // 启动监听服务
        if (audioCapture.hasPermission()) {
            PianoListeningService.start(context)
        }
        // 收集音高
        viewModelScope.launch {
            pitchDetector.pitches.collect { result -> handlePitch(result) }
        }
    }

    /**
     * 选择一首曲目并进入练琴模式。
     */
    fun selectSong(song: SongItem) {
        val notes = song.notes.mapIndexed { index, midi ->
            PracticeNote(
                midi = midi,
                name = midiToName(midi),
                status = if (index == 0) NoteStatus.CURRENT else NoteStatus.PENDING,
            )
        }
        _uiState.value = PracticeUiState(
            selectedSong = song.id,
            notes = notes,
            currentIndex = 0,
            correctCount = 0,
            totalCount = notes.size,
        )
    }

    /**
     * 返回曲目选择。
     */
    fun backToSongList() {
        _uiState.value = PracticeUiState()
    }

    /**
     * 处理音高检测结果。
     */
    private fun handlePitch(result: PitchResult) {
        if (handling) return
        val state = _uiState.value
        if (state.selectedSong == null || state.finished) return
        val note = result.note ?: return
        if (result.confidence < 0.1f) return
        val midi = note.midiNote
        if (midi < 0) return

        val currentNote = state.notes.getOrNull(state.currentIndex) ?: return
        if (midi == currentNote.midi) {
            // 弹对
            handling = true
            val newNotes = state.notes.toMutableList()
            newNotes[state.currentIndex] = currentNote.copy(status = NoteStatus.CORRECT)
            val newIndex = state.currentIndex + 1
            val newCorrect = state.correctCount + 1
            if (newIndex >= state.notes.size) {
                // 完成
                val score = newCorrect.toFloat() / state.notes.size
                _uiState.value = state.copy(
                    notes = newNotes,
                    currentIndex = newIndex,
                    correctCount = newCorrect,
                    score = score,
                    finished = true,
                )
                viewModelScope.launch {
                    _snackbar.emit("太棒了！整首曲子弹完啦！")
                    // 记录练习会话
                    progressRepository.recordSession(
                        lessonId = "practice_${state.selectedSong}",
                        durationMs = 0L,
                        accuracy = score,
                    )
                    handling = false
                }
            } else {
                // 高亮下一个
                newNotes[newIndex] = newNotes[newIndex].copy(status = NoteStatus.CURRENT)
                _uiState.value = state.copy(
                    notes = newNotes,
                    currentIndex = newIndex,
                    correctCount = newCorrect,
                )
                viewModelScope.launch {
                    _snackbar.emit("弹对了！继续~")
                    kotlinx.coroutines.delay(500)
                    handling = false
                }
            }
        } else {
            // 弹错
            handling = true
            val newNotes = state.notes.toMutableList()
            newNotes[state.currentIndex] = currentNote.copy(status = NoteStatus.WRONG)
            // 计算错误后的得分：每错一次扣分
            val newScore = state.correctCount.toFloat() / state.notes.size
            _uiState.value = state.copy(notes = newNotes)
            viewModelScope.launch {
                _snackbar.emit("再试一次~")
                kotlinx.coroutines.delay(500)
                // 恢复为 CURRENT
                val s = _uiState.value
                val restored = s.notes.toMutableList()
                if (restored.size > s.currentIndex) {
                    restored[s.currentIndex] = restored[s.currentIndex].copy(status = NoteStatus.CURRENT)
                    _uiState.value = s.copy(notes = restored)
                }
                handling = false
            }
        }
    }
}

/**
 * 将 [NoteStatus] 映射为键盘高亮颜色。
 */
fun NoteStatus.toColor(): Color? = when (this) {
    NoteStatus.PENDING -> null
    NoteStatus.CURRENT -> Warning
    NoteStatus.CORRECT -> Correct
    NoteStatus.WRONG -> Wrong
}
