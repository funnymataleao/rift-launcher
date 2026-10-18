package dev.rift.launcher.meta

import dev.rift.launcher.art.SteamGridDbClient

/**
 * A game's name as people (and encyclopedias) know it, from the name a dump or a file carries: without
 * extensions, disc codes ("SLUS 213.51"), years glued on with dots, region tags and "[!]", with dots and
 * underscores read as spaces and "Terminator, The" turned into "The Terminator". Used before every metadata search.
 */
object TitleCleaner {
    private val extension = Regex(
        "\\.(ps[1-4]|psp|iso|bin|cue|chd|nsp|xci|zip|7z|rar|rom|gba|nes|sfc|smc|md|gen|cso|rvz|wbfs|gcz|3ds|cia|nds|apk|pkg)$",
        RegexOption.IGNORE_CASE,
    )
    private val discCode = Regex("\\b[A-Z]{4}[ _-]?\\d{3}[._]?\\d{2}\\b")
    private val dottedYear = Regex("\\.(19|20)\\d{2}\\.?$")
    private val dotBetweenWords = Regex("(?<=[A-Za-z0-9])\\.(?=[A-Za-z])")
    private val article = Regex("^(.+?),\\s*(The|A|An)\\b(.*)$")

    fun clean(name: String): String {
        var s = name.trim()
        // Extensions and years glued on with dots can stack ("Game.PS2.2004", "Game.PS2.iso").
        while (true) {
            val stripped = s.replace(extension, "").replace(dottedYear, "")
            if (stripped == s) break
            s = stripped
        }
        s = s.replace(discCode, " ")
        // "Need.for.Speed.2" has no spaces at all: every dot is a space. Otherwise only "Mr.Driller"-style dots.
        s = if (!s.contains(' ')) s.replace('.', ' ') else s.replace(dotBetweenWords, " ")
        // Brackets, "v1.2", "[!]" and the rest of the dump tags.
        s = SteamGridDbClient.cleanTitle(s)
        s = s.replace(Regex("[™®©]"), "").replace(Regex("\\s+"), " ").trim(' ', '.', '-', ',')
        article.matchEntire(s)?.let { m ->
            val (head, art, tail) = m.destructured
            s = "$art $head$tail".replace(Regex("\\s+"), " ").trim()
        }
        return s.ifEmpty { name.trim() }
    }
}
