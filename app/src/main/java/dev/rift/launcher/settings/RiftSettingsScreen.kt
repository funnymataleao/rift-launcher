package dev.rift.launcher.settings

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.R
import dev.rift.launcher.ui.theme.RiftColors
import dev.rift.launcher.ui.theme.RiftMotion
import dev.rift.launcher.ui.theme.RiftShapes
import dev.rift.launcher.ui.theme.RiftSizes
import dev.rift.launcher.ui.theme.RiftTheme

private val RailWidth = 372.dp
private val ContentMaxWidth = 860.dp

/**
 * Rift's settings: a section list on the left (icon + name) and the rows of the chosen section on
 * the right, instead of GameNative's single long list.
 *
 * [onOpenPcPairing] opens the screen that finds and pairs a PC; the streaming section shows its entry row
 * only when it is given.
 *
 * Gamepad: up/down moves through the sections and the right pane follows, right enters the rows,
 * left comes back to the section list, LB/RB switch sections, A presses and B steps back (rows first,
 * then the screen). Every row reads and writes the same [app.gamenative.PrefManager] values GameNative
 * itself uses, so nothing set here is lost when going back to GameNative's own screens.
 */
@Composable
fun RiftSettingsScreen(
    onBack: () -> Unit,
    onOpenPcPairing: (() -> Unit)? = null,
    onSteamSignIn: (() -> Unit)? = null,
    onSteamSignOut: (() -> Unit)? = null,
) {
    val sections = remember { RiftSettingsSection.entries }
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val selected = sections[selectedIndex]

    val railItems = remember { List(sections.size) { FocusRequester() } }
    val contentEntry = remember { FocusRequester() }
    var contentHasFocus by remember { mutableStateOf(false) }

    BackHandler {
        if (contentHasFocus) {
            runCatching { railItems[selectedIndex].requestFocus() }
        } else {
            onBack()
        }
    }

    RiftTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(RiftColors.Background)
                .statusBarsPadding()
                .displayCutoutPadding()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val step = when (event.key.nativeKeyCode) {
                        KeyEvent.KEYCODE_BUTTON_L1 -> -1
                        KeyEvent.KEYCODE_BUTTON_R1 -> 1
                        else -> return@onPreviewKeyEvent false
                    }
                    selectedIndex = (selectedIndex + step).mod(sections.size)
                    runCatching { railItems[selectedIndex].requestFocus() }
                    true
                },
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                SectionRail(
                    sections = sections,
                    selectedIndex = selectedIndex,
                    railItems = railItems,
                    contentEntry = contentEntry,
                    onFocused = { selectedIndex = it },
                    onSelect = { index ->
                        selectedIndex = index
                        runCatching { contentEntry.requestFocus() }
                    },
                    onBack = onBack,
                )
                SectionPane(
                    section = selected,
                    contentEntry = contentEntry,
                    returnTo = railItems[selectedIndex],
                    onOpenPcPairing = onOpenPcPairing,
                    onSteamSignIn = onSteamSignIn,
                    onSteamSignOut = onSteamSignOut,
                    onContentFocus = { contentHasFocus = it },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    // Start with the section list focused so a gamepad works without touching the screen.
    LaunchedEffect(Unit) { runCatching { railItems[selectedIndex].requestFocus() } }
}

@Composable
private fun SectionRail(
    sections: List<RiftSettingsSection>,
    selectedIndex: Int,
    railItems: List<FocusRequester>,
    contentEntry: FocusRequester,
    onFocused: (Int) -> Unit,
    onSelect: (Int) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(start = 32.dp, end = 12.dp)
            .widthIn(min = RailWidth, max = RailWidth)
            .verticalScroll(rememberScrollState())
            .focusGroup()
            .padding(top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(bottom = 14.dp),
        ) {
            RiftRoundButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                description = stringResource(R.string.back),
                onClick = onBack,
            )
            Text(
                text = stringResource(R.string.rift_settings),
                color = RiftColors.OnSurface,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        sections.forEachIndexed { index, section ->
            if (index == 0 || sections[index - 1].group != section.group) {
                Text(
                    text = stringResource(section.group.titleRes).uppercase(),
                    color = RiftColors.OnSurfaceVariant,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(start = 18.dp, top = if (index == 0) 4.dp else 22.dp, bottom = 6.dp),
                )
            }
            RailItem(
                section = section,
                selected = index == selectedIndex,
                modifier = Modifier
                    .focusRequester(railItems[index])
                    .focusProperties { right = contentEntry }
                    .onFocusChanged { if (it.isFocused) onFocused(index) },
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun RailItem(
    section: RiftSettingsSection,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        if (focused) 1.04f else 1f,
        RiftMotion.focusSpring(),
        label = "railScale",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(RiftSizes.button)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RiftShapes.full)
            .background(
                when {
                    selected -> RiftColors.SurfaceContainerHigh
                    focused -> RiftColors.SurfaceContainer
                    else -> Color.Transparent
                },
            )
            .border(if (focused) 2.dp else 0.dp, if (focused) Color.White.copy(alpha = 0.9f) else Color.Transparent, RiftShapes.full)
            .clickable(interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(section.accent.color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = section.icon,
                contentDescription = null,
                tint = section.accent.color,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = stringResource(section.titleRes),
            color = if (selected) RiftColors.OnSurface else RiftColors.OnSurfaceVariant,
            fontSize = 18.5.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(RiftColors.Primary),
            )
        }
    }
}

@Composable
private fun SectionPane(
    section: RiftSettingsSection,
    contentEntry: FocusRequester,
    returnTo: FocusRequester,
    onOpenPcPairing: (() -> Unit)?,
    onSteamSignIn: (() -> Unit)?,
    onSteamSignOut: (() -> Unit)?,
    onContentFocus: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(start = 20.dp, end = 48.dp),
    ) {
        Row(
            modifier = Modifier.padding(top = 26.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SectionBadge(section.icon, size = 68.dp, accent = section.accent)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(section.titleRes),
                    color = RiftColors.OnSurface,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = stringResource(section.summaryRes),
                    color = RiftColors.OnSurfaceVariant,
                    fontSize = 16.5.sp,
                )
            }
        }
        key(section) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .focusRequester(contentEntry)
                    .focusProperties { left = returnTo }
                    .onFocusChanged { onContentFocus(it.hasFocus) }
                    .focusGroup(),
            ) {
                CompositionLocalProvider(LocalSettingsAccent provides section.accent) {
                Column(
                    modifier = Modifier
                        .widthIn(max = ContentMaxWidth)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 6.dp, bottom = 32.dp, start = 4.dp, end = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(34.dp),
                ) {
                    when (section) {
                        RiftSettingsSection.Sound -> SoundSettings()
                        RiftSettingsSection.Home -> HomeSettings()
                        RiftSettingsSection.Interface -> InterfaceSettings()
                        RiftSettingsSection.Controller -> ControllerSettings()
                        RiftSettingsSection.Display -> DisplaySettings()
                        RiftSettingsSection.Graphics -> GraphicsSettings()
                        RiftSettingsSection.Launch -> LaunchSettings()
                        RiftSettingsSection.AndroidGames -> AndroidGamesSettings()
                        RiftSettingsSection.Emulators -> EmulatorsSettings()
                        RiftSettingsSection.Streaming -> StreamingSettings(onOpenPcPairing)
                        RiftSettingsSection.Cloud -> CloudSettings()
                        RiftSettingsSection.Downloads -> DownloadsSettings()
                        RiftSettingsSection.Tabs -> TabsSettings()
                        RiftSettingsSection.Accounts -> AccountsSettings(onSteamSignIn, onSteamSignOut)
                        RiftSettingsSection.About -> AboutSettings()
                        RiftSettingsSection.Debug -> DebugSettings()
                    }
                }
                }
            }
        }
    }
}
