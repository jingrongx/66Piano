package com.pianokids.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * APK 安装器：调用系统 PackageInstaller 触发安装界面。
 *
 * Android 7.0+ 需要使用 FileProvider 暴露 apk 文件给系统安装器，
 * 否则会触发 `FileUriExposedException`。
 *
 * Android 8.0+ 还需要 [android.Manifest.permission.REQUEST_INSTALL_PACKAGES]
 * 权限（已在 AndroidManifest 中声明）。
 */
@Singleton
class ApkInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * 启动系统安装器安装指定 APK 文件。
     *
     * @return true 表示已成功发起安装 Intent；false 表示文件不存在或异常
     */
    fun install(apkFile: File): Boolean {
        if (!apkFile.exists() || apkFile.length() == 0L) return false
        return try {
            val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile,
                )
            } else {
                @Suppress("DEPRECATION")
                Uri.fromFile(apkFile)
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            true
        } catch (_: Throwable) {
            false
        }
    }
}
