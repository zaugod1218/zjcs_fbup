package com.napp.auto

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.media.projection.MediaProjection
import android.os.IBinder
import android.os.Looper
import kotlinx.coroutines.*
import java.io.File

enum class BotState {
    STOPPED, NAVIGATE, MATCHING, BATTLING, SETTLEMENT, ERROR
}

class AutoService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null
    private var screenCapture: ScreenCaptureHelper? = null
    private var matcher: TemplateMatcher? = null
    private var mediaProjection: MediaProjection? = null

    var currentState = BotState.STOPPED
    var failures = 0
    var cycles = 0
    var statusListener: ((String) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification("杖剑自动", "服务运行中"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> {
                val resultCode = intent.getIntExtra("resultCode", -1)
                val data = intent.getParcelableExtra("data", Intent::class.java)
                if (resultCode != -1 && data != null) {
                    val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
                    mediaProjection = mpm.getMediaProjection(resultCode, data)
                    screenCapture = ScreenCaptureHelper(this)
                    screenCapture?.start(mediaProjection!!)
                    matcher = TemplateMatcher(File(filesDir, Config.TEMPLATE_DIR))
                    startLoop()
                }
            }
            "STOP" -> stopLoop()
            "SET_TEMPLATE" -> {
                val name = intent.getStringExtra("templateName") ?: return START_STICKY
                val x = intent.getIntExtra("x", 0)
                val y = intent.getIntExtra("y", 0)
                saveTemplate(name, x, y)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startLoop() {
        currentState = BotState.NAVIGATE
        failures = 0
        cycles = 0
        updateStatus("启动中...")

        job = scope.launch {
            loop@ while (isActive) {
                updateStatus(stateText())
                when (currentState) {
                    BotState.NAVIGATE -> if (navigate()) {
                        currentState = BotState.MATCHING
                    } else {
                        updateStatus("导航失败，重试")
                    }

                    BotState.MATCHING -> if (match()) {
                        failures = 0
                        currentState = BotState.BATTLING
                    } else {
                        failures++
                        updateStatus("匹配失败 ($failures/$MAX_FAILURES)")
                        currentState = BotState.NAVIGATE
                    }

                    BotState.BATTLING -> if (battle()) {
                        currentState = BotState.SETTLEMENT
                    } else {
                        failures++
                        currentState = BotState.NAVIGATE
                    }

                    BotState.SETTLEMENT -> {
                        if (settle()) {
                            cycles++
                            failures = 0
                            updateStatus("已完成 $cycles 轮")
                        } else {
                            failures++
                        }
                        currentState = BotState.NAVIGATE
                    }

                    BotState.ERROR -> {
                        updateStatus("出错停止 ($failures 次失败)")
                        break@loop
                    }
                    BotState.STOPPED -> break@loop
                }

                if (failures >= Config.MAX_FAILURES) {
                    currentState = BotState.ERROR
                    break@loop
                }
                delay(Config.IDLE_INTERVAL)
            }
            stopLoop()
        }
    }

    private fun stopLoop() {
        job?.cancel()
        job = null
        currentState = BotState.STOPPED
        screenCapture?.stop()
        mediaProjection?.stop()
        matcher?.clearCache()
        updateStatus("已停止")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun navigate(): Boolean {
        updateStatus("导航中...")
        for (step in listOf("tab_2", "daily_dungeon", "match_btn")) {
            if (!tapTemplate(step, Config.NAVIGATE_TIMEOUT)) return false
            delay(1000)
        }
        return true
    }

    private suspend fun match(): Boolean {
        updateStatus("匹配中...")
        delay(1000)
        val screen = capture() ?: return false

        // 检查准备按钮
        var pos = matcher?.find(screen, "ready_btn")
        if (pos != null) {
            screen.recycle()
            doTap(pos.x, pos.y)
            delay(500)
            return true
        }

        // 检查确认按钮
        pos = matcher?.find(screen, "confirm_btn")
        if (pos != null) {
            screen.recycle()
            doTap(pos.x, pos.y)
            delay(500)
            return true
        }
        screen.recycle()

        // 等待准备按钮
        return waitForTemplate("ready_btn", Config.MATCHING_TIMEOUT)
    }

    private suspend fun battle(): Boolean {
        updateStatus("战斗中...")
        return waitForTemplate("settlement_close", Config.BATTLE_TIMEOUT)
    }

    private suspend fun settle(): Boolean {
        updateStatus("结算中...")
        val buttons = listOf("settlement_close", "confirm_btn", "again_btn")
        var tapped = true
        var attempts = 0
        while (tapped && attempts < 10) {
            tapped = false
            attempts++
            val screen = capture() ?: continue
            val result = matcher?.findAny(screen, buttons)
            screen.recycle()
            if (result != null) {
                doTap(result.second, result.third)
                tapped = true
                delay(800)
            }
        }
        return !tapped
    }

    private suspend fun tapTemplate(name: String, timeout: Long): Boolean {
        val screen = capture() ?: return false
        var pos = matcher?.find(screen, name)
        screen.recycle()
        if (pos != null) {
            doTap(pos.x, pos.y)
            return true
        }
        return waitForTemplate(name, timeout)
    }

    private suspend fun waitForTemplate(name: String, timeout: Long): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeout) {
            val screen = capture() ?: continue
            val pos = matcher?.find(screen, name)
            screen.recycle()
            if (pos != null) {
                doTap(pos.x, pos.y)
                return true
            }
            delay(1500)
        }
        return false
    }

    private fun capture(): Bitmap? {
        for (i in 0..5) {
            val bmp = screenCapture?.captureScreen()
            if (bmp != null) return bmp
            Thread.sleep(200)
        }
        return null
    }

    private fun doTap(x: Int, y: Int) {
        TapService.instance?.tap(x, y) ?: run {
            android.util.Log.w(Config.TAG, "无障碍服务未连接，无法点击")
        }
    }

    private fun stateText(): String = when (currentState) {
        BotState.NAVIGATE -> "导航"
        BotState.MATCHING -> "匹配"
        BotState.BATTLING -> "战斗"
        BotState.SETTLEMENT -> "结算"
        else -> "未知"
    }

    private fun updateStatus(text: String) {
        android.util.Log.i(Config.TAG, text)
        statusListener?.invoke(text)
        val notification = createNotification("杖剑自动", text)
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(1, notification)
    }

    private fun createNotification(title: String, text: String): Notification {
        val channelId = "napp_auto"
        return Notification.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "napp_auto", "杖剑自动", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "脚本运行状态" }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun saveTemplate(name: String, centerX: Int, centerY: Int) {
        val screen = capture() ?: return
        val size = 100
        val left = (centerX - size / 2).coerceAtLeast(0)
        val top = (centerY - size / 2).coerceAtLeast(0)
        val w = minOf(size, screen.width - left)
        val h = minOf(size, screen.height - top)
        if (w <= 0 || h <= 0) return

        val template = Bitmap.createBitmap(screen, left, top, w, h)
        screen.recycle()

        val dir = File(filesDir, Config.TEMPLATE_DIR)
        dir.mkdirs()
        val file = File(dir, "$name.png")
        file.outputStream().use { template.compress(Bitmap.CompressFormat.PNG, 100, it) }
        template.recycle()
        updateStatus("模板 $name 已保存")
        matcher?.loadTemplate(name)
    }

    override fun onDestroy() {
        stopLoop()
        scope.cancel()
        super.onDestroy()
    }
}
