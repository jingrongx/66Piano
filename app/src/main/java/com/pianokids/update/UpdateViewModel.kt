package com.pianokids.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 更新检测页面 UI 状态。
 *
 * @property checking 是否正在检测更新
 * @property downloading 是否正在下载 APK
 * @property downloadProgress 下载进度 0..100
 * @property hasUpdate 是否有新版本
 * @property latest 最新版本信息（null 表示尚未检测/检测失败）
 * @property currentVersionName 当前版本名（如 "1.0.0"）
 * @property currentVersionCode 当前版本号
 * @property useProxy 是否使用 ghproxy 加速（默认 true，适合国内用户）
 * @property message 一次性提示消息
 */
data class UpdateUiState(
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val downloadProgress: Int = 0,
    val hasUpdate: Boolean = false,
    val latest: UpdateChecker.UpdateInfo? = null,
    val currentVersionName: String = "",
    val currentVersionCode: Int = 0,
    val useProxy: Boolean = true,
    val message: String? = null,
)

/**
 * 更新检测 ViewModel。
 *
 * 提供三个核心能力：
 * 1. [checkForUpdate] 手动/自动检测更新
 * 2. [downloadAndInstall] 下载并触发系统安装器
 * 3. [toggleProxy] 切换 ghproxy 加速
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateChecker: UpdateChecker,
    private val apkInstaller: ApkInstaller,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    init {
        // 初始化当前版本信息（同步读 BuildConfig，不需要网络）
        val (name, code) = getCurrentVersionSync()
        _uiState.update {
            it.copy(
                currentVersionName = name,
                currentVersionCode = code,
            )
        }
        // 自动检测一次更新
        checkForUpdate()
    }

    /**
     * 检测更新。会根据 [UpdateUiState.useProxy] 决定是否走加速。
     */
    fun checkForUpdate() {
        _uiState.update { it.copy(checking = true, message = null) }
        viewModelScope.launch {
            val result = updateChecker.checkForUpdate(useProxy = _uiState.value.useProxy)
            _uiState.update {
                it.copy(
                    checking = false,
                    latest = result.latest,
                    hasUpdate = result.hasUpdate,
                    currentVersionName = result.currentVersionName,
                    currentVersionCode = result.currentVersionCode,
                    message = when {
                        result.latest == null -> "检测更新失败，请检查网络后重试"
                        result.hasUpdate -> "发现新版本 v${result.latest.version}"
                        else -> "已是最新版本 v${result.currentVersionName}"
                    },
                )
            }
        }
    }

    /**
     * 下载并安装新版本 APK。
     */
    fun downloadAndInstall() {
        val info = _uiState.value.latest ?: return
        if (_uiState.value.downloading) return
        _uiState.update { it.copy(downloading = true, downloadProgress = 0, message = "开始下载…") }
        viewModelScope.launch {
            val file = updateChecker.downloadApk(
                info = info,
                useProxy = _uiState.value.useProxy,
                onProgress = { pct ->
                    _uiState.update { it.copy(downloadProgress = pct) }
                },
            )
            _uiState.update { it.copy(downloading = false) }
            if (file == null) {
                _uiState.update { it.copy(message = "下载失败，请尝试切换加速开关后重试") }
                return@launch
            }
            _uiState.update { it.copy(message = "下载完成，正在调起系统安装器") }
            val ok = apkInstaller.install(file)
            if (!ok) {
                _uiState.update { it.copy(message = "无法启动安装器，请检查是否授予安装未知应用权限") }
            }
        }
    }

    /**
     * 切换 ghproxy 加速。
     */
    fun toggleProxy() {
        _uiState.update { it.copy(useProxy = !it.useProxy) }
    }

    /**
     * 消费一次性消息。
     */
    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun getCurrentVersionSync(): Pair<String, Int> {
        // 直接读 BuildConfig，无需 Context
        return try {
            com.pianokids.BuildConfig.VERSION_NAME to com.pianokids.BuildConfig.VERSION_CODE
        } catch (_: Throwable) {
            "" to 0
        }
    }
}
