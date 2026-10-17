package dev.rift.launcher.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Link
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.gamenative.PrefManager
import app.gamenative.R
import app.gamenative.api.AccountApi
import app.gamenative.api.ApiResult
import app.gamenative.api.DebugReportApi
import app.gamenative.api.SupportApi
import app.gamenative.ui.component.dialog.AccountSignInDialog
import app.gamenative.ui.component.dialog.DiscordLinkDialog
import app.gamenative.ui.component.dialog.openAccountUrl
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Store sign-ins first, one card each, then the GameNative account (plan, Discord). */
@Composable
internal fun AccountsSettings(onSteamSignIn: (() -> Unit)? = null, onSteamSignOut: (() -> Unit)? = null) {
    StoreAccountsSettings(onSteamSignIn, onSteamSignOut)
    GameNativeAccountGroup()
}

@Composable
private fun GameNativeAccountGroup() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val signedIn by PrefManager.gameNativeSignedIn
    val account by AccountApi.account
    var showSignIn by rememberSaveable { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var loadAttempt by remember { mutableIntStateOf(0) }
    var portalBusy by remember { mutableStateOf(false) }
    var portalMessage by remember { mutableStateOf<Int?>(null) }
    val discordConnected by PrefManager.discordRelayTokenPresent
    var discordLinkedName by remember { mutableStateOf("") }
    var showDiscordLink by rememberSaveable { mutableStateOf(false) }

    val openDiscordLinkHere: suspend () -> Boolean = {
        val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        PrefManager.discordOauthNonce = nonce
        val accountUrl = if (signedIn) {
            when (val result = SupportApi.startDiscordLink(nonce)) {
                is ApiResult.Success -> result.data
                else -> null
            }
        } else {
            null
        }
        openAccountUrl(context, accountUrl ?: "${DebugReportApi.OAUTH_START_URL}?app_state=$nonce")
    }

    LaunchedEffect(Unit) { AccountApi.loadSignedInState() }

    LaunchedEffect(discordConnected) {
        withContext(Dispatchers.IO) {
            PrefManager.discordRelayTokenPresent.value = PrefManager.discordRelayToken.isNotEmpty()
            discordLinkedName = PrefManager.discordLinkedName
        }
    }

    LaunchedEffect(signedIn, discordConnected) {
        if (signedIn && discordConnected && withContext(Dispatchers.IO) { PrefManager.discordMergePending }) {
            if (SupportApi.listConversations() is ApiResult.Success) AccountApi.fetchAccount()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) loadAttempt++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(signedIn, loadAttempt) {
        if (signedIn) {
            loadFailed = false
            loadFailed = AccountApi.fetchAccount() !is ApiResult.Success
        }
    }

    AccountSignInDialog(
        visible = showSignIn,
        onSignedIn = { showSignIn = false },
        onDismiss = { showSignIn = false },
    )
    DiscordLinkDialog(
        visible = showDiscordLink,
        signedIn = signedIn,
        onOpenHere = openDiscordLinkHere,
        onDismiss = { showDiscordLink = false },
    )

    SettingsGroup(title = stringResource(R.string.gamenative_account_title)) {
        if (!signedIn) {
            LinkRow(
                title = stringResource(R.string.gamenative_account_sign_in),
                subtitle = stringResource(R.string.gamenative_account_sign_in_subtitle),
                icon = Icons.AutoMirrored.Rounded.Login,
                onClick = { showSignIn = true },
            )
        } else {
            val current = account
            val managedElsewhere = managedElsewhereMessage(current?.tierSource)
            LinkRow(
                title = current?.email?.ifBlank { null } ?: stringResource(R.string.gamenative_account_title),
                subtitle = when {
                    current != null -> stringResource(R.string.gamenative_account_plan, tierLabel(current.tier))
                    loadFailed -> stringResource(R.string.gamenative_account_load_failed)
                    else -> stringResource(R.string.gamenative_account_loading)
                },
                icon = Icons.Rounded.AccountCircle,
                onClick = { if (current == null && loadFailed) loadAttempt++ },
            )
            LinkRow(
                title = stringResource(R.string.gamenative_account_manage_subscription),
                subtitle = stringResource(
                    portalMessage ?: managedElsewhere ?: R.string.gamenative_account_manage_subscription_subtitle,
                ),
                icon = Icons.Rounded.CreditCard,
                enabled = !portalBusy,
                onClick = {
                    portalBusy = true
                    portalMessage = R.string.gamenative_account_manage_subscription_opening
                    scope.launch {
                        portalMessage = when (val result = AccountApi.createPortal()) {
                            is ApiResult.Success -> {
                                if (openAccountUrl(context, result.data)) null
                                else R.string.gamenative_account_manage_subscription_failed
                            }
                            is ApiResult.HttpError -> when {
                                result.code != 409 -> R.string.gamenative_account_manage_subscription_failed
                                managedElsewhere != null -> managedElsewhere
                                else -> R.string.gamenative_account_manage_subscription_none
                            }
                            is ApiResult.NetworkError -> R.string.gamenative_account_manage_subscription_failed
                        }
                        portalBusy = false
                    }
                },
            )
            LinkRow(
                title = stringResource(R.string.gamenative_account_sign_out),
                subtitle = stringResource(R.string.gamenative_account_sign_out_subtitle),
                icon = Icons.AutoMirrored.Rounded.Logout,
                onClick = {
                    portalMessage = null
                    loadFailed = false
                    scope.launch { AccountApi.signOut() }
                },
            )
        }

        val discordName = account?.discordName ?: discordLinkedName.ifBlank { null }
        LinkRow(
            title = stringResource(R.string.gamenative_account_discord),
            subtitle = when {
                !discordConnected -> stringResource(R.string.gamenative_account_discord_not_connected)
                discordName != null -> stringResource(R.string.gamenative_account_discord_connected_as, discordName)
                else -> stringResource(R.string.gamenative_account_discord_connected)
            },
            icon = Icons.Rounded.Link,
            onClick = {
                if (discordConnected) {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            PrefManager.discordRelayToken = ""
                            PrefManager.discordLinkedName = ""
                            PrefManager.discordMergePending = false
                        }
                        discordLinkedName = ""
                    }
                } else {
                    showDiscordLink = true
                }
            },
        )
    }
}

private fun managedElsewhereMessage(tierSource: String?): Int? =
    when (tierSource) {
        "discord" -> R.string.gamenative_account_managed_discord
        "kofi" -> R.string.gamenative_account_managed_kofi
        else -> null
    }

@Composable
private fun tierLabel(tier: String): String =
    when (tier) {
        "none" -> stringResource(R.string.gamenative_account_plan_none)
        "basic" -> stringResource(R.string.gamenative_account_plan_basic)
        "pro" -> stringResource(R.string.gamenative_account_plan_pro)
        "patron" -> stringResource(R.string.gamenative_account_plan_patron)
        else -> tier.replaceFirstChar { it.uppercase() }
    }
