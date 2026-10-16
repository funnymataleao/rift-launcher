package dev.rift.launcher.home

import android.content.Context
import androidx.annotation.StringRes
import app.gamenative.R
import app.gamenative.data.LibraryItem
import app.gamenative.utils.PlatformAuthUtils

/**
 * A top tab on the Rift home screen, Beacon style.
 *
 * Tabs are built from the sources the user has set up: a section only appears once its source is
 * configured. Emulator platforms (PlayStation 2, Nintendo, ...), PC streaming and cloud gaming will
 * add their own section types here.
 */
sealed interface RiftSection {
    val key: String

    @get:StringRes
    val titleRes: Int

    /** Windows games from the stores GameNative connects to (Steam, GOG, Epic, Amazon, custom). */
    data object PcGames : RiftSection {
        override val key = "pc"
        override val titleRes = R.string.rift_section_pc_games
    }

    /** Android games the user added in Rift's settings. */
    data object AndroidGames : RiftSection {
        override val key = "android"
        override val titleRes = R.string.rift_section_android_games
    }
}

object RiftSections {
    /** Sections that are set up right now, in display order. */
    fun configured(context: Context, pcLibrary: List<LibraryItem>, androidGames: List<AndroidGame>): List<RiftSection> = buildList {
        if (PlatformAuthUtils.isSignedInToAnyPlatform(context) || pcLibrary.isNotEmpty()) {
            add(RiftSection.PcGames)
        }
        if (androidGames.isNotEmpty()) add(RiftSection.AndroidGames)
    }
}
