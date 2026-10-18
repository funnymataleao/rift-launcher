package dev.rift.launcher.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.R
import dev.rift.launcher.ui.theme.RiftColors
import dev.rift.launcher.ui.theme.RiftShapes
import dev.rift.launcher.ui.theme.RiftSizes

/**
 * The name of an Emulators or Android game, typed with the on-screen keyboard (or a real one). Saving a
 * blank name brings back the game's own. Like renaming a tab, in the same dialog.
 */
@Composable
internal fun RenameGameDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
    RiftDialog(
        title = stringResource(R.string.rift_rename_title),
        onDismiss = onDismiss,
        actions = {
            RiftPillButton(text = stringResource(R.string.rift_settings_cancel), onClick = onDismiss)
            RiftPillButton(text = stringResource(R.string.rift_settings_tab_save), onClick = { onSave(text) }, primary = true)
        },
    ) {
        RiftInputPlate {
            BasicTextField(
                value = text,
                onValueChange = { text = it.take(MAX_NAME_LENGTH) },
                singleLine = true,
                textStyle = TextStyle(color = FieldText, fontSize = 17.sp),
                cursorBrush = fieldCursor(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave(text) }),
                modifier = Modifier.weight(1f).focusRequester(field),
            )
        }
        NoteText(stringResource(R.string.rift_rename_hint))
    }
}

private const val MAX_NAME_LENGTH = 80
