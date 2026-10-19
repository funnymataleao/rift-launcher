package app.gamenative.ui.screen.library.components

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AirplaneTicket
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.rift.launcher.settings.RiftRadioMark
import dev.rift.launcher.settings.RiftCheckMark
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.rotate
import app.gamenative.PluviaApp
import app.gamenative.PrefManager
import app.gamenative.R
import app.gamenative.data.SteamFriend
import app.gamenative.events.SteamEvent
import app.gamenative.service.SteamService
import app.gamenative.ui.component.dialog.SupportersDialog
import app.gamenative.ui.screen.PluviaScreen
import app.gamenative.ui.screen.support.SupportReplyWatcher
import app.gamenative.ui.theme.PluviaTheme
import app.gamenative.ui.util.SteamIconImage
import app.gamenative.ui.util.adaptivePanelWidth
import app.gamenative.ui.util.shouldShowGamepadUI
import app.gamenative.utils.getAvatarURL
import `in`.dragonbra.javasteam.enums.EPersonaState
import dev.rift.launcher.settings.PlateGap
import dev.rift.launcher.settings.PlateShape
import dev.rift.launcher.settings.RowIcon
import dev.rift.launcher.settings.RowIconSize
import dev.rift.launcher.settings.RowTitleStyle
import dev.rift.launcher.settings.RowTexts
import dev.rift.launcher.settings.plateFill
import dev.rift.launcher.settings.plateSubtext
import dev.rift.launcher.settings.plateText
import dev.rift.launcher.ui.BlackGlass
import dev.rift.launcher.ui.DrawerBackdrop
import dev.rift.launcher.ui.RiftDrawer
import dev.rift.launcher.ui.rememberDrawerAccordionOpen
import dev.rift.launcher.ui.RiftDrawerPanel
import dev.rift.launcher.ui.theme.RiftAccent
import dev.rift.launcher.ui.theme.RiftColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/** The space inside a row of Rift's home panels before its icon and after its text. */
internal val DrawerRowPadding = 14.dp

/** The space between a row's icon and its text. */
internal val DrawerRowGap = 16.dp

internal val DrawerRowMinHeight = 72.dp

/** The badge dot of a row (unread replies) and the space before it. */
private val BadgeSize = 10.dp

/**
 * The width of Rift's two home panels, the System panel and the filters (Denis 2026-10-18): as narrow as the
 * System panel's longest line allows, so the space after that line to the panel's edge is the space before
 * the icons. Every line the panel can show is measured (signed in or not, online or not), so the width stays
 * put, and in the font the rows are drawn with, so nothing is ever cut off.
 */
@Composable
internal fun rememberHomePanelWidth(): Dp {
    val badged = stringResource(R.string.support_title)
    val titles = listOf(
        R.string.settings_text,
        R.string.app_downloads,
        R.string.steam_sign_in,
        R.string.rift_menu_go_online,
        R.string.rift_menu_go_offline,
        R.string.steam_sign_out,
    ).map { stringResource(it) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = LocalTextStyle.current.merge(RowTitleStyle)
    return remember(badged, titles, density, style) {
        with(density) {
            fun width(text: String) = measurer.measure(text, style, maxLines = 1, softWrap = false).size.width.toDp()
            val longest = maxOf(titles.maxOf(::width), width(badged) + DrawerRowGap + BadgeSize)
            val iconInset = RiftDrawer.Edge + DrawerRowPadding
            iconInset + RowIconSize + DrawerRowGap + longest + iconInset
        }
    }
}

/**
 * A single menu item in the System Menu: a full-width plate like a Rift settings row, filled with the
 * accent while focused, with dark text and icon on it.
 */
@Composable
internal fun SystemMenuItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    isDestructive: Boolean = false,
    badge: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .plateFill(isFocused)
            .focusRequester(focusRequester)
            .selectable(
                selected = isFocused,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .heightIn(min = DrawerRowMinHeight)
            .padding(horizontal = DrawerRowPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DrawerRowGap),
    ) {
        RowIcon(icon, isFocused, danger = isDestructive)
        RowTexts(text, null, danger = isDestructive, focused = isFocused, modifier = Modifier.weight(1f))
        if (badge) {
            Box(
                modifier = Modifier
                    .size(BadgeSize)
                    .background(plateText(isFocused), CircleShape),
            )
        }
    }
}

/** The chevron at the end of an accordion's header: down while closed, turned up while open. */
@Composable
internal fun DrawerChevron(expanded: Boolean, focused: Boolean) {
    val turn by animateFloatAsState(if (expanded) 180f else 0f, label = "drawerChevron")
    Icon(
        imageVector = Icons.Default.KeyboardArrowDown,
        contentDescription = null,
        tint = plateSubtext(focused),
        modifier = Modifier.size(24.dp).rotate(turn),
    )
}

/**
 * One choice an accordion unrolls (a sort, a filter, a Steam status): a dimmer plate than its header, the radio
 * (one choice) or check box (several) on the left in the header's icon column, the name where the header's name
 * starts. [icon] stands in the column instead for an action (clear).
 */
@Composable
internal fun DrawerChoiceRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    multiple: Boolean = false,
    icon: ImageVector? = null,
    trailingText: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .plateFill(isFocused, rest = RiftDrawer.PlateMuted)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = when {
                    icon != null -> Role.Button
                    multiple -> Role.Checkbox
                    else -> Role.RadioButton
                },
                onClick = onClick,
            )
            .heightIn(min = DrawerChoiceMinHeight)
            .padding(horizontal = DrawerRowPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DrawerRowGap),
    ) {
        Box(modifier = Modifier.size(RowIconSize), contentAlignment = Alignment.Center) {
            when {
                icon != null -> Icon(imageVector = icon, contentDescription = null, tint = plateSubtext(isFocused), modifier = Modifier.size(24.dp))
                multiple -> RiftCheckMark(selected, isFocused)
                else -> RiftRadioMark(selected, isFocused)
            }
        }
        if (leading != null) leading()
        RowTexts(text, null, danger = false, focused = isFocused, modifier = Modifier.weight(1f))
        if (trailingText != null) {
            Text(text = trailingText, color = plateSubtext(isFocused), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** A choice row: shorter than a header. */
internal val DrawerChoiceMinHeight = 56.dp

/** The colour of a Steam status dot: green Online, yellow Away, grey Invisible/Offline. */
@Composable
internal fun statusDotColor(state: EPersonaState): Color = when (state) {
    EPersonaState.Away -> PluviaTheme.colors.statusAway
    EPersonaState.Invisible, EPersonaState.Offline -> PluviaTheme.colors.statusOffline
    else -> PluviaTheme.colors.statusInstalled
}

/** The signed-in user's Steam status, kept live from the persona events (Online until Steam says otherwise). */
@Composable
internal fun rememberPersonaStatus(): EPersonaState {
    var status by remember { mutableStateOf(SteamService.instance?.localPersona?.value?.state ?: EPersonaState.Online) }
    DisposableEffect(true) {
        val onPersona: (SteamEvent.PersonaStateReceived) -> Unit = { event -> status = event.persona.state }
        val onLoggedOut: (SteamEvent.LoggedOut) -> Unit = { status = EPersonaState.Offline }
        PluviaApp.events.on<SteamEvent.PersonaStateReceived, Unit>(onPersona)
        PluviaApp.events.on<SteamEvent.LoggedOut, Unit>(onLoggedOut)
        onDispose {
            PluviaApp.events.off<SteamEvent.PersonaStateReceived, Unit>(onPersona)
            PluviaApp.events.off<SteamEvent.LoggedOut, Unit>(onLoggedOut)
        }
    }
    return status
}

/**
 * Full-screen System Menu
 * Opens with START button, shows profile and system settings
 */
@Composable
fun SystemMenu(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onNavigateRoute: (String) -> Unit,
    onDownloadsClick: () -> Unit = {},
    onLogout: () -> Unit,
    onGoOnline: () -> Unit,
    isOffline: Boolean = false,
    gogLoggedIn: Boolean = false,
    epicLoggedIn: Boolean = false,
    amazonLoggedIn: Boolean = false,
    onGogLoginClick: () -> Unit = {},
    onGogLogoutClick: () -> Unit = {},
    onEpicLoginClick: () -> Unit = {},
    onEpicLogoutClick: () -> Unit = {},
    onAmazonLoginClick: () -> Unit = {},
    onAmazonLogoutClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    // Rift draws the panel as a flat, full height glass sheet without a close button (B closes it).
    panelShape: Shape = RoundedCornerShape(topStart = 32.dp, bottomStart = 32.dp),
    panelShadowElevation: Dp = 24.dp,
    showCloseButton: Boolean = true,
    // Rift is its own app: no GameNative Discord link and no GameNative supporters list.
    showCommunityItems: Boolean = true,
    // Rift signs in to GOG, Epic and Amazon in Settings > Accounts.
    showStoreAccounts: Boolean = true,
    // Rift: what the panel's glass blurs (the home behind it).
    glassBackdrop: DrawerBackdrop? = null,
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val firstItemFocusRequester = remember { FocusRequester() }
    val profileFocusRequester = remember { FocusRequester() }
    val statusFocusRequester = remember { FocusRequester() }
    val density = LocalDensity.current

    var persona by remember { mutableStateOf<SteamFriend?>(null) }
    var selectedStatus by remember(persona) { mutableStateOf(persona?.state ?: EPersonaState.Online) }
    var showSupporters by remember { mutableStateOf(false) }
    // The profile's accordion of statuses: closed the first time, then as it was left (Denis 2026-10-19).
    var showStatusPicker by rememberDrawerAccordionOpen("system:profile")
    var statusAsk by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        persona = SteamService.instance?.localPersona?.value
        SteamService.userSteamId?.let {
            SteamService.requestUserPersona()
        }
    }

    DisposableEffect(true) {
        val onPersonaStateReceived: (SteamEvent.PersonaStateReceived) -> Unit = { event ->
            Timber.d("SystemMenu onPersonaStateReceived: ${event.persona.state}")
            persona = event.persona
            selectedStatus = event.persona.state
        }

        val onLoggedOut: (SteamEvent.LoggedOut) -> Unit = {
            persona = SteamFriend()
            showStatusPicker = false
        }

        PluviaApp.events.on<SteamEvent.PersonaStateReceived, Unit>(onPersonaStateReceived)
        PluviaApp.events.on<SteamEvent.LoggedOut, Unit>(onLoggedOut)

        onDispose {
            PluviaApp.events.off<SteamEvent.PersonaStateReceived, Unit>(onPersonaStateReceived)
            PluviaApp.events.off<SteamEvent.LoggedOut, Unit>(onLoggedOut)
        }
    }

    BackHandler(enabled = isOpen && showStatusPicker) {
        showStatusPicker = false
        runCatching { profileFocusRequester.requestFocus() }
    }
    BackHandler(enabled = isOpen && !showStatusPicker) {
        onDismiss()
    }

    SupportersDialog(visible = showSupporters, onDismiss = { showSupporters = false })

    val colorOnline = PluviaTheme.colors.statusInstalled
    val colorAway = PluviaTheme.colors.statusAway
    val colorOffline = PluviaTheme.colors.statusOffline

    val getStatusColor: (EPersonaState) -> Color = { state ->
        when (state) {
            EPersonaState.Online -> colorOnline
            EPersonaState.Away -> colorAway
            EPersonaState.Invisible, EPersonaState.Offline -> colorOffline
            else -> colorOnline
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Backdrop
        AnimatedVisibility(
            visible = isOpen,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(150)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }

        // Menu panel - slides from right
        AnimatedVisibility(
            visible = isOpen,
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = dev.rift.launcher.ui.RiftDrawer.SlideIn,
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = dev.rift.launcher.ui.RiftDrawer.SlideOut,
            ),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            // Rift: the frame and glass of every Rift panel, blurring the home behind it.
            RiftDrawerPanel(
                modifier = Modifier
                    .width(adaptivePanelWidth(rememberHomePanelWidth()))
                    .fillMaxHeight()
                    .clip(panelShape),
                backdrop = glassBackdrop,
            ) {
                // Header with close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.system_menu_title),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (showCloseButton) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.options_panel_close),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Menu items. The profile is the first plate, an accordion of Steam statuses (Denis 2026-10-19: not a
                // dropdown, closed by default), with the same gap as every other plate.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .focusGroup(),
                    verticalArrangement = Arrangement.spacedBy(PlateGap),
                ) {
                    val profileInteractionSource = remember { MutableInteractionSource() }
                    val isProfileFocused by profileInteractionSource.collectIsFocusedAsState()
                    val canPickStatus = !isOffline && SteamService.isLoggedIn
                    Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .plateFill(isProfileFocused)
                            .focusRequester(profileFocusRequester)
                            .selectable(
                                selected = isProfileFocused,
                                interactionSource = profileInteractionSource,
                                indication = null,
                                onClick = {
                                    if (canPickStatus) {
                                        showStatusPicker = !showStatusPicker
                                        if (showStatusPicker) statusAsk++
                                    }
                                },
                            )
                            .heightIn(min = DrawerRowMinHeight)
                            .padding(horizontal = DrawerRowPadding, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DrawerRowGap),
                    ) {
                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(RowIconSize)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            // The live persona arrives late (or not at all offline); the saved avatar is the top
                            // bar's, so the panel shows the same picture from the first frame.
                            val avatarHash = persona?.avatarHash?.takeIf { it.isNotEmpty() } ?: PrefManager.steamUserAvatarHash
                            if (avatarHash.isNotEmpty()) {
                                SteamIconImage(
                                    size = RowIconSize,
                                    image = { avatarHash.getAvatarURL() },
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // Name and status
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = persona?.name ?: stringResource(R.string.default_user_name),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = plateText(isProfileFocused),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(getStatusColor(selectedStatus), CircleShape),
                                )
                                Text(
                                    text = when (selectedStatus) {
                                        EPersonaState.Online -> stringResource(R.string.status_online)
                                        EPersonaState.Away -> stringResource(R.string.status_away)
                                        EPersonaState.Invisible -> stringResource(R.string.status_invisible)
                                        else -> selectedStatus.name
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = plateSubtext(isProfileFocused),
                                )
                            }
                        }

                        if (canPickStatus) DrawerChevron(expanded = showStatusPicker, focused = isProfileFocused)
                    }
                    AnimatedVisibility(
                        visible = showStatusPicker && canPickStatus,
                        enter = expandVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                        exit = shrinkVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) + fadeOut(),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().focusGroup().padding(top = PlateGap),
                            verticalArrangement = Arrangement.spacedBy(PlateGap),
                        ) {
                            listOf(
                                EPersonaState.Online to R.string.status_online,
                                EPersonaState.Away to R.string.status_away,
                                EPersonaState.Invisible to R.string.status_invisible,
                            ).forEach { (state, label) ->
                                DrawerChoiceRow(
                                    text = stringResource(label),
                                    selected = selectedStatus == state,
                                    modifier = if (selectedStatus == state) Modifier.focusRequester(statusFocusRequester) else Modifier,
                                    leading = {
                                        Box(modifier = Modifier.size(10.dp).background(statusDotColor(state), CircleShape))
                                    },
                                    onClick = {
                                        selectedStatus = state
                                        showStatusPicker = false
                                        runCatching { profileFocusRequester.requestFocus() }
                                        scope.launch { SteamService.setPersonaState(state) }
                                    },
                                )
                            }
                        }
                    }
                    }

                    SystemMenuItem(
                        text = stringResource(R.string.settings_text),
                        icon = Icons.Default.Settings,
                        onClick = {
                            onNavigateRoute(PluviaScreen.Settings.route)
                            onDismiss()
                        },
                        focusRequester = firstItemFocusRequester,
                    )

                    SystemMenuItem(
                        text = stringResource(R.string.support_title),
                        icon = Icons.Default.SupportAgent,
                        onClick = {
                            onNavigateRoute(PluviaScreen.Support.route)
                            onDismiss()
                        },
                        badge = SupportReplyWatcher.unread.value.isNotEmpty(),
                    )

                    SystemMenuItem(
                        text = stringResource(R.string.app_downloads),
                        icon = Icons.Default.Download,
                        onClick = {
                            onDownloadsClick()
                            onDismiss()
                        },
                    )

                    if (showCommunityItems) {
                        SystemMenuItem(
                            text = stringResource(R.string.help_and_support),
                            icon = Icons.AutoMirrored.Filled.Help,
                            onClick = {
                                uriHandler.openUri("https://discord.gg/2hKv4VfZfE")
                            },
                        )

                        SystemMenuItem(
                            text = stringResource(R.string.hall_of_fame),
                            icon = Icons.AutoMirrored.Filled.StarHalf,
                            onClick = { showSupporters = true },
                        )
                    }

                    if (isOffline || !SteamService.isLoggedIn) {
                        val goOnlineLabelRes = if (!SteamService.isLoggedIn) {
                            R.string.steam_sign_in
                        } else {
                            R.string.rift_menu_go_online
                        }
                        SystemMenuItem(
                            text = stringResource(goOnlineLabelRes),
                            icon = Icons.AutoMirrored.Filled.Login,
                            onClick = {
                                onGoOnline()
                                onDismiss()
                            },
                        )
                    } else {
                        SystemMenuItem(
                            text = stringResource(R.string.rift_menu_go_offline),
                            icon = Icons.AutoMirrored.Filled.AirplaneTicket,
                            onClick = {
                                onNavigateRoute(PluviaScreen.Home.route + "?offline=true") // TODO: test this
                                onDismiss()
                            },
                        )

                        SystemMenuItem(
                            text = stringResource(R.string.steam_sign_out),
                            icon = Icons.AutoMirrored.Filled.Logout,
                            onClick = {
                                onLogout()
                                onDismiss()
                            },
                            isDestructive = true,
                        )
                    }

                    if (showStoreAccounts) {
                    // GOG
                    SystemMenuItem(
                        text = stringResource(
                            if (gogLoggedIn) R.string.gog_settings_logout_title
                            else R.string.gog_settings_login_title,
                        ),
                        icon = if (gogLoggedIn) {
                            Icons.AutoMirrored.Filled.Logout
                        } else {
                            Icons.AutoMirrored.Filled.Login
                        },
                        onClick = {
                            if (gogLoggedIn) onGogLogoutClick() else onGogLoginClick()
                            onDismiss()
                        },
                        isDestructive = gogLoggedIn,
                    )

                    // Epic
                    SystemMenuItem(
                        text = stringResource(
                            if (epicLoggedIn) R.string.epic_settings_logout_title
                            else R.string.epic_settings_login_title,
                        ),
                        icon = if (epicLoggedIn) {
                            Icons.AutoMirrored.Filled.Logout
                        } else {
                            Icons.AutoMirrored.Filled.Login
                        },
                        onClick = {
                            if (epicLoggedIn) onEpicLogoutClick() else onEpicLoginClick()
                            onDismiss()
                        },
                        isDestructive = epicLoggedIn,
                    )

                    // Amazon
                    SystemMenuItem(
                        text = stringResource(
                            if (amazonLoggedIn) R.string.amazon_settings_logout_title
                            else R.string.amazon_settings_login_title,
                        ),
                        icon = if (amazonLoggedIn) {
                            Icons.AutoMirrored.Filled.Logout
                        } else {
                            Icons.AutoMirrored.Filled.Login
                        },
                        onClick = {
                            if (amazonLoggedIn) onAmazonLogoutClick() else onAmazonLoginClick()
                            onDismiss()
                        },
                        isDestructive = amazonLoggedIn,
                    )
                    }
                }

                // Gamepad hint at bottom (only on expanded screens)
                if (shouldShowGamepadUI()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.press_b_to_close),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
    }

    // Focus Settings when the menu opens. After a touch the window is in touch mode, where nothing takes
    // gamepad focus: leave it, and retry while the panel is still sliding in.
    val view = LocalView.current
    LaunchedEffect(isOpen) {
        if (!isOpen) return@LaunchedEffect
        repeat(15) {
            if (view.isInTouchMode) view.requestFocusFromTouch()
            if (runCatching { firstItemFocusRequester.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(60)
        }
    }
    // Opening the statuses with A moves focus onto the chosen one; an accordion left open does not take it.
    LaunchedEffect(statusAsk) {
        if (statusAsk == 0) return@LaunchedEffect
        repeat(15) {
            if (runCatching { statusFocusRequester.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(40)
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Preview(
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    device = "spec:width=1920px,height=1080px,dpi=440,orientation=landscape",
)
@Composable
private fun Preview_SystemMenu() {
    val context = LocalContext.current
    PrefManager.init(context)
    PluviaTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Fake background content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Game Library",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }

                SystemMenu(
                    isOpen = true,
                    onDismiss = { },
                    onNavigateRoute = { },
                    onLogout = { },
                    onGoOnline = { },
                    isOffline = false,
                    gogLoggedIn = false,
                    epicLoggedIn = false,
                    amazonLoggedIn = false,
                    onGogLoginClick = { },
                    onGogLogoutClick = { },
                    onEpicLoginClick = { },
                    onEpicLogoutClick = { },
                    onAmazonLoginClick = { },
                    onAmazonLogoutClick = { },
                )
            }
        }
    }
}
