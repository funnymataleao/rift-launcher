package dev.rift.launcher.stream

import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.activity.addCallback
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.gamenative.R
import app.gamenative.ui.util.SnackbarManager
import com.limelight.binding.PlatformBinding
import com.limelight.binding.audio.AndroidAudioRenderer
import com.limelight.binding.video.DecoderConfiguration
import com.limelight.binding.video.MediaCodecDecoderRenderer
import com.limelight.binding.video.MediaCodecHelper
import com.limelight.binding.video.PerfOverlayListener
import com.limelight.nvstream.NvConnection
import com.limelight.nvstream.NvConnectionListener
import com.limelight.nvstream.StreamConfiguration
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.input.ControllerPacket
import com.limelight.nvstream.input.MouseButtonPacket
import com.limelight.nvstream.jni.MoonBridge
import dev.rift.launcher.home.RiftControls
import dev.rift.launcher.overlay.OverlayPanel
import dev.rift.launcher.ui.BlackGlass
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Full-screen stream on top of the Artemis core, configured from [StreamSettings].
 * Input: one gamepad (with vibration) and touch as an absolute mouse.
 * Leave: Select + Start (or Back, or the button set in [RiftControls.streamMenuKey]) opens the side
 * panel with "End stream".
 */
class StreamActivity : ComponentActivity(), SurfaceHolder.Callback, NvConnectionListener, PerfOverlayListener {

    companion object {
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_HTTPS_PORT = "httpsPort"
        const val EXTRA_UNIQUE_ID = "uniqueId"
        const val EXTRA_SERVER_CERT = "serverCert"
        const val EXTRA_PC_NAME = "pcName"
        const val EXTRA_APP_NAME = "appName"
        const val EXTRA_APP_ID = "appId"
        const val EXTRA_APP_UUID = "appUuid"
        const val EXTRA_APP_HDR = "appHdr"

        /** The host can make a virtual screen of the stream's own size and rate (Apollo/Vibepollo). */
        const val EXTRA_VIRTUAL_DISPLAY_READY = "virtualDisplayReady"

        /** Set by a reconnect from the panel: it opens again on this row once the stream is back. */
        private const val EXTRA_PANEL_ROW = "panelRow"

        /** How long the panel waits after the last change of a picture setting before reconnecting. */
        private const val APPLY_DELAY_MS = 1200L

        /** The panel's copy of the stream is this many times smaller than the screen, and this fresh. */
        private const val BACKDROP_SCALE = 8
        private const val BACKDROP_FRAME_MS = 33L

        private const val TAG = "RiftStream"
        private const val MENU_COMBO = ControllerPacket.BACK_FLAG or ControllerPacket.PLAY_FLAG

        /** How long Select or Start waits for the other one before it goes to the PC. */
        private const val COMBO_WINDOW_MS = 150L
        private const val SUPPORTED_BUTTONS = ControllerPacket.A_FLAG or ControllerPacket.B_FLAG or
            ControllerPacket.X_FLAG or ControllerPacket.Y_FLAG or ControllerPacket.LB_FLAG or ControllerPacket.RB_FLAG or
            ControllerPacket.PLAY_FLAG or ControllerPacket.BACK_FLAG or ControllerPacket.LS_CLK_FLAG or
            ControllerPacket.RS_CLK_FLAG or ControllerPacket.SPECIAL_BUTTON_FLAG or
            ControllerPacket.UP_FLAG or ControllerPacket.DOWN_FLAG or ControllerPacket.LEFT_FLAG or ControllerPacket.RIGHT_FLAG
        private const val DPAD_FLAGS = ControllerPacket.UP_FLAG or ControllerPacket.DOWN_FLAG or
            ControllerPacket.LEFT_FLAG or ControllerPacket.RIGHT_FLAG
    }

    private lateinit var surfaceView: SurfaceView
    private lateinit var panel: PanelWindow
    private lateinit var statusText: TextView
    private lateinit var messageText: TextView
    private val hideMessage = Runnable { messageText.visibility = View.GONE }
    private lateinit var decoder: MediaCodecDecoderRenderer
    private lateinit var conn: NvConnection
    private lateinit var settings: StreamSettings
    private var ready = false
    private var started = false
    private var connected = false
    private var stopped = false
    private var streamWidth = 1920
    private var streamHeight = 1080
    private var panelOpen by mutableStateOf(false)

    /** The side panel; opening and closing it shows and hides its window. */
    private var menuOpen: Boolean
        get() = panelOpen
        set(value) {
            panelOpen = value
            surfaceView.removeCallbacks(copyBackdrop)
            if (value) {
                backdropFailed = false
                panel.show()
                surfaceView.post(copyBackdrop)
            } else {
                panel.dismiss()
                backdrop = null
            }
        }

    /** A small copy of the stream behind the panel, which the panel blurs into its glass. */
    private var backdrop by mutableStateOf<ImageBitmap?>(null)
    private val backdropBuffers = arrayOfNulls<Bitmap>(2)
    private var backdropBuffer = 0
    private var backdropFailed = false
    private val copyBackdrop: Runnable = Runnable { requestBackdrop() }
    private val mainHandler = Handler(Looper.getMainLooper())
    private var panelRow by mutableStateOf(StreamMenuRow.END)
    private var maxFps by mutableStateOf(60)
    private var screenSize by mutableStateOf<Pair<Int, Int>?>(null)
    private var changedRow = StreamMenuRow.END
    private var bTaken = false
    private val applyPicture = Runnable { restartStream(changedRow) }

    /** Why the stream broke, shown in the panel; null while it runs. */
    private var streamError by mutableStateOf<String?>(null)
    private var menuKey = KeyEvent.KEYCODE_UNKNOWN

    /** Select or Start held back from the PC while the other half of the combo may still come. */
    private var pendingFlags = 0
    private val sendPending = Runnable {
        if (pendingFlags != 0) {
            buttons = buttons or pendingFlags
            pendingFlags = 0
            sendController()
        }
    }
    private lateinit var host: String
    private lateinit var uniqueId: String
    private var serverCert: X509Certificate? = null

    private var buttons = 0
    private var leftTrigger = 0
    private var rightTrigger = 0
    private var lsX = 0
    private var lsY = 0
    private var rsX = 0
    private var rsY = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = StreamSettings(this)
        menuKey = RiftControls.streamMenuKey(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        surfaceView = SurfaceView(this)
        statusText = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(0x99000000.toInt())
            textSize = 13f
            setPadding(24, 12, 24, 12)
        }
        messageText = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(0xCC000000.toInt())
            textSize = 18f
            setPadding(40, 20, 40, 20)
            visibility = View.GONE
        }
        setContentView(
            FrameLayout(this).apply {
                setBackgroundColor(Color.BLACK)
                addView(surfaceView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER))
                addView(statusText, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START))
                addView(
                    messageText,
                    FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
                        .apply { bottomMargin = 64 },
                )
            },
        )
        panel = PanelWindow()
        onBackPressedDispatcher.addCallback(this) {
            if (streamError != null) {
                finish()
            } else if (menuOpen) {
                menuOpen = false
            } else {
                openMenu()
            }
        }

        host = intent.getStringExtra(EXTRA_HOST) ?: return finish()
        uniqueId = intent.getStringExtra(EXTRA_UNIQUE_ID) ?: return finish()
        val appSupportsHdr = intent.getBooleanExtra(EXTRA_APP_HDR, false)
        val app = NvApp(
            intent.getStringExtra(EXTRA_APP_NAME) ?: "app",
            intent.getStringExtra(EXTRA_APP_UUID),
            intent.getIntExtra(EXTRA_APP_ID, StreamConfiguration.INVALID_APP_ID),
            appSupportsHdr,
        )
        serverCert = intent.getByteArrayExtra(EXTRA_SERVER_CERT)?.let {
            CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(it)) as X509Certificate
        }
        statusText.text = getString(R.string.rift_stream_connecting, intent.getStringExtra(EXTRA_PC_NAME) ?: host)

        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay
        val size = Point()
        @Suppress("DEPRECATION")
        display.getRealSize(size)
        val nativeWidth = max(size.x, size.y) and 1.inv()
        val nativeHeight = min(size.x, size.y) and 1.inv()
        screenSize = nativeWidth to nativeHeight
        val fixedSize = StreamSettings.sizeOf(settings.resolution)
        if (fixedSize == null) {
            streamWidth = nativeWidth
            streamHeight = nativeHeight
        } else {
            streamWidth = fixedSize.first
            streamHeight = fixedSize.second
        }
        // The screen runs at its highest rate for the stream (Android keeps it at 60 Hz otherwise), and
        // the stream's frame rate is the screen's unless one is set.
        val current = display.mode
        val modes = display.supportedModes.filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
        val fastest = modes.maxByOrNull { it.refreshRate } ?: current
        maxFps = fastest.refreshRate.roundToInt()
        val fps = if (settings.fps > 0) settings.fps else maxFps
        val mode = modes.firstOrNull { abs(it.refreshRate - fps) < 1f } ?: fastest
        window.attributes = window.attributes.apply { preferredDisplayModeId = mode.modeId }
        val refreshRate = mode.refreshRate
        val bitrate = settings.bitrateMbps.takeIf { it > 0 }?.times(1000) ?: StreamSettings.autoBitrateKbps(streamWidth, streamHeight, fps)
        val virtualDisplay = settings.virtualDisplay && intent.getBooleanExtra(EXTRA_VIRTUAL_DISPLAY_READY, false)
        Log.i(TAG, "stream ${streamWidth}x$streamHeight@$fps (screen ${refreshRate}Hz), ${bitrate}kbps, codec=${settings.codec}, hdr=${settings.hdr}, virtual display=$virtualDisplay")

        val decoderConfig = DecoderConfiguration().apply {
            this.fps = fps.toFloat()
            this.bitrate = bitrate
            videoFormat = when (settings.codec) {
                "h264" -> DecoderConfiguration.FormatOption.FORCE_H264
                "hevc" -> DecoderConfiguration.FormatOption.FORCE_HEVC
                "av1" -> DecoderConfiguration.FormatOption.FORCE_AV1
                else -> DecoderConfiguration.FormatOption.AUTO
            }
            fullRange = settings.fullRange
            enableUltraLowLatency = settings.ultraLowLatency
            framePacing = settings.framePacing.coerceIn(
                DecoderConfiguration.FRAME_PACING_MIN_LATENCY,
                DecoderConfiguration.FRAME_PACING_MAX_SMOOTHNESS,
            )
            // Always measured, so the overlay can be switched on from the side panel mid-stream.
            enablePerfOverlay = true
        }
        // HDR only when the app supports it and the screen takes HDR10, as Artemis checks.
        val displayHdr10 = display.hdrCapabilities?.supportedHdrTypes?.contains(Display.HdrCapabilities.HDR_TYPE_HDR10) == true
        val wantHdr = settings.hdr && appSupportsHdr && displayHdr10

        // Artemis initializes the decoder quirks table before any decoder is created.
        val glRenderer = GlRenderer.get(this)
        MediaCodecHelper.initialize(this, glRenderer)
        decoder = MediaCodecDecoderRenderer(this, decoderConfig, { }, 0, false, wantHdr, false, glRenderer, this)
        if (!decoder.isAvcSupported) {
            SnackbarManager.show(getString(R.string.rift_stream_no_decoder))
            return finish()
        }

        var videoFormats = MoonBridge.VIDEO_FORMAT_H264
        if (decoder.isHevcSupported) {
            videoFormats = videoFormats or MoonBridge.VIDEO_FORMAT_H265
            if (wantHdr && decoder.isHevcMain10Hdr10Supported) videoFormats = videoFormats or MoonBridge.VIDEO_FORMAT_H265_MAIN10
        }
        if (decoder.isAv1Supported) {
            videoFormats = videoFormats or MoonBridge.VIDEO_FORMAT_AV1_MAIN8
            if (wantHdr && decoder.isAv1Main10Supported) videoFormats = videoFormats or MoonBridge.VIDEO_FORMAT_AV1_MAIN10
        }

        val config = StreamConfiguration.Builder()
            .setResolution(streamWidth, streamHeight)
            .setLaunchRefreshRate(fps.toFloat())
            .setRefreshRate(fps.toFloat())
            .setApp(app)
            .setBitrate(bitrate)
            .setEnableSops(settings.optimizeGameSettings)
            .setVirtualDisplay(virtualDisplay)
            .enableAdaptiveResolution(settings.adaptiveResolution)
            .setEnableUltraLowLatency(settings.ultraLowLatency)
            .enableLocalAudioPlayback(settings.playLocalAudio)
            .setMaxPacketSize(1392)
            .setRemoteConfiguration(
                when (settings.connection) {
                    "local" -> StreamConfiguration.STREAM_CFG_LOCAL
                    "remote" -> StreamConfiguration.STREAM_CFG_REMOTE
                    else -> StreamConfiguration.STREAM_CFG_AUTO
                },
            )
            .setSupportedVideoFormats(videoFormats)
            .setAttachedGamepadMaskByCount(settings.gamepadCount.takeIf { it > 0 } ?: connectedGamepads().coerceIn(1, 4))
            .setPersistGamepadsAfterDisconnect(settings.keepGamepads)
            .setClientRefreshRateX100((refreshRate * 100).toInt())
            .setAudioConfiguration(
                when (settings.audio) {
                    "5.1" -> MoonBridge.AUDIO_CONFIGURATION_51_SURROUND
                    "7.1" -> MoonBridge.AUDIO_CONFIGURATION_71_SURROUND
                    else -> MoonBridge.AUDIO_CONFIGURATION_STEREO
                },
            )
            .setColorSpace(decoder.preferredColorSpace)
            .setColorRange(decoder.preferredColorRange)
            .build()

        conn = NvConnection(
            applicationContext,
            ComputerDetails.AddressTuple(host, intent.getIntExtra(EXTRA_PORT, 47989)),
            intent.getIntExtra(EXTRA_HTTPS_PORT, 0),
            uniqueId,
            config,
            PlatformBinding.getCryptoProvider(this),
            serverCert,
        )
        ready = true
        surfaceView.holder.addCallback(this)
    }

    private fun hideSystemBars() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (ready && !started) {
            started = true
            decoder.setRenderTarget(holder.surface)
            conn.start(AndroidAudioRenderer(this, false), decoder, this)
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) = stopStream()

    override fun onStop() {
        super.onStop()
        stopStream()
        finish()
    }

    private fun stopStream(quitOnPc: Boolean = false) {
        val stopConnection = started && !stopped
        if (stopConnection) {
            stopped = true
            stopRumble()
            decoder.prepareForStop()
        }
        if (!stopConnection && !quitOnPc) return
        val port = intent.getIntExtra(EXTRA_PORT, 47989)
        val httpsPort = intent.getIntExtra(EXTRA_HTTPS_PORT, 0)
        val crypto = PlatformBinding.getCryptoProvider(applicationContext)
        val endFailed = getString(R.string.rift_stream_end_failed)
        thread {
            if (stopConnection) conn.stop()
            // Quitting the app is what makes the host drop its virtual display and wake the PC's monitor.
            if (quitOnPc) {
                runCatching { NvHTTP(ComputerDetails.AddressTuple(host, port), httpsPort, uniqueId, serverCert, crypto).quitApp() }
                    .onFailure { SnackbarManager.show(endFailed) }
            }
        }
    }

    /** "End stream": stops the stream, quits the game on the PC and returns to the PC streaming tab. */
    private fun endStream() {
        menuOpen = false
        stopStream(quitOnPc = true)
        finish()
    }

    /**
     * "Quit and start again" after a broken stream: the game is quit on the PC first, then the same
     * stream starts as a new session.
     */
    private fun relaunchStream() {
        menuOpen = false
        val port = intent.getIntExtra(EXTRA_PORT, 47989)
        val httpsPort = intent.getIntExtra(EXTRA_HTTPS_PORT, 0)
        val crypto = PlatformBinding.getCryptoProvider(applicationContext)
        val relaunch = Intent(intent).apply { removeExtra(EXTRA_PANEL_ROW) }
        thread {
            runCatching { NvHTTP(ComputerDetails.AddressTuple(host, port), httpsPort, uniqueId, serverCert, crypto).quitApp() }
                .onFailure { Log.w(TAG, "quit before relaunch failed", it) }
            runOnUiThread {
                startActivity(relaunch)
                finish()
            }
        }
    }

    /** Overlay switched in the side panel: the stats are measured all along, only their view toggles. */
    private fun showPerfOverlay(show: Boolean) {
        if (connected) statusText.visibility = if (show) View.VISIBLE else View.GONE
    }

    /** A picture setting changed in the panel: the stream reconnects with it after the last change. */
    private fun pictureChanged(row: String) {
        changedRow = row
        surfaceView.removeCallbacks(applyPicture)
        surfaceView.postDelayed(applyPicture, APPLY_DELAY_MS)
    }

    /**
     * A new connection with the new picture settings. The game keeps running on the PC, the same app
     * is resumed, and the panel, if still open, opens again on [row].
     */
    private fun restartStream(row: String) {
        if (isFinishing) return
        Log.i(TAG, "reconnecting with new picture settings from $row")
        stopStream()
        startActivity(Intent(intent).apply { if (menuOpen) putExtra(EXTRA_PANEL_ROW, row) else removeExtra(EXTRA_PANEL_ROW) })
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    /** Opens the side panel; the host gets a released pad so nothing stays held in the game. */
    private fun openMenu() {
        surfaceView.removeCallbacks(sendPending)
        pendingFlags = 0
        buttons = 0
        leftTrigger = 0
        rightTrigger = 0
        lsX = 0
        lsY = 0
        rsX = 0
        rsY = 0
        sendController()
        // The panel's window takes the focus so the d-pad and A work there. Touches on the stream leave
        // touch mode on, where Compose buttons take no focus, so the panel leaves it first.
        menuOpen = true
        panel.content.requestFocusFromTouch()
        Log.i(TAG, "panel opened, touch mode=${panel.content.isInTouchMode} focus=${panel.content.hasFocus()}")
    }

    // NvConnectionListener

    override fun stageStarting(stage: String) {
        runOnUiThread { statusText.text = getString(R.string.rift_stream_stage, stage) }
    }

    override fun stageComplete(stage: String) = Unit

    override fun stageFailed(stage: String, portFlags: Int, errorCode: Int): Boolean {
        runOnUiThread {
            SnackbarManager.show(getString(R.string.rift_stream_stage_failed, stage, errorCode))
            finish()
        }
        return false
    }

    override fun connectionStarted() {
        // The PC gets its virtual pad right away, not on the first button press, so a game started with
        // the stream already sees a controller.
        conn.sendControllerArrivalEvent(
            0, 1, MoonBridge.LI_CTYPE_XBOX, SUPPORTED_BUTTONS,
            (MoonBridge.LI_CCAP_ANALOG_TRIGGERS.toInt() or MoonBridge.LI_CCAP_RUMBLE.toInt()).toShort(),
        )
        sendController()
        runOnUiThread {
            connected = true
            if (!settings.perfOverlay) statusText.visibility = View.GONE
            val row = intent.getStringExtra(EXTRA_PANEL_ROW)
            if (row != null) {
                panelRow = row
                openMenu()
            } else {
                showMessage(getString(R.string.rift_stream_menu_hint), 4000)
            }
        }
    }

    override fun connectionTerminated(errorCode: Int) {
        runOnUiThread {
            if (errorCode == MoonBridge.ML_ERROR_GRACEFUL_TERMINATION || isFinishing) {
                finish()
                return@runOnUiThread
            }
            // The panel stays over the stream with the reason and a way out, instead of a silent return
            // home. No video at all usually means the game's session on the PC is stuck.
            Log.i(TAG, "stream terminated: $errorCode")
            stopStream()
            streamError = if (errorCode == MoonBridge.ML_ERROR_NO_VIDEO_TRAFFIC || errorCode == MoonBridge.ML_ERROR_NO_VIDEO_FRAME) {
                getString(R.string.rift_stream_no_video)
            } else {
                getString(R.string.rift_stream_terminated, errorCode)
            }
            statusText.visibility = View.GONE
            openMenu()
        }
    }

    override fun connectionStatusUpdate(connectionStatus: Int) = Unit

    override fun displayMessage(message: String) {
        runOnUiThread { showMessage(message, 6000) }
    }

    override fun displayTransientMessage(message: String) {
        runOnUiThread { showMessage(message, 2500) }
    }

    /** In-stream message over the video; snackbars live in the main activity. */
    private fun showMessage(message: String, durationMs: Long) {
        messageText.removeCallbacks(hideMessage)
        messageText.text = message
        messageText.visibility = View.VISIBLE
        messageText.postDelayed(hideMessage, durationMs)
    }

    override fun rumble(controllerNumber: Short, lowFreqMotor: Short, highFreqMotor: Short) {
        if (!settings.gamepadVibration || controllerNumber.toInt() != 0) return
        val strength = max(lowFreqMotor.toInt() and 0xFFFF, highFreqMotor.toInt() and 0xFFFF) shr 8
        val vibrator = gamepadVibrator() ?: return
        if (strength == 0) {
            vibrator.cancel()
        } else {
            // The host sends a new value when the effect changes, so a long one-shot is enough.
            vibrator.vibrate(VibrationEffect.createOneShot(60_000, strength.coerceIn(1, 255)))
        }
    }

    override fun rumbleTriggers(controllerNumber: Short, leftTrigger: Short, rightTrigger: Short) = Unit
    override fun setHdrMode(enabled: Boolean, hdrMetadata: ByteArray?) {
        Log.i(TAG, "host HDR ${if (enabled) "on" else "off"}")
        decoder.setHdrMode(enabled, hdrMetadata)
        // The window asks the screen for its HDR mode while the PC sends HDR.
        runOnUiThread { window.colorMode = if (enabled) ActivityInfo.COLOR_MODE_HDR else ActivityInfo.COLOR_MODE_DEFAULT }
    }
    override fun setMotionEventState(controllerNumber: Short, motionType: Byte, reportRateHz: Short) = Unit
    override fun setControllerLED(controllerNumber: Short, r: Byte, g: Byte, b: Byte) = Unit

    private fun gamepadVibrator(): Vibrator? {
        val device = InputDevice.getDeviceIds().asSequence()
            .mapNotNull { InputDevice.getDevice(it) }
            .firstOrNull { isGamepad(it.sources) } ?: return null
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            device.vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            device.vibrator
        }
        return vibrator.takeIf { it.hasVibrator() }
    }

    private fun stopRumble() {
        runCatching { gamepadVibrator()?.cancel() }
    }

    // PerfOverlayListener

    override fun onPerfUpdate(text: String) {
        runOnUiThread { statusText.text = text }
    }

    // Input: one gamepad

    private fun connectedGamepads() = InputDevice.getDeviceIds().count { id ->
        InputDevice.getDevice(id)?.let { !it.isVirtual && isGamepad(it.sources) } == true
    }

    private fun isGamepad(source: Int) =
        source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK

    private fun buttonFlag(keyCode: Int) = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> ControllerPacket.A_FLAG
        KeyEvent.KEYCODE_BUTTON_B -> ControllerPacket.B_FLAG
        KeyEvent.KEYCODE_BUTTON_X -> ControllerPacket.X_FLAG
        KeyEvent.KEYCODE_BUTTON_Y -> ControllerPacket.Y_FLAG
        KeyEvent.KEYCODE_BUTTON_L1 -> ControllerPacket.LB_FLAG
        KeyEvent.KEYCODE_BUTTON_R1 -> ControllerPacket.RB_FLAG
        KeyEvent.KEYCODE_BUTTON_THUMBL -> ControllerPacket.LS_CLK_FLAG
        KeyEvent.KEYCODE_BUTTON_THUMBR -> ControllerPacket.RS_CLK_FLAG
        // Some pads (and some Android builds) send Menu for Start and Back for Select.
        KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU -> ControllerPacket.PLAY_FLAG
        KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BACK -> ControllerPacket.BACK_FLAG
        KeyEvent.KEYCODE_BUTTON_MODE -> ControllerPacket.SPECIAL_BUTTON_FLAG
        KeyEvent.KEYCODE_DPAD_UP -> ControllerPacket.UP_FLAG
        KeyEvent.KEYCODE_DPAD_DOWN -> ControllerPacket.DOWN_FLAG
        KeyEvent.KEYCODE_DPAD_LEFT -> ControllerPacket.LEFT_FLAG
        KeyEvent.KEYCODE_DPAD_RIGHT -> ControllerPacket.RIGHT_FLAG
        else -> 0
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // The panel's own button opens and closes it and never reaches the PC.
        if (menuKey != KeyEvent.KEYCODE_UNKNOWN && event.keyCode == menuKey) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                if (menuOpen) menuOpen = false else openMenu()
            }
            return true
        }
        if (menuOpen) return true
        val flag = if (isGamepad(event.source)) buttonFlag(event.keyCode) else 0
        if (flag == 0) return super.dispatchKeyEvent(event)
        if (event.repeatCount > 0) return true
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (flag and MENU_COMBO != 0) {
                    // Select + Start opens the panel; neither button reaches the game when pressed together.
                    if ((pendingFlags or (buttons and MENU_COMBO) or flag) == MENU_COMBO) {
                        openMenu()
                        return true
                    }
                    pendingFlags = pendingFlags or flag
                    surfaceView.removeCallbacks(sendPending)
                    surfaceView.postDelayed(sendPending, COMBO_WINDOW_MS)
                    return true
                }
                buttons = buttons or flag
            }
            KeyEvent.ACTION_UP -> {
                // A quick tap of Select or Start still reaches the PC as a press and a release.
                if (pendingFlags and flag != 0) {
                    surfaceView.removeCallbacks(sendPending)
                    sendPending.run()
                }
                buttons = buttons and flag.inv()
            }
        }
        sendController()
        return true
    }

    /**
     * A key in the panel's window: the pad drives the panel there. B closes it, A and the d-pad reach
     * Compose through [dispatch].
     */
    private fun panelKey(event: KeyEvent, dispatch: (KeyEvent) -> Boolean): Boolean {
        if (menuKey != KeyEvent.KEYCODE_UNKNOWN && event.keyCode == menuKey) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) menuOpen = false
            return true
        }
        Log.i(TAG, "panel key ${KeyEvent.keyCodeToString(event.keyCode)} action=${event.action} focus=${panel.content.hasFocus()}")
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            // A slider that holds the pad lets go on B first; only a free B closes the panel.
            if (event.action == KeyEvent.ACTION_DOWN) bTaken = dispatch(event)
            if (event.action == KeyEvent.ACTION_UP && !bTaken) closePanel()
            return true
        }
        // The panel's rows take the pad's A themselves (sliders grab with it); what they leave goes on as
        // d-pad center, which Compose clicks on.
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_A) {
            return dispatch(event) ||
                dispatch(KeyEvent(event.downTime, event.eventTime, event.action, KeyEvent.KEYCODE_DPAD_CENTER, event.repeatCount, event.metaState, event.deviceId, event.scanCode, event.flags, event.source))
        }
        return dispatch(event)
    }

    /** After an error there is no stream to go back to: closing the panel leaves. */
    private fun closePanel() {
        if (streamError != null) finish() else menuOpen = false
    }

    /**
     * The side panel in a window of its own over the stream, so the system blurs the video behind its
     * glass where the device allows it, as Rift's panel over Android games does.
     */
    /**
     * Copies the stream's last frame, scaled down, for the panel's glass, a few dozen times a second
     * while the panel is open. Each copy goes to the buffer the panel isn't drawing.
     */
    private fun requestBackdrop() {
        if (!panelOpen || backdropFailed) return
        val width = surfaceView.width / BACKDROP_SCALE
        val height = surfaceView.height / BACKDROP_SCALE
        if (width <= 0 || height <= 0) return
        val buffer = backdropBuffers[backdropBuffer]?.takeIf { it.width == width && it.height == height }
            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { backdropBuffers[backdropBuffer] = it }
        PixelCopy.request(surfaceView, buffer, { result ->
            if (!panelOpen) return@request
            if (result == PixelCopy.SUCCESS) {
                backdrop = buffer.asImageBitmap()
                backdropBuffer = 1 - backdropBuffer
                surfaceView.postDelayed(copyBackdrop, BACKDROP_FRAME_MS)
            } else {
                // Nothing to copy (no frame yet, or the decoder's buffers can't be read): plain dark glass.
                Log.i(TAG, "panel glass: stream copy failed ($result), dark glass")
                backdropFailed = true
                backdrop = null
            }
        }, mainHandler)
    }

    private inner class PanelWindow : ComponentDialog(this@StreamActivity, android.R.style.Theme_Material_NoActionBar) {
        val content = ComposeView(this@StreamActivity).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setContent { Box {
                // The glass: the stream behind the panel, blurred and darkened. The system's own window
                // blur doesn't show over the stream on every device, so the panel blurs a copy itself.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(BlackGlass.Blur, BlurredEdgeTreatment.Rectangle)
                        .drawBehind {
                            val frame = backdrop ?: return@drawBehind
                            // The copy is the whole screen; the panel's window sits at its right edge.
                            val streamWidth = surfaceView.width.coerceAtLeast(1)
                            val scale = frame.width / streamWidth.toFloat()
                            val left = ((streamWidth - size.width) * scale).roundToInt().coerceAtLeast(0)
                            drawImage(
                                image = frame,
                                srcOffset = IntOffset(left, 0),
                                srcSize = IntSize((frame.width - left).coerceAtLeast(1), frame.height),
                                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                            )
                        },
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (backdrop != null) BlackGlass.Tint else ComposeColor(OverlayPanel.GLASS_SOLID)),
                )
                StreamMenu(
                    isOpen = panelOpen,
                    appName = intent.getStringExtra(EXTRA_APP_NAME).orEmpty(),
                    pcName = intent.getStringExtra(EXTRA_PC_NAME).orEmpty(),
                    onDismiss = ::closePanel,
                    onEndStream = ::endStream,
                    onPerfOverlayChange = ::showPerfOverlay,
                    onPictureChange = ::pictureChanged,
                    error = streamError,
                    onRelaunch = ::relaunchStream,
                    focusRow = panelRow,
                    maxFps = maxFps,
                    screenSize = screenSize,
                    ownWindow = true,
                )
            } }
        }

        init {
            setContentView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            setCanceledOnTouchOutside(true)
            setOnCancelListener { closePanel() }
            onBackPressedDispatcher.addCallback(this@PanelWindow) { closePanel() }
            window?.apply {
                val density = resources.displayMetrics.density
                val systemBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && windowManager.isCrossWindowBlurEnabled
                Log.i(TAG, "panel glass: own blur of the stream (system cross-window blur=$systemBlur)")
                setFormat(PixelFormat.TRANSLUCENT)
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setDimAmount(0f)
                setWindowAnimations(R.style.RiftOverlayPanelAnimation)
                addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
                val screenDp = resources.displayMetrics.widthPixels / density
                setLayout((min(OverlayPanel.PANEL_WIDTH_DP.toFloat(), screenDp * 0.85f) * density).toInt(), ViewGroup.LayoutParams.MATCH_PARENT)
                setGravity(Gravity.END or Gravity.TOP)
                // The panel's window holds the focus, and the focused window decides the system bars.
                @Suppress("DEPRECATION")
                decorView.systemUiVisibility = this@StreamActivity.window.decorView.systemUiVisibility
            }
        }

        override fun dispatchKeyEvent(event: KeyEvent) = panelKey(event) { super.dispatchKeyEvent(it) }
    }

    override fun onDestroy() {
        if (::panel.isInitialized) panel.dismiss()
        super.onDestroy()
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (menuOpen || !isGamepad(event.source) || event.action != MotionEvent.ACTION_MOVE) {
            return super.onGenericMotionEvent(event)
        }
        lsX = stick(event.getAxisValue(MotionEvent.AXIS_X))
        lsY = stick(-event.getAxisValue(MotionEvent.AXIS_Y))
        rsX = stick(event.getAxisValue(MotionEvent.AXIS_Z))
        rsY = stick(-event.getAxisValue(MotionEvent.AXIS_RZ))
        leftTrigger = trigger(max(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE)))
        rightTrigger = trigger(max(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS)))

        // Many pads report the d-pad as a hat axis instead of key events.
        if (event.device?.getMotionRange(MotionEvent.AXIS_HAT_X) != null) {
            val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            var hat = 0
            if (hatX < -0.5f) hat = hat or ControllerPacket.LEFT_FLAG
            if (hatX > 0.5f) hat = hat or ControllerPacket.RIGHT_FLAG
            if (hatY < -0.5f) hat = hat or ControllerPacket.UP_FLAG
            if (hatY > 0.5f) hat = hat or ControllerPacket.DOWN_FLAG
            buttons = (buttons and DPAD_FLAGS.inv()) or hat
        }
        sendController()
        return true
    }

    private fun stick(v: Float): Int = if (abs(v) < 0.07f) 0 else (v.coerceIn(-1f, 1f) * 0x7FFE).toInt()

    private fun trigger(v: Float): Int = (v.coerceIn(0f, 1f) * 0xFF).toInt()

    private fun sendController() {
        if (!started || stopped) return
        conn.sendControllerInput(
            0, 1, buttons, leftTrigger.toByte(), rightTrigger.toByte(),
            lsX.toShort(), lsY.toShort(), rsX.toShort(), rsY.toShort(),
        )
    }

    // Touch as absolute mouse

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!started || stopped || menuOpen) return true
        val x = event.x.toInt().toShort()
        val y = event.y.toInt().toShort()
        val w = surfaceView.width.toShort()
        val h = surfaceView.height.toShort()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                conn.sendMousePosition(x, y, w, h)
                conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT)
            }
            MotionEvent.ACTION_MOVE -> conn.sendMousePosition(x, y, w, h)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT)
        }
        return true
    }
}
