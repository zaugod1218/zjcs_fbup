package com.napp.auto

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
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

    companion object {
        var pendingCropName: String? = null
        var pendingCropPath: String? = null
        var templateJustSaved = false
    }

    private lateinit var statusText: TextView
    private lateinit var cycleText: TextView
    private lateinit var startBtn: Button
    private lateinit var setupBtn: Button
    private var isCapturing = false

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
            if (isCapturing) {
                beginCaptureSession()
            } else {
                startService()
            }
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
        // 从 CropActivity 返回，模板已保存 — 通知 CaptureService 进入下一个
        if (templateJustSaved) {
            templateJustSaved = false
            try { startService(Intent(this, CaptureService::class.java).apply { action = "NEXT" }) } catch (_: Exception) {}
            return
        }
        // 有等待裁剪的截图（用户手动回到 App）
        if (pendingCropName != null && pendingCropPath != null) {
            val name = pendingCropName!!
            val path = pendingCropPath!!
            pendingCropName = null
            pendingCropPath = null
            startActivity(Intent(this, CropActivity::class.java).apply {
                putExtra("imagePath", path)
                putExtra("templateName", name)
            })
        }
    }

    override fun onDestroy() {
        stopCaptureService()
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

        if (resultCode == -1) {
            Toast.makeText(this, "请先授权截屏", Toast.LENGTH_SHORT).show()
            isCapturing = true
            requestScreenCapture()
            return
        }

        beginCaptureSession()
    }

    private fun beginCaptureSession() {
        val initIntent = Intent(this, CaptureService::class.java).apply {
            action = "INIT"
            putExtra("resultCode", resultCode)
            putExtra("data", projectionData)
        }
        startForegroundService(initIntent)

        startService(Intent(this, CaptureService::class.java).apply { action = "START_CAPTURE" })

        Toast.makeText(this, "请在通知栏中操作截图", Toast.LENGTH_LONG).show()
    }

    private fun stopCaptureService() {
        val intent = Intent(this, CaptureService::class.java).apply { action = "STOP" }
        try { startService(intent) } catch (_: Exception) {}
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
