package com.pianokids.ui.challenge

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pianokids.audio.AudioCapture
import com.pianokids.audio.PitchDetector
import com.pianokids.audio.PitchResult
import com.pianokids.audio.PianoListeningService
import com.pianokids.data.prefs.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * 闯关模式。
 */
enum class ChallengeMode {
    /** 选择模式（未开始） */
    IDLE,
    /** 听音：听音辨音名 */
    EAR_TRAINING,
    /** 节奏：复读节奏 */
    RHYTHM,
    /** 视奏：看谱弹奏 */
    SIGHT_READING,
}

/**
 * 闯关题目。
 *
 * @property type 题目类型
 * @property targetMidi 听音/视奏的目标 MIDI 音符
 * @property options 听音选项（5 个）
 * @property sequence 视奏目标序列
 * @property rhythmPattern 节奏目标间隔（毫秒）
 */
data class ChallengeQuestion(
    val type: ChallengeMode,
    val targetMidi: Int = 0,
    val options: List<Int> = emptyList(),
    val sequence: List<Int> = emptyList(),
    val rhythmPattern: List<Long> = emptyList(),
)

/**
 * 闯关 UI 状态。
 *
 * @property mode 当前模式
 * @property round 当前回合（1-indexed）
 * @property totalRounds 总回合数
 * @property score 已得分（正确数 × 10）
 * @property highScore 历史最高分
 * @property question 当前题目
 * @property feedback 反馈文案（"correct"/"wrong"/null）
 * @property finished 是否已完成所有回合
 * @property sightReadingIndex 视奏当前已弹对的位置
 * @property rhythmTaps 用户敲击节奏的时间戳序列
 */
data class ChallengeUiState(
    val mode: ChallengeMode = ChallengeMode.IDLE,
    val round: Int = 0,
    val totalRounds: Int = 5,
    val score: Int = 0,
    val highScore: Int = 0,
    val question: ChallengeQuestion? = null,
    val feedback: String? = null,
    val finished: Boolean = false,
    val sightReadingIndex: Int = 0,
    val rhythmTaps: List<Long> = emptyList(),
    val questionStartTime: Long = 0L,
)

/**
 * 闯关大冒险 ViewModel。
 *
 * 三种模式各 5 回合：
 * - 听音：播放一个音，从 5 个选项中选出正确音名
 * - 节奏：敲击复读节奏
 * - 视奏：按顺序弹奏 3 个音
 *
 * 完成后根据分数奖励星星，并保存历史最高分。
 */
@HiltViewModel
class ChallengeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pitchDetector: PitchDetector,
    private val audioCapture: AudioCapture,
    private val userPreferences: UserPreferences,
) : ViewModel() {

    companion object {
        /** C4 D4 E4 F4 G4 — 听音/视奏候选池 */
        private val NOTE_POOL = listOf(60, 62, 64, 65, 67)

        /** MIDI -> 频率 */
        fun midiToFreq(midi: Int): Float =
            (440.0 * Math.pow(2.0, (midi - 69) / 12.0)).toFloat()

        /** 节奏容差（毫秒） */
        private const val RHYTHM_TOLERANCE_MS = 200L

        /** 节奏敲击间隔范围 */
        private const val RHYTHM_MIN_GAP = 350L
        private const val RHYTHM_MAX_GAP = 750L

        /** 节奏敲击数 */
        private const val RHYTHM_TAP_COUNT = 4

        /** 每题奖励星星数 */
        private const val STARS_PER_CORRECT = 2
    }

    private val _uiState = MutableStateFlow(ChallengeUiState())
    val uiState: StateFlow<ChallengeUiState> = _uiState.asStateFlow()

    private val _snackbar = MutableSharedFlow<String>()
    val snackbar: SharedFlow<String> = _snackbar.asSharedFlow()

    /** 刷新触发器：变更后重新读取 DataStore */
    private val prefsRefresh = MutableStateFlow(0)

    /** 历史最高分：随 [prefsRefresh] 变化重新读取 */
    val highScore: StateFlow<Int> = prefsRefresh
        .map { userPreferences.challengeHighScore() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0,
        )

    /** 视奏中是否正在处理一次正确命中 */
    private var handlingCorrect = false

    init {
        // 监听音高用于视奏模式
        viewModelScope.launch {
            pitchDetector.pitches.collect { result -> handlePitch(result) }
        }
    }

    /**
     * 选择模式开始挑战。
     */
    fun selectMode(mode: ChallengeMode) {
        if (mode == ChallengeMode.IDLE) return
        // 视奏模式需要音频采集
        if (mode == ChallengeMode.SIGHT_READING && audioCapture.hasPermission()) {
            PianoListeningService.start(context)
        }
        _uiState.value = ChallengeUiState(
            mode = mode,
            round = 1,
            totalRounds = 5,
            highScore = highScore.value,
        )
        nextQuestion()
    }

    /**
     * 生成下一题。
     */
    private fun nextQuestion() {
        val state = _uiState.value
        val mode = state.mode
        val random = Random(System.currentTimeMillis())

        val question = when (mode) {
            ChallengeMode.EAR_TRAINING -> {
                val target = NOTE_POOL.random(random)
                val options = (NOTE_POOL.toMutableList().also { it.shuffle(random) }).take(5)
                ChallengeQuestion(
                    type = mode,
                    targetMidi = target,
                    options = options,
                )
            }
            ChallengeMode.RHYTHM -> {
                val gaps = (1 until RHYTHM_TAP_COUNT).map {
                    random.nextLong(RHYTHM_MIN_GAP, RHYTHM_MAX_GAP)
                }
                ChallengeQuestion(type = mode, rhythmPattern = gaps)
            }
            ChallengeMode.SIGHT_READING -> {
                val seq = NOTE_POOL.toMutableList().also { it.shuffle(random) }.take(3)
                ChallengeQuestion(type = mode, sequence = seq)
            }
            ChallengeMode.IDLE -> return
        }

        _uiState.value = state.copy(
            question = question,
            feedback = null,
            sightReadingIndex = 0,
            rhythmTaps = emptyList(),
            questionStartTime = 0L,
        )

        // 听音和节奏模式：播放示范音
        when (mode) {
            ChallengeMode.EAR_TRAINING -> {
                viewModelScope.launch {
                    delay(300)
                    playTone(midiToFreq(question.targetMidi), 700L)
                }
            }
            ChallengeMode.RHYTHM -> {
                viewModelScope.launch {
                    delay(300)
                    playRhythm(question.rhythmPattern)
                }
            }
            ChallengeMode.SIGHT_READING -> {
                // 视奏无需示范音，等待用户弹奏
            }
            ChallengeMode.IDLE -> {}
        }
    }

    /**
     * 听音模式：提交答案。
     */
    fun submitEarTrainingAnswer(midi: Int) {
        val state = _uiState.value
        if (state.mode != ChallengeMode.EAR_TRAINING) return
        val correct = midi == state.question?.targetMidi
        applyAnswer(correct)
    }

    /**
     * 节奏模式：用户敲击一次。
     */
    fun recordRhythmTap() {
        val state = _uiState.value
        if (state.mode != ChallengeMode.RHYTHM) return
        val now = System.currentTimeMillis()
        val startTime = if (state.questionStartTime == 0L) {
            _uiState.value = state.copy(questionStartTime = now)
            now
        } else {
            state.questionStartTime
        }
        val newTaps = state.rhythmTaps + (now - startTime)
        _uiState.value = _uiState.value.copy(rhythmTaps = newTaps)

        if (newTaps.size >= RHYTHM_TAP_COUNT) {
            // 检查节奏匹配度
            val pattern = state.question?.rhythmPattern ?: emptyList()
            val matched = countMatchedTaps(newTaps, pattern)
            // 至少 3/4 匹配视为通过
            val correct = matched >= 3
            applyAnswer(correct)
        }
    }

    /**
     * 节奏模式：重新播放当前节奏。
     */
    fun replayRhythm() {
        val state = _uiState.value
        if (state.mode != ChallengeMode.RHYTHM) return
        viewModelScope.launch {
            playRhythm(state.question?.rhythmPattern ?: emptyList())
        }
    }

    /**
     * 听音模式：重新播放当前音。
     */
    fun replayEarTraining() {
        val state = _uiState.value
        if (state.mode != ChallengeMode.EAR_TRAINING) return
        viewModelScope.launch {
            playTone(midiToFreq(state.question?.targetMidi ?: 60), 700L)
        }
    }

    /**
     * 视奏模式：重置当前题（用户重新开始这题）。
     */
    fun resetSightReading() {
        _uiState.value = _uiState.value.copy(sightReadingIndex = 0, feedback = null)
    }

    /**
     * 应用一题的答案：计分并进入下一回合或结束。
     */
    private fun applyAnswer(correct: Boolean) {
        val state = _uiState.value
        val newScore = state.score + if (correct) 10 else 0
        _uiState.value = state.copy(
            score = newScore,
            feedback = if (correct) "correct" else "wrong",
        )
        viewModelScope.launch {
            _snackbar.emit(if (correct) "正确！+10 分" else "再接再厉~")
            delay(800)
            if (state.round >= state.totalRounds) {
                finishChallenge(newScore)
            } else {
                _uiState.value = _uiState.value.copy(round = state.round + 1)
                nextQuestion()
            }
        }
    }

    /**
     * 处理视奏模式的音高检测。
     */
    private fun handlePitch(result: PitchResult) {
        if (handlingCorrect) return
        val state = _uiState.value
        if (state.mode != ChallengeMode.SIGHT_READING) return
        val seq = state.question?.sequence ?: return
        val note = result.note ?: return
        if (result.confidence < 0.1f) return
        val midi = note.midiNote
        if (midi < 0) return

        val idx = state.sightReadingIndex
        if (idx >= seq.size) return
        if (midi != seq[idx]) return

        handlingCorrect = true
        val newIndex = idx + 1
        _uiState.value = state.copy(sightReadingIndex = newIndex)
        viewModelScope.launch {
            if (newIndex >= seq.size) {
                _snackbar.emit("全部弹对！")
                delay(300)
                handlingCorrect = false
                applyAnswer(true)
            } else {
                _snackbar.emit("弹对了！下一个~")
                delay(500)
                handlingCorrect = false
            }
        }
    }

    /**
     * 结束挑战：保存最高分、发放星星奖励。
     */
    private suspend fun finishChallenge(finalScore: Int) {
        val correctCount = finalScore / 10
        val rewardStars = correctCount * STARS_PER_CORRECT
        userPreferences.completeChallenge(finalScore, rewardStars)
        prefsRefresh.value = prefsRefresh.value + 1
        _uiState.value = _uiState.value.copy(
            finished = true,
            highScore = maxOf(highScore.value, finalScore),
        )
        _snackbar.emit("挑战完成！奖励 $rewardStars 颗星")
    }

    /**
     * 回到模式选择页。
     */
    fun resetToModePicker() {
        _uiState.value = ChallengeUiState(highScore = highScore.value)
    }

    /**
     * 计算用户敲击与节奏模式匹配数。
     */
    private fun countMatchedTaps(userTaps: List<Long>, pattern: List<Long>): Int {
        if (userTaps.size != RHYTHM_TAP_COUNT || pattern.size != RHYTHM_TAP_COUNT - 1) return 0
        var matched = 1 // 第一拍默认匹配
        for (i in 0 until RHYTHM_TAP_COUNT - 1) {
            val userGap = userTaps[i + 1] - userTaps[i]
            val targetGap = pattern[i]
            if (kotlin.math.abs(userGap - targetGap) <= RHYTHM_TOLERANCE_MS) {
                matched++
            }
        }
        return matched
    }

    /**
     * 播放一段正弦波音调。
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
                try { audioTrack.stop() } catch (_: IllegalStateException) {}
                audioTrack.release()
            } catch (_: Exception) {
                // 播放失败时静默忽略
            }
        }
    }

    /**
     * 播放节奏示范。
     */
    private suspend fun playRhythm(gaps: List<Long>) {
        // 第一拍立即响
        playTone(midiToFreq(72), 100L)
        for (gap in gaps) {
            delay(gap)
            playTone(midiToFreq(72), 100L)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // 不在此停止监听服务：其他页面可能仍需要
    }
}
