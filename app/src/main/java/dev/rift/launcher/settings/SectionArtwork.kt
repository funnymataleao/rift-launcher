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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.OpenInNew
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.R
import dev.rift.launcher.ui.theme.RiftColors
import dev.rift.launcher.ui.theme.RiftShapes
import dev.rift.launcher.ui.theme.RiftSizes

private const val API_KEY_PAGE = "https://www.steamgriddb.com/profile/preferences/api"

/** The user's own SteamGridDB key, which the game-art picker (Y, "Change art") needs. */
@Composable
internal fun ArtworkSettings() {
    val prefs = rememberRiftPrefs()
    val uriHandler = LocalUriHandler.current
    var key by remember { mutableStateOf(prefs.steamGridDbKey) }
    var editing by remember { mutableStateOf(false) }

    SettingsGroup {
        LinkRow(
            title = stringResource(R.string.rift_settings_art_key),
            subtitle = stringResource(R.string.rift_settings_art_key_summary),
            icon = Icons.Rounded.Key,
            value = if (key.isBlank()) stringResource(R.string.rift_settings_art_key_none) else maskedKey(key),
            onClick = { editing = true },
        )
        LinkRow(
            title = stringResource(R.string.rift_settings_art_get),
            subtitle = stringResource(R.string.rift_settings_art_get_summary),
            icon = Icons.Rounded.OpenInNew,
            onClick = { uriHandler.openUri(API_KEY_PAGE) },
        )
        if (key.isNotBlank()) {
            LinkRow(
                title = stringResource(R.string.rift_settings_art_remove),
                icon = Icons.Rounded.Delete,
                danger = true,
                onClick = {
                    prefs.steamGridDbKey = ""
                    key = ""
                },
            )
        }
    }
    NoteText(stringResource(R.string.rift_settings_art_note), underGroup = true)

    if (editing) {
        KeyDialog(
            initial = key,
            onSave = {
                prefs.steamGridDbKey = it
                key = prefs.steamGridDbKey
                editing = false
            },
            onDismiss = { editing = false },
        )
    }
}

/** "••••1a2b": enough to recognise the key, never the whole of it. */
private fun maskedKey(key: String): String = "••••" + key.trim().takeLast(4)

@Composable
private fun KeyDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val clipboard = LocalClipboardManager.current
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
    RiftDialog(
        title = stringResource(R.string.rift_settings_art_key),
        onDismiss = onDismiss,
        actions = {
            RiftPillButton(
                text = stringResource(R.string.rift_settings_art_paste),
                icon = Icons.Rounded.ContentPaste,
                onClick = { clipboard.getText()?.text?.let { text = it.trim() } },
            )
            RiftPillButton(text = stringResource(R.string.rift_settings_cancel), onClick = onDismiss)
            RiftPillButton(text = stringResource(R.string.rift_settings_tab_save), onClick = { onSave(text) }, primary = true)
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = RiftSizes.button)
                .background(RiftColors.SurfaceContainerHigh, RiftShapes.full)
                .border(1.dp, RiftColors.OutlineVariant, RiftShapes.full)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it.filterNot(Char::isWhitespace).take(MAX_KEY_LENGTH) },
                singleLine = true,
                textStyle = TextStyle(color = RiftColors.OnSurface, fontSize = 17.sp),
                cursorBrush = SolidColor(RiftColors.OnSurface),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave(text) }),
                modifier = Modifier.weight(1f).focusRequester(field),
            )
        }
        NoteText(stringResource(R.string.rift_settings_art_key_hint))
    }
}

private const val MAX_KEY_LENGTH = 128
