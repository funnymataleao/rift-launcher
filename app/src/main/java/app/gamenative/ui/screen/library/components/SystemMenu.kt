package app.gamenative.ui.screen.library.components

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import dev.rift.launcher.settings.RowTexts
import dev.rift.launcher.settings.plateFill
import dev.rift.launcher.settings.plateSubtext
import dev.rift.launcher.settings.plateText
import dev.rift.launcher.ui.BlackGlass
import dev.rift.launcher.ui.theme.RiftAccent
import dev.rift.launcher.ui.theme.RiftColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

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
            .plateFill(isFocused, rest = Color.Transparent)
            .focusRequester(focusRequester)
            .selectable(
                selected = isFocused,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .heightIn(min = 72.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RowIcon(icon, isFocused, danger = isDestructive)
        RowTexts(text, null, danger = isDestructive, focused = isFocused, modifier = Modifier.weight(1f))
        if (badge) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(plateText(isFocused), CircleShape),
            )
        }
    }
}

/**
 * Status option item for the dropdown
 */
@Composable
private fun StatusOption(
    text: String,
    statusColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .plateFill(isFocused, rest = Color.Transparent)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .selectable(
                selected = isFocused,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .heightIn(min = 60.dp)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(statusColor, CircleShape),
        )
        RowTexts(text, null, danger = false, focused = isFocused, modifier = Modifier.weight(1f))
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = if (isFocused) plateText(true) else RiftAccent.current.main,
                modifier = Modifier.size(22.dp),
            )
        }
    }
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
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val firstItemFocusRequester = remember { FocusRequester() }
    val profileFocusRequester = remember { FocusRequester() }
    val statusFocusRequester = remember { FocusRequester() }
    var profileWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    var persona by remember { mutableStateOf<SteamFriend?>(null) }
    var selectedStatus by remember(persona) { mutableStateOf(persona?.state ?: EPersonaState.Online) }
    var showSupporters by remember { mutableStateOf(false) }
    var showStatusPicker by remember { mutableStateOf(false) }

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
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            ),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Surface(
                modifier = Modifier
                    .width(adaptivePanelWidth(380.dp))
                    .fillMaxHeight(),
                shape = panelShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = panelShadowElevation,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 20.dp),
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

                    // Profile section
                    val profileInteractionSource = remember { MutableInteractionSource() }
                    val isProfileFocused by profileInteractionSource.collectIsFocusedAsState()

                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { profileWidth = with(density) { it.size.width.toDp() } }
                                .plateFill(isProfileFocused)
                                .focusRequester(profileFocusRequester)
                                .selectable(
                                    selected = isProfileFocused,
                                    interactionSource = profileInteractionSource,
                                    indication = null,
                                    onClick = { if (!isOffline && SteamService.isLoggedIn) showStatusPicker = !showStatusPicker },
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // Avatar
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (persona?.avatarHash?.isNotEmpty() == true) {
                                    SteamIconImage(
                                        size = 48.dp,
                                        image = { persona?.avatarHash?.getAvatarURL() },
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

                            // Dropdown indicator (only when online)
                            if (!isOffline && SteamService.isLoggedIn) {
                                Icon(
                                    imageVector = if (showStatusPicker) {
                                        Icons.Default.KeyboardArrowUp
                                    } else {
                                        Icons.Default.KeyboardArrowDown
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = plateSubtext(isProfileFocused),
                                )
                            }
                        }

                        // Status picker dropdown
                        // As wide as the profile plate, near-opaque black glass, accent focus like the rows.
                        androidx.compose.material3.DropdownMenu(
                            expanded = showStatusPicker,
                            onDismissRequest = { showStatusPicker = false },
                            modifier = Modifier.width(profileWidth.coerceAtLeast(280.dp)),
                            shape = PlateShape,
                            containerColor = BlackGlass.Solid.copy(alpha = 0.96f),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .focusGroup(),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.status),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = RiftColors.OnSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                                )
                                listOf(
                                    Triple(EPersonaState.Online, stringResource(R.string.status_online), PluviaTheme.colors.statusInstalled),
                                    Triple(EPersonaState.Away, stringResource(R.string.status_away), PluviaTheme.colors.statusAway),
                                    Triple(EPersonaState.Invisible, stringResource(R.string.status_invisible), PluviaTheme.colors.statusOffline),
                                ).forEach { (state, label, color) ->
                                    StatusOption(
                                        text = label,
                                        statusColor = color,
                                        isSelected = selectedStatus == state,
                                        focusRequester = if (selectedStatus == state) statusFocusRequester else null,
                                        onClick = {
                                            selectedStatus = state
                                            showStatusPicker = false
                                            scope.launch {
                                                SteamService.setPersonaState(state)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Menu items
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .focusGroup(),
                        verticalArrangement = Arrangement.spacedBy(PlateGap),
                    ) {
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
                                R.string.steam_go_online
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
                                text = stringResource(R.string.steam_go_offline),
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
    LaunchedEffect(showStatusPicker) {
        if (!showStatusPicker) return@LaunchedEffect
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
