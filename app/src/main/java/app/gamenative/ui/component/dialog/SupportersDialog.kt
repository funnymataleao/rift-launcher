package app.gamenative.ui.component.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.gamenative.PluviaApp
import app.gamenative.utils.KofiSupporter
import app.gamenative.utils.fetchKofiSupporters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SupportersDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    var supporters by remember { mutableStateOf<List<KofiSupporter>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isLoading = true
        val data = withContext(Dispatchers.IO) {
            fetchKofiSupporters(PluviaApp.supabase)
        }
        supporters = data
        isLoading = false
    }

    val members = remember(supporters) {
        supporters.filter { it.oneOff == false }.sortedByDescending { it.total ?: 0.0 }
    }
    val oneOffs = remember(supporters) {
        supporters.filter { it.oneOff != false }.sortedByDescending { it.total ?: 0.0 }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text("Hall of Fame") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isLoading) {
                    Text("Loading…")
                } else {
                    if (members.isNotEmpty()) {
                        Text(
                            text = "Members",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                            members.forEach { sup ->
                                Text(
                                    text = (sup.name ?: "Anonymous"),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    if (oneOffs.isNotEmpty()) {
                        Text(
                            text = "Supporters",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            oneOffs.forEach { sup ->
                                Text(text = (sup.name ?: "Anonymous"))
                            }
                        }
                    }

                    if (members.isEmpty() && oneOffs.isEmpty()) {
                        Text("No supporters yet.")
                    }
                }
            }
        }
    )
}


