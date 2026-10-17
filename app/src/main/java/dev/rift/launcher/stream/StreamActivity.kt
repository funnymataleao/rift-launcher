package dev.rift.launcher.stream

import android.graphics.Color
import android.graphics.Point
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Display
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
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

        private const val MENU_COMBO = ControllerPacket.BACK_FLAG or ControllerPacket.PLAY_FLAG

        /** How long Select or Start waits for the other one before it goes to the PC. */
        private const val COMBO_WINDOW_MS = 150L
        private const val DPAD_FLAGS = ControllerPacket.UP_FLAG or ControllerPacket.DOWN_FLAG or
            ControllerPacket.LEFT_FLAG or ControllerPacket.RIGHT_FLAG
    }

    private lateinit var surfaceView: SurfaceView
    private lateinit var menuView: ComposeView
    private lateinit var statusText: TextView
    private lateinit var messageText: TextView
    private val hideMessage = Runnable { messageText.visibility = View.GONE }
    private lateinit var decoder: MediaCodecDecoderRenderer
    private lateinit var conn: NvConnection
    private lateinit var settings: StreamSettings
    private var ready = false
    private var started = false
    private var stopped = false
    private var streamWidth = 1920
    private var streamHeight = 1080
    private var menuOpen by mutableStateOf(false)
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
                menuView = ComposeView(context).apply {
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setContent {
                        StreamMenu(
                            isOpen = menuOpen,
                            appName = intent.getStringExtra(EXTRA_APP_NAME).orEmpty(),
                            pcName = intent.getStringExtra(EXTRA_PC_NAME).orEmpty(),
                            onDismiss = { menuOpen = false },
                            onEndStream = ::endStream,
                        )
                    }
                }
                addView(menuView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                addView(
                    messageText,
                    FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
                        .apply { bottomMargin = 64 },
                )
            },
        )
        onBackPressedDispatcher.addCallback(this) {
            if (menuOpen) {
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
        val fixedSize = StreamSettings.sizeOf(settings.resolution)
        if (fixedSize == null) {
            streamWidth = nativeWidth
            streamHeight = nativeHeight
        } else {
            streamWidth = fixedSize.first
            streamHeight = fixedSize.second
        }
        val refreshRate = display.mode.refreshRate
        val fps = if (settings.fps > 0) settings.fps else min(refreshRate.roundToInt(), 120)
        val bitrate = settings.bitrateMbps.takeIf { it > 0 }?.times(1000) ?: StreamSettings.autoBitrateKbps(streamWidth, streamHeight, fps)

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
            enablePerfOverlay = settings.perfOverlay
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
            .setVirtualDisplay(settings.virtualDisplay)
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
        // The panel's buttons live in the Compose layer; it takes the focus so the d-pad and A work there.
        menuView.requestFocus()
        menuOpen = true
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
        runOnUiThread {
            if (!settings.perfOverlay) statusText.visibility = View.GONE
            showMessage(getString(R.string.rift_stream_menu_hint), 4000)
        }
    }

    override fun connectionTerminated(errorCode: Int) {
        runOnUiThread {
            if (errorCode != MoonBridge.ML_ERROR_GRACEFUL_TERMINATION) {
                SnackbarManager.show(getString(R.string.rift_stream_terminated, errorCode))
            }
            finish()
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
    override fun setHdrMode(enabled: Boolean, hdrMetadata: ByteArray?) = decoder.setHdrMode(enabled, hdrMetadata)
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
        // With the panel open the pad drives the panel: B closes it, A and the d-pad reach Compose.
        if (menuOpen) {
            if (event.keyCode == KeyEvent.KEYCODE_BUTTON_B) {
                if (event.action == KeyEvent.ACTION_UP) menuOpen = false
                return true
            }
            return super.dispatchKeyEvent(event)
        }
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
