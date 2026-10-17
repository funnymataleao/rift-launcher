package dev.rift.launcher.art

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * The pictures Steam's store itself gives a game. Last resort for a game that has none of the
 * standard library pictures yet (a game that just came out, or just came to the account): the store page
 * always has a header, and says its address, so the card is not left grey.
 */
object SteamStoreArt {
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val cache = ConcurrentHashMap<Int, List<String>>()

    /** The store's pictures of [appId], best first for a portrait cover when [portrait], else a wide one. Empty when the store does not answer. */
    fun urls(appId: Int, portrait: Boolean): List<String> {
        val all = cache[appId] ?: load(appId).also { if (it.isNotEmpty()) cache[appId] = it }
        return if (portrait) all.asReversed() else all
    }

    // Wide first: header, then the capsules; the page background last.
    private fun load(appId: Int): List<String> = runCatching {
        val request = Request.Builder()
            .url("https://store.steampowered.com/api/appdetails?appids=$appId&filters=basic")
            .build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val data = JSONObject(response.body.string()).optJSONObject(appId.toString())?.optJSONObject("data")
                ?: return@use emptyList()
            listOf("header_image", "capsule_imagev5", "capsule_image", "background_raw")
                .mapNotNull { data.optString(it).takeIf(String::isNotBlank) }
        }
    }.onFailure { Timber.tag("RiftArt").w(it, "Steam store art for %d", appId) }.getOrDefault(emptyList())
}
