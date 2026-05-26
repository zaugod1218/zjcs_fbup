package com.napp.auto

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class CropActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val imagePath = intent.getStringExtra("imagePath")
        val templateName = intent.getStringExtra("templateName")
        if (imagePath == null || templateName == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val originalBmp = BitmapFactory.decodeFile(imagePath) ?: run {
            Toast.makeText(this, "加载截图失败", Toast.LENGTH_LONG).show()
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val imageView = ImageView(this).apply {
            setBackgroundColor(Color.DKGRAY)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        setContentView(imageView)

        // 缩小显示适配屏幕宽度
        val scale = resources.displayMetrics.widthPixels.toFloat() / originalBmp.width
        val sh = (originalBmp.height * scale).toInt()
        val displayBmp = Bitmap.createScaledBitmap(originalBmp, resources.displayMetrics.widthPixels, sh, true)
        imageView.setImageBitmap(displayBmp)

        Toast.makeText(this, "请点击按钮所在位置", Toast.LENGTH_SHORT).show()

        imageView.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                val scaleX = originalBmp.width.toFloat() / displayBmp.width
                val scaleY = originalBmp.height.toFloat() / displayBmp.height
                val ox = (event.x * scaleX).toInt()
                val oy = (event.y * scaleY).toInt()

                // 裁剪 80x80 区域作为模板
                val size = 80
                val left = (ox - size / 2).coerceIn(0, originalBmp.width - size)
                val top = (oy - size / 2).coerceIn(0, originalBmp.height - size)
                val w = minOf(size, originalBmp.width - left)
                val h = minOf(size, originalBmp.height - top)

                val template = Bitmap.createBitmap(originalBmp!!, left, top, w, h)

                val dir = File(filesDir, Config.TEMPLATE_DIR)
                dir.mkdirs()
                val file = File(dir, "$templateName.png")
                FileOutputStream(file).use { template.compress(Bitmap.CompressFormat.PNG, 100, it) }
                template.recycle()

                Toast.makeText(this, "模板 $templateName 已保存!", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
            true
        }
    }
}
