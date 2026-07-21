package com.pianokids.tts

/**
 * TTS 提供者接口。
 *
 * 策略：
 * 1. 默认使用 [SystemTtsProvider]（Android 系统 TextToSpeech）
 * 2. 如果系统 TTS 不可用（无引擎 / 无中文语音数据），尝试使用 [OfflineTtsProvider]
 * 3. 如果离线 TTS 也未集成，则 [FallbackTtsProvider] 静默降级
 *
 * 离线 TTS 实现（如 Sherpa-ONNX）应实现此接口并通过 Hilt 提供绑定。
 * 当前版本未集成离线模型，留作扩展点。
 */
interface TtsProvider {

    /**
     * 初始化 TTS 引擎。异步执行，完成后通过 [onReady] 回调。
     *
     * @return true 表示初始化已启动（异步），false 表示完全不可用
     */
    fun initialize(onReady: () -> Unit): Boolean

    /**
     * 是否已就绪可以朗读。
     */
    fun isReady(): Boolean

    /**
     * 朗读文本。
     *
     * @param text 文本
     * @param flush 是否清空之前的队列
     */
    fun speak(text: String, flush: Boolean = true)

    /**
     * 停止朗读。
     */
    fun stop()

    /**
     * 释放资源。
     */
    fun shutdown()

    /**
     * 提供者名称（用于调试/日志）。
     */
    val name: String
}

/**
 * 占位的 fallback TTS 提供者：所有方法静默无操作。
 *
 * 当系统 TTS 和离线 TTS 都不可用时使用，避免业务代码崩溃。
 *
 * **扩展点**：要接入离线 AI 大模型（如 Sherpa-ONNX、VITS），
 * 实现一个 [TtsProvider] 并在 Hilt 中绑定优先级高于本类即可。
 */
class FallbackTtsProvider : TtsProvider {
    override val name: String = "Fallback( Silent )"

    override fun initialize(onReady: () -> Unit): Boolean {
        // 立即回调"已就绪"，但 speak 实际不发声
        onReady()
        return true
    }

    override fun isReady(): Boolean = true
    override fun speak(text: String, flush: Boolean) { /* 静默 */ }
    override fun stop() { /* 无操作 */ }
    override fun shutdown() { /* 无操作 */ }
}
