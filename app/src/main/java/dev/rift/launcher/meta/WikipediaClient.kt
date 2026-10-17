package dev.rift.launcher.meta

import app.gamenative.data.looseTitleKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * A game's description, genre, developer and first release date from Wikipedia (the lead of the article, in the
 * app's language first, then English) and Wikidata. Only an article that is about a video game, on the right
 * system, and named exactly like the game (or the game plus a subtitle, when it is the only one) counts;
 * anything less sure is no answer. The text is CC BY-SA, so the page shows "Wikipedia" under it.
 */
object WikipediaClient {
    private const val TAG = "RiftMeta"
    private const val MAX_DESCRIPTION = 900

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "RiftLauncher/1.0 (https://github.com/funnymataleao/rift-launcher)")
                    .build(),
            )
        }
        .build()

    /** The system's name for a search, and what an article's lead says when it is about that system. */
    private class SystemInfo(val search: String, val pattern: Regex)

    private fun system(platformId: String?): SystemInfo? = when (platformId) {
        "switch" -> SystemInfo("Nintendo Switch", Regex("nintendo switch"))
        "ps2" -> SystemInfo("PlayStation 2", Regex("playstation 2|\\bps2\\b"))
        "ps3" -> SystemInfo("PlayStation 3", Regex("playstation 3|\\bps3\\b"))
        "ps4" -> SystemInfo("PlayStation 4", Regex("playstation 4|\\bps4\\b"))
        "ps1" -> SystemInfo("PlayStation", Regex("playstation(?! [2-5])|\\bpsx\\b|\\bps1\\b"))
        "psp" -> SystemInfo("PlayStation Portable", Regex("playstation portable|\\bpsp\\b"))
        "vita" -> SystemInfo("PlayStation Vita", Regex("vita"))
        "3ds" -> SystemInfo("Nintendo 3DS", Regex("3ds"))
        "nds" -> SystemInfo("Nintendo DS", Regex("nintendo ds|\\bnds\\b"))
        "gc_wii" -> SystemInfo("GameCube", Regex("gamecube|\\bwii\\b"))
        "dreamcast" -> SystemInfo("Dreamcast", Regex("dreamcast"))
        "gba" -> SystemInfo("Game Boy Advance", Regex("game boy advance"))
        "snes" -> SystemInfo("Super Nintendo", Regex("super nintendo|super nes|\\bsnes\\b|super famicom"))
        "nes" -> SystemInfo("Nintendo Entertainment System", Regex("nintendo entertainment system|\\bnes\\b|famicom"))
        "genesis" -> SystemInfo("Sega Genesis", Regex("mega drive|genesis"))
        else -> null
    }

    private val mobile = SystemInfo("Android", Regex("android|\\bios\\b|mobile|smartphone|iphone"))
    private val gameWords = Regex("video ?game|game|spiel|juego|jeu |gioco|jogo|игр|ゲーム|게임|游戏|gra |hra ")

    /** [platformId] is an emulator system's id; [android] marks a mobile game; otherwise any system is accepted. */
    suspend fun find(title: String, platformId: String?, android: Boolean, appLanguage: String): MetaResult {
        val system = if (android) mobile else system(platformId)
        var unreachable: String? = null
        val misses = mutableListOf<String>()
        for (language in listOf(appLanguage, "en").distinct()) {
            when (val result = lookup(title, system, language)) {
                is MetaResult.Found -> return result
                is MetaResult.Miss -> misses += "$language: ${result.reason}"
                is MetaResult.Unreachable -> unreachable = unreachable ?: "$language: ${result.reason}"
            }
        }
        return if (unreachable != null) MetaResult.Unreachable(unreachable) else MetaResult.Miss(misses.joinToString("; "))
    }

    private class Failure(val reason: String) : Exception()

    private suspend fun lookup(title: String, system: SystemInfo?, language: String): MetaResult = withContext(Dispatchers.IO) {
        try {
            val key = looseTitleKey(title)
            val query = buildString {
                append(title)
                system?.let { append(' ').append(it.search) }
                append(if (language == "ru") " игра" else " video game")
            }
            val hits = json(
                api(language).newBuilder()
                    .addQueryParameter("action", "query").addQueryParameter("list", "search")
                    .addQueryParameter("srsearch", query).addQueryParameter("srlimit", "10")
                    .addQueryParameter("srnamespace", "0").addQueryParameter("format", "json").addQueryParameter("utf8", "1")
                    .build().toString(),
            ).optJSONObject("query")?.optJSONArray("search").objects()
            if (hits.isEmpty()) return@withContext MetaResult.Miss("no article found")

            // The article's own name, without a "(2017 video game)" disambiguator.
            fun bare(name: String) = name.replace(Regex("\\s*\\([^)]*\\)\\s*$"), "")
            val exact = hits.filter { looseTitleKey(bare(it.optString("title"))) == key }
            val subtitled = hits.filter { hit ->
                val name = bare(hit.optString("title"))
                val cut = Regex("\\s*(:|\\s[-–—]\\s)").find(name)?.range?.first ?: return@filter false
                looseTitleKey(name.substring(0, cut)) == key
            }
            val candidates = exact.ifEmpty { subtitled.takeIf { it.size == 1 }.orEmpty() }.take(3)
            if (candidates.isEmpty()) return@withContext MetaResult.Miss("no article with the same name")

            val pages = json(
                api(language).newBuilder()
                    .addQueryParameter("action", "query").addQueryParameter("prop", "extracts|pageprops")
                    .addQueryParameter("exintro", "1").addQueryParameter("explaintext", "1")
                    .addQueryParameter("exlimit", "3").addQueryParameter("ppprop", "wikibase_item")
                    .addQueryParameter("redirects", "1").addQueryParameter("format", "json")
                    .addQueryParameter("pageids", candidates.joinToString("|") { it.optLong("pageid").toString() })
                    .build().toString(),
            ).optJSONObject("query")?.optJSONObject("pages")
            val verified = candidates.mapNotNull { hit ->
                val page = pages?.optJSONObject(hit.optLong("pageid").toString()) ?: return@mapNotNull null
                val extract = page.optString("extract").trim()
                val lead = extract.take(1500).lowercase()
                val aboutAGame = gameWords.containsMatchIn(lead.take(400))
                val onSystem = system == null || system.pattern.containsMatchIn(lead)
                if (extract.isNotEmpty() && aboutAGame && onSystem) page else null
            }
            if (verified.isEmpty()) return@withContext MetaResult.Miss("article is not about this game on this system")
            if (verified.size > 1) return@withContext MetaResult.Miss("several articles fit")
            val page = verified.single()

            val facts = page.optJSONObject("pageprops")?.optString("wikibase_item")?.takeIf { it.isNotEmpty() }
                ?.let { wikidata(it, language) }
            MetaResult.Found(
                GameMeta(
                    description = trim(page.optString("extract")),
                    genres = facts?.genres.orEmpty(),
                    developer = facts?.developer.orEmpty(),
                    releaseDate = facts?.releaseDate ?: 0L,
                    source = "wikipedia",
                ),
            )
        } catch (e: Failure) {
            MetaResult.Unreachable(e.reason)
        }
    }

    private class Facts(val genres: List<String>, val developer: String, val releaseDate: Long)

    /** Genre (P136), developer (P178) and first publication date (P577) from Wikidata; null when it cannot be read. */
    private fun wikidata(qid: String, language: String): Facts? = try {
        val entity = json(
            "https://www.wikidata.org/w/api.php".toHttpUrl().newBuilder()
                .addQueryParameter("action", "wbgetentities").addQueryParameter("ids", qid)
                .addQueryParameter("props", "claims").addQueryParameter("format", "json").build().toString(),
        ).optJSONObject("entities")?.optJSONObject(qid)?.optJSONObject("claims")
        fun ids(property: String): List<String> = entity?.optJSONArray(property).objects().mapNotNull {
            it.optJSONObject("mainsnak")?.optJSONObject("datavalue")?.optJSONObject("value")?.optString("id")?.ifEmpty { null }
        }.take(4)
        val genreIds = ids("P136")
        val developerIds = ids("P178").take(2)
        val labels = (genreIds + developerIds).distinct().takeIf { it.isNotEmpty() }?.let { all ->
            json(
                "https://www.wikidata.org/w/api.php".toHttpUrl().newBuilder()
                    .addQueryParameter("action", "wbgetentities").addQueryParameter("ids", all.joinToString("|"))
                    .addQueryParameter("props", "labels").addQueryParameter("languages", "$language|en")
                    .addQueryParameter("languagefallback", "1").addQueryParameter("format", "json").build().toString(),
            ).optJSONObject("entities")
        }
        fun label(id: String): String? {
            val l = labels?.optJSONObject(id)?.optJSONObject("labels") ?: return null
            return (l.optJSONObject(language) ?: l.optJSONObject("en"))?.optString("value")?.ifEmpty { null }
        }
        val dates = entity?.optJSONArray("P577").objects().mapNotNull { claim ->
            val value = claim.optJSONObject("mainsnak")?.optJSONObject("datavalue")?.optJSONObject("value") ?: return@mapNotNull null
            if (value.optInt("precision") < 11) return@mapNotNull null
            val time = value.optString("time")
            if (!time.startsWith("+")) return@mapNotNull null
            runCatching {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                    .parse(time.substring(1, 11))?.time?.div(1000)
            }.getOrNull()
        }
        Facts(
            genres = genreIds.mapNotNull(::label).map { it.replaceFirstChar { c -> c.uppercase() } },
            developer = developerIds.mapNotNull(::label).joinToString(", "),
            releaseDate = dates.minOrNull() ?: 0L,
        )
    } catch (e: Failure) {
        Timber.tag(TAG).i("Wikidata unavailable for %s: %s", qid, e.reason)
        null
    }

    /** The lead's paragraphs up to [MAX_DESCRIPTION] characters, cut at a sentence end. */
    private fun trim(extract: String): String {
        val text = extract.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n\n")
        if (text.length <= MAX_DESCRIPTION) return text
        val head = text.take(MAX_DESCRIPTION)
        val end = head.lastIndexOfAny(charArrayOf('.', '!', '?', '。'))
        return if (end > MAX_DESCRIPTION / 2) head.substring(0, end + 1) else head.trimEnd() + "…"
    }

    private fun api(language: String) = "https://${language.lowercase().filter { it in 'a'..'z' }.ifEmpty { "en" }}.wikipedia.org/w/api.php".toHttpUrl()

    private fun json(url: String): JSONObject {
        try {
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) throw Failure("HTTP ${response.code}")
                return JSONObject(response.body.string())
            }
        } catch (e: IOException) {
            throw Failure("network ${e.javaClass.simpleName}")
        } catch (e: JSONException) {
            throw Failure("unreadable answer")
        }
    }

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}
