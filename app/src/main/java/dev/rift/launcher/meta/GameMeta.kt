package dev.rift.launcher.meta

import org.json.JSONArray
import org.json.JSONObject

/** What a metadata source knows about a game, kept next to the art Rift saves for it. */
data class GameMeta(
    val description: String = "",
    /** 0..100, or null when the source has no rating for the game. */
    val rating: Int? = null,
    val ratingCount: Int? = null,
    val genres: List<String> = emptyList(),
    /** Themes, game modes and perspectives: everything that is not a genre. */
    val tags: List<String> = emptyList(),
    val developer: String = "",
    /** Unix seconds, 0 when unknown. */
    val releaseDate: Long = 0L,
) {
    val isEmpty: Boolean get() = description.isBlank() && rating == null && genres.isEmpty() && tags.isEmpty() && developer.isBlank() && releaseDate == 0L

    fun toJson(): String = JSONObject().apply {
        put("d", description)
        rating?.let { put("r", it) }
        ratingCount?.let { put("c", it) }
        put("g", JSONArray(genres))
        put("t", JSONArray(tags))
        put("dev", developer)
        put("rel", releaseDate)
    }.toString()

    companion object {
        fun fromJson(json: String): GameMeta? = try {
            val o = JSONObject(json)
            GameMeta(
                description = o.optString("d"),
                rating = if (o.has("r")) o.optInt("r") else null,
                ratingCount = if (o.has("c")) o.optInt("c") else null,
                genres = o.optJSONArray("g").strings(),
                tags = o.optJSONArray("t").strings(),
                developer = o.optString("dev"),
                releaseDate = o.optLong("rel"),
            )
        } catch (e: org.json.JSONException) {
            null
        }

        private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { getString(it) }
    }
}
