package com.pianokids.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PCM 音频帧：归一化的浮点采样（-1f..1f）。
 * @property samples 长度恒为 [AudioCapture.FRAME_SIZE]
 * @property timestampNs 采样起始的系统时间戳（纳秒，System.nanoTime）
 */
data class AudioFrame(val samples: FloatArray, val timestampNs: Long) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioFrame) return false
        if (timestampNs != other.timestampNs) return false
        if (!samples.contentEquals(other.samples)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = samples.contentHashCode()
        result = 31 * result + timestampNs.hashCode()
        return result
    }
}

/**
 * 麦克风采集器：用 [AudioRecord] 录制 44.1kHz / mono / 16-bit PCM，
 * 并以 [Flow] 形式对外暴露归一化后的浮点音频帧。
 *
 * - 不主动申请权限；调用方需先取得 [Manifest.permission.RECORD_AUDIO]。
 * - 通过 [frames] 暴露的流不主动启动采集，需先调用 [start]。
 * - 错误恢复：若读取过程抛异常，会尝试重建 AudioRecord 并继续。
 *
 * @property context 用于权限检查与 AudioRecord 创建
 */
@Singleton
class AudioCapture @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "AudioCapture"
        /** 采样率（Hz） */
        const val SAMPLE_RATE = 44_100
        /** 每帧采样数 */
        const val FRAME_SIZE = 2_048
        /** SharedFlow 重放缓冲：保留最近 1 帧 */
        private const val REPLAY = 1
        /** SharedFlow 容量：超出时丢弃最旧帧 */
        private const val EXTRA_CAPACITY = 8
    }

    private val supervisor = SupervisorJob()
    private val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Default + supervisor)

    private val _frames = MutableSharedFlow<AudioFrame>(
        replay = REPLAY,
        extraBufferCapacity = EXTRA_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** 对外暴露的音频帧流。订阅者从任意调度器消费皆可。 */
    val frames: Flow<FloatArray> = _frames.asSharedFlow().map { it.samples }

    @Volatile
    private var record: AudioRecord? = null

    @Volatile
    private var captureJob: Job? = null

    @Volatile
    private var running = false

    /**
     * 检查麦克风权限是否已授予。
     */
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * 启动采集。若已经在运行则直接返回。
     * @return true 表示成功开始采集；false 表示无权限或 AudioRecord 初始化失败。
     */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        if (!hasPermission()) {
            Log.w(TAG, "start: 没有 RECORD_AUDIO 权限")
            return false
        }

        val created = createRecord()
        if (created == null) {
            Log.e(TAG, "start: AudioRecord 创建失败")
            return false
        }
        record = created
        try {
            created.startRecording()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "start: startRecording 失败", e)
            releaseRecord(created)
            return false
        }

        running = true
        captureJob = scope.launch { captureLoop(created) }
        return true
    }

    /**
     * 停止采集并释放资源。
     */
    fun stop() {
        running = false
        captureJob?.cancel()
        captureJob = null
        record?.let { releaseRecord(it) }
        record = null
    }

    /**
     * 采集主循环：在 [Dispatchers.Default] 上持续读取，转 FloatArray 后发送。
     * 若 AudioRecord 进入错误状态，尝试重建一次。
     */
    private suspend fun captureLoop(initial: AudioRecord) {
        var active = initial
        val shortBuf = ShortArray(FRAME_SIZE)
        val floatBuf = FloatArray(FRAME_SIZE)
        var errorRetries = 0

        withContext(Dispatchers.Default) {
            while (running && isActive) {
                val readCount = try {
                    active.read(shortBuf, 0, FRAME_SIZE)
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "read 抛异常，尝试重建", e)
                    -1
                }

                when {
                    readCount > 0 -> {
                        errorRetries = 0
                        // 16-bit PCM (-32768..32767) -> [-1, 1]
                        for (i in 0 until readCount) {
                            floatBuf[i] = shortBuf[i] / 32_768.0f
                        }
                        // 不足一帧时尾部保留上一帧的旧值；保持帧长度恒定
                        if (readCount < FRAME_SIZE) {
                            for (i in readCount until FRAME_SIZE) {
                                floatBuf[i] = 0f
                            }
                        }
                        val frame = AudioFrame(
                            samples = floatBuf.copyOf(),
                            timestampNs = System.nanoTime(),
                        )
                        _frames.tryEmit(frame)
                    }

                    readCount == 0 || readCount == AudioRecord.ERROR_INVALID_OPERATION -> {
                        // 罕见：偶发返回 0，下一轮再试
                    }

                    else -> {
                        // ERROR_BAD_VALUE / ERROR_DEAD_OBJECT 等：重建一次
                        if (errorRetries < 2) {
                            errorRetries++
                            Log.w(TAG, "read 返回 $readCount，尝试重建 AudioRecord")
                            releaseRecord(active)
                            val rebuilt = createRecord()
                            if (rebuilt != null) {
                                try {
                                    rebuilt.startRecording()
                                } catch (e: IllegalStateException) {
                                    Log.e(TAG, "重建后 startRecording 失败", e)
                                    running = false
                                    return@withContext
                                }
                                record = rebuilt
                                active = rebuilt
                            } else {
                                Log.e(TAG, "重建 AudioRecord 失败，停止采集")
                                running = false
                                return@withContext
                            }
                        } else {
                            Log.e(TAG, "重试次数耗尽，停止采集")
                            running = false
                            return@withContext
                        }
                    }
                }
            }
        }
    }

    /**
     * 计算 AudioRecord 所需最小缓冲区并实例化。
     */
    @SuppressLint("MissingPermission")
    private fun createRecord(): AudioRecord? {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) {
            Log.e(TAG, "getMinBufferSize 失败: $minBuf")
            return null
        }
        // 缓冲区至少容纳 2 帧，避免欠载
        val bufferSize = maxOf(minBuf, FRAME_SIZE * 4)
        return try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "AudioRecord 构造参数非法", e)
            null
        } catch (e: SecurityException) {
            Log.e(TAG, "AudioRecord 缺少权限", e)
            null
        }
    }

    private fun releaseRecord(r: AudioRecord) {
        try {
            if (r.state == AudioRecord.STATE_INITIALIZED) {
                r.stop()
            }
        } catch (e: IllegalStateException) {
            // 忽略：可能本来就没在录制
        }
        try {
            r.release()
        } catch (e: Exception) {
            Log.w(TAG, "release 异常", e)
        }
    }
}
