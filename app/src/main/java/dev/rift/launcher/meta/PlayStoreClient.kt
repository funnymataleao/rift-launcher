package dev.rift.launcher.meta

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * An Android game's description, developer, genre and rating from its Google Play page, found by package name
 * (so the game is exactly the installed one). Read from the page's structured data; a page that does not carry
 * it, or an app Play does not have, gives nothing.
 */
object PlayStoreClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val structuredData = Regex("<script type=\"application/ld\\+json\"[^>]*>(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
    private val metaDescription = Regex("<meta (?:property=\"og:description\"|name=\"description\") content=\"([^\"]*)\"")

    suspend fun find(packageName: String, language: String): MetaResult = withContext(Dispatchers.IO) {
        val url = "https://play.google.com/store/apps/details".toHttpUrl().newBuilder()
            .addQueryParameter("id", packageName).addQueryParameter("hl", language).build()
        val html = try {
            http.newCall(Request.Builder().url(url).header("Accept-Language", language).build()).execute().use { response ->
                when {
                    response.code == 404 -> return@withContext MetaResult.Miss("not on Google Play")
                    !response.isSuccessful -> return@withContext MetaResult.Unreachable("Google Play HTTP ${response.code}")
                    // A consent page (regions with a cookie wall) is not the app's page.
                    response.request.url.host != "play.google.com" -> return@withContext MetaResult.Miss("Google Play asked for consent")
                }
                response.body.string()
            }
        } catch (e: IOException) {
            return@withContext MetaResult.Unreachable("Google Play network ${e.javaClass.simpleName}")
        }
        val app = structuredData.findAll(html).mapNotNull { m ->
            try {
                JSONObject(m.groupValues[1]).takeIf { it.optString("@type").contains("Application") }
            } catch (e: JSONException) {
                null
            }
        }.firstOrNull()
        val description = clean(app?.optString("description").orEmpty())
            .ifEmpty { clean(metaDescription.find(html)?.groupValues?.get(1).orEmpty()) }
        if (description.isEmpty()) return@withContext MetaResult.Miss("Google Play page has no description")
        val author = app?.opt("author")
        val developer = when (author) {
            is JSONObject -> author.optString("name")
            is JSONArray -> author.optJSONObject(0)?.optString("name").orEmpty()
            else -> ""
        }
        val rating = app?.optJSONObject("aggregateRating")
        val value = rating?.optDouble("ratingValue", Double.NaN)?.takeIf { !it.isNaN() && it > 0 }
        MetaResult.Found(
            GameMeta(
                description = description,
                rating = value?.let { (it * 20).toInt().coerceIn(0, 100) },
                ratingCount = rating?.optString("ratingCount")?.toIntOrNull(),
                ratingText = value?.let { String.format(Locale.US, "Google Play %.1f / 5", it) }.orEmpty(),
                genres = listOfNotNull(genre(app?.optString("applicationCategory").orEmpty())),
                developer = developer,
                source = "play",
            ),
        )
    }

    /** "GAME_ROLE_PLAYING" as "Role Playing"; "GAME" alone and non-game categories give nothing. */
    private fun genre(category: String): String? {
        if (!category.startsWith("GAME_")) return null
        return category.removePrefix("GAME_").split('_').filter { it.isNotEmpty() }
            .joinToString(" ") { it.lowercase().replaceFirstChar(Char::uppercase) }
            .takeIf { it.isNotEmpty() }
    }

    private fun clean(text: String): String = text
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace(Regex("<br\\s*/?>"), "\n").replace(Regex("<[^>]+>"), "")
        .lines().joinToString("\n") { it.trim() }.replace(Regex("\n{3,}"), "\n\n").trim()
}
