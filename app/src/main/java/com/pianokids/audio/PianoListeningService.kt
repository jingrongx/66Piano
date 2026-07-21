package com.pianokids.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pianokids.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 钢琴监听前台服务。
 *
 * - 创建通知渠道 [CHANNEL_ID]（"piano_listening"）。
 * - 启动 [AudioCapture] 并保持运行；停止时关闭采集。
 * - 提供 [start] / [stop] 静态方法便于 UI 调用。
 */
@AndroidEntryPoint
class PianoListeningService : Service() {

    companion object {
        private const val TAG = "PianoListeningService"
        private const val CHANNEL_ID = "piano_listening"
        private const val NOTIFICATION_ID = 0xC0DE

        /**
         * 启动监听服务。
         */
        fun start(context: Context) {
            val intent = Intent(context, PianoListeningService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * 停止监听服务。
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, PianoListeningService::class.java))
        }
    }

    @Inject
    lateinit var audioCapture: AudioCapture

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 启动前台通知（microphone 类型对齐 manifest，仅在 API 29+ 传类型）
        startForegroundCompat()
        // 启动音频采集；权限缺失时仅记日志，服务继续运行（由 UI 层授权后重启）
        val ok = audioCapture.start()
        if (!ok) {
            Log.w(TAG, "AudioCapture 启动失败（可能缺少权限或设备不可用）")
        }
        // START_STICKY：被系统回收后尝试重启，保持练琴连续性
        return START_STICKY
    }

    /**
     * 兼容 API 26+ 的 startForeground：
     * - API 29+ 启动时声明 microphone 前台服务类型。
     * - 旧版本仅以普通前台服务运行。
     */
    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
            audioCapture.stop()
        } catch (e: Exception) {
            Log.w(TAG, "AudioCapture.stop 异常", e)
        }
        super.onDestroy()
    }

    /**
     * 创建通知渠道；Android O+ 必需。
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.service_listening_title),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.service_listening_text)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    /**
     * 构造前台通知。
     */
    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_listening_title))
            .setContentText(getString(R.string.service_listening_text))
            .setSmallIcon(R.drawable.ic_piano_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
