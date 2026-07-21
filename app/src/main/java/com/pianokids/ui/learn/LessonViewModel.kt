package com.pianokids.ui.learn

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.Color
import com.pianokids.audio.AudioCapture
import com.pianokids.audio.PianoListeningService
import com.pianokids.audio.PitchDetector
import com.pianokids.audio.PitchResult
import com.pianokids.data.repo.ProgressRepository
import com.pianokids.ui.theme.Correct
import com.pianokids.ui.theme.Warning
import com.pianokids.ui.theme.Wrong
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.sin

/**
 * 课程教学 UI 状态。
 *
 * @property step 当前步骤（0~4）
 * @property soloProgress 自己弹步骤中已完成的音符数
 * @property highlightedNotes 键盘上需高亮的 MIDI 音符
 * @property noteColors 每个高亮音符的颜色
 * @property demoPlayed 示范音是否已播放
 * @property completed 课程是否已完成
 */
data class LessonUiState(
    val step: Int = 0,
    val soloProgress: Int = 0,
    val highlightedNotes: Set<Int> = emptySet(),
    val noteColors: Map<Int, Color> = emptyMap(),
    val demoPlayed: Boolean = false,
    val completed: Boolean = false,
)

/**
 * "第 1 课：认识钢琴"教学 ViewModel。
 *
 * 5 个步骤：看动画 → 看示范 → 跟我弹 → 自己弹 → 领奖励。
 * 全程通过 [snackbar] 发送语音提示，完成后调用 [ProgressRepository.completeLesson]。
 */
@HiltViewModel
class LessonViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pitchDetector: PitchDetector,
    private val audioCapture: AudioCapture,
    private val progressRepository: ProgressRepository,
) : ViewModel() {

    companion object {
        /** MIDI: C4=60, D4=62, E4=64 */
        private const val MIDI_C4 = 60
        private const val MIDI_D4 = 62
        private const val MIDI_E4 = 64

        /** 跟我弹目标音符 */
        private val FOLLOW_TARGETS = listOf(MIDI_C4)
        /** 自己弹目标音符（依次） */
        private val SOLO_TARGETS = listOf(MIDI_C4, MIDI_D4, MIDI_E4)

        /** MIDI -> 频率 */
        fun midiToFreq(midi: Int): Float =
            (440.0 * Math.pow(2.0, (midi - 69) / 12.0)).toFloat()
    }

    private val _uiState = MutableStateFlow(LessonUiState())
    val uiState: StateFlow<LessonUiState> = _uiState.asStateFlow()

    private val _snackbar = MutableSharedFlow<String>()
    val snackbar: SharedFlow<String> = _snackbar.asSharedFlow()

    /** 标记是否正在处理一次正确命中（避免重复触发） */
    private var handlingCorrect = false

    init {
        // 启动监听服务，为步骤 3、4 的音高检测做准备
        if (audioCapture.hasPermission()) {
            PianoListeningService.start(context)
        }
        // 收集音高检测结果
        viewModelScope.launch {
            pitchDetector.pitches.collect { result -> handlePitch(result) }
        }
        // 初始化高亮：步骤 0 无高亮
        updateHighlightsForStep(0)
    }

    /**
     * 进入下一步。
     */
    fun nextStep() {
        val current = _uiState.value.step
        if (current >= 4) return
        val next = current + 1
        if (next == 4) {
            // 进入领奖励步骤：完成课程
            viewModelScope.launch {
                progressRepository.completeLesson("lesson_1", 3)
                _uiState.value = _uiState.value.copy(step = 4, completed = true)
                _snackbar.emit("恭喜完成第 1 课！")
            }
        } else {
            _uiState.value = _uiState.value.copy(
                step = next,
                demoPlayed = if (next == 1) false else _uiState.value.demoPlayed,
            )
            updateHighlightsForStep(next)
        }
    }

    /**
     * 播放示范音（步骤 1）。
     * 用系统 AudioTrack 生成正弦波，不使用 NativeAudioEngine。
     */
    fun playDemoTone() {
        val freq = midiToFreq(MIDI_C4)
        viewModelScope.launch {
            playTone(freq, 800L)
            _uiState.value = _uiState.value.copy(demoPlayed = true)
            _snackbar.emit("听，这是中央 C 的声音~")
        }
    }

    /**
     * 根据当前步骤更新键盘高亮。
     */
    private fun updateHighlightsForStep(step: Int) {
        val state = _uiState.value
        when (step) {
            0 -> {
                // 看动画：不高亮
                _uiState.value = state.copy(highlightedNotes = emptySet(), noteColors = emptyMap())
            }
            1 -> {
                // 看示范：高亮中央 C
                _uiState.value = state.copy(
                    highlightedNotes = setOf(MIDI_C4),
                    noteColors = mapOf(MIDI_C4 to Color(0xFFFF8A65)),
                )
            }
            2 -> {
                // 跟我弹：高亮 C4（当前应弹）
                _uiState.value = state.copy(
                    highlightedNotes = setOf(MIDI_C4),
                    noteColors = mapOf(MIDI_C4 to Warning),
                )
            }
            3 -> {
                // 自己弹：高亮第一个目标 C4
                _uiState.value = state.copy(
                    soloProgress = 0,
                    highlightedNotes = setOf(MIDI_C4),
                    noteColors = mapOf(MIDI_C4 to Warning),
                )
            }
            4 -> {
                // 领奖励：清空高亮
                _uiState.value = state.copy(highlightedNotes = emptySet(), noteColors = emptyMap())
            }
        }
    }

    /**
     * 处理音高检测结果：在步骤 2、3 中判断是否弹对。
     */
    private fun handlePitch(result: PitchResult) {
        if (handlingCorrect) return
        val state = _uiState.value
        val note = result.note ?: return
        if (result.confidence < 0.1f) return
        val midi = note.midiNote
        if (midi < 0) return

        when (state.step) {
            2 -> {
                // 跟我弹：弹 C4
                if (midi == FOLLOW_TARGETS[0]) {
                    handlingCorrect = true
                    _uiState.value = state.copy(noteColors = state.noteColors + (midi to Correct))
                    viewModelScope.launch {
                        _snackbar.emit("弹对了！")
                        delay(800)
                        handlingCorrect = false
                        nextStep()
                    }
                } else {
                    viewModelScope.launch { _snackbar.emit("再试一次~") }
                }
            }
            3 -> {
                // 自己弹：依次弹 C D E
                val targetIndex = state.soloProgress
                if (targetIndex < SOLO_TARGETS.size && midi == SOLO_TARGETS[targetIndex]) {
                    handlingCorrect = true
                    val newProgress = targetIndex + 1
                    val newColors = state.noteColors + (midi to Correct)
                    if (newProgress >= SOLO_TARGETS.size) {
                        // 全部完成
                        _uiState.value = state.copy(soloProgress = newProgress, noteColors = newColors)
                        viewModelScope.launch {
                            _snackbar.emit("太棒了！全部弹对啦！")
                            delay(1000)
                            handlingCorrect = false
                            nextStep()
                        }
                    } else {
                        // 高亮下一个目标
                        val nextMidi = SOLO_TARGETS[newProgress]
                        _uiState.value = state.copy(
                            soloProgress = newProgress,
                            noteColors = newColors + (nextMidi to Warning),
                            highlightedNotes = state.highlightedNotes + nextMidi,
                        )
                        viewModelScope.launch {
                            _snackbar.emit("弹对了！下一个~")
                            delay(600)
                            handlingCorrect = false
                        }
                    }
                } else if (midi in SOLO_TARGETS) {
                    // 弹了正确的音但顺序不对
                    _uiState.value = state.copy(noteColors = state.noteColors + (midi to Wrong))
                    viewModelScope.launch {
                        _snackbar.emit("再试一次~")
                        delay(400)
                        // 恢复颜色
                        val s = _uiState.value
                        _uiState.value = s.copy(noteColors = s.noteColors - midi)
                    }
                } else {
                    viewModelScope.launch { _snackbar.emit("再试一次~") }
                }
            }
        }
    }

    /**
     * 用系统 AudioTrack 播放一段正弦波音调。
     *
     * @param frequency 频率（Hz）
     * @param durationMs 持续时间（毫秒）
     */
    private suspend fun playTone(frequency: Float, durationMs: Long) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            try {
                val sampleRate = 44_100
                val sampleCount = (sampleRate * durationMs / 1000).toInt()
                val buffer = ShortArray(sampleCount)
                val amplitude = Short.MAX_VALUE * 0.3
                val fadeSamples = (sampleRate * 0.01).toInt() // 10ms 淡入淡出
                for (i in 0 until sampleCount) {
                    val t = i.toDouble() / sampleRate
                    val fade = when {
                        i < fadeSamples -> i.toDouble() / fadeSamples
                        i > sampleCount - fadeSamples ->
                            (sampleCount - i).toDouble() / fadeSamples
                        else -> 1.0
                    }
                    buffer[i] = (amplitude * fade * sin(2.0 * PI * frequency * t)).toInt().toShort()
                }

                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .build(),
                    )
                    .setBufferSizeInBytes(sampleCount * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(buffer, 0, sampleCount)
                audioTrack.play()
                delay(durationMs + 100)
                try {
                    audioTrack.stop()
                } catch (_: IllegalStateException) {
                }
                audioTrack.release()
            } catch (_: Exception) {
                // 播放失败时静默忽略，不影响教学流程
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // 不在此停止监听服务：其他页面可能仍需要
    }
}
