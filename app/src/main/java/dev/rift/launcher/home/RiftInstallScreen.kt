package dev.rift.launcher.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.gamenative.R
import app.gamenative.ui.data.GameDisplayInfo
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil.CoilImage
import dev.rift.launcher.settings.CheckRow
import dev.rift.launcher.settings.PlateGap
import dev.rift.launcher.settings.RiftPillButton
import dev.rift.launcher.settings.RiftRoundButton
import dev.rift.launcher.ui.theme.RiftColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ScreenPadding = 48.dp
private val HeroHeight = 300.dp

/** One game or DLC on the install screen. */
data class RiftInstallItem(
    val key: Int,
    val title: String,
    val subtitle: String?,
    val checked: Boolean,
    val enabled: Boolean,
    val onCheckedChange: (Boolean) -> Unit,
)

/**
 * The install screen of every store (Steam, Epic, GOG, Amazon): hero art with the title, the sizes, the
 * Install button and the components to pick, all on one left edge. Install takes focus as soon as it can,
 * so it shows in the accent colour; the stores only supply their numbers and items.
 */
@Composable
fun RiftInstallScreen(
    displayInfo: GameDisplayInfo,
    downloadSize: String,
    installSize: String?,
    availableSpace: String,
    installEnabled: Boolean,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
    items: List<RiftInstallItem> = emptyList(),
    note: String? = null,
    warning: String? = null,
    selectAll: Pair<String, () -> Unit>? = null,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
        ),
    ) {
        val installFocus = remember { FocusRequester() }
        var focusedOnce by remember { mutableStateOf(false) }
        LaunchedEffect(installEnabled) {
            if (installEnabled && !focusedOnce) {
                focusedOnce = runCatching { installFocus.requestFocus() }.isSuccess
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(RiftColors.Background)
                .verticalScroll(rememberScrollState()),
        ) {
            InstallHero(displayInfo, onDismiss)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = ScreenPadding, end = ScreenPadding, top = 8.dp, bottom = ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                    SizeStat(stringResource(R.string.rift_install_download_size), downloadSize)
                    if (installSize != null) SizeStat(stringResource(R.string.rift_install_size_on_disk), installSize)
                    SizeStat(stringResource(R.string.rift_install_available_space), availableSpace)
                }

                if (note != null) {
                    Text(text = note, color = RiftColors.Primary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                if (warning != null) {
                    Text(text = warning, color = RiftColors.Error, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RiftPillButton(
                        text = stringResource(R.string.install),
                        onClick = onInstall,
                        primary = true,
                        icon = Icons.Rounded.Download,
                        enabled = installEnabled,
                        modifier = Modifier.focusRequester(installFocus),
                    )
                    if (selectAll != null) {
                        RiftPillButton(text = selectAll.first, onClick = selectAll.second)
                    }
                }

                if (items.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = stringResource(R.string.rift_install_components),
                            color = RiftColors.OnSurface,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(PlateGap)) {
                            items.forEach { item ->
                                CheckRow(
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    checked = item.checked,
                                    enabled = item.enabled,
                                    onCheckedChange = item.onCheckedChange,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstallHero(displayInfo: GameDisplayInfo, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HeroHeight),
    ) {
        if (displayInfo.heroImageUrl != null) {
            CoilImage(
                modifier = Modifier.fillMaxSize(),
                imageModel = { displayInfo.heroImageUrl },
                imageOptions = ImageOptions(contentScale = ContentScale.Crop),
                failure = { Box(Modifier.fillMaxSize().background(RiftColors.SurfaceContainer)) },
            )
        } else {
            Box(Modifier.fillMaxSize().background(RiftColors.SurfaceContainer))
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.35f),
                        0.45f to Color.Transparent,
                        1f to RiftColors.Background,
                    ),
                ),
        )

        RiftRoundButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            description = stringResource(R.string.rift_install_back),
            onClick = onBack,
            modifier = Modifier.padding(start = ScreenPadding, top = 32.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = ScreenPadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = displayInfo.name,
                color = Color.White,
                fontSize = 40.sp,
                lineHeight = 46.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            val year = remember(displayInfo.releaseDate) {
                if (displayInfo.releaseDate > 0) {
                    SimpleDateFormat("yyyy", Locale.getDefault()).format(Date(displayInfo.releaseDate * 1000))
                } else {
                    ""
                }
            }
            val subtitle = listOf(displayInfo.developer, year).filter { it.isNotBlank() }.joinToString(" • ")
            if (subtitle.isNotEmpty()) {
                Text(text = subtitle, color = RiftColors.OnSurfaceVariant, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SizeStat(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, color = RiftColors.OnSurfaceVariant, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(text = value, color = RiftColors.OnSurface, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}
