package dev.rift.launcher.home

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlin.math.abs
import kotlin.math.min

/**
 * The two colours a focused cover's frame, glow and reflection are drawn in.
 *
 * A tiny copy of the cover is bucketed by hue, weighting saturated and bright pixels, so a
 * blue cover gives blue and a light blue, and a red and blue cover gives red and blue.
 */
object CoverColors {
    private const val SAMPLE_SIZE = 24
    private const val HUE_BINS = 12
    private val cache = LruCache<String, List<Color>>(256)

    suspend fun palette(context: Context, url: String): List<Color>? {
        if (url.isBlank()) return null
        cache.get(url)?.let { return it }

        val request = ImageRequest.Builder(context)
            .data(url)
            .size(SAMPLE_SIZE)
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return null
        val colors = palette(result.drawable.toBitmap(SAMPLE_SIZE, SAMPLE_SIZE, Bitmap.Config.ARGB_8888))
        cache.put(url, colors)
        return colors
    }

    fun palette(bitmap: Bitmap): List<Color> {
        val weights = DoubleArray(HUE_BINS)
        val r = DoubleArray(HUE_BINS)
        val g = DoubleArray(HUE_BINS)
        val b = DoubleArray(HUE_BINS)
        val hsv = FloatArray(3)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                android.graphics.Color.colorToHSV(pixel, hsv)
                if (hsv[1] < 0.18f || hsv[2] < 0.18f) continue
                val bin = ((hsv[0] / 360f) * HUE_BINS).toInt().coerceIn(0, HUE_BINS - 1)
                val weight = (hsv[1] * hsv[2]).toDouble()
                weights[bin] += weight
                r[bin] += android.graphics.Color.red(pixel) * weight
                g[bin] += android.graphics.Color.green(pixel) * weight
                b[bin] += android.graphics.Color.blue(pixel) * weight
            }
        }

        val first = weights.indices.maxByOrNull { weights[it] }?.takeIf { weights[it] > 0.0 }
            ?: return listOf(Color.White, Color(0xFFB8B8B8))
        // A second hue counts only if it is clearly different and carries real weight.
        val second = weights.indices
            .filter { hueDistance(it, first) >= 2 && weights[it] >= weights[first] * 0.25 }
            .maxByOrNull { weights[it] }

        fun binColor(bin: Int) = vivid(
            android.graphics.Color.rgb(
                (r[bin] / weights[bin]).toInt(),
                (g[bin] / weights[bin]).toInt(),
                (b[bin] / weights[bin]).toInt(),
            ),
        )

        val main = binColor(first)
        return listOf(main, second?.let(::binColor) ?: lerp(main, Color.White, 0.65f))
    }

    private fun hueDistance(a: Int, b: Int): Int {
        val d = abs(a - b)
        return min(d, HUE_BINS - d)
    }

    /** Bright enough to read on black, without turning neon. */
    private fun vivid(argb: Int): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(argb, hsv)
        hsv[1] = (hsv[1] * 1.1f).coerceAtMost(1f)
        hsv[2] = hsv[2].coerceIn(0.8f, 1f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }
}
