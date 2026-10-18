package dev.rift.launcher.meta

/** What a metadata source answered, so the log can say why a game got nothing. */
sealed class MetaResult {
    data class Found(val meta: GameMeta) : MetaResult()

    /** The source answered but has no (sure) match: not asked again for a day. */
    data class Miss(val reason: String) : MetaResult()

    /** The source could not be reached: asked again next time. */
    data class Unreachable(val reason: String) : MetaResult()
}
