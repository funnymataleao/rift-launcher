package dev.rift.launcher.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.R
import dev.rift.launcher.assistant.ai.AiConnection
import dev.rift.launcher.assistant.ai.AiException
import dev.rift.launcher.assistant.ai.AiModel
import dev.rift.launcher.assistant.ai.AiProvider
import dev.rift.launcher.assistant.ai.AiSettings
import dev.rift.launcher.ui.theme.RiftAccent
import dev.rift.launcher.ui.theme.RiftColors
import dev.rift.launcher.ui.theme.RiftShapes
import kotlin.coroutines.cancellation.CancellationException

private sealed interface PickerState {
    data object Loading : PickerState
    data class Choose(val models: List<AiModel>) : PickerState
    /** Typing the id by hand: the list could not be had ([reason] says why), or the user asked to. */
    data class Manual(val reason: String?) : PickerState
}

/**
 * Picks the model for [connection] from the provider's own list (Anthropic and the OpenAI-compatible services all
 * publish one at `/models`), loaded with the saved key each time it opens. The current model is marked. When the
 * list can't be had, the reason is shown above a field to type the id; "Enter a model id…" at the end of the list
 * opens the same field. Used by Settings › AI assistant and by the model line on the assistant page.
 */
@Composable
internal fun AssistantModelPicker(connection: AiConnection, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { AiSettings.of(context) }
    val current = remember { settings.model(connection) }
    var state by remember { mutableStateOf<PickerState>(PickerState.Loading) }

    LaunchedEffect(connection.id) {
        state = try {
            val models = loadModels(settings, connection)
            if (models.isEmpty()) PickerState.Manual(context.getString(R.string.rift_assistant_models_empty)) else PickerState.Choose(models)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PickerState.Manual(context.getString(R.string.rift_assistant_models_failed, failureText(context, connection.type, e)))
        }
    }

    when (val shown = state) {
        PickerState.Loading -> LoadingDialog(onDismiss)
        is PickerState.Choose -> {
            val models = shown.models
            RiftChoiceDialog(
                title = stringResource(R.string.rift_assistant_model),
                options = models.map { if (it.name != it.id) "${it.name} · ${it.id}" else it.id } +
                    stringResource(R.string.rift_assistant_model_manual),
                // A saved model the provider no longer lists is marked nowhere; the field below shows it.
                selectedIndex = models.indexOfFirst { it.id == current },
                onSelect = {
                    if (it == models.size) {
                        state = PickerState.Manual(null)
                    } else {
                        settings.setModel(connection, models[it].id)
                    }
                },
                // Choosing an option also dismisses; going on to the field must not close the picker.
                onDismiss = { if (state !is PickerState.Manual) onDismiss() },
            )
        }
        is PickerState.Manual -> ManualModelDialog(
            initial = current.orEmpty(),
            placeholder = connection.type.recommendedModel ?: "model-id",
            reason = shown.reason,
            onDismiss = onDismiss,
            onSave = {
                if (it.isNotBlank()) settings.setModel(connection, it.trim())
                onDismiss()
            },
        )
    }
}

/** The provider's models: Anthropic's newest first, as it sends them; the others sorted by id. */
private suspend fun loadModels(settings: AiSettings, connection: AiConnection): List<AiModel> {
    val models = settings.clientFor(connection).listModels().distinctBy { it.id }
    return if (connection.type == AiProvider.Anthropic) models else models.sortedBy { it.id.lowercase() }
}

private fun failureText(context: Context, provider: AiProvider, e: Exception): String =
    if (e is AiException) errorText(context, provider, e) else context.getString(R.string.rift_assistant_error_other, e.javaClass.simpleName)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingDialog(onDismiss: () -> Unit) {
    RiftDialog(
        title = stringResource(R.string.rift_assistant_model),
        onDismiss = onDismiss,
        actions = {
            val cancel = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { cancel.requestFocus() } }
            RiftPillButton(text = stringResource(R.string.rift_assistant_cancel), onClick = onDismiss, modifier = Modifier.focusRequester(cancel))
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LoadingIndicator(color = RiftAccent.current.main, modifier = Modifier.size(40.dp))
            Text(stringResource(R.string.rift_assistant_loading_models), color = RiftColors.OnSurfaceVariant, fontSize = 16.sp)
        }
    }
}

@Composable
private fun ManualModelDialog(
    initial: String,
    placeholder: String,
    reason: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    var focused by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
    RiftDialog(
        title = stringResource(R.string.rift_assistant_model),
        onDismiss = onDismiss,
        actions = {
            RiftPillButton(text = stringResource(R.string.rift_assistant_cancel), onClick = onDismiss)
            RiftPillButton(text = stringResource(R.string.rift_assistant_save), onClick = { onSave(text) }, primary = true)
        },
    ) {
        reason?.let { Text(text = it, color = RiftColors.Warning, fontSize = 15.sp, lineHeight = 21.sp) }
        // A text field is never filled with the accent: dark grey, a lighter grey while it has focus, accent cursor.
        Row(
            modifier = Modifier
                .onFocusChanged { focused = it.hasFocus }
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RiftShapes.full)
                .background(if (focused) FIELD_FOCUSED else RiftColors.SurfaceContainerHigh)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (text.isEmpty()) Text(placeholder, color = RiftColors.OnSurfaceVariant, fontSize = 17.sp)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = TextStyle(color = RiftColors.OnSurface, fontSize = 17.sp),
                    cursorBrush = SolidColor(RiftAccent.current.main),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
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

private val FIELD_FOCUSED = Color(0xFF333333)
