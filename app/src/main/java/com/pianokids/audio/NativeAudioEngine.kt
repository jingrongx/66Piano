package com.pianokids.audio

/**
 * 音高/音符信息。
 *
 * @property noteName 音名（如 "A4"、"C#5"），无效时为 "N/A"
 * @property midiNote MIDI 音符号（A4=69），无效时为 -1
 * @property centsOff 与最近音名的偏差（单位：音分，-50~50）
 * @property frequency 原始频率（Hz）
 */
data class NoteInfo(
    val noteName: String,
    val midiNote: Int,
    val centsOff: Float,
    val frequency: Float,
)

/**
 * 原生音频引擎单例。
 *
 * 负责加载 [piano_audio] 动态库，并对外暴露 Kotlin 友好的 JNI 包装。
 * 所有方法都是线程安全的，可在任意协程调度器上调用。
 */
object NativeAudioEngine {

    @Volatile
    private var loaded = false

    /**
     * 尝试加载 native 库。失败时仅记录状态，不抛异常，
     * 调用方在使用 native 函数前应自行处理（返回值会标识失败）。
     */
    private fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            try {
                System.loadLibrary("piano_audio")
                loaded = true
            } catch (e: UnsatisfiedLinkError) {
                // 库未就绪：上层调用时会得到默认值
                loaded = false
            }
        }
    }

    // ============== JNI 原生函数声明 ==============

    /**
     * 用 YIN 算法检测一段 PCM 浮点数据的基频。
     * @return 频率（Hz）；无声或失败返回 -1
     */
    external fun nativeDetectPitch(buffer: FloatArray, length: Int, sampleRate: Int): Float

    /**
     * 计算一帧信号的 onset 能量。
     */
    external fun nativeDetectOnset(buffer: FloatArray, length: Int): Float

    /**
     * 频率转音名（如 "A4"）。
     */
    external fun nativeFreqToNoteName(freq: Float): String

    /**
     * 频率与指定 MIDI 音的音分偏差。
     */
    external fun nativeFreqToCentsOff(freq: Float, midiNote: Int): Float

    /**
     * 频率转 MIDI 音符号（A4=69）。
     */
    external fun nativeFreqToMidiNote(freq: Float): Int

    // ============== Kotlin 友好包装 ==============

    /**
     * 检测基频。若库未加载或输入异常返回 -1。
     */
    fun detectPitch(buffer: FloatArray, sampleRate: Int): Float {
        ensureLoaded()
        if (!loaded || buffer.isEmpty()) return -1f
        return try {
            nativeDetectPitch(buffer, buffer.size, sampleRate)
        } catch (e: UnsatisfiedLinkError) {
            -1f
        }
    }

    /**
     * 计算 onset 能量（>=0）。
     */
    fun detectOnset(buffer: FloatArray): Float {
        ensureLoaded()
        if (!loaded || buffer.isEmpty()) return 0f
        return try {
            nativeDetectOnset(buffer, buffer.size)
        } catch (e: UnsatisfiedLinkError) {
            0f
        }
    }

    /**
     * 频率转 [NoteInfo]。无效频率返回 midiNote=-1 的占位实例。
     */
    fun freqToNote(freq: Float): NoteInfo {
        ensureLoaded()
        if (!loaded || freq <= 0f) {
            return NoteInfo(noteName = "N/A", midiNote = -1, centsOff = 0f, frequency = freq)
        }
        return try {
            val name = nativeFreqToNoteName(freq) ?: "N/A"
            val midi = nativeFreqToMidiNote(freq)
            val cents = if (midi >= 0) nativeFreqToCentsOff(freq, midi) else 0f
            NoteInfo(noteName = name, midiNote = midi, centsOff = cents, frequency = freq)
        } catch (e: UnsatisfiedLinkError) {
            NoteInfo(noteName = "N/A", midiNote = -1, centsOff = 0f, frequency = freq)
        }
    }
}
