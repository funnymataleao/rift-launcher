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
import dev.rift.launcher.settings.RenameGameDialog

/** The library entry an Android game's page and its art are known by. */
fun AndroidGame.toPageItem() = LibraryItem(appId = "ANDROID_$packageName", name = RiftGameNames.display("ANDROID_$packageName", label), isInstalled = true)

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
    originalName: String,
    onPlay: () -> Unit,
    onBack: () -> Unit,
    onChangeArt: () -> Unit,
    onRefreshMeta: (() -> Unit)? = null,
    onPickMeta: (() -> Unit)? = null,
    native: Boolean = true,
    platformId: String? = null,
) {
    val context = LocalContext.current
    val names by RiftGameNames.names(context).collectAsStateWithLifecycle()
    val game = remember(item, names) { item.copy(name = names[item.appId] ?: originalName) }
    val artVersion by GameArtStore.version.collectAsStateWithLifecycle()
    val shown = remember(game, artVersion) { GameArtStore.apply(context, game) }
    LaunchedEffect(game.appId, game.name) { RiftAutoArt.ensure(context, game) }
    val metaVersion by RiftAutoMeta.version.collectAsStateWithLifecycle()
    var meta by remember(game.appId, game.name, metaVersion) { mutableStateOf(RiftAutoMeta.cached(context, game.appId)) }
    LaunchedEffect(game.appId, game.name, metaVersion) { RiftAutoMeta.ensure(context, game, platformId)?.let { meta = it } }
    LaunchedEffect(meta, game.appId) {
        timber.log.Timber.tag("RiftMeta").i(
            "page %s: description %s, source %s, genres+tags %d",
            game.name,
            if (meta?.description.isNullOrBlank()) "no" else "yes",
            meta?.source ?: "none",
            (meta?.genres?.size ?: 0) + (meta?.tags?.size ?: 0),
        )
    }
    // Rift: trailers and screenshots from Steam by title, for the hero background and the Media section.
    var media by remember(game.name) { mutableStateOf(StoreGameDetails()) }
    LaunchedEffect(game.name) { media = app.gamenative.data.StoreDetailsRepository.getMediaByTitle(game.name) }
    val ratingLabel = stringResource(R.string.rift_meta_rating)
    val displayInfo = remember(shown, meta, media) {
        GameDisplayInfo(
            name = shown.name,
            developer = meta?.developer.orEmpty(),
            releaseDate = meta?.releaseDate ?: 0L,
            hltbStats = meta?.takeIf { it.hasBeatTimes }?.let {
                app.gamenative.utils.HltbService.Stats(
                    mainHours = app.gamenative.utils.HltbService.formatHours(it.beatMain),
                    mainPlusHours = app.gamenative.utils.HltbService.formatHours(it.beatExtras),
                    completeHours = app.gamenative.utils.HltbService.formatHours(it.beatComplete),
                    allStylesHours = app.gamenative.utils.HltbService.UNKNOWN_HOURS,
                )
            },
            storeDetails = meta?.let { m ->
                StoreGameDetails(
                    description = m.description,
                    reviewPercentage = m.rating,
                    reviewCount = m.ratingCount,
                    reviewSummary = m.rating?.let { m.ratingText.ifBlank { ratingLabel } },
                    tags = m.genres + m.tags,
                    source = m.source,
                    screenshots = media.screenshots,
                    videos = media.videos,
                )
            } ?: media,
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
    var renaming by remember { mutableStateOf(false) }
    val options = remember(native, platformId, onChangeArt, onRefreshMeta, onPickMeta) {
        if (!native) {
            emptyList()
        } else {
            buildList {
                add(AppMenuOption(AppOptionMenuType.ChangeArt, onChangeArt))
                if (platformId != null) add(AppMenuOption(AppOptionMenuType.ChooseEmulator) { choosingEmulator = true })
                add(AppMenuOption(AppOptionMenuType.Rename) { renaming = true })
                onRefreshMeta?.let { add(AppMenuOption(AppOptionMenuType.RefreshMeta, it)) }
                onPickMeta?.let { add(AppMenuOption(AppOptionMenuType.ChooseMeta, it)) }
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
                dialogOpen = choosingEmulator || renaming,
            )
        }
    }
    if (renaming) {
        RenameGameDialog(
            initial = game.name,
            onSave = { name ->
                RiftGameNames.rename(context, game.appId, name, originalName)
                renaming = false
            },
            onDismiss = { renaming = false },
        )
    }
    if (choosingEmulator && platformId != null) {
        EmulatorChoiceDialog(platformId) { choosingEmulator = false }
    }
}
