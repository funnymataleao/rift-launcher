package app.gamenative.ui.component

import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import dev.rift.launcher.ui.theme.RiftAccent
import dev.rift.launcher.ui.theme.RiftColors
import dev.rift.launcher.ui.theme.RiftShapes

/**
 * OutlinedTextField wrapper that sets IME_FLAG_NO_EXTRACT_UI on the EditorInfo.
 * Works around a GBoard bug where the composition overlay renders stale text
 * on mid-text deletion in Compose TextFields.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun NoExtractOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions? = null,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape? = null,
    colors: TextFieldColors? = null,
) {
    val focusManager = LocalFocusManager.current

    // Rift: a plate without an outline, grey at rest and filled with the accent while it has focus.
    val focused by interactionSource.collectIsFocusedAsState()
    val accent = RiftAccent.current
    val fieldShape = shape ?: if (singleLine) RiftShapes.full else RiftShapes.card
    val fieldColors = colors ?: riftTextFieldColors(focused)
    val fieldModifier = modifier
        .clip(fieldShape)
        .then(if (focused) Modifier.background(accent.brush) else Modifier)

    val resolvedOptions = if (keyboardOptions.imeAction == ImeAction.Default && singleLine) {
        keyboardOptions.copy(imeAction = ImeAction.Done)
    } else {
        keyboardOptions
    }

    val resolvedActions = keyboardActions
        ?: if (singleLine) KeyboardActions(onDone = { focusManager.clearFocus() })
        else KeyboardActions.Default

    // A focused Compose text field swallows D-pad up/down (for cursor movement),
    // which traps controller focus on the field. In a single-line field up/down are
    // never used for editing, so intercept them *before* the field and turn them into
    // focus moves, letting a gamepad navigate past the field. Left/right (cursor) and
    // typing are untouched.
    val gamepadModifier = if (singleLine) {
        fieldModifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionUp -> {
                    focusManager.moveFocus(FocusDirection.Up)
                    true
                }
                Key.DirectionDown -> {
                    focusManager.moveFocus(FocusDirection.Down)
                    true
                }
                else -> false
            }
        }
    } else {
        fieldModifier
    }

    InterceptPlatformTextInput(
        interceptor = { request, nextHandler ->
            val modifiedRequest = PlatformTextInputMethodRequest { outAttributes ->
                request.createInputConnection(outAttributes).also {
                    outAttributes.imeOptions = outAttributes.imeOptions or
                        EditorInfo.IME_FLAG_NO_EXTRACT_UI
                }
            }
            nextHandler.startInputMethod(modifiedRequest)
        },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = gamepadModifier,
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle,
            label = label,
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            prefix = prefix,
            suffix = suffix,
            supportingText = supportingText,
            isError = isError,
            visualTransformation = visualTransformation,
            keyboardOptions = resolvedOptions,
            keyboardActions = resolvedActions,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            interactionSource = interactionSource,
            shape = fieldShape,
            colors = fieldColors,
        )
    }
}

/** Colours of a Rift text plate: grey at rest, dark on the accent fill (drawn behind it) while focused. */
@Composable
private fun riftTextFieldColors(focused: Boolean): TextFieldColors {
    val accent = RiftAccent.current
    val text = if (focused) accent.onAccent else RiftColors.OnSurface
    val hint = if (focused) accent.onAccent.copy(alpha = 0.6f) else RiftColors.OnSurfaceVariant
    val container = if (focused) Color.Transparent else RiftColors.SurfaceContainerHigh
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = text,
        unfocusedTextColor = text,
        disabledTextColor = text.copy(alpha = 0.5f),
        focusedContainerColor = container,
        unfocusedContainerColor = container,
        disabledContainerColor = container,
        cursorColor = text,
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        disabledBorderColor = Color.Transparent,
        errorBorderColor = RiftColors.Error,
        focusedLabelColor = hint,
        unfocusedLabelColor = hint,
        focusedPlaceholderColor = hint,
        unfocusedPlaceholderColor = hint,
        focusedLeadingIconColor = text,
        unfocusedLeadingIconColor = hint,
        focusedTrailingIconColor = text,
        unfocusedTrailingIconColor = hint,
    )
}
