package dev.rift.launcher.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.R
import dev.rift.launcher.home.AndroidGames
import dev.rift.launcher.settings.LinkRow
import dev.rift.launcher.settings.PlateGap
import dev.rift.launcher.settings.PlateShape
import dev.rift.launcher.settings.RowIcon
import dev.rift.launcher.settings.RowMinHeight
import dev.rift.launcher.settings.RowTexts
import dev.rift.launcher.settings.plateFill
import dev.rift.launcher.settings.plateSubtext
import dev.rift.launcher.settings.plateText
import dev.rift.launcher.ui.LocalWindowBlur
import dev.rift.launcher.ui.RiftDrawerPanel
import dev.rift.launcher.ui.layOutAsDrawer
import dev.rift.launcher.ui.theme.RiftAccent
import dev.rift.launcher.ui.theme.RiftColors
import dev.rift.launcher.ui.theme.RiftGlassTheme

/**
 * Rift's right-hand panel shown over another app: a full-height window in the frame and glass of every
 * Rift panel ([RiftDrawerPanel]). B or a tap outside closes it.
 */
internal class OverlayPanel(
    private val service: AccessibilityService,
    private val gamePackage: String,
    private val onBackToRift: () -> Unit,
    private val onCloseGame: () -> Unit,
    private val onGameMenu: () -> Unit,
    private val onDismissed: () -> Unit,
) {
    private val dialog = ComponentDialog(service, android.R.style.Theme_Material_NoActionBar)

    fun show() {
        val window = dialog.window ?: return
        window.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnDismissListener { onDismissed() }
        // Another app is behind the panel and can't be copied: the window blurs it where Android allows
        // that, otherwise the glass is solid.
        var windowBlur by mutableStateOf(false)
        dialog.setContentView(
            ComposeView(service).apply {
                setContent {
                    RiftGlassTheme {
                        CompositionLocalProvider(LocalWindowBlur provides windowBlur) {
                            RiftDrawerPanel(modifier = Modifier.fillMaxSize()) {
                                OverlayPanelBody(service, gamePackage, onBackToRift, onCloseGame, onGameMenu)
                            }
                        }
                    }
                }
            },
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        window.layOutAsDrawer(service) { windowBlur = it }
        runCatching { dialog.show() }.onFailure { onDismissed() }
    }

    fun dismiss() {
        runCatching { dialog.dismiss() }
    }
}

/** The panel's rows, shared by the accessibility overlay and the fallback activity [RiftPanelActivity]. */
@Composable
internal fun OverlayPanelBody(
    context: Context,
    gamePackage: String,
    onBackToRift: () -> Unit,
    onCloseGame: () -> Unit,
    onGameMenu: (() -> Unit)?,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val label = remember {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(gamePackage, 0)).toString()
        }.getOrDefault(gamePackage)
    }
    val icon = remember { AndroidGames.icon(context, gamePackage) }

    Column(verticalArrangement = Arrangement.spacedBy(PlateGap)) {
        Row(
            modifier = Modifier.padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.size(14.dp))
            }
            Text(
                text = label,
                color = RiftColors.OnSurface,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LinkRow(
            title = stringResource(R.string.rift_overlay_back_to_rift),
            subtitle = stringResource(R.string.rift_overlay_back_to_rift_summary),
            icon = Icons.Rounded.Home,
            onClick = onBackToRift,
            modifier = Modifier.focusRequester(first),
        )
        if (onGameMenu != null) {
            LinkRow(
                title = stringResource(R.string.rift_overlay_game_menu),
                subtitle = stringResource(R.string.rift_overlay_game_menu_summary),
                icon = Icons.Rounded.Menu,
                onClick = onGameMenu,
            )
        }
        CloseGameRow(onCloseGame)
        VolumeRow(context)
    }
}

/** Ends the game and its emulator. Closing loses unsaved progress, so the first press only asks and the second closes. */
@Composable
private fun CloseGameRow(onCloseGame: () -> Unit) {
    var asked by remember { mutableStateOf(false) }
    LinkRow(
        title = stringResource(R.string.rift_overlay_close_game),
        subtitle = stringResource(if (asked) R.string.rift_overlay_close_game_confirm else R.string.rift_overlay_close_game_summary),
        icon = Icons.Rounded.PowerSettingsNew,
        danger = asked,
        onClick = { if (asked) onCloseGame() else asked = true },
        modifier = Modifier.onFocusChanged { if (!it.isFocused) asked = false },
    )
}

/** Media volume, changed with left and right like a Steam Deck slider; it applies at once. */
@Composable
private fun VolumeRow(context: Context) {
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var level by remember { mutableIntStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    fun set(value: Int) {
        level = value.coerceIn(0, max)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .plateFill(focused)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key.nativeKeyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { set(level - 1); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { set(level + 1); true }
                    else -> false
                }
            }
            .focusable(interactionSource = interaction)
            .heightIn(min = RowMinHeight)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            RowIcon(Icons.AutoMirrored.Rounded.VolumeUp, focused)
            RowTexts(stringResource(R.string.rift_overlay_volume), null, danger = false, focused = focused, modifier = Modifier.weight(1f))
            Text(
                text = "${level * 100 / max}%",
                color = plateSubtext(focused),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        val track = if (focused) RiftAccent.current.onAccent else RiftColors.OnSurface
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(PlateShape)
                .background(track.copy(alpha = 0.18f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(level.toFloat() / max)
                    .fillMaxHeight()
                    .clip(PlateShape)
                    .background(if (focused) plateText(true) else RiftAccent.current.main),
            )
        }
    }
}
