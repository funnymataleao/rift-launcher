package dev.rift.launcher.art

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import app.gamenative.data.LibraryItem
import dev.rift.launcher.settings.RiftPrefs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A game page of an Android or emulator game has no store behind it, so it would have no banner and no
 * logo. When the game is opened, SteamGridDB's first banner and logo for its name are picked for it
 * (once; a game it does not know is asked again after a day). Anything the person picked stays.
 */
object RiftAutoArt {
    private const val PREFS = "rift_game_art_auto_page"
    private const val RETRY_MS = 24 * 60 * 60 * 1000L
    private val lock = Mutex()

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    suspend fun ensure(context: Context, item: LibraryItem) {
        val key = RiftPrefs.of(context).steamGridDbKey
        if (key.isBlank() || item.name.isBlank()) return
        val kinds = listOf(ArtKind.Hero, ArtKind.Logo).filter { GameArtStore.get(context, item.appId, it).isEmpty() }
        if (kinds.isEmpty()) return
        lock.withLock {
            val p = prefs(context)
            val tried = p.getLong(item.appId, 0L)
            if (System.currentTimeMillis() - tried < RETRY_MS) return
            try {
                val path = SteamGridDbClient.resolve(key, SteamGridDbClient.Game(null, item.name))
                for (kind in kinds) {
                    val option = SteamGridDbClient.options(key, path, kind, 0).firstOrNull() ?: continue
                    GameArtStore.pick(context, item.appId, kind, option.url)
                }
                // Found (or nothing to find): not asked again for now either way.
                p.edit { putLong(item.appId, System.currentTimeMillis()) }
            } catch (e: ArtError) {
                if (e !== ArtError.Network) p.edit { putLong(item.appId, System.currentTimeMillis()) }
            } catch (e: java.io.IOException) {
                // No network: try again next time the page opens.
            }
        }
    }
}
