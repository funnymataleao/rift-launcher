package dev.rift.launcher.cloud

import android.content.Context
import app.gamenative.utils.Net
import java.io.File
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/** A Game Pass game that plays in the cloud, with the store's art and texts. */
data class CloudGame(
    val productId: String,
    val title: String,
    val poster: String,
    val hero: String,
    val titledHero: String,
    val developer: String,
    val publisher: String,
    val description: String,
    /** Unix seconds, 0 when the store has no date. */
    val releaseDate: Long,
    val categories: List<String>,
    val screenshots: List<String>,
) {
    /** The path xbox.com uses for the game, the same way Better xCloud builds it. */
    val slug: String
        get() = title.replace(Regex("[;,/?:@&=+_`~$%#^*()!^™®©]"), "")
            .replace("|", "-")
            .replace(Regex(" {2,}"), " ")
            .trim()
            .take(50)
            .replace(' ', '-')
            .lowercase()
}

/** The cloud catalog: every game, and the product ids of Game Pass's "Most popular" list in its order. */
data class CloudCatalog(val games: List<CloudGame>, val popular: List<String>, val fetchedAt: Long)

/**
 * Xbox Cloud Gaming's catalog, from the same public endpoints xbox.com/play and Better xCloud read:
 * the Game Pass lists (catalog.gamepass.com "sigls") give the product ids, the Microsoft Store display
 * catalog gives titles, texts and art. Neither is a documented API, so the last good catalog is kept in a
 * file and shown when a refresh fails.
 */
object GamePassCatalog {
    private const val SIGL_URL = "https://catalog.gamepass.com/sigls/v2"
    private const val PRODUCTS_URL = "https://displaycatalog.mp.microsoft.com/v7.0/products"

    /** "All cloud games" and "Most popular", ids as Better xCloud uses them. */
    private const val SIGL_ALL_CLOUD = "29a81209-df6f-41fd-a528-2ae6b91f719c"
    private const val SIGL_POPULAR = "e7590b22-e299-44db-ae22-25c61405454c"

    private const val BATCH = 20
    private const val PARALLEL = 4
    private const val MAX_AGE_MS = 24L * 60 * 60 * 1000

    private fun cacheFile(context: Context) = File(context.filesDir, "cloud/gamepass-catalog.json")

    /** The catalog saved last time, or null before the first download. */
    suspend fun cached(context: Context): CloudCatalog? = withContext(Dispatchers.IO) {
        runCatching { cacheFile(context).takeIf { it.exists() }?.readText()?.let(::parseCache) }
            .onFailure { Timber.w(it, "Cloud catalog cache unreadable") }
            .getOrNull()
    }

    fun isStale(catalog: CloudCatalog?): Boolean =
        catalog == null || System.currentTimeMillis() - catalog.fetchedAt > MAX_AGE_MS

    /** Downloads the catalog again and saves it; null if the network or the endpoints fail. */
    suspend fun refresh(context: Context): CloudCatalog? = withContext(Dispatchers.IO) {
        runCatching {
            val language = XcloudAccount.catalogLanguages().substringBefore(',')
            val all = siglIds(SIGL_ALL_CLOUD, language)
            val popular = runCatching { siglIds(SIGL_POPULAR, language) }.getOrDefault(emptyList())
            val gate = Semaphore(PARALLEL)
            val games = coroutineScope {
                all.chunked(BATCH).map { batch -> async { gate.withPermit { products(batch) } } }.awaitAll().flatten()
            }
            // Keep the list's order; drop ids the store did not return.
            val byId = games.associateBy { it.productId }
            val ordered = all.mapNotNull { byId[it] }
            if (ordered.isEmpty()) error("empty catalog")
            CloudCatalog(ordered, popular, System.currentTimeMillis()).also { catalog ->
                val file = cacheFile(context)
                file.parentFile?.mkdirs()
                file.writeText(toCache(catalog))
            }
        }.onFailure { Timber.w(it, "Cloud catalog refresh failed") }.getOrNull()
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        Net.http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} for $url")
            return response.body.string()
        }
    }

    /** A Game Pass list: the first entry describes the list, the rest are {"id": productId}. */
    private fun siglIds(siglId: String, language: String): List<String> {
        val json = JSONArray(get("$SIGL_URL?id=$siglId&language=$language&market=${XcloudAccount.MARKET}"))
        return (0 until json.length()).mapNotNull { json.optJSONObject(it)?.optString("id")?.takeIf(String::isNotBlank) }.distinct()
    }

    private fun products(ids: List<String>): List<CloudGame> {
        val url = "$PRODUCTS_URL?bigIds=${ids.joinToString(",")}&market=${XcloudAccount.MARKET}" +
            "&languages=${XcloudAccount.catalogLanguages()}&MS-CV=DGU1mcuYo0WMMp+F.1"
        val products = JSONObject(get(url)).optJSONArray("Products") ?: return emptyList()
        return (0 until products.length()).mapNotNull { products.optJSONObject(it)?.let(::parseProduct) }
    }

    private fun parseProduct(product: JSONObject): CloudGame? {
        val id = product.optString("ProductId").ifBlank { return null }
        val texts = product.optJSONArray("LocalizedProperties")?.optJSONObject(0) ?: return null
        val title = texts.optString("ProductTitle").ifBlank { return null }
        val images = texts.optJSONArray("Images") ?: JSONArray()
        fun image(vararg purposes: String): String {
            for (purpose in purposes) {
                for (i in 0 until images.length()) {
                    val image = images.optJSONObject(i) ?: continue
                    if (image.optString("ImagePurpose").equals(purpose, ignoreCase = true)) return absolute(image.optString("Uri"))
                }
            }
            return ""
        }
        val screenshots = (0 until images.length()).mapNotNull { i ->
            images.optJSONObject(i)?.takeIf { it.optString("ImagePurpose").equals("Screenshot", ignoreCase = true) }
                ?.optString("Uri")?.let(::absolute)
        }
        val released = product.optJSONArray("MarketProperties")?.optJSONObject(0)?.optString("OriginalReleaseDate").orEmpty()
        val properties = product.optJSONObject("Properties")
        val categories = properties?.optJSONArray("Categories")?.let { array -> (0 until array.length()).map { array.optString(it) } }
            ?: listOfNotNull(properties?.optString("Category")?.takeIf(String::isNotBlank))
        return CloudGame(
            productId = id,
            title = title,
            poster = sized(image("Poster", "BoxArt"), 600, 900),
            hero = sized(image("SuperHeroArt", "TitledHeroArt", "Screenshot"), 1920, 1080),
            titledHero = sized(image("TitledHeroArt", "SuperHeroArt", "BoxArt"), 1280, 720),
            developer = texts.optString("DeveloperName").ifBlank { texts.optString("PublisherName") },
            publisher = texts.optString("PublisherName"),
            description = texts.optString("ProductDescription").ifBlank { texts.optString("ShortDescription") },
            releaseDate = runCatching { Instant.parse(released).epochSecond }.getOrDefault(0L).coerceAtLeast(0L),
            categories = categories.filter(String::isNotBlank),
            screenshots = screenshots.filter(String::isNotBlank).take(8),
        )
    }

    /** Store image links come without a scheme ("//store-images..."). */
    private fun absolute(uri: String): String = when {
        uri.isBlank() -> ""
        uri.startsWith("//") -> "https:$uri"
        else -> uri
    }

    /** The store's image server scales on request, so covers do not download full-size posters. */
    private fun sized(url: String, width: Int, height: Int): String =
        if (url.contains("store-images.s-microsoft.com") && !url.contains('?')) "$url?q=90&w=$width&h=$height" else url

    private fun toCache(catalog: CloudCatalog): String = JSONObject()
        .put("fetchedAt", catalog.fetchedAt)
        .put("popular", JSONArray(catalog.popular))
        .put(
            "games",
            JSONArray(
                catalog.games.map { game ->
                    JSONObject()
                        .put("id", game.productId)
                        .put("title", game.title)
                        .put("poster", game.poster)
                        .put("hero", game.hero)
                        .put("titledHero", game.titledHero)
                        .put("developer", game.developer)
                        .put("publisher", game.publisher)
                        .put("description", game.description)
                        .put("releaseDate", game.releaseDate)
                        .put("categories", JSONArray(game.categories))
                        .put("screenshots", JSONArray(game.screenshots))
                },
            ),
        )
        .toString()

    private fun parseCache(raw: String): CloudCatalog {
        val json = JSONObject(raw)
        fun JSONArray?.strings() = if (this == null) emptyList() else (0 until length()).map { optString(it) }
        val games = json.getJSONArray("games")
        return CloudCatalog(
            games = (0 until games.length()).map { i ->
                val game = games.getJSONObject(i)
                CloudGame(
                    productId = game.getString("id"),
                    title = game.getString("title"),
                    poster = game.optString("poster"),
                    hero = game.optString("hero"),
                    titledHero = game.optString("titledHero"),
                    developer = game.optString("developer"),
                    publisher = game.optString("publisher"),
                    description = game.optString("description"),
                    releaseDate = game.optLong("releaseDate"),
                    categories = game.optJSONArray("categories").strings(),
                    screenshots = game.optJSONArray("screenshots").strings(),
                )
            },
            popular = json.optJSONArray("popular").strings(),
            fetchedAt = json.optLong("fetchedAt"),
        )
    }
}
