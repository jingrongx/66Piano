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
import com.pianokids.tts.TtsHelper
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
 * @property content 当前课程内容
 */
data class LessonUiState(
    val step: Int = 0,
    val soloProgress: Int = 0,
    val highlightedNotes: Set<Int> = emptySet(),
    val noteColors: Map<Int, Color> = emptyMap(),
    val demoPlayed: Boolean = false,
    val completed: Boolean = false,
    val content: LessonContent = LessonCatalog.get("lesson_1"),
)

/**
 * 通用课程教学 ViewModel，支持加载 [LessonCatalog] 中任意一节课。
 *
 * 调用方在进入 [LessonScreen] 时必须调用 [loadLesson] 指定 lessonId。
 *
 * 5 个步骤：看动画 → 看示范 → 跟我弹 → 自己弹 → 领奖励。
 * 全程通过 [snackbar] 发送提示，并通过 [TtsHelper] 语音播报。
 * 完成后调用 [ProgressRepository.completeLesson]。
 */
@HiltViewModel
class LessonViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pitchDetector: PitchDetector,
    private val audioCapture: AudioCapture,
    private val progressRepository: ProgressRepository,
    private val ttsHelper: TtsHelper,
) : ViewModel() {

    companion object {
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
        // 收集音高检测结果（init 中仅订阅流，不立即启动前台服务）
        viewModelScope.launch {
            pitchDetector.pitches.collect { result -> handlePitch(result) }
        }
    }

    /**
     * 进入需要音高检测的步骤（2 跟我弹、3 自己弹）时调用，启动监听服务。
     */
    private fun ensureListeningService() {
        if (audioCapture.hasPermission()) {
            PianoListeningService.start(context)
        }
    }

    /**
     * 加载指定课程。应在进入页面时立即调用。
     */
    fun loadLesson(lessonId: String) {
        val content = LessonCatalog.get(lessonId)
        _uiState.value = LessonUiState(content = content)
        updateHighlightsForStep(0)
        // 播放第 0 步的 TTS 引导
        content.ttsLines.getOrNull(0)?.let { ttsHelper.speak(it) }
    }

    /**
     * 进入下一步。
     */
    fun nextStep() {
        val current = _uiState.value
        val content = current.content
        if (current.step >= 4) return
        val next = current.step + 1
        if (next == 4) {
            // 进入领奖励步骤：完成课程
            viewModelScope.launch {
                progressRepository.completeLesson(content.id, content.rewardStars)
                _uiState.value = _uiState.value.copy(step = 4, completed = true)
                val msg = "恭喜完成 ${content.title}！获得 ${content.rewardStars} 颗星"
                _snackbar.emit(msg)
                ttsHelper.speak(content.ttsLines.getOrNull(4) ?: msg)
            }
        } else {
            _uiState.value = _uiState.value.copy(
                step = next,
                demoPlayed = if (next == 1) false else _uiState.value.demoPlayed,
            )
            updateHighlightsForStep(next)
            // 进入步骤 2（跟我弹）时启动监听服务，为音高检测做准备
            if (next == 2) {
                ensureListeningService()
            }
            content.ttsLines.getOrNull(next)?.let { ttsHelper.speak(it) }
        }
    }

    /**
     * 播放示范音（步骤 1）。
     * 用系统 AudioTrack 生成正弦波，不使用 NativeAudioEngine。
     */
    fun playDemoTone() {
        val content = _uiState.value.content
        val freq = midiToFreq(content.demoMidi)
        viewModelScope.launch {
            playTone(freq, 800L)
            _uiState.value = _uiState.value.copy(demoPlayed = true)
            val msg = "听，这是 ${content.demoName} 的声音~"
            _snackbar.emit(msg)
            ttsHelper.speak(content.ttsLines.getOrNull(1) ?: msg)
        }
    }

    /**
     * 根据当前步骤更新键盘高亮。
     */
    private fun updateHighlightsForStep(step: Int) {
        val state = _uiState.value
        val content = state.content
        when (step) {
            0 -> {
                _uiState.value = state.copy(highlightedNotes = emptySet(), noteColors = emptyMap())
            }
            1 -> {
                _uiState.value = state.copy(
                    highlightedNotes = setOf(content.demoMidi),
                    noteColors = mapOf(content.demoMidi to Color(0xFFFF8A65)),
                )
            }
            2 -> {
                _uiState.value = state.copy(
                    highlightedNotes = setOf(content.followTargets.first()),
                    noteColors = mapOf(content.followTargets.first() to Warning),
                )
            }
            3 -> {
                _uiState.value = state.copy(
                    soloProgress = 0,
                    highlightedNotes = setOf(content.soloTargets.first()),
                    noteColors = mapOf(content.soloTargets.first() to Warning),
                )
            }
            4 -> {
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
        val content = state.content
        val note = result.note ?: return
        if (result.confidence < 0.1f) return
        val midi = note.midiNote
        if (midi < 0) return

        when (state.step) {
            2 -> {
                // 跟我弹
                if (midi == content.followTargets[0]) {
                    handlingCorrect = true
                    _uiState.value = state.copy(noteColors = state.noteColors + (midi to Correct))
                    viewModelScope.launch {
                        _snackbar.emit("弹对了！")
                        ttsHelper.speak("弹对了")
                        delay(800)
                        handlingCorrect = false
                        nextStep()
                    }
                } else {
                    viewModelScope.launch {
                        _snackbar.emit("再试一次~")
                        ttsHelper.speak("再试一次")
                    }
                }
            }
            3 -> {
                // 自己弹：依次弹奏 soloTargets
                val targets = content.soloTargets
                val targetIndex = state.soloProgress
                if (targetIndex < targets.size && midi == targets[targetIndex]) {
                    handlingCorrect = true
                    val newProgress = targetIndex + 1
                    val newColors = state.noteColors + (midi to Correct)
                    if (newProgress >= targets.size) {
                        _uiState.value = state.copy(soloProgress = newProgress, noteColors = newColors)
                        viewModelScope.launch {
                            _snackbar.emit("太棒了！全部弹对啦！")
                            ttsHelper.speak("太棒了，全部弹对啦")
                            delay(1000)
                            handlingCorrect = false
                            nextStep()
                        }
                    } else {
                        val nextMidi = targets[newProgress]
                        _uiState.value = state.copy(
                            soloProgress = newProgress,
                            noteColors = newColors + (nextMidi to Warning),
                            highlightedNotes = state.highlightedNotes + nextMidi,
                        )
                        viewModelScope.launch {
                            _snackbar.emit("弹对了！下一个~")
                            ttsHelper.speak("弹对了，下一个")
                            delay(600)
                            handlingCorrect = false
                        }
                    }
                } else if (midi in targets) {
                    _uiState.value = state.copy(noteColors = state.noteColors + (midi to Wrong))
                    viewModelScope.launch {
                        _snackbar.emit("再试一次~")
                        ttsHelper.speak("再试一次")
                        delay(400)
                        val s = _uiState.value
                        _uiState.value = s.copy(noteColors = s.noteColors - midi)
                    }
                } else {
                    viewModelScope.launch {
                        _snackbar.emit("再试一次~")
                        ttsHelper.speak("再试一次")
                    }
                }
            }
        }
    }

    /**
     * 用系统 AudioTrack 播放一段正弦波音调。
     */
    private suspend fun playTone(frequency: Float, durationMs: Long) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            try {
                val sampleRate = 44_100
                val sampleCount = (sampleRate * durationMs / 1000).toInt()
                val buffer = ShortArray(sampleCount)
                val amplitude = Short.MAX_VALUE * 0.3
                val fadeSamples = (sampleRate * 0.01).toInt()
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
