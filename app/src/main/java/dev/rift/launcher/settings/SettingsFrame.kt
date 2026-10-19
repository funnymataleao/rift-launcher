package dev.rift.launcher.settings

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rift.launcher.ui.theme.RiftColors
import dev.rift.launcher.ui.theme.RiftTheme

/** The space from the screen's edge to everything on a settings screen: back arrow, list and page alike. */
internal val SettingsEdge = 48.dp

/** The gap between the section list and the page of the section. */
internal val PaneStart = 20.dp

/** Press feedback is the focus fill alone: no ripples and no state layers on anything inside. */
internal object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {}

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = -1
}

/**
 * THE frame of every settings screen (Rift's Settings, a game's container settings): the same screen
 * insets, one back arrow and ONE title at the same size and place (nothing small above it), then the
 * [content] below, whose edges are [SettingsEdge] too. No screen sets its own margins around this.
 *
 * [modifier] reaches the whole screen (key handlers); [onBack] is the arrow.
 */
@Composable
internal fun SettingsFrame(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    // A page beside the title column, from the very top: its first heading lines up with the title.
    pane: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    RiftTheme {
        CompositionLocalProvider(
            LocalIndication provides NoIndication,
            LocalContentColor provides RiftColors.OnSurface,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(RiftColors.Background)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .displayCutoutPadding()
                    .then(modifier),
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                Column(modifier = if (pane == null) Modifier.weight(1f).fillMaxHeight() else Modifier.fillMaxHeight()) {
                    Row(
                        modifier = Modifier
                            .then(if (pane == null) Modifier.fillMaxWidth() else Modifier)
                            .padding(start = SettingsEdge, end = if (pane == null) SettingsEdge else 12.dp, top = 12.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        RiftRoundButton(
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            description = title,
                            onClick = onBack,
                        )
                        Text(
                            text = title,
                            color = RiftColors.OnSurface,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                    content()
                }
                pane?.invoke(this)
                }
            }
        }
    }
}
