package com.pianokids.ui.tuner

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pianokids.audio.AudioCapture
import com.pianokids.audio.PianoListeningService
import com.pianokids.audio.PitchDetector
import com.pianokids.audio.PitchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 调音器 ViewModel。
 *
 * 收集 [PitchDetector.pitches]，启动监听服务。
 */
@HiltViewModel
class TunerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pitchDetector: PitchDetector,
    private val audioCapture: AudioCapture,
) : ViewModel() {

    /**
     * 当前音高检测结果；未检测到声音时为 null。
     */
    val pitch: StateFlow<PitchResult?> = pitchDetector.pitches
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /**
     * 启动监听服务（前台服务会调用 [AudioCapture.start]）。
     * 若无麦克风权限则服务会启动但采集不工作。
     */
    fun startListening() {
        if (audioCapture.hasPermission()) {
            PianoListeningService.start(context)
        }
    }

    /**
     * 判断是否拥有麦克风权限。
     */
    fun hasMicPermission(): Boolean = audioCapture.hasPermission()
}
