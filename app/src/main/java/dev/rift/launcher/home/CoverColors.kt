package dev.rift.launcher.home

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult

/**
 * Picks a cover's dominant colour for the soft glow under a focused card.
 *
 * Decodes a tiny copy of the cover and averages it, weighting saturated pixels so the glow follows
 * the cover's accent rather than its dark background.
 */
object CoverColors {
    private const val SAMPLE_SIZE = 16
    private val cache = LruCache<String, Color>(256)

    suspend fun dominant(context: Context, url: String): Color? {
        if (url.isBlank()) return null
        cache.get(url)?.let { return it }

        val request = ImageRequest.Builder(context)
            .data(url)
            .size(SAMPLE_SIZE)
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return null
        val bitmap = result.drawable.toBitmap(SAMPLE_SIZE, SAMPLE_SIZE, Bitmap.Config.ARGB_8888)

        val color = average(bitmap) ?: return null
        cache.put(url, color)
        return color
    }

    private fun average(bitmap: Bitmap): Color? {
        var r = 0.0
        var g = 0.0
        var b = 0.0
        var total = 0.0
        val hsv = FloatArray(3)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                android.graphics.Color.colorToHSV(pixel, hsv)
                // Saturated, reasonably bright pixels carry the cover's identity.
                val weight = 0.05 + hsv[1] * hsv[2]
                r += android.graphics.Color.red(pixel) * weight
                g += android.graphics.Color.green(pixel) * weight
                b += android.graphics.Color.blue(pixel) * weight
                total += weight
            }
        }
        if (total == 0.0) return null

        val avg = android.graphics.Color.rgb((r / total).toInt(), (g / total).toInt(), (b / total).toInt())
        android.graphics.Color.colorToHSV(avg, hsv)
        // Keep the glow visible on the near-black background without making it loud.
        hsv[1] = (hsv[1] * 1.15f).coerceAtMost(1f)
        hsv[2] = hsv[2].coerceIn(0.7f, 0.95f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }
}
