package com.pianokids.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android TextToSpeech 封装。
 *
 * 单例，整个 APP 共用一个 TTS 引擎实例。
 * 用于课程教学、闯关、家长报告等场景的语音引导。
 *
 * 调用方：
 * - [speak] 直接朗读，重复调用会取消上一次未完成的语句
 * - [stop] 取消当前朗读
 *
 * 注意：TTS 引擎初始化是异步的，[isReady] 为 true 后才会真正发声；
 * 初始化前的语句会被排队（[queued]），初始化完成后自动播放。
 */
@Singleton
class TtsHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    private val _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled

    /** 启用前的语句缓冲 */
    private val queued = ArrayDeque<String>()

    @Volatile
    private var engine: TextToSpeech? = null

    private val initializing = AtomicBoolean(false)

    /**
     * 惰性初始化 TTS 引擎。可重复调用，仅第一次会真正初始化。
     */
    fun ensureInitialized() {
        if (engine != null || !initializing.compareAndSet(false, true)) return
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val r = engine?.setLanguage(Locale.SIMPLIFIED_CHINESE)
                if (r == TextToSpeech.LANG_AVAILABLE || r == TextToSpeech.LANG_COUNTRY_AVAILABLE) {
                    _isReady.value = true
                    // 播放排队的语句
                    while (queued.isNotEmpty()) {
                        val text = queued.removeFirst()
                        speakNow(text)
                    }
                }
            } else {
                Log.w(TAG, "TTS 初始化失败: $status")
            }
            initializing.set(false)
        }
    }

    /**
     * 设置开关。关闭后 [speak] 静默无操作。
     */
    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        if (!enabled) stop()
    }

    /**
     * 朗读一段中文文本。
     *
     * @param text 文本
     * @param flush 是否清空之前的队列
     */
    fun speak(text: String, flush: Boolean = true) {
        if (!_isEnabled.value) return
        ensureInitialized()
        if (!_isReady.value) {
            if (flush) queued.clear()
            queued.addLast(text)
            return
        }
        speakNow(text, flush)
    }

    private fun speakNow(text: String, flush: Boolean = true) {
        val eng = engine ?: return
        eng.speak(
            text,
            if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
            null,
            "pianokids_${System.nanoTime()}",
        )
    }

    /**
     * 立即停止朗读。
     */
    fun stop() {
        engine?.stop()
    }

    /**
     * 释放资源（一般在 Application.onTerminate 或进程结束时调用）。
     */
    fun shutdown() {
        engine?.stop()
        engine?.shutdown()
        engine = null
        _isReady.value = false
    }

    companion object {
        private const val TAG = "TtsHelper"
    }
}
