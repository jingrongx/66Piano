package com.pianokids.update

import android.content.Context
import android.os.Build
import com.pianokids.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用更新检查器。
 *
 * 通过 GitHub Release 托管的 `latest.json` 检测新版本。
 * 国内网络默认使用 [GH_PROXY_PREFIX] 加速访问 GitHub 资源。
 *
 * Manifest URL（由 App 拼接，使用 ghproxy 加速）：
 * ```
 * https://ghproxy.net/https://github.com/jingrongx/66Piano/releases/latest/download/latest.json
 * ```
 *
 * APK 直链（直连 GitHub，可能国内访问慢）：
 * ```
 * https://github.com/jingrongx/66Piano/releases/download/v1.0.0/PianoKids-v1.0.0.apk
 * ```
 *
 * APK 加速链接（由 ghproxy 反向代理）：
 * ```
 * https://ghproxy.net/https://github.com/jingrongx/66Piano/releases/download/v1.0.0/PianoKids-v1.0.0.apk
 * ```
 */
@Singleton
class UpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * 最新版本信息。
     *
     * @property version 版本号字符串（如 "1.0.0"）
     * @property versionCode 版本号数字（用于比较）
     * @property tagName GitHub Release 的 tag 名（如 "v1.0.0"）
     * @property apkUrl 直链（GitHub 官方）
     * @property apkUrlProxy ghproxy 加速链接
     * @property manifestUrlProxy manifest 自身的加速链接
     * @property releaseNotes 更新内容（Markdown）
     * @property publishedAt 发布时间 ISO8601
     */
    data class UpdateInfo(
        val version: String,
        val versionCode: Int,
        val tagName: String,
        val apkUrl: String,
        val apkUrlProxy: String,
        val manifestUrlProxy: String,
        val releaseNotes: String,
        val publishedAt: String,
    )

    /**
     * 检测结果。
     *
     * @property hasUpdate 是否有新版本
     * @property latest 最新版本信息（即使没有更新也会返回远端版本）
     * @property currentVersionCode 当前应用 versionCode
     * @property currentVersionName 当前应用 versionName
     */
    data class CheckResult(
        val hasUpdate: Boolean,
        val latest: UpdateInfo?,
        val currentVersionCode: Int,
        val currentVersionName: String,
    )

    /**
     * 从 GitHub Release 获取 latest.json。
     *
     * 默认走 ghproxy 加速，避免国内网络无法访问 GitHub。
     *
     * @param useProxy 是否使用 ghproxy 加速
     */
    suspend fun fetchLatest(useProxy: Boolean = true): UpdateInfo? = withContext(Dispatchers.IO) {
        val url = if (useProxy) MANIFEST_URL_PROXY else MANIFEST_URL_DIRECT
        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "PianoKids/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.SDK_INT})")
                setRequestProperty("Accept", "application/json")
            }
            conn.useCaches = false
            conn.connect()
            try {
                if (conn.responseCode !in 200..299) return@runCatching null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                parseManifest(body)
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    /**
     * 检查更新（默认使用加速地址）。
     *
     * 自动与当前应用版本比较，返回 [CheckResult]。
     */
    suspend fun checkForUpdate(useProxy: Boolean = true): CheckResult = withContext(Dispatchers.IO) {
        val currentCode = BuildConfig.VERSION_CODE
        val currentName = BuildConfig.VERSION_NAME
        val latest = fetchLatest(useProxy)
        val hasUpdate = latest != null && latest.versionCode > currentCode
        CheckResult(
            hasUpdate = hasUpdate,
            latest = latest,
            currentVersionCode = currentCode,
            currentVersionName = currentName,
        )
    }

    /**
     * 下载 APK 到应用缓存目录。
     *
     * @param info 最新版本信息
     * @param useProxy 是否使用 ghproxy 加速
     * @param onProgress 进度回调（0..100）
     * @return 下载好的 APK 文件，失败返回 null
     */
    suspend fun downloadApk(
        info: UpdateInfo,
        useProxy: Boolean = true,
        onProgress: (Int) -> Unit = {},
    ): File? = withContext(Dispatchers.IO) {
        val url = if (useProxy) info.apkUrlProxy else info.apkUrl
        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "PianoKids/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.SDK_INT})")
            }
            conn.connect()
            try {
                if (conn.responseCode !in 200..299) return@runCatching null
                val total = conn.contentLengthLong.coerceAtLeast(1L)
                val file = File(context.cacheDir, "pianokids-update-${info.version}.apk")
                if (file.exists()) file.delete()
                FileOutputStream(file).use { out ->
                    val buffer = ByteArray(8 * 1024)
                    var read: Int
                    var downloaded = 0L
                    val input = conn.inputStream
                    while (true) {
                        read = input.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        downloaded += read
                        val pct = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                        onProgress(pct)
                    }
                }
                file
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    // ============== 内部 ==============

    private fun parseManifest(json: String): UpdateInfo? = runCatching {
        val o = JSONObject(json)
        UpdateInfo(
            version = o.optString("version"),
            versionCode = o.optInt("versionCode"),
            tagName = o.optString("tagName"),
            apkUrl = o.optString("apkUrl"),
            apkUrlProxy = o.optString("apkUrlProxy"),
            manifestUrlProxy = o.optString("manifestUrlProxy"),
            releaseNotes = o.optString("releaseNotes"),
            publishedAt = o.optString("publishedAt"),
        ).takeIf { it.version.isNotBlank() && it.versionCode > 0 && it.apkUrl.isNotBlank() }
    }.getOrNull()

    companion object {
        /** ghproxy 加速前缀，拼接到 GitHub URL 前面即可。 */
        const val GH_PROXY_PREFIX = "https://ghproxy.net/"

        /** manifest 直链（GitHub 官方，国内可能访问慢/失败）。 */
        private const val MANIFEST_URL_DIRECT =
            "https://github.com/jingrongx/66Piano/releases/latest/download/latest.json"

        /** manifest 加速链接（ghproxy 反向代理）。 */
        private const val MANIFEST_URL_PROXY = "$GH_PROXY_PREFIX$MANIFEST_URL_DIRECT"

        /**
         * 为任意 GitHub 资源 URL 拼接 ghproxy 加速前缀。
         * 例如：
         * - 入参 `https://github.com/jingrongx/66Piano/blob/main/README.md`
         * - 出参 `https://ghproxy.net/https://github.com/jingrongx/66Piano/blob/main/README.md`
         */
        fun withProxy(githubUrl: String): String =
            if (githubUrl.startsWith(GH_PROXY_PREFIX)) githubUrl else "$GH_PROXY_PREFIX$githubUrl"
    }
}
