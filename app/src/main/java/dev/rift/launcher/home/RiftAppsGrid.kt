package dev.rift.launcher.home

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.R
import dev.rift.launcher.ui.theme.RiftColors
import dev.rift.launcher.ui.theme.RiftShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * B on the home, Beacon style: every installed Android app in a grid over the blurred home.
 * A opens an app, B closes the grid. Same app list as the Android games picker in settings.
 */
@Composable
fun RiftAppsGrid(isOpen: Boolean, onDismiss: () -> Unit) {
    BackHandler(enabled = isOpen) { onDismiss() }
    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn() + scaleIn(initialScale = 0.96f, animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
        exit = fadeOut() + scaleOut(targetScale = 0.96f),
    ) {
        AppsGridContent(onDismiss = onDismiss)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppsGridContent(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val apps by produceState<List<AndroidGame>?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { AndroidGames.launchable(context) }.getOrDefault(emptyList()) }
    }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(apps) { if (!apps.isNullOrEmpty()) runCatching { firstFocus.requestFocus() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .onPreviewKeyEvent { event ->
                // B closes; both halves of the press are consumed so focus never falls out.
                if (event.nativeKeyEvent.keyCode != KeyEvent.KEYCODE_BUTTON_B) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyUp) onDismiss()
                true
            },
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(top = 36.dp)) {
            Text(
                text = stringResource(R.string.rift_apps_title),
                color = RiftColors.OnSurface,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = 48.dp),
            )
            Spacer(modifier = Modifier.height(20.dp))
            val list = apps
            if (list == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator(color = RiftColors.OnSurface) }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 132.dp),
                    contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 8.dp, bottom = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(list, key = { it.packageName }) { app ->
                        AppTile(
                            app = app,
                            focusRequester = if (app == list.first()) firstFocus else null,
                            onClick = { AndroidGames.launch(context, app) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppTile(app: AndroidGame, focusRequester: FocusRequester?, onClick: () -> Unit) {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "appScale")
    val icon by produceState(AndroidGames.iconCached(app.packageName), app.packageName) {
        if (value == null) value = withContext(Dispatchers.IO) { AndroidGames.icon(context, app.packageName) }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(if (focused) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f), RiftShapes.card)
            .border(1.dp, if (focused) Color.White.copy(alpha = 0.35f) else Color.Transparent, RiftShapes.card)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            icon?.let { Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(64.dp)) }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = app.label,
            color = if (focused) RiftColors.OnSurface else RiftColors.OnSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
