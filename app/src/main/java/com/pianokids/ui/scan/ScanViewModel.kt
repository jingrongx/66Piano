package com.pianokids.ui.scan

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pianokids.music.NoteSequence
import com.pianokids.scan.SheetMusicRecognizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 识别状态。
 */
sealed interface ScanState {
    /** 等待拍照/选择图片 */
    data object Idle : ScanState
    /** 正在识别 */
    data object Recognizing : ScanState
    /** 识别完成 */
    data class Done(
        val result: SheetMusicRecognizer.Result,
        val previewBitmap: Bitmap?,
    ) : ScanState
    /** 识别失败 */
    data class Error(val message: String) : ScanState
}

/**
 * 拍照识谱 ViewModel。
 *
 * 接受一张图片（拍照或从相册选），用 [SheetMusicRecognizer] 识别后输出 [NoteSequence]。
 * 用户可在编辑器中进一步微调。
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    application: Application,
    private val recognizer: SheetMusicRecognizer,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    /**
     * 从 Uri 加载图片并识别。
     */
    fun recognizeUri(uri: Uri) {
        viewModelScope.launch {
            _state.value = ScanState.Recognizing
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    loadDownsampled(uri)
                }
                if (bitmap == null) {
                    _state.value = ScanState.Error("无法加载图片")
                    return@launch
                }
                val result = withContext(Dispatchers.Default) {
                    recognizer.recognize(bitmap, "拍照识谱")
                }
                _state.value = ScanState.Done(result, bitmap)
            } catch (e: Throwable) {
                _state.value = ScanState.Error(e.message ?: "识别失败")
            }
        }
    }

    /**
     * 从 Bitmap 直接识别（用于 CameraX 拍照）。
     */
    fun recognizeBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            _state.value = ScanState.Recognizing
            try {
                val result = withContext(Dispatchers.Default) {
                    recognizer.recognize(bitmap, "拍照识谱")
                }
                _state.value = ScanState.Done(result, bitmap)
            } catch (e: Throwable) {
                _state.value = ScanState.Error(e.message ?: "识别失败")
            }
        }
    }

    /**
     * 获取已识别的 NoteSequence（用于跳转编辑器）。
     */
    fun getResultSequence(): NoteSequence? = (_state.value as? ScanState.Done)?.result?.sequence

    /**
     * 重置到 Idle。
     */
    fun reset() {
        _state.value = ScanState.Idle
    }

    /**
     * 用 BitmapFactory 加载并降采样，避免 OOM。
     */
    private fun loadDownsampled(uri: Uri): Bitmap? {
        val resolver = getApplication<Application>().contentResolver
        val opts = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        val target = 1600
        val sample = calculateSampleSize(opts.outWidth, opts.outHeight, target)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
    }

    private fun calculateSampleSize(w: Int, h: Int, target: Int): Int {
        if (w <= 0 || h <= 0) return 1
        var sample = 1
        var longest = maxOf(w, h)
        while (longest / 2 >= target) {
            longest /= 2
            sample *= 2
        }
        return sample
    }
}
