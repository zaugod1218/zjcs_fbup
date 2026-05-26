package com.napp.auto

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var cycleText: TextView
    private lateinit var startBtn: Button
    private lateinit var setupBtn: Button
    private var isCapturing = false
    private var captureIndex = 0

    private var resultCode = -1
    private var projectionData: Intent? = null
    private var serviceBound = false
    private var floatingView: FloatingCaptureView? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {}
        override fun onServiceDisconnected(name: ComponentName?) {}
    }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            resultCode = result.resultCode
            projectionData = result.data
            if (isCapturing) {
                captureIndex = 0
                showFloatingView()
            } else {
                startService()
            }
        } else {
            Toast.makeText(this, "需要截屏权限才能运行", Toast.LENGTH_LONG).show()
        }
    }

    private val cropResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            advanceToNextTemplate()
        } else {
            if (isCapturing && captureIndex < Config.TEMPLATE_NAMES.size) {
                showFloatingView()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        cycleText = findViewById(R.id.cycleText)
        startBtn = findViewById(R.id.startBtn)
        setupBtn = findViewById(R.id.setupBtn)

        checkPermissions()

        startBtn.setOnClickListener {
            when (startBtn.text.toString()) {
                "开始运行" -> requestScreenCapture()
                "停止" -> {
                    stopService(Intent(this, AutoService::class.java))
                    startBtn.text = "开始运行"
                    statusText.text = "已停止"
                }
            }
        }

        setupBtn.setOnClickListener { startTemplateCapture() }

        if (!isAccessibilityEnabled()) {
            AlertDialog.Builder(this)
                .setTitle("需要无障碍服务")
                .setMessage("请授予点击权限，否则无法自动点击屏幕")
                .setPositiveButton("去开启") { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton("稍后", null)
                .show()
        }
    }

    override fun onDestroy() {
        floatingView?.dismiss()
        if (serviceBound) {
            try { unbindService(connection) } catch (_: Exception) {}
        }
        super.onDestroy()
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
    }

    private fun requestScreenCapture() {
        val intent = ScreenCaptureHelper.createIntent(this)
        mediaProjectionLauncher.launch(intent)
    }

    private fun startService() {
        val intent = Intent(this, AutoService::class.java).apply {
            action = "START"
            putExtra("resultCode", resultCode)
            putExtra("data", projectionData)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
        serviceBound = true
        startBtn.text = "停止"
        statusText.text = "启动中..."
    }

    private fun startTemplateCapture() {
        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        // 检查悬浮窗权限（在游戏上方显示截图按钮）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("需要悬浮窗权限")
                .setMessage("需要在游戏上方显示截图按钮，请开启「显示悬浮窗」权限")
                .setPositiveButton("去设置") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
                .setNegativeButton("取消", null)
                .show()
            return
        }

        if (resultCode == -1) {
            Toast.makeText(this, "请先授权截屏", Toast.LENGTH_SHORT).show()
            isCapturing = true
            requestScreenCapture()
            return
        }

        captureIndex = 0
        showFloatingView()
    }

    private fun showFloatingView() {
        if (captureIndex >= Config.TEMPLATE_NAMES.size) {
            Toast.makeText(this, "所有模板采集完成！", Toast.LENGTH_LONG).show()
            isCapturing = false
            return
        }
        val name = Config.TEMPLATE_NAMES[captureIndex]
        floatingView = FloatingCaptureView(this)
        floatingView?.show(
            templateName = name,
            index = captureIndex + 1,
            total = Config.TEMPLATE_NAMES.size,
            onCapture = { floatingCapture(name) },
            onSkip = { advanceToNextTemplate() },
            onFinishAll = {
                floatingView?.dismiss()
                floatingView = null
                isCapturing = false
                Toast.makeText(this, "模板采集完成！", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun floatingCapture(name: String) {
        floatingView?.dismiss()
        floatingView = null

        Thread {
            try {
                val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
                val proj = mpm.getMediaProjection(resultCode, projectionData!!)
                val helper = ScreenCaptureHelper(this)
                helper.start(proj)
                Thread.sleep(300)
                var bmp: Bitmap? = null
                for (i in 0..5) {
                    bmp = helper.captureScreen()
                    if (bmp != null) break
                    Thread.sleep(200)
                }
                helper.stop()

                runOnUiThread {
                    if (bmp != null) {
                        val file = File(cacheDir, "capture_temp.png")
                        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
                        bmp.recycle()
                        val intent = Intent(this@MainActivity, CropActivity::class.java).apply {
                            putExtra("imagePath", file.absolutePath)
                            putExtra("templateName", name)
                        }
                        cropResultLauncher.launch(intent)
                    } else {
                        Toast.makeText(this@MainActivity, "截图失败", Toast.LENGTH_SHORT).show()
                        showFloatingView()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(Config.TAG, "截图失败", e)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "截图失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    showFloatingView()
                }
            }
        }.start()
    }

    private fun advanceToNextTemplate() {
        captureIndex++
        if (captureIndex < Config.TEMPLATE_NAMES.size) {
            showFloatingView()
        } else {
            isCapturing = false
            Toast.makeText(this, "所有模板采集完成！", Toast.LENGTH_LONG).show()
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        try {
            val enabledServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val shortForm = "$packageName/.TapService"
            val fullForm = "$packageName/$packageName.TapService"
            return enabledServices.contains(shortForm) || enabledServices.contains(fullForm)
        } catch (_: Exception) {
            return false
        }
    }
}
