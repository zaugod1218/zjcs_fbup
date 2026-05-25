package com.napp.auto

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Environment
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class CropActivity : AppCompatActivity() {

    private var originalBmp: Bitmap? = null
    private var overlayBmp: Bitmap? = null
    private var templateName: String = ""
    private var resultCode = -1
    private var projectionData: android.content.Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        templateName = intent.getStringExtra("templateName") ?: ""
        resultCode = intent.getIntExtra("resultCode", -1)
        projectionData = intent.getParcelableExtra("data", android.content.Intent::class.java)

        val imageView = ImageView(this).apply {
            setBackgroundColor(Color.DKGRAY)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setOnClickListener { null }
        }
        setContentView(imageView)

        // 截图
        Thread {
            val bmp = captureScreenshot()
            runOnUiThread {
                if (bmp != null) {
                    originalBmp = bmp
                    // 缩小显示（手机屏幕显示全屏截图）
                    val scale = resources.displayMetrics.widthPixels.toFloat() / bmp.width
                    val sh = (bmp.height * scale).toInt()
                    overlayBmp = Bitmap.createScaledBitmap(bmp, resources.displayMetrics.widthPixels, sh, true)
                    imageView.setImageBitmap(overlayBmp)

                    Toast.makeText(this, "请点击按钮所在位置", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "截图失败", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }.start()

        // 点击选择模板位置
        imageView.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP && originalBmp != null && overlayBmp != null) {
                // 映射点击坐标到原始截图坐标
                val scaleX = originalBmp!!.width.toFloat() / overlayBmp!!.width
                val scaleY = originalBmp!!.height.toFloat() / overlayBmp!!.height
                val ox = (event.x * scaleX).toInt()
                val oy = (event.y * scaleY).toInt()

                // 裁剪 80x80 区域作为模板
                val size = 80
                val left = (ox - size / 2).coerceIn(0, originalBmp!!.width - size)
                val top = (oy - size / 2).coerceIn(0, originalBmp!!.height - size)
                val w = minOf(size, originalBmp!!.width - left)
                val h = minOf(size, originalBmp!!.height - top)

                val template = Bitmap.createBitmap(originalBmp!!, left, top, w, h)

                // 保存
                val dir = File(filesDir, Config.TEMPLATE_DIR)
                dir.mkdirs()
                val file = File(dir, "$templateName.png")
                FileOutputStream(file).use { template.compress(Bitmap.CompressFormat.PNG, 100, it) }
                template.recycle()

                Toast.makeText(this, "模板 $templateName 已保存!", Toast.LENGTH_SHORT).show()
                finish()
            }
            true
        }
    }

    private fun captureScreenshot(): Bitmap? {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val proj = mpm.getMediaProjection(resultCode, projectionData!!)
        val helper = ScreenCaptureHelper(this)
        helper.start(proj)
        Thread.sleep(500)
        var bmp: Bitmap? = null
        var attempts = 0
        while (bmp == null && attempts < 8) {
            bmp = helper.captureScreen()
            Thread.sleep(200)
            attempts++
        }
        helper.stop()
        return bmp
    }
}
