// SPDX-License-Identifier: GPL-3.0-or-later
package dev.rift.providers.android

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.rift.model.LaunchSource
import dev.rift.model.LaunchTarget
import dev.rift.model.ProvidedGame
import dev.rift.providers.GameProvider
import dev.rift.providers.LaunchResult
import dev.rift.providers.ProviderStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Android games installed on this device: launcher apps that declare the game category. */
@Singleton
class AndroidGamesProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : GameProvider {

    override val id = ID
    override val sources = setOf(LaunchSource.NATIVE)
    override val status: StateFlow<ProviderStatus> = MutableStateFlow(ProviderStatus.Ready).asStateFlow()

    override suspend fun games(): List<ProvidedGame> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryLauncherActivities(launcher)
            .map { it.activityInfo.applicationInfo }
            .filter { it.packageName != context.packageName && it.isGame() }
            .distinctBy { it.packageName }
            .map { app ->
                ProvidedGame(
                    title = app.loadLabel(pm).toString(),
                    target = LaunchTarget(
                        providerId = ID,
                        source = LaunchSource.NATIVE,
                        externalId = app.packageName,
                        label = "Android",
                    ),
                )
            }
    }

    override suspend fun launch(target: LaunchTarget): LaunchResult {
        val intent = context.packageManager.getLaunchIntentForPackage(target.externalId)
            ?: return LaunchResult.Failed("${target.externalId} is not installed")
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return LaunchResult.Started
    }

    @Suppress("DEPRECATION") // FLAG_IS_GAME is still what many older games set instead of the category.
    private fun ApplicationInfo.isGame(): Boolean =
        category == ApplicationInfo.CATEGORY_GAME || (flags and ApplicationInfo.FLAG_IS_GAME) != 0

    private fun PackageManager.queryLauncherActivities(intent: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            queryIntentActivities(intent, 0)
        }

    companion object {
        const val ID = "android"
    }
}
