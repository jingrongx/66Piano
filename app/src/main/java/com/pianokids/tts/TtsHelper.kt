package com.pianokids.tts

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import com.pianokids.data.prefs.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 文本转语音（TTS）统一入口。
 *
 * **优先级**：
 * 1. Android 系统 TextToSpeech（默认）
 * 2. 检测不到 TTS 引擎或中文语音时，调用 [FallbackTtsProvider]
 *    （留作扩展点，未来可接入 Sherpa-ONNX 等离线 AI 大模型）
 *
 * 单例，整个 APP 共用一个 TTS 引擎实例。
 * 用于课程教学、闯关、家长报告等场景的语音引导。
 *
 * 调用方：
 * - [speak] 直接朗读，重复调用会取消上一次未完成的语句
 * - [stop] 取消当前朗读
 *
 * 启用状态会从 [UserPreferences] 异步加载，APP 重启后保持上次的开关设置。
 *
 * 系统不可用时可通过 [installSystemTts] 跳转到应用商店/设置引导用户安装 TTS 引擎。
 */
@Singleton
class TtsHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferences,
) {

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    private val _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled

    /** 系统 TTS 是否可用（用于 UI 提示）。 */
    private val _isSystemTtsAvailable = MutableStateFlow(false)
    val isSystemTtsAvailable: StateFlow<Boolean> = _isSystemTtsAvailable

    /** 启用前的语句缓冲 */
    private val queued = ArrayDeque<String>()

    @Volatile
    private var engine: TextToSpeech? = null

    /**
     * 离线 TTS 提供者。当系统 TTS 不可用时启用。
     * 当前为 [FallbackTtsProvider]（静默），未来可替换为 Sherpa-ONNX 实现。
     */
    @Volatile
    private var offlineProvider: TtsProvider = FallbackTtsProvider()

    /** 当前正在使用的提供者："system" / "offline" / "none" */
    private val _activeProvider = MutableStateFlow("none")
    val activeProvider: StateFlow<String> = _activeProvider

    private val initializing = AtomicBoolean(false)

    /** 应用级协程，用于初始化时加载持久化状态 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // 启动时异步加载持久化的 TTS 开关状态，避免阻塞构造
        appScope.launch {
            try {
                _isEnabled.value = userPreferences.ttsEnabled()
            } catch (t: Throwable) {
                Log.w(TAG, "加载 ttsEnabled 失败，使用默认值 true", t)
            }
        }
    }

    /**
     * 惰性初始化 TTS 引擎。可重复调用，仅第一次会真正初始化。
     *
     * 策略：
     * 1. 先尝试系统 TTS
     * 2. 若系统 TTS 初始化失败 / 不支持中文 → 切换到离线 TTS（当前为静默 fallback）
     */
    fun ensureInitialized() {
        if (engine != null || !initializing.compareAndSet(false, true)) return
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val r = engine?.setLanguage(Locale.SIMPLIFIED_CHINESE)
                if (r == TextToSpeech.LANG_AVAILABLE || r == TextToSpeech.LANG_COUNTRY_AVAILABLE) {
                    _isReady.value = true
                    _isSystemTtsAvailable.value = true
                    _activeProvider.value = "system"
                    // 播放排队的语句
                    flushQueue()
                } else {
                    Log.w(TAG, "系统 TTS 不支持中文（result=$r），切换到离线 TTS")
                    switchToOfflineProvider()
                }
            } else {
                Log.w(TAG, "系统 TTS 初始化失败: $status，切换到离线 TTS")
                switchToOfflineProvider()
            }
            initializing.set(false)
        }
    }

    /**
     * 切换到离线 TTS 提供者（当前为静默 fallback）。
     *
     * **扩展点**：未来接入 Sherpa-ONNX 等离线模型时，在此构造并初始化：
     * ```kotlin
     * offlineProvider = SherpaOnnxTtsProvider(context, modelPath)
     * offlineProvider.initialize { _isReady.value = true }
     * ```
     */
    private fun switchToOfflineProvider() {
        _isSystemTtsAvailable.value = false
        _activeProvider.value = "offline"
        try {
            offlineProvider.initialize {
                _isReady.value = true
                flushQueue()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "离线 TTS 初始化失败", t)
            _isReady.value = false
            _activeProvider.value = "none"
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
        when (_activeProvider.value) {
            "system" -> speakWithSystemTts(text, flush)
            "offline" -> offlineProvider.speak(text, flush)
            else -> { /* 静默 */ }
        }
    }

    private fun speakWithSystemTts(text: String, flush: Boolean) {
        val eng = engine ?: return
        eng.speak(
            text,
            if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
            null,
            "pianokids_${System.nanoTime()}",
        )
    }

    private fun flushQueue() {
        while (queued.isNotEmpty()) {
            val text = queued.removeFirst()
            when (_activeProvider.value) {
                "system" -> speakWithSystemTts(text, flush = false)
                "offline" -> offlineProvider.speak(text, flush = false)
            }
        }
    }

    /**
     * 立即停止朗读。
     */
    fun stop() {
        engine?.stop()
        if (_activeProvider.value == "offline") offlineProvider.stop()
    }

    /**
     * 释放资源（一般在 Application.onTerminate 或进程结束时调用）。
     */
    fun shutdown() {
        engine?.stop()
        engine?.shutdown()
        engine = null
        offlineProvider.shutdown()
        _isReady.value = false
        _activeProvider.value = "none"
    }

    /**
     * 检测系统 TTS 是否可用。
     *
     * 可用于在 UI 上提示用户：若不可用，建议安装第三方 TTS 引擎
     * （如 Google TTS、讯飞输入法、搜狗输入法等）。
     */
    fun isSystemTtsReady(): Boolean = _isSystemTtsAvailable.value

    /**
     * 跳转到系统 TTS 设置页面（让用户安装/启用 TTS 引擎）。
     */
    fun openSystemTtsSettings() {
        runCatching {
            val intent = Intent().apply {
                action = "com.android.settings.TTS_SETTINGS"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            // 兜底：跳转到应用详情页
            runCatching {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }

    companion object {
        private const val TAG = "TtsHelper"
    }
}
