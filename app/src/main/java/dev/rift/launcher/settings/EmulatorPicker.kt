package dev.rift.launcher.settings

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.gamenative.R
import app.gamenative.ui.util.SnackbarManager
import dev.rift.launcher.home.AndroidGame
import dev.rift.launcher.home.AndroidGames
import dev.rift.launcher.ui.theme.RiftColors
import dev.rift.launcher.ui.theme.RiftShapes
import dev.rift.launcher.ui.theme.RiftSizes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Every app that can be opened from a launcher, kept current: reloaded each time Rift comes back to the
 * front (after installing an emulator from Google Play or a download) and when the system reports an app
 * added, replaced or removed. So an emulator the person has just installed is in the list at once.
 */
@Composable
internal fun rememberInstalledApps(): List<AndroidGame> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) version++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                version++
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        context.registerReceiver(receiver, filter)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    val apps by produceState(AndroidGames.cachedLaunchable.orEmpty(), version) {
        value = withContext(Dispatchers.IO) { runCatching { AndroidGames.launchable(context) }.getOrDefault(value) }
    }
    return apps
}

/** Opens the emulator's own page: Google Play if it has an app there, otherwise the official site or releases page. */
internal fun openEmulatorDownload(context: Context, download: EmulatorDownload) {
    val intents = buildList {
        download.playId?.let {
            add(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$it")))
            add(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$it")))
        }
        download.url?.let { add(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
    }
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: ActivityNotFoundException) {
            // Try the next address.
        }
    }
    SnackbarManager.show(context.getString(R.string.rift_emulator_download_failed))
}

private sealed interface PickerRow {
    data class Header(val title: String, val note: String? = null) : PickerRow
    data class Installed(val packageName: String, val label: String) : PickerRow
    data class Download(val label: String, val download: EmulatorDownload) : PickerRow
}

/**
 * Picks the app that plays [platform]: the emulators Rift knows for it that are installed come first, then
 * the known ones that can be downloaded (Google Play or the official page), then every other app on the
 * device, as in Beacon. Whatever is picked is used as it is, so any emulator works, known or not. A search
 * field narrows the lists by name.
 */
@Composable
internal fun EmulatorPickerDialog(
    platform: PlatformDef,
    onPick: (packageName: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val apps = rememberInstalledApps()
    var query by remember { mutableStateOf("") }
    val rows by remember(apps, query, platform) {
        derivedStateOf { pickerRows(context, platform, apps, query.trim()) }
    }
    val installedHeader = stringResource(R.string.rift_emulator_group_installed)
    val downloadHeader = stringResource(R.string.rift_emulator_group_download)
    val downloadNote = stringResource(R.string.rift_emulator_download_hint)
    val allHeader = stringResource(R.string.rift_emulator_group_all)
    val playLabel = stringResource(R.string.rift_emulator_download_play)
    val siteLabel = stringResource(R.string.rift_emulator_download_site)

    RiftDialog(title = stringResource(R.string.rift_settings_pick_emulator), onDismiss = onDismiss) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = RiftSizes.button)
                .background(RiftColors.SurfaceContainerHigh, RiftShapes.full)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = RiftColors.OnSurfaceVariant, modifier = Modifier.size(22.dp))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(stringResource(R.string.rift_emulator_search_hint), color = RiftColors.OnSurfaceVariant, fontSize = 16.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it.take(40) },
                    singleLine = true,
                    textStyle = TextStyle(color = RiftColors.OnSurface, fontSize = 16.sp),
                    cursorBrush = SolidColor(RiftColors.OnSurface),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        val firstRow = remember { FocusRequester() }
        val firstKey = rows.firstOrNull { it !is PickerRow.Header }
        LaunchedEffect(firstKey == null) {
            if (firstKey != null) runCatching { firstRow.requestFocus() }
        }
        if (rows.isEmpty()) {
            Text(stringResource(R.string.rift_emulator_nothing_found), color = RiftColors.OnSurfaceVariant, fontSize = 15.sp)
        }
        LazyColumn(
            modifier = Modifier.heightIn(max = 380.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(rows, key = { row ->
                when (row) {
                    is PickerRow.Header -> "h:${row.title}"
                    is PickerRow.Installed -> "i:${row.packageName}"
                    is PickerRow.Download -> "d:${row.label}"
                }
            }) { row ->
                when (row) {
                    is PickerRow.Header -> Column(modifier = Modifier.padding(start = 6.dp, top = 6.dp)) {
                        Text(
                            text = when (row.title) {
                                HEADER_INSTALLED -> installedHeader
                                HEADER_DOWNLOAD -> downloadHeader
                                HEADER_ALL -> allHeader
                                else -> allHeader
                            },
                            color = RiftColors.OnSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        if (row.title == HEADER_DOWNLOAD) {
                            Text(downloadNote, color = RiftColors.OnSurfaceVariant, fontSize = 13.sp)
                        }
                    }
                    is PickerRow.Installed -> PickerAppRow(
                        label = row.label,
                        packageName = row.packageName,
                        trailing = null,
                        modifier = if (row == firstKey) Modifier.focusRequester(firstRow) else Modifier,
                        onClick = {
                            onPick(row.packageName)
                            onDismiss()
                        },
                    )
                    is PickerRow.Download -> PickerAppRow(
                        label = row.label,
                        packageName = null,
                        trailing = if (row.download.playId != null) playLabel else siteLabel,
                        modifier = if (row == firstKey) Modifier.focusRequester(firstRow) else Modifier,
                        onClick = { openEmulatorDownload(context, row.download) },
                    )
                }
            }
        }
    }
}

/**
 * The emulator choice as a page of the Emulators section (not a pop-up): the same lists as [EmulatorPickerDialog]
 * (installed for this system, downloads, every other app), on the settings plates with the app icons. The
 * caller handles Back by closing the page.
 */
@Composable
internal fun EmulatorPickerPage(platform: PlatformDef, onPick: (packageName: String) -> Unit) {
    val context = LocalContext.current
    val apps = rememberInstalledApps()
    var query by remember { mutableStateOf("") }
    val rows by remember(apps, query, platform) {
        derivedStateOf { pickerRows(context, platform, apps, query.trim()) }
    }
    val playLabel = stringResource(R.string.rift_emulator_download_play)
    val siteLabel = stringResource(R.string.rift_emulator_download_site)
    val firstRow = remember { FocusRequester() }
    val firstKey = rows.firstOrNull { it !is PickerRow.Header }
    LaunchedEffect(firstKey == null) {
        if (firstKey != null) runCatching { firstRow.requestFocus() }
    }

    SettingsGroup {
        PickerSearchField(query = query, onChange = { query = it })
        if (platform.noteRes != 0) NoteText(stringResource(platform.noteRes))
    }
    if (rows.isEmpty()) NoteText(stringResource(R.string.rift_emulator_nothing_found))

    // The flat list is cut at its headers: each header opens a group of plates.
    val sections = ArrayList<Pair<PickerRow.Header, MutableList<PickerRow>>>()
    for (row in rows) {
        if (row is PickerRow.Header) sections += row to ArrayList() else sections.lastOrNull()?.second?.add(row)
    }
    for ((header, items) in sections) {
        SettingsGroup(
            title = stringResource(
                when (header.title) {
                    HEADER_INSTALLED -> R.string.rift_emulator_group_installed
                    HEADER_DOWNLOAD -> R.string.rift_emulator_group_download
                    else -> R.string.rift_emulator_group_all
                },
            ),
        ) {
            items.forEach { row ->
                val mod = if (row == firstKey) Modifier.focusRequester(firstRow) else Modifier
                when (row) {
                    is PickerRow.Installed -> LinkRow(
                        modifier = mod,
                        title = row.label,
                        leading = { PickerIcon(row.packageName) },
                        onClick = { onPick(row.packageName) },
                    )
                    is PickerRow.Download -> LinkRow(
                        modifier = mod,
                        title = row.label,
                        leading = { PickerIcon(null) },
                        value = if (row.download.playId != null) playLabel else siteLabel,
                        onClick = { openEmulatorDownload(context, row.download) },
                    )
                    is PickerRow.Header -> Unit
                }
            }
            if (header.title == HEADER_DOWNLOAD) NoteText(stringResource(R.string.rift_emulator_download_hint))
        }
    }
}

/** The search field, on a plate like the settings rows. */
@Composable
private fun PickerSearchField(query: String, onChange: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .plateFill(focused)
            .heightIn(min = RiftSizes.button)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = plateSubtext(focused), modifier = Modifier.size(22.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(stringResource(R.string.rift_emulator_search_hint), color = plateSubtext(focused), fontSize = 16.sp)
            }
            BasicTextField(
                value = query,
                onValueChange = { onChange(it.take(40)) },
                singleLine = true,
                textStyle = TextStyle(color = plateText(focused), fontSize = 16.sp),
                cursorBrush = SolidColor(plateText(focused)),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            )
        }
    }
}

/** An app's icon at the start of a plate; a download arrow for an emulator that is not installed yet. */
@Composable
private fun PickerIcon(packageName: String?) {
    val context = LocalContext.current
    val icon by produceState<Bitmap?>(packageName?.let { AndroidGames.iconCached(it) }, packageName) {
        if (packageName != null && value == null) value = withContext(Dispatchers.IO) { AndroidGames.icon(context, packageName) }
    }
    Box(modifier = Modifier.size(40.dp).clip(RiftShapes.control), contentAlignment = Alignment.Center) {
        val bitmap = icon
        if (bitmap != null) {
            Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.size(40.dp))
        } else if (packageName == null) {
            Icon(Icons.Rounded.Download, contentDescription = null, tint = RiftColors.OnSurfaceVariant, modifier = Modifier.size(26.dp))
        }
    }
}

private const val HEADER_INSTALLED = "installed"
private const val HEADER_DOWNLOAD = "download"
private const val HEADER_ALL = "all"

private fun pickerRows(context: Context, platform: PlatformDef, apps: List<AndroidGame>, query: String): List<PickerRow> {
    val installedPackages = apps.map { it.packageName }
    val labels = apps.associate { it.packageName to it.label }
    fun matches(label: String) = query.isEmpty() || label.contains(query, ignoreCase = true)

    val known = platform.emulators.mapNotNull { emulator ->
        EmulatorCatalog.installedPackage(emulator, installedPackages)?.let { pkg -> PickerRow.Installed(pkg, labels[pkg] ?: emulator.label) }
    }.distinctBy { it.packageName }.filter { matches(it.label) }
    val knownPackages = known.map { it.packageName }.toSet()
    val downloads = platform.emulators
        .filter { emulator -> EmulatorCatalog.installedPackage(emulator, installedPackages) == null }
        .mapNotNull { emulator -> emulator.download?.let { PickerRow.Download(emulator.label, it) } }
        .filter { matches(it.label) }
    val others = apps
        .filter { it.packageName !in knownPackages && it.packageName != context.packageName }
        .filter { matches(it.label) }
        .sortedBy { it.label.lowercase() }
        .map { PickerRow.Installed(it.packageName, it.label) }

    return buildList {
        if (known.isNotEmpty()) {
            add(PickerRow.Header(HEADER_INSTALLED))
            addAll(known)
        }
        if (downloads.isNotEmpty()) {
            add(PickerRow.Header(HEADER_DOWNLOAD))
            addAll(downloads)
        }
        if (others.isNotEmpty()) {
            add(PickerRow.Header(HEADER_ALL))
            addAll(others)
        }
    }
}

@Composable
private fun PickerAppRow(
    label: String,
    packageName: String?,
    trailing: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val context = LocalContext.current
    val icon by produceState<Bitmap?>(packageName?.let { AndroidGames.iconCached(it) }, packageName) {
        if (packageName != null && value == null) value = withContext(Dispatchers.IO) { AndroidGames.icon(context, packageName) }
    }
    val contentColor = if (focused) RiftColors.OnPrimary else RiftColors.OnSurface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = RiftSizes.button)
            .clip(RiftShapes.full)
            .background(if (focused) RiftColors.Primary else RiftColors.SurfaceContainer)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(modifier = Modifier.size(36.dp).clip(RiftShapes.control), contentAlignment = Alignment.Center) {
            val bitmap = icon
            if (bitmap != null) {
                Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.size(36.dp))
            } else if (packageName == null) {
                Icon(Icons.Rounded.Download, contentDescription = null, tint = contentColor, modifier = Modifier.size(24.dp))
            }
        }
        Text(
            text = label,
            color = contentColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(text = trailing, color = if (focused) RiftColors.OnPrimary else RiftColors.OnSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        } else {
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = contentColor, modifier = Modifier.size(24.dp))
        }
    }
}
