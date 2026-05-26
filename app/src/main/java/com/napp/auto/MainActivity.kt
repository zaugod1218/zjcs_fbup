package com.napp.auto

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
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

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var cycleText: TextView
    private lateinit var startBtn: Button
    private lateinit var setupBtn: Button
    private var isCapturing = false
    private var captureIndex = 0
    private var pendingTemplateName: String? = null

    private var resultCode = -1
    private var projectionData: Intent? = null
    private var serviceBound = false

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
            startService()
        } else {
            Toast.makeText(this, "需要截屏权限才能运行", Toast.LENGTH_LONG).show()
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

        // 检查无障碍服务是否开启
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

    override fun onResume() {
        super.onResume()
        // 自动截屏模式
        if (isCapturing && pendingTemplateName != null) {
            captureTemplate(pendingTemplateName!!)
            pendingTemplateName = null
        }
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
        if (resultCode == -1) {
            Toast.makeText(this, "请先授权截屏", Toast.LENGTH_SHORT).show()
            requestScreenCapture()
            return
        }

        captureIndex = 0
        captureNextTemplate()
    }

    private fun captureNextTemplate() {
        if (captureIndex >= Config.TEMPLATE_NAMES.size) {
            Toast.makeText(this, "所有模板采集完成！", Toast.LENGTH_LONG).show()
            return
        }
        val name = Config.TEMPLATE_NAMES[captureIndex]
        isCapturing = true
        pendingTemplateName = name

        AlertDialog.Builder(this)
            .setTitle("采集模板 ${captureIndex + 1}/${Config.TEMPLATE_NAMES.size}")
            .setMessage("请进入游戏，让「$name」按钮显示在屏幕上\n\n点击「截图」后，点击按钮所在位置")
            .setPositiveButton("截图") { _, _ -> captureTemplate(name) }
            .setNegativeButton("跳过", null)
            .show()
    }

    private fun captureTemplate(name: String) {
        if (resultCode == -1) return

        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
        val proj = mpm.getMediaProjection(resultCode, projectionData!!)
        val helper = ScreenCaptureHelper(this)
        helper.start(proj)
        Thread.sleep(500)
        var bmp = helper.captureScreen()
        var attempts = 0
        while (bmp == null && attempts < 5) {
            Thread.sleep(300)
            bmp = helper.captureScreen()
            attempts++
        }
        if (bmp == null) {
            helper.stop()
            Toast.makeText(this, "截图失败", Toast.LENGTH_SHORT).show()
            return
        }

        // 在新的 Activity 中显示截图让用户点击
        val intent = Intent(this, CropActivity::class.java).apply {
            putExtra("templateName", name)
            putExtra("resultCode", resultCode)
            putExtra("data", projectionData)
        }
        startActivity(intent)
        helper.stop()
    }

    private fun isAccessibilityEnabled(): Boolean {
        try {
            val enabledServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            // 兼容 MIUI/HyperOS 使用完整类路径格式
            val shortForm = "$packageName/.TapService"
            val fullForm = "$packageName/$packageName.TapService"
            return enabledServices.contains(shortForm) || enabledServices.contains(fullForm)
        } catch (_: Exception) {
            return false
        }
    }

    override fun onDestroy() {
        if (serviceBound) {
            try { unbindService(connection) } catch (_: Exception) {}
        }
        super.onDestroy()
    }
}
