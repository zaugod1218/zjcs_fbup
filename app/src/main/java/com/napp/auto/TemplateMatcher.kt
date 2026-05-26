package com.napp.auto

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

data class MatchResult(val x: Int, val y: Int)

class TemplateMatcher(private val templateDir: File) {

    private val cache = mutableMapOf<String, Bitmap>()

    fun loadTemplate(name: String): Bitmap? {
        if (name in cache) return cache[name]
        val file = File(templateDir, "$name.png")
        if (!file.exists()) return null
        val bmp = BitmapFactory.decodeFile(file.absolutePath)
        if (bmp != null) cache[name] = bmp
        return bmp
    }

    fun find(screen: Bitmap, templateName: String, threshold: Float = Config.CONFIDENCE): MatchResult? {
        val template = loadTemplate(templateName) ?: return null

        // 缩小截图和模板到 1/2 以提高速度
        val scale = 0.5f
        val sw = (screen.width * scale).toInt()
        val sh = (screen.height * scale).toInt()
        val scaledScreen = Bitmap.createScaledBitmap(screen, sw, sh, true)
        val tw = (template.width * scale).toInt()
        val th = (template.height * scale).toInt()
        val scaledTemplate = Bitmap.createScaledBitmap(template, tw, th, true)

        if (tw > sw || th > sh) return null

        // 归一化交叉相关 (NCC)
        val tPixels = IntArray(tw * th)
        scaledTemplate.getPixels(tPixels, 0, tw, 0, 0, tw, th)
        val tMean = tPixels.map { it and 0xFF }.average().toFloat()
        val tNorm = tPixels.map { (it and 0xFF) - tMean }.toFloatArray()
        var normTotal = 0f
        for (v in tNorm) normTotal += v * v
        val tNormFactor = Math.sqrt(normTotal.toDouble()).toFloat()

        var bestVal = -1f
        var bestX = 0
        var bestY = 0

        val sPixels = IntArray(sw * sh)
        scaledScreen.getPixels(sPixels, 0, sw, 0, 0, sw, sh)

        // 每隔 2 像素采样一次
        val step = 2
        var y = 0
        while (y <= sh - th) {
            var x = 0
            while (x <= sw - tw) {
                var sum = 0f
                for (ty in 0 until th) {
                    for (tx in 0 until tw) {
                        val si = (y + ty) * sw + (x + tx)
                        val sVal = (sPixels[si] and 0xFF) - tMean
                        sum += sVal * tNorm[ty * tw + tx]
                    }
                }
                val corr = if (tNormFactor > 0) sum / tNormFactor else 0f

                if (corr > bestVal) {
                    bestVal = corr
                    bestX = x
                    bestY = y
                }
                x += step
            }
            y += step
        }

        scaledScreen.recycle()

        if (bestVal >= threshold) {
            return MatchResult(
                (bestX / scale + template.width / 2).toInt(),
                (bestY / scale + template.height / 2).toInt()
            )
        }
        return null
    }

    fun findAny(screen: Bitmap, names: List<String>, threshold: Float = Config.CONFIDENCE): Triple<String, Int, Int>? {
        for (name in names) {
            val r = find(screen, name, threshold) ?: continue
            return Triple(name, r.x, r.y)
        }
        return null
    }

    fun clearCache() {
        cache.values.forEach { it.recycle() }
        cache.clear()
    }
}
