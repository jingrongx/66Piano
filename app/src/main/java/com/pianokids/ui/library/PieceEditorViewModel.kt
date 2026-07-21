package com.pianokids.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pianokids.data.repo.PiecesRepository
import com.pianokids.music.Note
import com.pianokids.music.NoteDuration
import com.pianokids.music.NoteSequence
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 编辑器中一个可编辑的音符项。
 *
 * @property midi MIDI 音符号，[Note.REST_MIDI] 表示休止符
 * @property beats 时值（以四分音符为单位：1=四分, 0.5=八分, 2=二分, 4=全音符）
 */
data class EditableNote(
    val midi: Int,
    val beats: Double,
)

/**
 * 编辑器 UI 状态。
 *
 * @property pieceId 编辑已有乐谱时的 id；新建时为 null
 * @property title 标题
 * @property tempo BPM
 * @property notes 编辑器中的音符列表
 * @property source 来源（CUSTOM / SCAN）
 * @property coverColor 封面色（ARGB int）
 * @property loading 是否正在加载已有数据
 * @property savedEvent 保存成功事件的 pieceId（一次性事件，UI 消费后清空）
 */
data class PieceEditorUiState(
    val pieceId: Long? = null,
    val title: String = "",
    val tempo: Int = 120,
    val notes: List<EditableNote> = emptyList(),
    val source: NoteSequence.Source = NoteSequence.Source.CUSTOM,
    val coverColor: Int = 0xFFFF8A65.toInt(),
    val loading: Boolean = false,
    val savedId: Long? = null,
)

/**
 * 自定义乐谱编辑器 ViewModel。
 *
 * 支持从已有乐谱加载，也支持新建。
 * 每个音符默认四分音符（1 beat），用户可调音高与时值。
 */
@HiltViewModel
class PieceEditorViewModel @Inject constructor(
    private val piecesRepository: PiecesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PieceEditorUiState())
    val uiState: StateFlow<PieceEditorUiState> = _uiState.asStateFlow()

    /**
     * 新建乐谱：初始化 8 个 C4 四分音符。
     * 若 [com.pianokids.ui.scan.ScanResultHolder] 中有拍照识谱结果，优先加载。
     */
    fun startNew() {
        val scanned = com.pianokids.ui.scan.ScanResultHolder.consume()
        if (scanned != null) {
            startFromSequence(scanned)
            return
        }
        _uiState.value = PieceEditorUiState(
            title = "",
            tempo = 120,
            notes = List(8) { EditableNote(midi = 60, beats = 1.0) },
            source = NoteSequence.Source.CUSTOM,
        )
    }

    /**
     * 加载已有乐谱用于编辑。
     */
    fun loadPiece(id: Long) {
        _uiState.value = _uiState.value.copy(loading = true)
        viewModelScope.launch {
            val entity = piecesRepository.get(id) ?: run {
                _uiState.value = _uiState.value.copy(loading = false)
                return@launch
            }
            val seq = piecesRepository.toSequence(entity)
            val notes = seq.notes.map { EditableNote(it.midi, beatsFromMs(it.durationMs, seq.tempo)) }
            _uiState.value = PieceEditorUiState(
                pieceId = entity.id,
                title = entity.title,
                tempo = seq.tempo,
                notes = notes,
                source = seq.source,
                coverColor = entity.coverColor,
            )
        }
    }

    /**
     * 从外部 NoteSequence 初始化（用于拍照识谱后进入编辑器）。
     */
    fun startFromSequence(sequence: NoteSequence) {
        val notes = sequence.notes.map { EditableNote(it.midi, beatsFromMs(it.durationMs, sequence.tempo)) }
        _uiState.value = PieceEditorUiState(
            title = sequence.title,
            tempo = sequence.tempo,
            notes = if (notes.isEmpty()) List(8) { EditableNote(60, 1.0) } else notes,
            source = sequence.source,
        )
    }

    fun updateTitle(value: String) {
        _uiState.update { it.copy(title = value) }
    }

    fun updateTempo(value: Int) {
        _uiState.update { it.copy(tempo = value.coerceIn(40, 240)) }
    }

    /**
     * 修改指定位置音符的音高（按半音上下移动）。
     */
    fun changeNotePitch(index: Int, delta: Int) {
        val current = _uiState.value
        val newNotes = current.notes.toMutableList()
        if (index !in newNotes.indices) return
        val n = newNotes[index]
        val newMidi = (n.midi + delta).coerceIn(36, 96)
        newNotes[index] = n.copy(midi = newMidi)
        _uiState.value = current.copy(notes = newNotes)
    }

    /**
     * 设置指定位置音符的音高为某个具体 MIDI 值。
     */
    fun setNotePitch(index: Int, midi: Int) {
        val current = _uiState.value
        val newNotes = current.notes.toMutableList()
        if (index !in newNotes.indices) return
        newNotes[index] = newNotes[index].copy(midi = midi.coerceIn(36, 96))
        _uiState.value = current.copy(notes = newNotes)
    }

    /**
     * 循环切换指定位置音符的时值：
     * 0.25 → 0.5 → 1.0 → 2.0 → 0.25
     */
    fun cycleNoteDuration(index: Int) {
        val current = _uiState.value
        val newNotes = current.notes.toMutableList()
        if (index !in newNotes.indices) return
        val n = newNotes[index]
        val next = when (n.beats) {
            NoteDuration.SIXTEENTH -> NoteDuration.EIGHTH
            NoteDuration.EIGHTH -> NoteDuration.QUARTER
            NoteDuration.QUARTER -> NoteDuration.HALF
            NoteDuration.HALF -> NoteDuration.WHOLE
            else -> NoteDuration.QUARTER
        }
        newNotes[index] = n.copy(beats = next)
        _uiState.value = current.copy(notes = newNotes)
    }

    /**
     * 切换为休止符 / 恢复 C4。
     */
    fun toggleRest(index: Int) {
        val current = _uiState.value
        val newNotes = current.notes.toMutableList()
        if (index !in newNotes.indices) return
        val n = newNotes[index]
        newNotes[index] = if (n.midi == Note.REST_MIDI) {
            n.copy(midi = 60)
        } else {
            n.copy(midi = Note.REST_MIDI)
        }
        _uiState.value = current.copy(notes = newNotes)
    }

    fun addNote() {
        val current = _uiState.value
        val last = current.notes.lastOrNull()?.copy() ?: EditableNote(60, 1.0)
        _uiState.value = current.copy(notes = current.notes + last)
    }

    fun removeNote(index: Int) {
        val current = _uiState.value
        if (current.notes.size <= 1) return
        val newNotes = current.notes.toMutableList().apply { removeAt(index) }
        _uiState.value = current.copy(notes = newNotes)
    }

    /**
     * 保存：把 EditableNote 转回 NoteSequence 并写入数据库。
     */
    fun save() {
        val current = _uiState.value
        val tempo = current.tempo
        var t = 0L
        val notes = current.notes.map { en ->
            val dur = NoteDuration.ms(en.beats, tempo)
            val note = Note(
                midi = en.midi,
                startMs = t,
                durationMs = dur,
            )
            t += dur
            note
        }
        val sequence = NoteSequence(
            title = current.title,
            tempo = tempo,
            notes = notes,
            durationMs = t,
            source = current.source,
        )
        viewModelScope.launch {
            val id = piecesRepository.save(
                id = current.pieceId,
                title = current.title,
                sequence = sequence,
                coverColor = current.coverColor,
            )
            _uiState.value = current.copy(savedId = id)
        }
    }

    fun consumeSavedEvent() {
        _uiState.update { it.copy(savedId = null) }
    }

    /**
     * 由毫秒反推 beats（以四分音符为单位）。
     */
    private fun beatsFromMs(ms: Long, tempo: Int): Double {
        val beatMs = 60_000.0 / tempo.coerceAtLeast(1)
        return (ms / beatMs).let {
            // 对齐到最近的常用时值
            when {
                it >= 4.0 -> 4.0
                it >= 2.0 -> 2.0
                it >= 1.0 -> 1.0
                it >= 0.5 -> 0.5
                else -> 0.25
            }
        }
    }
}
