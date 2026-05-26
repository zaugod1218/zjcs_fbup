package com.napp.auto

import android.app.Notification
import android.app.NotificationManager
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
            .setContentText("截屏服务运行中")
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
            "CAPTURE" -> {
                captureTemplate(intent.getStringExtra("name") ?: return START_STICKY)
            }
            "STOP" -> {
                screenCapture?.stop()
                mediaProjection?.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun captureTemplate(name: String) {
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

            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(1003, Notification.Builder(this, "napp_auto")
                .setContentTitle("截图完成")
                .setContentText("返回 NappAuto 选择按钮位置")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setAutoCancel(true)
                .build())
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
