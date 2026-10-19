package dev.rift.launcher.ui

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FiniteAnimationSpec
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.gamenative.R
import app.gamenative.utils.BrightnessManager
import dev.rift.launcher.settings.LocalPlateRest
import kotlin.coroutines.resume
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Rift's side panels (Denis 2026-10-17): the System panel on the home, the in-game quick menu, the
 * stream's and the cloud game's panels and the panel over Android games all look the same: dark glass
 * over a blurred picture of what lies behind, and one inset on every side, so the plates, pictures and
 * buttons of every panel stand on the same lines.
 *
 * Android's own window blur is switched off on the tablet ("Allow window-level blurs" in the developer
 * options, disable_window_blurs=1), so a panel blurs a picture of what is behind it itself
 * ([DrawerBackdrop]): the home's recorded layer, or a copy of the game's or the stream's surface or of
 * the window. The window blur is only used where nothing can be copied (the panel over another app) and
 * only while the system allows it. With nothing to blur the glass is nearly opaque black, never see-through.
 */
object RiftDrawer {
    /** The panels over a game or a stream. The home's two panels are as wide as their longest line (rememberHomePanelWidth). */
    val Width = 440.dp

    /** The one space between the panel's edge and its outermost plates, pictures and buttons, on all four sides. */
    val Edge = 20.dp

    /**
     * The black over the blurred picture: nearly black, so the panel is always darker than the dimmed
     * screen beside it and what is behind only just shows through (Denis 2026-10-17).
     */
    val Tint = Color.Black.copy(alpha = 0.88f)

    /** The tint over a blurred copy of a game or a stream (darker pictures than the home's). */
    val GameTint = Color.Black.copy(alpha = 0.62f)

    /** With nothing to blur. */
    val Solid = Color.Black.copy(alpha = 0.96f)

    /**
     * The plates in a panel (a profile, a row, a chip) without focus: a light glass over the black, so every
     * row reads as a plate (Denis 2026-10-19: black plates melted into the panel and the rows' insets looked
     * like broken padding). The chosen chip is lighter still, the focused plate takes the accent.
     */
    val Plate = Color.White.copy(alpha = 0.09f)

    val Blur = 40.dp

    /** The rail of tabs on the outer edge, and the round buttons on it. */
    val RailWidth = 56.dp
    private val RailButton = 48.dp
    internal val RailButtonInset = (RailWidth - RailButton) / 2

    /** The hairline between the open tab and the rail. */
    val SeparatorColor = Color.White.copy(alpha = 0.06f)

    /**
     * How a panel slides in from its edge: a spring without bounce, so it never runs past the edge and leaves a
     * gap behind it (Denis 2026-10-17: the bounce showed a gap on the right).
     */
    val SlideIn: FiniteAnimationSpec<IntOffset> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    /** How a panel slides out. */
    val SlideOut: FiniteAnimationSpec<IntOffset> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
}

/** What a panel's glass blurs. */
sealed interface DrawerBackdrop {
    /** The screen as a Compose layer, in root coordinates (the home records one). */
    class Layer(val layer: GraphicsLayer) : DrawerBackdrop

    /** A scaled-down copy of what lies behind the panel, covering [bounds] of the window (in pixels). */
    class Picture(val image: ImageBitmap, val bounds: Rect) : DrawerBackdrop
}

/** Whether the panel's window blurs what is behind it itself (Android's window blur, where it is allowed). */
val LocalWindowBlur = compositionLocalOf { false }

/**
 * Paints the panel's glass behind its content: [backdrop] lined up with the panel and blurred (the content
 * stays sharp), then [RiftDrawer.Tint]. Without a backdrop the tint lies on the window's own blur where
 * there is one ([LocalWindowBlur]), otherwise the glass is [RiftDrawer.Solid].
 */
@Composable
fun Modifier.drawerGlass(backdrop: DrawerBackdrop?): Modifier {
    val blurred = rememberGraphicsLayer()
    var inRoot by remember { mutableStateOf(Offset.Zero) }
    var inWindow by remember { mutableStateOf(Offset.Zero) }
    val windowBlur = LocalWindowBlur.current
    val canBlur = backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    return this
        .onGloballyPositioned {
            inRoot = it.positionInRoot()
            inWindow = it.positionInWindow()
        }
        .drawBehind {
            if (!canBlur) {
                drawRect(if (windowBlur) RiftDrawer.Tint else RiftDrawer.Solid)
                return@drawBehind
            }
            val px = RiftDrawer.Blur.toPx()
            blurred.renderEffect = BlurEffect(px, px, TileMode.Clamp)
            blurred.record(IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1))) {
                // Draw the source shifted into panel-local coordinates. Clip the source to this layer;
                // letting the blur sample a differently-sized root/surface was what produced the bright
                // vertical blur seam at the drawer's left edge.
                clipRect(0f, 0f, size.width, size.height) {
                    when (backdrop) {
                        is DrawerBackdrop.Layer -> translate(-inRoot.x, -inRoot.y) { drawLayer(backdrop.layer) }
                        is DrawerBackdrop.Picture -> drawImage(
                            image = backdrop.image,
                            dstOffset = IntOffset((backdrop.bounds.left - inWindow.x).roundToInt(), (backdrop.bounds.top - inWindow.y).roundToInt()),
                            dstSize = IntSize(backdrop.bounds.width.roundToInt(), backdrop.bounds.height.roundToInt()),
                            filterQuality = FilterQuality.Low,
                        )
                        null -> Unit
                    }
                }
            }
            // Rift: the blur spreads past the layer's edges by its radius; clipped, or it lay as a blurred strip
            // beside the panel (in games, on the left of the quick menu).
            clipRect(0f, 0f, size.width, size.height) { drawLayer(blurred) }
            // A copy of a game is mostly dark already: a lighter tint, or the glass read as plain black.
            drawRect(if (backdrop is DrawerBackdrop.Picture) RiftDrawer.GameTint else RiftDrawer.Tint)
        }
}

/**
 * The frame of every side panel: the glass, [RiftDrawer.Edge] on all four sides (inside the system bars),
 * the open page in a column and, when there is one, the [rail] of tabs on the outer edge behind a hairline.
 * [rowInset] is the space the content's rows keep on their sides themselves (the quick menu's rows keep
 * 8 dp), so their plates still start [RiftDrawer.Edge] from the panel's edge.
 */
@Composable
fun RiftDrawerPanel(
    modifier: Modifier = Modifier,
    backdrop: DrawerBackdrop? = null,
    rowInset: Dp = 0.dp,
    rail: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.drawerGlass(backdrop)) {
      CompositionLocalProvider(LocalPlateRest provides RiftDrawer.Plate) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(
                    start = RiftDrawer.Edge - rowInset,
                    end = if (rail != null) RiftDrawer.Edge - RiftDrawer.RailButtonInset else RiftDrawer.Edge - rowInset,
                    top = RiftDrawer.Edge,
                    bottom = RiftDrawer.Edge,
                ),
        ) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), content = content)
            if (rail != null) {
                // The hairline sits as far from the plates as from the rail's buttons.
                Box(
                    modifier = Modifier
                        .padding(start = RiftDrawer.Edge - rowInset, end = RiftDrawer.Edge - RiftDrawer.RailButtonInset)
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(RiftDrawer.SeparatorColor),
                )
                Column(
                    modifier = Modifier.width(RiftDrawer.RailWidth).fillMaxHeight().focusGroup(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = rail,
                )
            }
        }
      }
    }
}

/** How much smaller than the screen the copies behind a panel are: they are blurred anyway. */
private const val BACKDROP_SCALE = 8

/** A live copy is taken this often while a panel is open. */
private const val BACKDROP_FRAME_MS = 50L

private const val TAG = "RiftDrawer"

/**
 * A live copy of [surface] (a game's or a stream's picture, which Compose can't see) for a panel's
 * glass, taken a few dozen times a second while [active]. The last copy stays while the panel slides
 * out. Null when the surface can't be copied: the glass is solid then.
 */
@Composable
fun rememberSurfaceBackdrop(surface: SurfaceView?, active: Boolean): DrawerBackdrop.Picture? {
    var picture by remember { mutableStateOf<DrawerBackdrop.Picture?>(null) }
    LaunchedEffect(surface, active) {
        if (!active || surface == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@LaunchedEffect
        // Two buffers: the copy goes into the one the glass isn't drawing.
        val buffers = arrayOfNulls<Bitmap>(2)
        var next = 0
        val main = Handler(Looper.getMainLooper())
        while (isActive) {
            val width = surface.width / BACKDROP_SCALE
            val height = surface.height / BACKDROP_SCALE
            if (width <= 0 || height <= 0 || !surface.holder.surface.isValid) {
                delay(BACKDROP_FRAME_MS)
                continue
            }
            val buffer = buffers[next]?.takeIf { it.width == width && it.height == height }
                ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { buffers[next] = it }
            val result = suspendCancellableCoroutine { done ->
                runCatching { PixelCopy.request(surface, buffer, { done.resume(it) }, main) }
                    .onFailure { done.resume(PixelCopy.ERROR_UNKNOWN) }
            }
            if (result != PixelCopy.SUCCESS) {
                // No frame yet, or a picture that can't be read back: plain dark glass.
                Log.i(TAG, "glass: surface copy failed ($result), dark glass")
                if (picture == null) return@LaunchedEffect
                delay(BACKDROP_FRAME_MS)
                continue
            }
            val at = IntArray(2).also(surface::getLocationInWindow)
            picture = DrawerBackdrop.Picture(
                buffer.asImageBitmap(),
                Rect(at[0].toFloat(), at[1].toFloat(), (at[0] + surface.width).toFloat(), (at[1] + surface.height).toFloat()),
            )
            next = 1 - next
            delay(BACKDROP_FRAME_MS)
        }
    }
    return picture
}

/**
 * One copy of this window as the panel opens (a web page behind it, which Compose can't see), for the
 * panel's glass. The panel is still off screen then, so the copy is what lies behind it.
 */
@Composable
fun rememberWindowBackdrop(active: Boolean): DrawerBackdrop.Picture? {
    val view = LocalView.current
    var picture by remember { mutableStateOf<DrawerBackdrop.Picture?>(null) }
    LaunchedEffect(active) {
        if (!active || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@LaunchedEffect
        val window = BrightnessManager.findActivity(view.context)?.window ?: return@LaunchedEffect
        val decor = window.decorView
        val width = decor.width / BACKDROP_SCALE
        val height = decor.height / BACKDROP_SCALE
        if (width <= 0 || height <= 0) return@LaunchedEffect
        val buffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val result = suspendCancellableCoroutine { done ->
            runCatching { PixelCopy.request(window, buffer, { done.resume(it) }, Handler(Looper.getMainLooper())) }
                .onFailure { done.resume(PixelCopy.ERROR_UNKNOWN) }
        }
        if (result == PixelCopy.SUCCESS) {
            picture = DrawerBackdrop.Picture(buffer.asImageBitmap(), Rect(0f, 0f, decor.width.toFloat(), decor.height.toFloat()))
        } else {
            Log.i(TAG, "glass: window copy failed ($result), dark glass")
        }
    }
    return picture
}

/**
 * Lays a panel's own window along the right edge of the screen: full height, [RiftDrawer.Width] wide (at
 * most 85 % of the screen), sliding in and out, nothing dimmed. The window is see-through; the panel paints
 * its glass with [drawerGlass]. Where Android allows window blur, the window also blurs what is behind it,
 * and [onWindowBlur] hears whether it does (the system can switch it off and on, for example to save power).
 */
fun Window.layOutAsDrawer(context: Context, onWindowBlur: (Boolean) -> Unit) {
    val density = context.resources.displayMetrics.density
    setFormat(PixelFormat.TRANSLUCENT)
    setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
    setDimAmount(0f)
    setWindowAnimations(R.style.RiftOverlayPanelAnimation)
    addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
    val screenDp = context.resources.displayMetrics.widthPixels / density
    setLayout((min(RiftDrawer.Width.value, screenDp * 0.85f) * density).toInt(), ViewGroup.LayoutParams.MATCH_PARENT)
    setGravity(Gravity.END or Gravity.TOP)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val listener = java.util.function.Consumer<Boolean> { enabled ->
        setBackgroundBlurRadius(if (enabled) (RiftDrawer.Blur.value * density).toInt() else 0)
        onWindowBlur(enabled)
    }
    decorView.addOnAttachStateChangeListener(
        object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                windowManager.addCrossWindowBlurEnabledListener(context.mainExecutor, listener)
            }

            override fun onViewDetachedFromWindow(v: View) {
                windowManager.removeCrossWindowBlurEnabledListener(listener)
            }
        },
    )
}
