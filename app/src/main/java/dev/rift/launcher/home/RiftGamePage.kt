package dev.rift.launcher.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gamenative.R
import app.gamenative.data.LibraryItem
import app.gamenative.ui.data.AppMenuOption
import app.gamenative.ui.data.DownloadDisplayDetails
import app.gamenative.ui.data.GameDisplayInfo
import app.gamenative.ui.enums.AppOptionMenuType
import app.gamenative.ui.screen.library.AppScreenContent
import dev.rift.launcher.art.GameArtStore
import dev.rift.launcher.art.RiftAutoArt
import dev.rift.launcher.meta.RiftAutoMeta
import app.gamenative.data.StoreGameDetails
import dev.rift.launcher.settings.EmulatorChoiceDialog

/** The library entry an Android game's page and its art are known by. */
fun AndroidGame.toPageItem() = LibraryItem(appId = "ANDROID_$packageName", name = label, isInstalled = true)

/**
 * The game page of a game that has no store of its own (Android games, emulator games): GameNative's
 * own game page, the same template as every other tab's, with the blocks the source has. [native] games
 * run on this device and get the settings icon and the "Properties" page (art, and for emulator games
 * the emulator); [platformId] is the emulator game's system. Nothing is shown that the source cannot
 * give: no achievements, no store details, no install status.
 */
@Composable
internal fun RiftGamePage(
    item: LibraryItem,
    onPlay: () -> Unit,
    onBack: () -> Unit,
    onChangeArt: () -> Unit,
    native: Boolean = true,
    platformId: String? = null,
) {
    val context = LocalContext.current
    val artVersion by GameArtStore.version.collectAsStateWithLifecycle()
    val shown = remember(item, artVersion) { GameArtStore.apply(context, item) }
    LaunchedEffect(item.appId) { RiftAutoArt.ensure(context, item) }
    var meta by remember(item.appId) { mutableStateOf(RiftAutoMeta.cached(context, item.appId)) }
    LaunchedEffect(item.appId) { RiftAutoMeta.ensure(context, item, platformId)?.let { meta = it } }
    val ratingLabel = stringResource(R.string.rift_meta_rating)
    val displayInfo = remember(shown, meta) {
        GameDisplayInfo(
            name = shown.name,
            developer = meta?.developer.orEmpty(),
            releaseDate = meta?.releaseDate ?: 0L,
            storeDetails = meta?.let { m ->
                StoreGameDetails(
                    description = m.description,
                    reviewPercentage = m.rating,
                    reviewCount = m.ratingCount,
                    reviewSummary = m.rating?.let { ratingLabel },
                    tags = m.genres + m.tags,
                )
            } ?: StoreGameDetails(),
            heroImageUrl = shown.heroImageUrl.ifEmpty { null },
            iconUrl = shown.customIconUrl.ifEmpty { null },
            gameId = 0,
            appId = shown.appId,
            logoUrl = shown.customLogoUrl.ifEmpty { null },
            capsuleUrl = shown.capsuleImageUrl.ifEmpty { null },
            headerUrl = shown.headerImageUrl.ifEmpty { null },
        )
    }
    var choosingEmulator by remember { mutableStateOf(false) }
    val options = remember(native, platformId, onChangeArt) {
        if (!native) {
            emptyList()
        } else {
            buildList {
                add(AppMenuOption(AppOptionMenuType.ChangeArt, onChangeArt))
                if (platformId != null) add(AppMenuOption(AppOptionMenuType.ChooseEmulator) { choosingEmulator = true })
            }
        }
    }
    val action = remember(onPlay, native) {
        RiftStreamAction(onStream = onPlay, labelRes = R.string.rift_play, icon = Icons.Default.PlayArrow, showLocalDetails = false, showOptions = native)
    }
    Surface(color = MaterialTheme.colorScheme.surface) {
        CompositionLocalProvider(LocalRiftStreamAction provides action) {
            AppScreenContent(
                displayInfo = displayInfo,
                downloadDisplayDetails = DownloadDisplayDetails(
                    isInstalled = true,
                    isValidToDownload = false,
                    isDownloading = false,
                    downloadProgress = 0f,
                    hasPartialDownload = false,
                    isUpdatePending = false,
                ),
                onDownloadInstallClick = onPlay,
                onPauseResumeClick = {},
                onDeleteDownloadClick = {},
                onUpdateClick = {},
                onBack = onBack,
                optionsMenu = options,
                dialogOpen = choosingEmulator,
            )
        }
    }
    if (choosingEmulator && platformId != null) {
        EmulatorChoiceDialog(platformId) { choosingEmulator = false }
    }
}
