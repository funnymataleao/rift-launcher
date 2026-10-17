package dev.rift.launcher.home

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A game's logo, drawn at a size that reads the same from game to game. Logos come with very different
 * proportions and with transparent margins of every width, so fitting each into one fixed box makes some
 * huge and some tiny. Here the transparent margins are cut off, then the visible logo gets about the same
 * area whatever its shape (a wide wordmark ends up wider and lower than a square emblem), within the box
 * the caller gives it: [modifier] sets the largest the logo may be, [alignment] where it sits in it.
 * [onFailed] is called when the picture cannot be loaded, so the caller can show the name instead.
 */
@Composable
fun RiftLogo(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.BottomStart,
    onFailed: () -> Unit = {},
) {
    val context = LocalContext.current
    val failed = rememberUpdatedState(onFailed)
    val logo by produceState<TrimmedLogo?>(null, url) {
        value = null
        val loaded = withContext(Dispatchers.IO) { TrimmedLogos.load(context, url) }
        if (loaded == null) failed.value() else value = loaded
    }
    BoxWithConstraints(modifier = modifier, contentAlignment = alignment) {
        val shown = logo ?: return@BoxWithConstraints
        val maxW = maxWidth.value
        val maxH = maxHeight.value
        val aspect = shown.bitmap.width.toFloat() / shown.bitmap.height
        // The share of the box's area a logo takes; a square one reaches the box's height, a very wide one its width.
        val area = maxW * maxH * AREA_SHARE
        var h = sqrt(area / aspect)
        var w = h * aspect
        val fit = min(1f, min(maxW / w, maxH / h))
        w *= fit
        h *= fit
        Image(
            bitmap = shown.bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(w.dp, h.dp),
        )
    }
}

private const val AREA_SHARE = 0.45f

/** A logo with its fully transparent margins cut away. */
class TrimmedLogo(val bitmap: Bitmap)

internal object TrimmedLogos {
    private const val LOAD_SIZE = 1000
    private const val OPAQUE_FROM = 12
    private val cache = object : LruCache<String, TrimmedLogo>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: TrimmedLogo) = value.bitmap.byteCount
    }

    /** Off the main thread. Null when the picture does not load. */
    suspend fun load(context: Context, url: String): TrimmedLogo? {
        if (url.isBlank()) return null
        cache.get(url)?.let { return it }
        val request = ImageRequest.Builder(context).data(url).size(LOAD_SIZE).allowHardware(false).build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return null
        val source = runCatching { result.drawable.toBitmap(config = Bitmap.Config.ARGB_8888) }.getOrNull() ?: return null
        val trimmed = TrimmedLogo(trim(source))
        cache.put(url, trimmed)
        Timber.tag("RiftArt").i("Logo %dx%d trimmed to %dx%d", source.width, source.height, trimmed.bitmap.width, trimmed.bitmap.height)
        return trimmed
    }

    /** [source] cut to the box around everything that is not (nearly) transparent; whole when it has none. */
    private fun trim(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val row = IntArray(w)
        var left = w
        var right = -1
        var top = h
        var bottom = -1
        for (y in 0 until h) {
            source.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                if ((row[x] ushr 24) >= OPAQUE_FROM) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    bottom = y
                }
            }
        }
        if (right < left || bottom < top) return source
        if (left == 0 && top == 0 && right == w - 1 && bottom == h - 1) return source
        return Bitmap.createBitmap(source, left, top, right - left + 1, bottom - top + 1)
    }
}
