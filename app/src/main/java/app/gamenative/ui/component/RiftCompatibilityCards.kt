package app.gamenative.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.gamenative.R
import app.gamenative.data.CommunityCompatibilitySummary
import app.gamenative.ui.component.dialog.CommunityHardwareScope
import dev.rift.launcher.community.RiftBestConfig
import dev.rift.launcher.ui.theme.RiftColors
import dev.rift.launcher.ui.theme.RiftSizes
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Rift: the PC Games compatibility block as three cards of one height (community verdict, the
 * game's own profile, the best community profile for this device). The full list of profiles and
 * reports with its filters stays in the profiles screen, opened from "Все отчёты" / "Все профили".
 */
@Composable
internal fun RiftCompatibilityCards(
    summary: CommunityCompatibilitySummary,
    loading: Boolean,
    loadError: Boolean,
    onRetry: () -> Unit,
    onViewReports: () -> Unit,
    gpuRuns: Int?,
    profile: List<Pair<String, String>>?,
    profileFromCommunity: Boolean,
    onEditProfile: () -> Unit,
    onRollback: () -> Unit,
    best: RiftBestConfig?,
    bestLoading: Boolean,
    onApplyBest: () -> Unit,
    wide: Boolean,
    modifier: Modifier = Modifier,
) {
    val cards: List<@Composable (Modifier) -> Unit> = listOf(
        { m -> CommunityVerdictCard(summary, loading, loadError, onRetry, onViewReports, gpuRuns, m) },
        { m -> MyProfileCard(profile, profileFromCommunity, onEditProfile, onRollback, m) },
        { m -> BestProfileCard(best, bestLoading, onApplyBest, onViewReports, m) },
    )
    if (wide) {
        Row(
            modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            cards.forEach { it(Modifier.weight(1f).fillMaxHeight()) }
        }
    } else {
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            cards.forEach { it(Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun CompatCard(
    modifier: Modifier,
    background: Brush = Brush.linearGradient(listOf(RiftColors.Surface, RiftColors.Surface)),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .heightIn(min = 360.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(RiftColors.Surface)
            .background(background)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun CardLabel(text: String, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = RiftColors.OnSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke(this)
    }
}

@Composable
private fun CardChip(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun CardRow(label: String, value: String, divider: Boolean) {
    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = RiftColors.OnSurfaceVariant, modifier = Modifier.weight(1f))
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = RiftColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        if (divider) HorizontalDivider(color = RiftColors.OutlineVariant)
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), color = RiftColors.OnSurface)
        Text(label, style = MaterialTheme.typography.labelMedium, color = RiftColors.OnSurfaceVariant)
    }
}

/** Card button: a pill of the Rift button height, accent-filled when it is the card's main action. */
@Composable
private fun CardButton(text: String, onClick: () -> Unit, modifier: Modifier, primary: Boolean = false, enabled: Boolean = true) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(50)
    // Rift: two states (Denis 17.10): the button in focus is filled with the accent, every other one is grey.
    val container = if (focused) dev.rift.launcher.ui.theme.RiftAccent.current.brush else androidx.compose.ui.graphics.SolidColor(Color.White.copy(alpha = 0.1f))
    Box(
        modifier = modifier
            .height(RiftSizes.button)
            .clip(shape)
            .background(container, alpha = if (enabled) 1f else 0.4f)
            .selectable(
                selected = focused,
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (primary) FontWeight.ExtraBold else FontWeight.SemiBold),
            color = when {
                focused -> RiftColors.OnPrimary
                enabled -> RiftColors.OnSurface
                else -> RiftColors.OnSurfaceVariant
            },
            maxLines = 1,
        )
    }
}

@Composable
private fun CommunityVerdictCard(
    summary: CommunityCompatibilitySummary,
    loading: Boolean,
    loadError: Boolean,
    onRetry: () -> Unit,
    onViewReports: () -> Unit,
    gpuRuns: Int?,
    modifier: Modifier,
) {
    val style = getCommunityBadgeStyle(summary.verdict, compact = false)
    val tint = if (summary.verdictLoaded) style.iconTint else RiftColors.OnSurfaceVariant
    CompatCard(
        modifier = modifier,
        background = Brush.linearGradient(listOf(tint.copy(alpha = 0.18f), Color.Transparent)),
    ) {
        CardLabel(stringResource(R.string.rift_compat_community)) {
            if (loading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = RiftColors.OnSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                imageVector = if (summary.verdictLoaded) style.icon else Icons.Rounded.HelpOutline,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(32.dp),
            )
            Text(
                text = stringResource(
                    when {
                        summary.verdictLoaded -> style.labelResId
                        loading && !summary.loadFailed -> R.string.community_compatibility_checking
                        else -> R.string.community_compatibility_unavailable
                    },
                ),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = RiftColors.OnSurface,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(summary.medianFps?.toString() ?: "–", stringResource(R.string.rift_compat_median_fps), Modifier.weight(1f))
            StatTile(summary.sessionCount.toString(), stringResource(R.string.rift_compat_sessions), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(summary.reportCount.toString(), stringResource(R.string.rift_compat_reports), Modifier.weight(1f))
            StatTile((gpuRuns ?: 0).toString(), stringResource(R.string.rift_compat_on_gpu), Modifier.weight(1f))
        }
        Text(
            text = stringResource(R.string.community_compatibility_short_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = RiftColors.OnSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        if (!loading && loadError) {
            CardButton(stringResource(R.string.community_compatibility_retry), onRetry, Modifier.fillMaxWidth())
        } else {
            CardButton(stringResource(R.string.rift_compat_all_reports), onViewReports, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun MyProfileCard(
    profile: List<Pair<String, String>>?,
    fromCommunity: Boolean,
    onEdit: () -> Unit,
    onRollback: () -> Unit,
    modifier: Modifier,
) {
    CompatCard(modifier = modifier) {
        CardLabel(stringResource(R.string.rift_compat_my_profile)) {
            if (fromCommunity) CardChip(stringResource(R.string.rift_compat_from_community), RiftColors.OnSurfaceVariant)
        }
        if (profile == null) {
            Text(
                text = stringResource(R.string.rift_compat_no_profile),
                style = MaterialTheme.typography.bodyMedium,
                color = RiftColors.OnSurfaceVariant,
            )
        } else {
            Column {
                profile.forEachIndexed { index, (label, value) -> CardRow(label, value, divider = index < profile.lastIndex) }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CardButton(stringResource(R.string.rift_compat_edit), onEdit, Modifier.weight(1f), enabled = profile != null)
            CardButton(stringResource(R.string.rift_compat_rollback), onRollback, Modifier.weight(1f), enabled = fromCommunity)
        }
    }
}

@Composable
private fun BestProfileCard(
    best: RiftBestConfig?,
    loading: Boolean,
    onApply: () -> Unit,
    onAllProfiles: () -> Unit,
    modifier: Modifier,
) {
    CompatCard(modifier = modifier) {
        CardLabel(stringResource(R.string.rift_compat_best)) {
            when {
                loading -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = RiftColors.OnSurfaceVariant)
                best != null -> CardChip(
                    stringResource(
                        when (best.scope) {
                            CommunityHardwareScope.CURRENT_DEVICE -> R.string.rift_compat_scope_device
                            CommunityHardwareScope.CURRENT_GPU -> R.string.rift_compat_scope_gpu
                            CommunityHardwareScope.COMPATIBLE_GPUS -> R.string.rift_compat_scope_family
                        },
                    ).uppercase(Locale.getDefault()),
                    if (best.scope == CommunityHardwareScope.COMPATIBLE_GPUS) RiftColors.Warning else RiftColors.Success,
                )
            }
        }
        if (best == null) {
            Text(
                text = stringResource(if (loading) R.string.rift_compat_best_loading else R.string.rift_compat_best_none),
                style = MaterialTheme.typography.bodyMedium,
                color = RiftColors.OnSurfaceVariant,
            )
        } else {
            val run = best.run
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = run.rating.toString(),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        brush = dev.rift.launcher.ui.theme.RiftAccent.current.brush,
                    ),
                )
                Text(
                    text = listOfNotNull(
                        stringResource(R.string.rift_compat_of_five),
                        run.averageFps?.let { stringResource(R.string.rift_compat_fps_value, it.toInt()) },
                        run.sessionLengthSeconds?.takeIf { it > 0 }?.let { formatSession(it) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = RiftColors.OnSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            val rows = listOf(
                stringResource(R.string.rift_compat_device) to run.device.model,
                stringResource(R.string.community_config_wine) to run.configString("wineVersion"),
                stringResource(R.string.community_config_wrapper) to run.configString("dxwrapper"),
                stringResource(R.string.rift_compat_added) to formatDate(run.createdAt),
            ).filter { it.second.isNotBlank() }
            Column {
                rows.forEachIndexed { index, (label, value) -> CardRow(label, value, divider = index < rows.lastIndex) }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CardButton(stringResource(R.string.rift_compat_apply), onApply, Modifier.weight(1f), primary = best != null, enabled = best != null)
            CardButton(stringResource(R.string.rift_compat_all_profiles), onAllProfiles, Modifier.weight(1f))
        }
    }
}

@Composable
private fun formatSession(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) {
        stringResource(R.string.rift_compat_session_hm, hours.toInt(), minutes.toInt())
    } else {
        stringResource(R.string.rift_compat_session_m, minutes.toInt().coerceAtLeast(1))
    }
}

private fun formatDate(value: String): String {
    if (value.isBlank()) return ""
    return runCatching {
        OffsetDateTime.parse(value)
            .atZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }.getOrDefault(value.substringBefore('T'))
}
