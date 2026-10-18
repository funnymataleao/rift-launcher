package dev.rift.launcher.settings

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rift.launcher.ui.theme.RiftColors
import dev.rift.launcher.ui.theme.RiftTheme

/** One section in the sidebar of [RiftSidebarScreen]. */
internal class SidebarEntry(val title: String, val icon: ImageVector)

/**
 * A full screen of sections as Rift's own Settings: the list on the left (the chosen one dotted, L1 and R1
 * switch), the page of the section on the right, and only a back arrow at the top. Back (or B) goes from the
 * page to the list, and from the list out. [content] is the page of the selected section.
 */
@Composable
internal fun RiftSidebarScreen(
    title: String,
    entries: List<SidebarEntry>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val railItems = remember(entries.size) { List(entries.size) { FocusRequester() } }
    val contentEntry = remember { FocusRequester() }
    var contentHasFocus by remember { mutableStateOf(false) }

    BackHandler {
        if (contentHasFocus) {
            runCatching { railItems[selectedIndex].requestFocus() }
        } else {
            onBack()
        }
    }

    SettingsFrame(
        title = title,
        onBack = onBack,
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val step = when (event.key.nativeKeyCode) {
                KeyEvent.KEYCODE_BUTTON_L1 -> -1
                KeyEvent.KEYCODE_BUTTON_R1 -> 1
                else -> return@onPreviewKeyEvent false
            }
            val next = (selectedIndex + step).mod(entries.size)
            onSelect(next)
            runCatching { railItems[next].requestFocus() }
            true
        },
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Rail(
                entries = entries,
                selectedIndex = selectedIndex,
                railItems = railItems,
                contentEntry = contentEntry,
                onFocused = onSelect,
                onSelect = { index ->
                    onSelect(index)
                    runCatching { contentEntry.requestFocus() }
                },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = PaneStart, end = SettingsEdge),
            ) {
                key(selectedIndex) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .focusRequester(contentEntry)
                            .onFocusChanged { contentHasFocus = it.hasFocus }
                            .focusGroup()
                            // After the group, so it reaches the rows inside: Left always goes back to the
                            // selected section in the list, like B.
                            .focusProperties { left = railItems[selectedIndex] },
                    ) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = ContentMaxWidth)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(top = 16.dp, bottom = 56.dp, start = 4.dp, end = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(GroupGap),
                        ) {
                            content()
                            PageEnd()
                        }
                    }
                }
            }
        }
    }

    // Start on the list, so a gamepad works without touching the screen.
    LaunchedEffect(Unit) { runCatching { railItems[selectedIndex].requestFocus() } }
}

@Composable
private fun Rail(
    entries: List<SidebarEntry>,
    selectedIndex: Int,
    railItems: List<FocusRequester>,
    contentEntry: FocusRequester,
    onFocused: (Int) -> Unit,
    onSelect: (Int) -> Unit,
) {
    // As wide as the longest title, like the list in Settings.
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val rowStyle = LocalTextStyle.current.merge(TextStyle(fontSize = 18.5.sp, fontWeight = RailSelectedWeight))
    val railWidth = with(density) {
        val rows = entries.maxOf { measurer.measure(it.title, rowStyle, maxLines = 1, softWrap = false).size.width.toDp() }
        RailInset + (RailItemHeight - RailInset * 2) + RailGap + rows + RailGap + RailDot + RailEndInset
    }
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(start = SettingsEdge, end = 12.dp)
            .width(railWidth)
            .verticalScroll(rememberScrollState())
            .focusGroup()
            .padding(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        entries.forEachIndexed { index, entry ->
            RailItem(
                title = entry.title,
                icon = entry.icon,
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
