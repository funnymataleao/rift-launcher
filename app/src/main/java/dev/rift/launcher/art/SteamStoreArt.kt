package dev.rift.launcher.art

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * The pictures Steam's store itself gives a game. A game that came out recently has its library pictures only
 * under addresses with a hash in them (`.../apps/<id>/<hash>/library_capsule.jpg`); the old fixed addresses
 * (`.../apps/<id>/library_600x900.jpg`) answer 404 for it, so its card stayed grey. The store's item service
 * names the real addresses, many games in one request; the store page's header is the last resort.
 */
object SteamStoreArt {
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private const val CDN = "https://shared.akamai.steamstatic.com/store_item_assets/"
    private const val BATCH = 50

    /** Steam's own pictures of one game, best first in each list; a list is empty when Steam has none of that kind. */
    data class Assets(val capsule: List<String>, val wide: List<String>, val hero: List<String>) {
        fun of(kind: ArtKind): List<String> = when (kind) {
            ArtKind.Capsule -> capsule
            ArtKind.Wide -> wide
            ArtKind.Hero -> hero
            else -> emptyList()
        }
    }

    private val cache = ConcurrentHashMap<Int, Assets>()
    private val storePage = ConcurrentHashMap<Int, List<String>>()

    /** The store's pictures of [appId], best first for a portrait cover when [portrait], else a wide one. Empty when the store does not answer. */
    fun urls(appId: Int, portrait: Boolean): List<String> {
        val assets = assets(appId)
        val own = if (portrait) assets?.capsule.orEmpty() + assets?.wide.orEmpty() else assets?.wide.orEmpty() + assets?.hero.orEmpty()
        if (own.isNotEmpty()) return own.distinct()
        // Not in the item service: the store page's wide pictures (on a tall card cropped art beats a grey one).
        return storePage[appId] ?: loadStorePage(appId).also { if (it.isNotEmpty()) storePage[appId] = it }
    }

    /** Steam's pictures of [appId], or null when the store does not answer. */
    fun assets(appId: Int): Assets? = cache[appId] ?: assets(listOf(appId))[appId]

    /** Steam's pictures of every game of [appIds] the store knows, asked [BATCH] at a time. */
    fun assets(appIds: List<Int>): Map<Int, Assets> {
        val found = HashMap<Int, Assets>()
        appIds.filter { !cache.containsKey(it) }.distinct().chunked(BATCH).forEach { chunk ->
            runCatching { loadItems(chunk) }
                .onFailure { Timber.tag("RiftArt").w(it, "Steam store items for %d games", chunk.size) }
                .getOrNull()
                ?.forEach { (id, assets) -> cache[id] = assets }
        }
        appIds.forEach { id -> cache[id]?.let { found[id] = it } }
        return found
    }

    private fun loadItems(appIds: List<Int>): Map<Int, Assets> {
        val input = JSONObject()
            .put("ids", JSONArray().apply { appIds.forEach { put(JSONObject().put("appid", it)) } })
            .put("context", JSONObject().put("language", "english").put("country_code", "US"))
            .put("data_request", JSONObject().put("include_assets", true))
        val url = "https://api.steampowered.com/IStoreBrowseService/GetItems/v1/".toHttpUrl().newBuilder()
            .addQueryParameter("input_json", input.toString())
            .build()
        return http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val items = JSONObject(response.body.string()).optJSONObject("response")?.optJSONArray("store_items") ?: return@use emptyMap()
            buildMap {
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val id = item.optInt("appid", item.optInt("id", 0)).takeIf { it > 0 } ?: continue
                    val a = item.optJSONObject("assets") ?: continue
                    val format = a.optString("asset_url_format").takeIf { it.contains("\${FILENAME}") } ?: continue
                    fun file(name: String): String? = a.optString(name).takeIf(String::isNotBlank)?.let { CDN + format.replace("\${FILENAME}", it) }
                    put(
                        id,
                        Assets(
                            capsule = listOfNotNull(file("library_capsule"), file("library_capsule_2x")),
                            wide = listOfNotNull(file("header"), file("header_2x"), file("main_capsule")),
                            hero = listOfNotNull(file("library_hero"), file("library_hero_2x")),
                        ),
                    )
                }
            }
        }
    }

    // The store page: its header and capsules, wide only.
    private fun loadStorePage(appId: Int): List<String> = runCatching {
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
