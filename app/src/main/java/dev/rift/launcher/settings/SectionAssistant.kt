package dev.rift.launcher.settings

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.Alignment
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.sp
import app.gamenative.R
import dev.rift.launcher.assistant.ai.AiConnection
import dev.rift.launcher.assistant.ai.AiException
import dev.rift.launcher.assistant.ai.AiProvider
import dev.rift.launcher.assistant.ai.AiSecrets
import dev.rift.launcher.assistant.ai.AiSettings
import dev.rift.launcher.assistant.ai.OllamaDiscovery
import dev.rift.launcher.ui.theme.RiftColors
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

private val OUTPUT_TOKEN_OPTIONS = listOf(2000, 4000, 8000, 16000)
private val MONTHLY_LIMIT_OPTIONS = listOf(0L, 500_000L, 1_000_000L, 5_000_000L)

private enum class AssistantDialog { Add, Key, Address, Model, Rename, Remove }

/**
 * Settings › AI assistant: any number of providers the user brings a key for (several of one kind too), one of
 * them active; for the active one its key (encrypted on the device, shown only by its last four characters), the
 * address, the model from the provider's own list and a connection check; then limits with this month's token
 * count. Nothing here sends a request until the user asks.
 */
@Composable
internal fun AssistantSettings() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val settings = remember { AiSettings.of(context) }
    var revision by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<AssistantDialog?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var checkResult by remember { mutableStateOf<String?>(null) }
    // A provider just added: its key (or address) is asked for, then its model.
    var settingUp by remember { mutableStateOf(false) }

    // Re-read prefs whenever something was saved.
    val connections = remember(revision) { settings.connections }
    val active = remember(revision) { settings.active }
    val key = remember(revision) { active?.let(settings::apiKey).orEmpty() }
    val baseUrl = remember(revision) { active?.let(settings::baseUrl).orEmpty() }
    val model = remember(revision) { active?.let(settings::model) }
    val usage = remember(revision) { settings.usage() }
    fun saved() {
        revision++
        checkResult = null
    }

    val checking = stringResource(R.string.rift_assistant_checking)
    val connectedText = stringResource(R.string.rift_assistant_connected)

    SettingsGroup(title = stringResource(R.string.rift_assistant_group_providers)) {
        val activeText = stringResource(R.string.rift_assistant_active)
        connections.forEach { connection ->
            LinkRow(
                title = connectionName(connection),
                subtitle = connectionSummary(connection, settings.model(connection)),
                value = if (connection.id == active?.id) activeText else null,
                icon = Icons.Rounded.Cloud,
                onClick = {
                    settings.activate(connection)
                    saved()
                },
            )
        }
        LinkRow(
            title = stringResource(R.string.rift_assistant_add_provider),
            icon = Icons.Rounded.Add,
            onClick = { dialog = AssistantDialog.Add },
        )
    }

    if (active != null) {
        SettingsGroup(title = connectionName(active)) {
            LinkRow(
                title = stringResource(R.string.rift_assistant_key),
                subtitle = if (active.type.needsKey) null else stringResource(R.string.rift_assistant_key_optional),
                value = if (key.isEmpty()) stringResource(R.string.rift_assistant_not_set) else AiSecrets.lastFour(key),
                icon = Icons.Rounded.Key,
                onClick = { dialog = AssistantDialog.Key },
            )
            LinkRow(
                title = stringResource(R.string.rift_assistant_address),
                subtitle = stringResource(R.string.rift_assistant_address_hint),
                value = baseUrl.substringAfter("://").ifEmpty { stringResource(R.string.rift_assistant_not_set) },
                icon = Icons.Rounded.Link,
                onClick = { dialog = AssistantDialog.Address },
            )
            LinkRow(
                title = stringResource(R.string.rift_assistant_model),
                value = model ?: stringResource(R.string.rift_assistant_not_set),
                icon = Icons.Rounded.SmartToy,
                onClick = { dialog = AssistantDialog.Model },
            )
            LinkRow(
                title = stringResource(R.string.rift_assistant_check),
                subtitle = checkResult,
                icon = Icons.Rounded.NetworkCheck,
                chevron = false,
                onClick = {
                    scope.launch {
                        busy = checking
                        var result = runRequest(context, settings, active) { it.listModels() }
                        if (result is Outcome.Err && active.type == AiProvider.Ollama) {
                            val found = OllamaDiscovery.find(context)
                            if (found != null) {
                                settings.setBaseUrl(active, found)
                                result = runRequest(context, settings, active) { it.listModels() }
                                saved()
                            }
                        }
                        busy = null
                        checkResult = result.fold(
                            onSuccess = { models ->
                                if (active.type == AiProvider.Ollama && models.isNotEmpty()) {
                                    val current = settings.model(active)
                                    if (models.none { it.id == current }) settings.setModel(active, models.first().id)
                                }
                                connectedText.format(models.size)
                            },
                            onFailure = { it },
                        )
                    }
                },
            )
            active.type.keysUrl?.let { url ->
                LinkRow(
                    title = stringResource(R.string.rift_assistant_get_key),
                    value = url.substringAfter("://").substringBefore('/'),
                    onClick = { runCatching { uriHandler.openUri(url) } },
                )
            }
            LinkRow(
                title = stringResource(R.string.rift_assistant_rename),
                icon = Icons.Rounded.Edit,
                onClick = { dialog = AssistantDialog.Rename },
            )
            LinkRow(
                title = stringResource(R.string.rift_assistant_remove_provider),
                icon = Icons.Rounded.DeleteOutline,
                danger = true,
                onClick = { dialog = AssistantDialog.Remove },
            )
        }
    }

    SettingsGroup(title = stringResource(R.string.rift_assistant_group_permissions)) {
        SwitchRow(
            title = stringResource(R.string.rift_assistant_allow_env),
            subtitle = stringResource(R.string.rift_assistant_allow_env_hint),
            checked = remember(revision) { settings.allowEnvAndArgs },
            onCheckedChange = {
                settings.allowEnvAndArgs = it
                saved()
            },
        )
    }

    SettingsGroup(title = stringResource(R.string.rift_assistant_group_limits)) {
        val outputIndex = OUTPUT_TOKEN_OPTIONS.indexOf(settings.maxOutputTokens).coerceAtLeast(0)
        StepSliderRow(
            title = stringResource(R.string.rift_assistant_max_output),
            subtitle = stringResource(R.string.rift_assistant_max_output_hint),
            options = OUTPUT_TOKEN_OPTIONS.map { tokensLabel(it.toLong()) },
            selectedIndex = outputIndex,
            onSelect = {
                settings.maxOutputTokens = OUTPUT_TOKEN_OPTIONS[it]
                saved()
            },
        )
        val noLimit = stringResource(R.string.rift_assistant_no_limit)
        StepSliderRow(
            title = stringResource(R.string.rift_assistant_monthly_limit),
            options = MONTHLY_LIMIT_OPTIONS.map { if (it == 0L) noLimit else tokensLabel(it) },
            selectedIndex = MONTHLY_LIMIT_OPTIONS.indexOf(settings.monthlyTokenLimit).coerceAtLeast(0),
            onSelect = {
                settings.monthlyTokenLimit = MONTHLY_LIMIT_OPTIONS[it]
                saved()
            },
        )
        InfoRow(
            title = stringResource(R.string.rift_assistant_usage),
            value = stringResource(R.string.rift_assistant_usage_value, usage.requests, tokensLabel(usage.totalTokens)),
        )
        LinkRow(
            title = stringResource(R.string.rift_assistant_usage_reset),
            icon = Icons.Rounded.DeleteOutline,
            chevron = false,
            onClick = {
                settings.resetUsage()
                saved()
            },
        )
    }

    busy?.let { RiftBusyDialog(it) }

    when (dialog) {
        AssistantDialog.Add -> {
            val providers = AiProvider.entries
            val labels = providers.map { providerLabel(it) }
            RiftChoiceDialog(
                title = stringResource(R.string.rift_assistant_add_provider),
                options = labels,
                selectedIndex = -1,
                onSelect = {
                    val type = providers[it]
                    val taken = connections.map { c -> c.name }.toSet()
                    val name = (sequenceOf(labels[it]) + generateSequence(2) { n -> n + 1 }.map { n -> "${labels[it]} $n" })
                        .first { candidate -> candidate !in taken }
                    settings.add(type, name)
                    settingUp = true
                    saved()
                    // Choosing closes this dialog; the next one opens right after.
                    scope.launch {
                        if (type == AiProvider.Ollama) {
                            busy = "Searching for Ollama…"
                            val connection = settings.active
                            val found = OllamaDiscovery.find(context)
                            busy = null
                            if (connection != null && found != null) {
                                settings.setBaseUrl(connection, found)
                                saved()
                                dialog = AssistantDialog.Model
                            } else {
                                checkResult = "Ollama was not found on this device or local network"
                                dialog = AssistantDialog.Address
                            }
                            settingUp = false
                        } else {
                            dialog = if (type.needsKey) AssistantDialog.Key else AssistantDialog.Address
                        }
                    }
                },
                onDismiss = { dialog = null },
            )
        }
        AssistantDialog.Key -> if (active != null) {
            TextInputDialog(
                title = stringResource(R.string.rift_assistant_key),
                initial = "",
                placeholder = stringResource(R.string.rift_assistant_key_placeholder),
                secret = true,
                onDismiss = {
                    dialog = null
                    settingUp = false
                },
                onSave = {
                    settings.setApiKey(active, it)
                    // With a key the provider's model list can be had: pick from it right away.
                    dialog = if (it.isNotBlank()) AssistantDialog.Model else null
                    settingUp = false
                    saved()
                },
                onClear = if (key.isNotEmpty()) {
                    {
                        settings.setApiKey(active, "")
                        dialog = null
                        saved()
                    }
                } else {
                    null
                },
            )
        }
        AssistantDialog.Address -> if (active != null) {
            TextInputDialog(
                title = stringResource(R.string.rift_assistant_address),
                initial = baseUrl,
                placeholder = if (active.type == AiProvider.Ollama) "http://192.168.1.10:11434/v1" else active.type.defaultBaseUrl.ifEmpty { "http://192.168.1.10:11434/v1" },
                secret = false,
                onDismiss = {
                    dialog = null
                    settingUp = false
                },
                onSave = {
                    settings.setBaseUrl(active, it)
                    dialog = if (settingUp) AssistantDialog.Model else null
                    settingUp = false
                    saved()
                },
            )
        }
        AssistantDialog.Model -> if (active != null) {
            AssistantModelPicker(
                connection = active,
                onDismiss = {
                    dialog = null
                    saved()
                },
            )
        }
        AssistantDialog.Rename -> if (active != null) {
            TextInputDialog(
                title = stringResource(R.string.rift_assistant_rename),
                initial = connectionName(active),
                placeholder = providerLabel(active.type),
                secret = false,
                onDismiss = { dialog = null },
                onSave = {
                    settings.rename(active, it)
                    dialog = null
                    saved()
                },
            )
        }
        AssistantDialog.Remove -> if (active != null) {
            RiftConfirmDialog(
                title = stringResource(R.string.rift_assistant_remove_provider),
                message = stringResource(R.string.rift_assistant_remove_confirm, connectionName(active)),
                confirmText = stringResource(R.string.rift_assistant_remove),
                dismissText = stringResource(R.string.rift_assistant_cancel),
                onConfirm = {
                    settings.remove(active)
                    dialog = null
                    saved()
                },
                onDismiss = { dialog = null },
            )
        }
        null -> Unit
    }
}

/** A provider's name; one never renamed shows its kind's label in the device's language. */
@Composable
internal fun connectionName(connection: AiConnection): String =
    if (connection.name == connection.type.label) providerLabel(connection.type) else connection.name

/** The kind of a renamed provider and its model, or only the model when the name already says the kind. */
@Composable
internal fun connectionSummary(connection: AiConnection, model: String?): String? {
    val kind = providerLabel(connection.type).takeIf { connectionName(connection) != it }
    return listOfNotNull(kind, model).joinToString(" · ").ifEmpty { null }
}

@Composable
internal fun providerLabel(provider: AiProvider): String =
    if (provider == AiProvider.Custom) stringResource(R.string.rift_assistant_provider_custom) else provider.label

/** "4 тыс." / "1,5 млн" style, from the device's language. */
@Composable
private fun tokensLabel(tokens: Long): String = when {
    tokens >= 1_000_000 -> stringResource(R.string.rift_assistant_tokens_millions, formatShort(tokens / 1_000_000.0))
    tokens >= 1_000 -> stringResource(R.string.rift_assistant_tokens_thousands, formatShort(tokens / 1_000.0))
    else -> tokens.toString()
}

private fun formatShort(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(java.util.Locale.getDefault(), "%.1f", value)

/**
 * Runs one request against the provider as it is saved now. Failures come back as a sentence for the user;
 * the key never appears in them.
 */
private suspend fun <T> runRequest(
    context: android.content.Context,
    settings: AiSettings,
    connection: AiConnection,
    block: suspend (dev.rift.launcher.assistant.ai.AiClient) -> T,
): Outcome<T, String> = try {
    Outcome.Ok(block(settings.clientFor(connection)))
} catch (e: CancellationException) {
    throw e
} catch (e: AiException) {
    Outcome.Err(errorText(context, connection.type, e))
} catch (e: Exception) {
    Outcome.Err(context.getString(R.string.rift_assistant_error_other, e.javaClass.simpleName))
}

private sealed interface Outcome<out T, out E> {
    data class Ok<T>(val value: T) : Outcome<T, Nothing>
    data class Err<E>(val error: E) : Outcome<Nothing, E>

    fun onSuccess(action: (T) -> Unit): Outcome<T, E> = also { if (it is Ok) action(it.value) }
    fun onFailure(action: (E) -> Unit): Outcome<T, E> = also { if (it is Err) action(it.error) }
    fun <R> fold(onSuccess: (T) -> R, onFailure: (E) -> R): R = when (this) {
        is Ok -> onSuccess(value)
        is Err -> onFailure(error)
    }
}

internal fun errorText(context: android.content.Context, provider: AiProvider, e: AiException): String {
    val detail = e.detail.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
    return when (e.kind) {
        AiException.Kind.NO_KEY -> context.getString(R.string.rift_assistant_error_no_key)
        AiException.Kind.AUTH -> context.getString(R.string.rift_assistant_error_auth) + detail
        AiException.Kind.REGION -> context.getString(R.string.rift_assistant_error_region, provider.label)
        // Not the provider's own wording: say plainly that the API balance is empty and where to top it up.
        AiException.Kind.QUOTA -> context.getString(
            R.string.rift_assistant_error_quota,
            provider.label,
            (provider.billingUrl ?: provider.keysUrl)?.substringAfter("://") ?: provider.label,
        )
        AiException.Kind.RATE_LIMIT -> context.getString(R.string.rift_assistant_error_rate_limit)
        AiException.Kind.NOT_FOUND -> context.getString(R.string.rift_assistant_error_not_found) + detail
        AiException.Kind.NETWORK -> context.getString(R.string.rift_assistant_error_network)
        AiException.Kind.SERVER -> context.getString(R.string.rift_assistant_error_server) + detail
        AiException.Kind.BAD_RESPONSE -> context.getString(R.string.rift_assistant_error_bad_response) + detail
    }
}

/** A one-line text entry in Rift's dialog. [secret] hides what is typed, for keys. */
@Composable
private fun TextInputDialog(
    title: String,
    initial: String,
    placeholder: String,
    secret: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onClear: (() -> Unit)? = null,
) {
    var text by remember { mutableStateOf(initial) }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
    RiftDialog(
        title = title,
        onDismiss = onDismiss,
        actions = {
            if (onClear != null) RiftPillButton(text = stringResource(R.string.rift_assistant_remove), onClick = onClear)
            RiftPillButton(text = stringResource(R.string.rift_assistant_cancel), onClick = onDismiss)
            RiftPillButton(text = stringResource(R.string.rift_assistant_save), onClick = { onSave(text) }, primary = true)
        },
    ) {
        RiftInputPlate {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (text.isEmpty()) Text(placeholder, color = FieldHint, fontSize = 17.sp)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = TextStyle(color = FieldText, fontSize = 17.sp),
                    cursorBrush = fieldCursor(),
                    visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (secret) KeyboardType.Password else KeyboardType.Uri,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onSave(text) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(field),
                )
            }
        }
    }
}
