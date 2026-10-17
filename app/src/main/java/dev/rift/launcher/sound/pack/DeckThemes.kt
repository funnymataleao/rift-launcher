package dev.rift.launcher.sound.pack

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/** One audio pack in the DeckThemes catalog: [downloadId] is the blob that holds the zip. */
data class DeckPack(
    val id: String,
    val name: String,
    val author: String,
    val version: String,
    val downloads: Int,
    val downloadId: String,
    val music: Boolean,
)

/** Why a DeckThemes request or install gave nothing. */
sealed class PackError : Exception() {
    object Network : PackError()
    object BadPack : PackError()
}

/**
 * Client for the DeckThemes catalog that Decky's Audio Loader uses (https://api.deckthemes.com): the list is
 * `GET /themes?filters=AUDIO.Audio|AUDIO.Music`, a pack's zip is `GET /blobs/<download.id>`. A pack is a folder
 * with a `pack.json` and audio files named after Steam Deck's own sounds. The packs are made by their authors and
 * are never bundled with Rift; the user picks and downloads them.
 */
object DeckThemesClient {
    private const val BASE = "https://api.deckthemes.com"
    private const val TAG = "RiftPacks"
    const val PAGE_SIZE = 30
    private const val MAX_PACK_BYTES = 120L * 1024 * 1024

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** One page (1-based) of sound or music packs, most downloaded first. */
    suspend fun list(music: Boolean, page: Int): List<DeckPack> = withContext(Dispatchers.IO) {
        val url = "$BASE/themes".toHttpUrl().newBuilder()
            .addQueryParameter("filters", if (music) "AUDIO.Music" else "AUDIO.Audio")
            .addQueryParameter("order", "Most Downloaded")
            .addQueryParameter("perPage", PAGE_SIZE.toString())
            .addQueryParameter("page", page.toString())
            .build()
        try {
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) throw PackError.Network
                val items = JSONObject(response.body.string()).optJSONArray("items") ?: return@use emptyList()
                (0 until items.length()).mapNotNull { i -> parse(items.optJSONObject(i), music) }
            }
        } catch (e: IOException) {
            Timber.tag(TAG).w(e, "Catalog request failed")
            throw PackError.Network
        } catch (e: JSONException) {
            Timber.tag(TAG).w(e, "Catalog answered with something unreadable")
            throw PackError.Network
        }
    }

    private fun parse(o: JSONObject?, music: Boolean): DeckPack? {
        o ?: return null
        val id = o.optString("id").ifEmpty { return null }
        val download = o.optJSONObject("download") ?: return null
        val downloadId = download.optString("id").ifEmpty { return null }
        val name = o.optString("displayName").ifEmpty { o.optString("name") }.ifEmpty { return null }
        val author = o.optString("specifiedAuthor").ifEmpty { o.optJSONObject("author")?.optString("username").orEmpty() }
        return DeckPack(id, name, author, o.optString("version"), download.optInt("downloadCount"), downloadId, music)
    }

    /** Downloads [pack] and unpacks it into the app's own storage; throws [PackError] when it cannot be used. */
    suspend fun install(context: Context, pack: DeckPack) = withContext(Dispatchers.IO) {
        val dir = InstalledPacks.dir(context, pack.id)
        val tmp = File(dir.parentFile, "${pack.id}.tmp")
        tmp.deleteRecursively()
        tmp.mkdirs()
        try {
            http.newCall(Request.Builder().url("$BASE/blobs/${pack.downloadId}").build()).execute().use { response ->
                if (!response.isSuccessful) throw PackError.Network
                unzip(response.body.byteStream(), tmp)
            }
            if (!File(tmp, "pack.json").isFile || !InstalledPacks.hasAudio(tmp)) throw PackError.BadPack
            dir.deleteRecursively()
            if (!tmp.renameTo(dir)) throw PackError.BadPack
            Unit
        } catch (e: IOException) {
            Timber.tag(TAG).w(e, "Pack download failed: %s", pack.name)
            throw PackError.Network
        } finally {
            tmp.deleteRecursively()
        }
    }

    /**
     * Unpacks the files of the pack. Packs sit in one top folder, so every file is written flat by its own name:
     * nothing in the zip can choose where it lands.
     */
    private fun unzip(input: java.io.InputStream, into: File) {
        var total = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name.substringAfterLast('/').substringAfterLast('\\')
                if (name.isEmpty() || name.startsWith(".")) continue
                File(into, name).outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = zip.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > MAX_PACK_BYTES) throw PackError.BadPack
                        out.write(buffer, 0, n)
                    }
                }
            }
        }
    }
}
