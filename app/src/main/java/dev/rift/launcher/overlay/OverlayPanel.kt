package dev.rift.launcher.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.os.Build
import android.view.Gravity
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import dev.rift.launcher.settings.PlateShape
import dev.rift.launcher.settings.RowIcon
import dev.rift.launcher.settings.RowMinHeight
import dev.rift.launcher.settings.RowTexts
import dev.rift.launcher.settings.plateFill
import dev.rift.launcher.settings.plateSubtext
import dev.rift.launcher.settings.plateText
import dev.rift.launcher.ui.theme.RiftAccent
import dev.rift.launcher.ui.theme.RiftColors
import dev.rift.launcher.ui.theme.RiftGlassTheme

/**
 * Rift's right-hand panel shown over another app: a full-height window of black glass that blurs the
 * game behind it where the device allows cross-window blur. B or a tap outside closes it.
 */
internal class OverlayPanel(
    private val service: AccessibilityService,
    private val gamePackage: String,
    private val onBackToRift: () -> Unit,
    private val onDismissed: () -> Unit,
) {
    private val dialog = ComponentDialog(service, android.R.style.Theme_Material_NoActionBar)

    fun show() {
        val density = service.resources.displayMetrics.density
        val window = dialog.window ?: return
        window.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnDismissListener { onDismissed() }
        dialog.setContentView(
            ComposeView(service).apply {
                setContent { RiftGlassTheme { PanelContent() } }
            },
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        val blur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (service.getSystemService(Context.WINDOW_SERVICE) as WindowManager).isCrossWindowBlurEnabled
        window.setBackgroundDrawable(ColorDrawable(if (blur) GLASS_OVER_BLUR else GLASS_SOLID))
        if (blur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) window.setBackgroundBlurRadius((48 * density).toInt())
        window.setDimAmount(0f)
        window.setWindowAnimations(R.style.RiftOverlayPanelAnimation)
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        window.setLayout((PANEL_WIDTH_DP * density).toInt(), ViewGroup.LayoutParams.MATCH_PARENT)
        window.setGravity(Gravity.END or Gravity.TOP)
        runCatching { dialog.show() }.onFailure { onDismissed() }
    }

    fun dismiss() {
        runCatching { dialog.dismiss() }
    }

    @Composable
    private fun PanelContent() {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
        val label = remember {
            runCatching {
                val pm = service.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(gamePackage, 0)).toString()
            }.getOrDefault(gamePackage)
        }
        val icon = remember { AndroidGames.icon(service, gamePackage) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
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
            VolumeRow()
        }
    }

    /** Media volume, changed with left and right like a Steam Deck slider; it applies at once. */
    @Composable
    private fun VolumeRow() {
        val audio = remember { service.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
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

    private companion object {
        const val PANEL_WIDTH_DP = 440
        val GLASS_OVER_BLUR = AndroidColor.argb(158, 8, 8, 8)
        val GLASS_SOLID = AndroidColor.argb(235, 8, 8, 8)
    }
}
