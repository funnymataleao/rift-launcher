package dev.rift.launcher.art

import android.content.Context
import app.gamenative.data.GameSource
import app.gamenative.enums.AppType
import dagger.hilt.android.EntryPointAccessors
import dev.rift.launcher.stream.StreamLibraryEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * "Default covers" (Y in the art picker), like SteamGridDB's "apply default art": every Steam game of the account
 * gets Steam's own cover, wide picture and banner wherever the person has not picked one, from the addresses Steam
 * names for it now. That fills the grey cards of new games, whose pictures are not at the old fixed addresses.
 */
object StandardArt {
    /** Returns how many games got a picture; 0 when Steam did not answer or the library is empty. */
    suspend fun apply(context: Context): Int = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val ids = runCatching {
            EntryPointAccessors.fromApplication(app, StreamLibraryEntryPoint::class.java)
                .steamAppDao()
                .getAllOwnedAppsAsList()
                .filter { it.type == AppType.game || it.type == AppType.demo }
                .map { it.id }
        }.onFailure { Timber.tag("RiftArt").w(it, "Default art: no Steam library") }.getOrDefault(emptyList())
        if (ids.isEmpty()) return@withContext 0
        val found = SteamStoreArt.assets(ids)
        Timber.tag("RiftArt").i("Default art: Steam answered for %d of %d games", found.size, ids.size)
        GameArtStore.applyStandard(app, found.mapKeys { (id, _) -> "${GameSource.STEAM.name}_$id" })
    }
}
