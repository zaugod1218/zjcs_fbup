package com.napp.auto

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class FloatingCaptureView(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var container: LinearLayout? = null
    private var shown = false

    fun show(
        templateName: String,
        index: Int,
        total: Int,
        onCapture: () -> Unit,
        onSkip: () -> Unit,
        onFinishAll: () -> Unit
    ) {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (shown) return

        container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)

            val infoText = TextView(context).apply {
                text = "采集 $index/$total: 「$templateName」"
                setTextColor(Color.WHITE)
                textSize = 15f
            }
            addView(infoText)

            val hintText = TextView(context).apply {
                text = "让按钮显示在屏幕上，然后点截图"
                setTextColor(Color.parseColor("#BBBBBB"))
                textSize = 12f
                setPadding(0, 4, 0, 12)
            }
            addView(hintText)

            val btnRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL

                addView(Button(context).apply {
                    text = "截图"
                    setOnClickListener { onCapture() }
                    setPadding(24, 8, 24, 8)
                })

                addView(Button(context).apply {
                    text = "跳过"
                    setOnClickListener { onSkip() }
                    setPadding(24, 8, 24, 8)
                })

                addView(Button(context).apply {
                    text = "完成"
                    setOnClickListener { onFinishAll() }
                    setPadding(24, 8, 24, 8)
                })
            }
            addView(btnRow)

            setBackgroundColor(Color.parseColor("#CC333333"))
            setOnClickListener { /* 透传点击 */ }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 200
        }

        windowManager?.addView(container, params)
        shown = true
    }

    fun dismiss() {
        if (!shown) return
        try {
            container?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {}
        container = null
        shown = false
    }

    fun isShowing() = shown
}
