package dev.rift.launcher.settings

import android.content.Context
import coil.imageLoader
import dev.rift.launcher.art.GameArtStore
import dev.rift.launcher.meta.RiftAutoMeta
import dev.rift.launcher.sound.pack.InstalledPacks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * What Rift can download again and so may delete to free space: the pictures the image loader keeps, the
 * covers and logos found by themselves, the saved game descriptions and the sound pack previews. Never
 * touched: games, saves, settings, the pictures the user picked and the sound packs the user downloaded.
 */
internal object CacheStorage {
    class Usage(val cache: Long, val soundPacks: Long)

    suspend fun measure(context: Context): Usage = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        Usage(
            cache = (app.imageLoader.diskCache?.size ?: 0L) +
                GameArtStore.autoBytes(app) +
                RiftAutoMeta.bytes(app) +
                previews(app).walkBottomUp().filter { it.isFile }.sumOf { it.length() },
            soundPacks = InstalledPacks.list(app).sumOf { pack -> pack.dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() } },
        )
    }

    /** Deletes the cache; returns the bytes it took. */
    suspend fun clear(context: Context): Long = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val loader = app.imageLoader
        var freed = loader.diskCache?.size ?: 0L
        loader.memoryCache?.clear()
        loader.diskCache?.clear()
        freed += GameArtStore.clearAuto(app)
        freed += RiftAutoMeta.clearAll(app)
        val previews = previews(app)
        freed += previews.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
        previews.deleteRecursively()
        freed
    }

    private fun previews(context: Context) = File(context.cacheDir, "audio_preview")
}
