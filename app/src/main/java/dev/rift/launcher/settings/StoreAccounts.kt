package dev.rift.launcher.settings

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.VideogameAsset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import app.gamenative.PluviaApp
import app.gamenative.PrefManager
import app.gamenative.R
import app.gamenative.events.AndroidEvent
import app.gamenative.events.SteamEvent
import app.gamenative.service.SteamService
import app.gamenative.service.amazon.AmazonAuthManager
import app.gamenative.service.ea.EaAuthManager
import app.gamenative.service.epic.EpicAuthManager
import app.gamenative.service.gog.GOGAuthManager
import app.gamenative.service.rockstar.RockstarAuthManager
import app.gamenative.ui.icons.Amazon
import app.gamenative.ui.icons.Steam
import app.gamenative.ui.screen.auth.AmazonOAuthActivity
import app.gamenative.ui.screen.auth.EpicOAuthActivity
import app.gamenative.ui.screen.auth.GOGOAuthActivity
import app.gamenative.ui.util.PlatformAuthUiHelpers
import app.gamenative.ui.util.PlatformLogoutCallbacks
import app.gamenative.ui.util.SnackbarManager
import app.gamenative.utils.PlatformOAuthHandlers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

private enum class Store(val accent: SettingsAccent) {
    Steam(SettingsAccent.Blue),
    Epic(SettingsAccent.Lilac),
    Gog(SettingsAccent.Rose),
    Amazon(SettingsAccent.Lemon),
    Ea(SettingsAccent.Mint),
    Rockstar(SettingsAccent.Peach),
}

private data class StoreStatus(val signedIn: Boolean, val account: String?)

/**
 * One card per PC store with its sign-in state. Signing in and out goes through GameNative's own screens
 * and helpers: its Steam login screen and the Epic, GOG and Amazon web logins. The games of a store show
 * up on the home screen as soon as it is signed in.
 */
@Composable
internal fun StoreAccountsSettings(onSteamSignIn: (() -> Unit)?, onSteamSignOut: (() -> Unit)?) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleScope = lifecycleOwner.lifecycleScope
    var refresh by remember { mutableIntStateOf(0) }
    var confirmSignOut by rememberSaveable { mutableStateOf<String?>(null) }

    // Sign-in state lives in files, preferences and the Steam connection, none of which Compose observes.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        val onSteamChanged: (SteamEvent.LogonEnded) -> Unit = { refresh++ }
        val onSteamLoggedOut: (SteamEvent.LoggedOut) -> Unit = { refresh++ }
        PluviaApp.events.on<SteamEvent.LogonEnded, Unit>(onSteamChanged)
        PluviaApp.events.on<SteamEvent.LoggedOut, Unit>(onSteamLoggedOut)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            PluviaApp.events.off<SteamEvent.LogonEnded, Unit>(onSteamChanged)
            PluviaApp.events.off<SteamEvent.LoggedOut, Unit>(onSteamLoggedOut)
        }
    }

    val gogLogin = rememberOAuthLogin(
        activity = GOGOAuthActivity::class.java,
        extraCode = GOGOAuthActivity.EXTRA_AUTH_CODE,
        extraError = GOGOAuthActivity.EXTRA_ERROR,
        cancelRes = R.string.gog_login_cancel,
        successRes = R.string.gog_login_success_title,
        scope = lifecycleScope,
        onDone = { refresh++ },
    ) { code, onError, onSuccess ->
        PlatformOAuthHandlers.handleGogAuthentication(
            context = context,
            authCode = code,
            coroutineScope = lifecycleScope,
            onLoadingChange = { },
            onError = onError,
            onSuccess = { onSuccess() },
            onDialogClose = { },
        )
    }
    val epicLogin = rememberOAuthLogin(
        activity = EpicOAuthActivity::class.java,
        extraCode = EpicOAuthActivity.EXTRA_AUTH_CODE,
        extraError = EpicOAuthActivity.EXTRA_ERROR,
        cancelRes = R.string.epic_login_cancel,
        successRes = R.string.epic_login_success_title,
        scope = lifecycleScope,
        onDone = { refresh++ },
    ) { code, onError, onSuccess ->
        PlatformOAuthHandlers.handleEpicAuthentication(
            context = context,
            authCode = code,
            coroutineScope = lifecycleScope,
            onLoadingChange = { },
            onError = onError,
            onSuccess = onSuccess,
            onDialogClose = { },
        )
    }
    val amazonLogin = rememberOAuthLogin(
        activity = AmazonOAuthActivity::class.java,
        extraCode = AmazonOAuthActivity.EXTRA_AUTH_CODE,
        extraError = AmazonOAuthActivity.EXTRA_ERROR,
        cancelRes = R.string.amazon_login_cancel,
        successRes = R.string.amazon_login_success_title,
        scope = lifecycleScope,
        onDone = { refresh++ },
    ) { code, onError, onSuccess ->
        PlatformOAuthHandlers.handleAmazonAuthentication(
            context = context,
            authCode = code,
            coroutineScope = lifecycleScope,
            onLoadingChange = { },
            onError = onError,
            onSuccess = onSuccess,
            onDialogClose = { },
        )
    }

    // GOG can also hand its code back through a deep link instead of the login screen's result.
    DisposableEffect(Unit) {
        val onGogAuthCode: (AndroidEvent.GOGAuthCodeReceived) -> Unit = { event ->
            Timber.i("[RiftSettings]: received GOG auth code event")
            gogLogin.handleCode(event.authCode)
        }
        PluviaApp.events.on<AndroidEvent.GOGAuthCodeReceived, Unit>(onGogAuthCode)
        onDispose { PluviaApp.events.off<AndroidEvent.GOGAuthCodeReceived, Unit>(onGogAuthCode) }
    }

    val statuses = remember(refresh) { loadStatuses(context) }

    fun signOut(store: Store) {
        val callbacks = PlatformLogoutCallbacks(onSuccess = { refresh++ })
        when (store) {
            Store.Steam -> onSteamSignOut?.invoke()
            Store.Epic -> PlatformAuthUiHelpers.logoutEpic(context, lifecycleScope, callbacks)
            Store.Gog -> PlatformAuthUiHelpers.logoutGog(context, lifecycleScope, callbacks)
            Store.Amazon -> PlatformAuthUiHelpers.logoutAmazon(context, lifecycleScope, callbacks)
            Store.Ea -> {
                EaAuthManager.logout(context)
                refresh++
            }
            Store.Rockstar -> {
                RockstarAuthManager.logout(context)
                refresh++
            }
        }
    }

    Store.entries.forEach { store ->
        val status = statuses.getValue(store)
        val signIn: (() -> Unit)? = when (store) {
            Store.Steam -> onSteamSignIn
            Store.Epic -> epicLogin.launch
            Store.Gog -> gogLogin.launch
            Store.Amazon -> amazonLogin.launch
            Store.Ea, Store.Rockstar -> null
        }
        StoreCard(
            store = store,
            status = status,
            canSignIn = signIn != null,
            onClick = {
                if (status.signedIn) confirmSignOut = store.name else signIn?.invoke()
            },
        )
    }

    confirmSignOut?.let { name ->
        val store = Store.valueOf(name)
        RiftConfirmDialog(
            title = stringResource(R.string.rift_settings_sign_out_title, storeTitle(store)),
            message = stringResource(R.string.rift_settings_sign_out_message),
            confirmText = stringResource(R.string.rift_settings_sign_out),
            dismissText = stringResource(R.string.rift_settings_cancel),
            onConfirm = {
                signOut(store)
                confirmSignOut = null
            },
            onDismiss = { confirmSignOut = null },
        )
    }
}

@Composable
private fun storeTitle(store: Store): String = when (store) {
    Store.Steam -> stringResource(R.string.rift_store_steam)
    Store.Epic -> stringResource(R.string.rift_store_epic)
    Store.Gog -> stringResource(R.string.rift_store_gog)
    Store.Amazon -> stringResource(R.string.rift_store_amazon)
    Store.Ea -> stringResource(R.string.settings_accounts_ea_title)
    Store.Rockstar -> stringResource(R.string.settings_accounts_rockstar_title)
}

@Composable
private fun StoreCard(store: Store, status: StoreStatus, canSignIn: Boolean, onClick: () -> Unit) {
    val vector: ImageVector? = when (store) {
        Store.Steam -> Icons.Filled.Steam
        Store.Amazon -> Icons.Filled.Amazon
        Store.Ea -> Icons.Rounded.SportsEsports
        Store.Rockstar -> Icons.Rounded.VideogameAsset
        else -> null
    }
    val painter: Painter? = when (store) {
        Store.Epic -> painterResource(R.drawable.ic_epic)
        Store.Gog -> painterResource(R.drawable.ic_gog)
        else -> null
    }
    CompositionLocalProvider(LocalSettingsAccent provides store.accent) {
        SettingsGroup {
            LinkRow(
                title = storeTitle(store),
                subtitle = when {
                    !status.signedIn -> stringResource(R.string.settings_accounts_signed_out)
                    status.account != null -> status.account
                    else -> stringResource(R.string.rift_settings_store_signed_in)
                },
                icon = vector,
                iconPainter = painter,
                value = when {
                    status.signedIn -> stringResource(R.string.rift_settings_sign_out)
                    canSignIn -> stringResource(R.string.rift_settings_sign_in)
                    else -> null
                },
                enabled = status.signedIn || canSignIn,
                onClick = onClick,
            )
        }
    }
}

private fun loadStatuses(context: Context): Map<Store, StoreStatus> {
    val steamIn = SteamService.isLoggedIn
    val eaName = EaAuthManager.displayName(context)
    val rockstarName = RockstarAuthManager.nickname(context)
    return mapOf(
        Store.Steam to StoreStatus(steamIn, if (steamIn) PrefManager.steamUserName.ifBlank { null } else null),
        Store.Epic to StoreStatus(
            EpicAuthManager.hasStoredCredentials(context),
            EpicAuthManager.loadCredentials(context)?.displayName?.ifBlank { null },
        ),
        Store.Gog to StoreStatus(GOGAuthManager.hasStoredCredentials(context), null),
        Store.Amazon to StoreStatus(AmazonAuthManager.hasStoredCredentials(context), null),
        Store.Ea to StoreStatus(eaName != null, eaName),
        Store.Rockstar to StoreStatus(rockstarName != null, rockstarName),
    )
}

/** Starts a store's web login and finishes the sign-in with the code it returns. */
private class OAuthLogin(val launch: () -> Unit, val handleCode: (String) -> Unit)

@Composable
private fun rememberOAuthLogin(
    activity: Class<*>,
    extraCode: String,
    extraError: String,
    @StringRes cancelRes: Int,
    @StringRes successRes: Int,
    scope: CoroutineScope,
    onDone: () -> Unit,
    authenticate: suspend (code: String, onError: (String?) -> Unit, onSuccess: () -> Unit) -> Unit,
): OAuthLogin {
    val context = LocalContext.current
    val handleCode: (String) -> Unit = { code ->
        scope.launch {
            authenticate(
                code,
                { message -> if (message != null) SnackbarManager.show(message) },
                {
                    SnackbarManager.show(context.getString(successRes))
                    onDone()
                },
            )
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val code = if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.getStringExtra(extraCode)
        } else {
            null
        }
        if (code == null) {
            SnackbarManager.show(result.data?.getStringExtra(extraError) ?: context.getString(cancelRes))
        } else {
            handleCode(code)
        }
    }
    return remember(activity, launcher) {
        OAuthLogin(launch = { launcher.launch(Intent(context, activity)) }, handleCode = handleCode)
    }
}
