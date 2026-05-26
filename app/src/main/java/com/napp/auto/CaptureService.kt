package com.napp.auto

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import java.io.File
import java.io.FileOutputStream

class CaptureService : Service() {

    private var screenCapture: ScreenCaptureHelper? = null
    private var mediaProjection: android.media.projection.MediaProjection? = null
    private var captureIndex = 0
    private var isCapturing = false

    override fun onCreate() {
        super.onCreate()
        val channelId = "napp_capture"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(channelId) == null) {
            val channel = android.app.NotificationChannel(
                channelId, "模板采集", NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }
        startForeground(2, Notification.Builder(this, channelId)
            .setContentTitle("模板采集中")
            .setContentText("正在准备截屏服务")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "INIT" -> {
                val rc = intent.getIntExtra("resultCode", -1)
                val data = intent.getParcelableExtra("data", Intent::class.java)
                if (rc != -1 && data != null) {
                    val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    mediaProjection = mpm.getMediaProjection(rc, data)
                    screenCapture = ScreenCaptureHelper(this)
                    screenCapture?.start(mediaProjection!!)
                }
            }
            "START_CAPTURE" -> {
                captureIndex = 0
                startForeground(2, buildCaptureNotification())
            }
            "CAPTURE" -> {
                if (isCapturing || screenCapture == null) return START_STICKY
                isCapturing = true
                val name = Config.TEMPLATE_NAMES[captureIndex]
                Thread {
                    captureAndCrop(name)
                    isCapturing = false
                }.start()
            }
            "SKIP" -> {
                if (!isCapturing) advanceToNext()
            }
            "FINISH" -> {
                stopCapture()
            }
            "NEXT" -> {
                advanceToNext()
            }
            "STOP" -> {
                stopCapture()
            }
        }
        return START_STICKY
    }

    private fun captureAndCrop(name: String) {
        var bmp: Bitmap? = null
        for (i in 0..15) {
            bmp = screenCapture?.captureScreen()
            if (bmp != null) break
            Thread.sleep(300)
        }

        if (bmp != null) {
            val file = File(cacheDir, "capture_temp.png")
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
            bmp.recycle()

            MainActivity.pendingCropName = name
            MainActivity.pendingCropPath = file.absolutePath

            updateCaptureCompleteNotification()
        } else {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(1003, Notification.Builder(this, "napp_capture")
                .setContentTitle("截图失败")
                .setContentText("无法获取画面，请重试")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setAutoCancel(true)
                .build())
            // Don't advance — user can retry by tapping "截图" again
        }
    }

    private fun advanceToNext() {
        captureIndex++
        if (captureIndex < Config.TEMPLATE_NAMES.size) {
            startForeground(2, buildCaptureNotification())
        } else {
            stopCapture()
        }
    }

    private fun stopCapture() {
        screenCapture?.stop()
        mediaProjection?.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildCaptureNotification(): Notification {
        val name = Config.TEMPLATE_NAMES[captureIndex]
        val total = Config.TEMPLATE_NAMES.size
        val index = captureIndex + 1

        val capturePI = PendingIntent.getService(this, 1,
            Intent(this, CaptureService::class.java).apply { action = "CAPTURE" },
            PendingIntent.FLAG_IMMUTABLE)

        val skipPI = PendingIntent.getService(this, 2,
            Intent(this, CaptureService::class.java).apply { action = "SKIP" },
            PendingIntent.FLAG_IMMUTABLE)

        val finishPI = PendingIntent.getService(this, 3,
            Intent(this, CaptureService::class.java).apply { action = "FINISH" },
            PendingIntent.FLAG_IMMUTABLE)

        return Notification.Builder(this, "napp_capture")
            .setContentTitle("采集 $index/$total: 「$name」")
            .setContentText("让按钮显示在屏幕上，然后点截图")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_camera, "截图", capturePI)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "跳过", skipPI)
            .addAction(android.R.drawable.ic_menu_delete, "完成", finishPI)
            .build()
    }

    private fun updateCaptureCompleteNotification() {
        val openAppPI = PendingIntent.getActivity(this, 4,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        startForeground(2, Notification.Builder(this, "napp_capture")
            .setContentTitle("截图完成")
            .setContentText("点击返回应用选择按钮位置")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setAutoCancel(true)
            .setContentIntent(openAppPI)
            .build())
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
