package dev.rift.launcher.home

import app.gamenative.utils.unaccent

/**
 * How Rift's search matches a game name: any part of it, whatever the case, accents, spaces and punctuation, and
 * Russian typed for a Latin title (and back) by sound: "форза" finds "Forza Horizon 2", "forzahor" finds it too.
 */
object RiftSearch {
    private val cyrillic = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "e", 'ж' to "zh",
        'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o",
        'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "ts",
        'ч' to "ch", 'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu",
        'я' to "ya",
    )

    /** Lower case Latin letters and digits only: what both the name and the query are compared as. */
    fun normalize(text: String): String {
        val out = StringBuilder(text.length)
        for (ch in text.lowercase().unaccent()) {
            val latin = cyrillic[ch]
            when {
                latin != null -> out.append(latin)
                ch.isLetterOrDigit() -> out.append(ch)
            }
        }
        return out.toString()
    }

    fun matches(name: String, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        if (name.contains(q, ignoreCase = true)) return true
        val nq = normalize(q)
        return nq.isNotEmpty() && normalize(name).contains(nq)
    }
}
