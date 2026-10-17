package dev.rift.launcher.settings

import dev.rift.launcher.sound.riftKeySounds
import dev.rift.launcher.ui.theme.RiftAccent
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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

internal val RailInset = 8.dp
internal val RailItemHeight = 52.dp
internal val RailGap = 14.dp
internal val RailDot = 8.dp
internal val RailEndInset = 14.dp
private val CaptionInset = 18.dp
internal val ContentMaxWidth = 860.dp

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
    var selectedIndex by rememberSaveable { mutableIntStateOf((RiftSettingsSection.takeRequested() ?: RiftSettingsSection.AndroidGames).ordinal) }
    val selected = sections[selectedIndex]

    val railItems = remember { List(sections.size) { FocusRequester() } }
    val contentEntry = remember { FocusRequester() }
    var contentHasFocus by remember { mutableStateOf(false) }

    // Going to another app (a browser for a key, say) leaves a mark with this section: if the system drops
    // Rift meanwhile, it comes back here. Coming back to this screen alive, or leaving it by Back, clears it.
    val context = LocalContext.current
    val currentSection = rememberUpdatedState(selected)
    DisposableEffect(context) {
        val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<ComponentActivity>().firstOrNull()
        val lifecycle = activity?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> SettingsReturn.mark(context, currentSection.value)
                Lifecycle.Event.ON_START -> SettingsReturn.clear(context)
                else -> Unit
            }
        }
        lifecycle?.addObserver(observer)
        onDispose {
            lifecycle?.removeObserver(observer)
            // Left through Back (the activity is still on screen); a destroyed activity keeps the mark.
            if (lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true) SettingsReturn.clear(context)
        }
    }

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
                .navigationBarsPadding()
                .displayCutoutPadding()
                .riftKeySounds(panelKeys = false, touch = true)
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
    LaunchedEffect(Unit) {
        // After a restart the focus goes into the page, where the person was.
        val target = if (SettingsReturn.focusContentOnOpen) contentEntry else railItems[selectedIndex]
        SettingsReturn.focusContentOnOpen = false
        runCatching { target.requestFocus() }.onFailure { runCatching { railItems[selectedIndex].requestFocus() } }
    }
}

@Composable
private fun SectionRail(
    sections: List<RiftSettingsSection>,
    selectedIndex: Int,
    railItems: List<FocusRequester>,
    contentEntry: FocusRequester,
    onFocused: (Int) -> Unit,
    onSelect: (Int) -> Unit,
) {
    // As wide as the longest row (its name set in the selected weight, plus the icon, the dot and the
    // insets), so the sidebar takes no more room than it needs.
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val rowStyle = TextStyle(fontSize = 18.5.sp, fontWeight = FontWeight.ExtraBold)
    val captionStyle = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
    val titles = sections.map { stringResource(it.titleRes) }
    val captions = sections.map { stringResource(it.group.titleRes).uppercase() }
    val railWidth = with(density) {
        val rows = titles.maxOf { measurer.measure(it, rowStyle, maxLines = 1).size.width.toDp() }
        val caption = captions.maxOf { measurer.measure(it, captionStyle, maxLines = 1).size.width.toDp() }
        maxOf(
            RailInset + (RailItemHeight - RailInset * 2) + RailGap + rows + RailGap + RailDot + RailEndInset,
            CaptionInset + caption + RailEndInset,
        )
    }
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(start = 32.dp, end = 12.dp)
            .width(railWidth)
            .verticalScroll(rememberScrollState())
            .focusGroup()
            .padding(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        sections.forEachIndexed { index, section ->
            if (index == 0 || sections[index - 1].group != section.group) {
                Text(
                    text = stringResource(section.group.titleRes).uppercase(),
                    color = RiftColors.OnSurfaceVariant,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(start = CaptionInset, top = if (index == 0) 4.dp else 22.dp, bottom = 6.dp),
                )
            }
            RailItem(
                title = stringResource(section.titleRes),
                icon = section.icon,
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
internal fun RailItem(
    title: String,
    icon: ImageVector,
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
    val colors = iconColors(focused)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(RailItemHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .plateFill(
                focused = focused,
                rest = if (selected) RiftColors.SurfaceContainerHigh else Color.Transparent,
            )
            .clickable(interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            // The icon disc fills the height minus one inset, so left, top and bottom are the same.
            .padding(start = RailInset, end = RailEndInset, top = RailInset, bottom = RailInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RailGap),
    ) {
        Box(
            modifier = Modifier
                .size(RailItemHeight - RailInset * 2)
                .clip(CircleShape)
                .background(colors.disc),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.symbol,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = title,
            color = if (focused) plateText(true) else if (selected) RiftColors.OnSurface else RiftColors.OnSurfaceVariant,
            fontSize = 18.5.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .size(RailDot)
                    .clip(CircleShape)
                    .background(if (focused) SolidColor(plateText(true)) else RiftAccent.current.brush),
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
        key(section) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .focusRequester(contentEntry)
                    .onFocusChanged { onContentFocus(it.hasFocus) }
                    .focusGroup()
                    // After the group, so it reaches the rows inside: Left always goes back to the
                    // selected section in the list, like B, not to whatever sits across from the row.
                    .focusProperties { left = returnTo },
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = ContentMaxWidth)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 16.dp, bottom = 56.dp, start = 4.dp, end = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(GroupGap),
                ) {
                    when (section) {
                        RiftSettingsSection.Sound -> SoundSettings()
                        RiftSettingsSection.Home -> HomeSettings()
                        RiftSettingsSection.Artwork -> ArtworkSettings()
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
                        RiftSettingsSection.HiddenGames -> HiddenGamesSettings()
                        RiftSettingsSection.Accounts -> AccountsSettings(onSteamSignIn, onSteamSignOut)
                        RiftSettingsSection.Assistant -> AssistantSettings()
                        RiftSettingsSection.Device -> DeviceSettings()
                        RiftSettingsSection.About -> AboutSettings()
                        RiftSettingsSection.Debug -> DebugSettings()
                    }
                }
            }
        }
    }
}
