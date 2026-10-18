package dev.rift.launcher.assistant.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Screenshots for the assistant: scaled down and sent as JPEG, so a request stays small. */
internal object ChatImages {

    private const val MAX_SIDE = 1280
    private const val QUALITY = 80

    /** The picture at [uri] as base64 JPEG, its longer side at most [MAX_SIDE]; null when it can't be read. */
    suspend fun encode(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null
            val scale = MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height)
            val bitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
            } else {
                decoded
            }
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }.onFailure { Timber.w(it, "Assistant: screenshot unreadable") }.getOrNull()
    }

    fun decode(data: String): ImageBitmap? = runCatching {
        val bytes = Base64.decode(data, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}
