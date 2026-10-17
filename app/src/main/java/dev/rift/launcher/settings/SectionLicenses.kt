package dev.rift.launcher.settings

import android.content.Context
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.Constants
import app.gamenative.R

/** One component Rift is built on: its name, licence, where its source is and the licence text or notice. */
internal data class LicenseEntry(
    val title: String,
    val license: String?,
    val url: String?,
    val paragraphs: List<String>,
)

private const val PAGE_LIST = "licenses"
private const val PAGE_ENTRY = "license:"

/** Page keys of the licence pages inside the About section. */
internal fun isLicensePage(page: String?) = page == PAGE_LIST || page?.startsWith(PAGE_ENTRY) == true

internal const val LICENSES_PAGE = PAGE_LIST

/** One page up from a licence page. */
internal fun licensePageBack(page: String): String? = if (page.startsWith(PAGE_ENTRY)) PAGE_LIST else null

/**
 * The open-source licences of everything Rift is built on, as pages of the About section: the list, then one
 * page per component. This is the only place in Rift that names the projects it grew from.
 */
@Composable
internal fun LicensesPages(page: String, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val entries = remember { loadLicenses(context) }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    fun requester(key: String) = requesters.getOrPut(key) { FocusRequester() }
    var previous by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(page) {
        val from = previous
        previous = page
        // Coming back to the list lands on the component that was open; every other page on its first row.
        val target = if (page == PAGE_LIST && from?.startsWith(PAGE_ENTRY) == true) from else "first"
        runCatching { requester(target).requestFocus() }
    }

    if (page == PAGE_LIST) {
        SettingsGroup(title = stringResource(R.string.rift_licenses_title)) {
            entries.forEachIndexed { index, entry ->
                val key = PAGE_ENTRY + index
                LinkRow(
                    modifier = Modifier
                        .focusRequester(requester(key))
                        .then(if (index == 0) Modifier.focusRequester(requester("first")) else Modifier),
                    title = entry.title,
                    subtitle = entry.license,
                    icon = Icons.Rounded.Code,
                    onClick = { onOpen(key) },
                )
            }
        }
        return
    }

    val entry = entries.getOrNull(page.removePrefix(PAGE_ENTRY).toIntOrNull() ?: -1) ?: return
    SettingsGroup(title = entry.title) {
        if (entry.url != null) {
            LinkRow(
                modifier = Modifier.focusRequester(requester("first")),
                title = stringResource(R.string.rift_licenses_source),
                subtitle = entry.url.removePrefix("https://"),
                icon = Icons.Rounded.Code,
                onClick = { uriHandler.openUri(entry.url) },
            )
        }
        entry.paragraphs.forEachIndexed { index, text ->
            TextPlate(
                text = text,
                modifier = if (index == 0 && entry.url == null) Modifier.focusRequester(requester("first")) else Modifier,
            )
        }
    }
}

/** A paragraph of licence text. Focusable, so the page can be scrolled to it with the gamepad. */
@Composable
private fun TextPlate(text: String, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Text(
        text = text,
        color = plateText(focused),
        fontSize = 15.sp,
        lineHeight = 21.sp,
        modifier = modifier
            .fillMaxWidth()
            .plateFill(focused)
            .focusable(interactionSource = interaction)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    )
}

private const val GPL_NOTICE =
    "This program is free software: you can redistribute it and/or modify it under the terms of the GNU General " +
        "Public License version 3 as published by the Free Software Foundation."
private const val GPL_WARRANTY =
    "It is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied " +
        "warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for " +
        "more details. The full text is at https://www.gnu.org/licenses/gpl-3.0.html"

/** Rift itself and the projects it is built on come first, then every component of the bundled notices file. */
private fun loadLicenses(context: Context): List<LicenseEntry> {
    val entries = mutableListOf(
        LicenseEntry(
            title = "Rift Launcher",
            license = "GPL-3.0",
            url = Constants.Misc.RIFT_SOURCE_LINK,
            paragraphs = listOf(GPL_NOTICE, GPL_WARRANTY, "The complete source code of this version is available at " + Constants.Misc.RIFT_SOURCE_LINK),
        ),
        LicenseEntry(
            title = "GameNative",
            license = "GPL-3.0",
            url = "https://github.com/utkarshdalal/GameNative",
            paragraphs = listOf(
                "Rift Launcher is a fork of GameNative, a free software project by Utkarsh Dalal and contributors. " +
                    "The app, its store integrations and its container management are distributed under the GNU General Public License version 3.",
                GPL_WARRANTY,
            ),
        ),
        LicenseEntry(
            title = "Pluvia",
            license = "GPL-3.0",
            url = "https://github.com/oxters168/Pluvia",
            paragraphs = listOf(
                "GameNative is based on Pluvia, Copyright (C) 2024 Oxters Wyzgowski, distributed under the GNU General Public License version 3.",
                GPL_WARRANTY,
            ),
        ),
        LicenseEntry(
            title = "Libraries and engine components",
            license = null,
            url = null,
            paragraphs = context.getString(R.string.libraries_message).lines().map { it.trim() }.filter { it.isNotEmpty() },
        ),
    )
    val notices = runCatching {
        context.assets.open("licenses/third_party_notices.txt").bufferedReader().use { it.readText() }
    }.getOrDefault("")
    val xcloudLicense = runCatching {
        context.assets.open("better-xcloud/LICENSE").bufferedReader().use { it.readText() }
    }.getOrDefault("")
    notices.split(Regex("(?m)^## ")).drop(1).forEach { section ->
        val title = section.substringBefore('\n').cleaned()
        val body = section.substringAfter('\n')
        val paragraphs = paragraphsOf(body).toMutableList()
        if (title.startsWith("Better xCloud") && xcloudLicense.isNotBlank()) paragraphs += paragraphsOf(xcloudLicense)
        val license = Regex("""License:\**\s*(.+)""").find(body)?.groupValues?.get(1)
            ?.cleaned()?.substringBefore(" (")?.substringBefore(';')?.substringBefore(" \u2014")?.trimEnd('.', ',', ' ')
            ?.takeIf { it.isNotBlank() && it.length <= 48 }
        if (title.startsWith("Better xCloud") && paragraphs.isNotEmpty()) {
            entries += LicenseEntry(title = "Better xCloud", license = "MIT", url = "https://github.com/redphx/better-xcloud", paragraphs = paragraphs)
        } else if (paragraphs.isNotEmpty()) {
            entries += LicenseEntry(title = title, license = license, url = null, paragraphs = paragraphs)
        }
    }
    return entries
}

/** Markdown marks off, links written out. */
private fun String.cleaned(): String = this
    .replace(Regex("""\[([^\]]+)]\(([^)]+)\)"""), "$1 ($2)")
    .replace("**", "")
    .replace("`", "")
    .replace(Regex("^#+\\s*"), "")
    .trim()

/** Paragraphs of a notices section: blank lines separate them, lines inside one are joined except list items. */
private fun paragraphsOf(body: String): List<String> =
    body.split(Regex("\n\\s*\n"))
        .map { block ->
            block.lines()
                .map { it.trimEnd() }
                .filterNot { it.trim() == "---" || it.trim().startsWith("###") }
                .joinToString("") { line ->
                    val t = line.trim()
                    when {
                        t.isEmpty() -> ""
                        t.startsWith("- ") || Regex("^\\d+\\. ").containsMatchIn(t) -> "\n$t"
                        else -> " $t"
                    }
                }
                .cleaned()
        }
        .filter { it.isNotBlank() }
