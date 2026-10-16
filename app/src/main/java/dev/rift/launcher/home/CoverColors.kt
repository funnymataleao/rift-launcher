package dev.rift.launcher.home

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlin.math.abs
import kotlin.math.min

/**
 * The two colours a focused cover's frame is drawn in, taken with Palette from the same image the
 * tile shows: the vibrant swatch and the dominant one, muted swatches as the fallback.
 */
object CoverColors {
    private const val SAMPLE_SIZE = 96
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
        val colors = palette(result.drawable.toBitmap(config = Bitmap.Config.ARGB_8888))
        cache.put(url, colors)
        return colors
    }

    fun palette(bitmap: Bitmap): List<Color> {
        val palette = Palette.from(bitmap).maximumColorCount(16).generate()
        val vibrant = (palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.darkVibrantSwatch ?: palette.mutedSwatch)?.rgb
        val dominant = (palette.dominantSwatch ?: palette.lightMutedSwatch ?: palette.mutedSwatch)?.rgb
        val first = vibrant ?: dominant ?: return listOf(Color.White, Color(0xFFB8B8B8))
        val main = visible(first)
        // When both swatches are the same hue, pair the colour with a light tint of itself.
        val second = dominant?.takeIf { it != first && hueDistance(it, first) > 25f }?.let(::visible)
            ?: lerp(main, Color.White, 0.6f)
        return listOf(main, second)
    }

    private fun hueDistance(a: Int, b: Int): Float {
        val ha = FloatArray(3).also { android.graphics.Color.colorToHSV(a, it) }
        val hb = FloatArray(3).also { android.graphics.Color.colorToHSV(b, it) }
        // Greys have no real hue, so treat a grey against a colour as different.
        if (ha[1] < 0.15f || hb[1] < 0.15f) return if (abs(ha[2] - hb[2]) > 0.25f) 180f else 0f
        val d = abs(ha[0] - hb[0])
        return min(d, 360f - d)
    }

    /** Bright enough to read as a thin line on black, without changing the hue. */
    private fun visible(argb: Int): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(argb, hsv)
        hsv[2] = hsv[2].coerceIn(0.75f, 1f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }
}
