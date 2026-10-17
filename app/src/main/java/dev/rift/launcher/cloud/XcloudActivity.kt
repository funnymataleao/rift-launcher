package dev.rift.launcher.cloud

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import app.gamenative.R
import dev.rift.launcher.home.RiftControls
import dev.rift.launcher.stream.StreamMenu
import dev.rift.launcher.stream.StreamMenuRow
import dev.rift.launcher.ui.rememberWindowBackdrop
import org.json.JSONObject
import timber.log.Timber

/**
 * Xbox Cloud Gaming in Rift's own full-screen browser, with the Better xCloud userscript loaded before
 * xbox.com's own scripts. The gamepad reaches the game through the browser's Gamepad API.
 *
 * The script's settings are Rift's ([CloudPlaySettings]) and its own menus and name are kept out of
 * sight. The gamepad's Home (or Select + Start, or the edge swipe) opens Rift's side panel with
 * "End stream" and the settings worth changing while playing; B closes it. Opened with [EXTRA_SIGN_IN]
 * it shows xbox.com/play for signing in and closes by itself once xbox.com says the account is signed in.
 */
class XcloudActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_SIGN_IN = "signIn"

        private const val SCRIPT_ASSET = "better-xcloud/better-xcloud.user.js"
        private const val ORIGIN = "https://www.xbox.com"
        private const val SIGN_IN_POLL_MS = 1_500L
        private const val SIGNED_IN_CLOSE_MS = 1_200L
        private const val HINT_MS = 4_000L
        private const val XBOX_MENU_PRESS_MS = 150L

        /** Logcat tag for the page's console and load errors; release builds log them too, for the tablet's logs. */
        private const val LOG_TAG = "Xcloud"

        private const val SIGNED_IN_CHECK =
            "(function(){try{return !!(window.xbcUser&&window.xbcUser.isSignedIn)}catch(e){return false}})()"

        /** Starts [game] in the cloud. */
        fun play(context: Context, game: CloudGame) {
            XcloudAccount.markPlayed(context, game.productId)
            val url = "$ORIGIN/${XcloudAccount.pageLocale()}/play/launch/${Uri.encode(game.slug)}/${game.productId}"
            context.startActivity(
                Intent(context, XcloudActivity::class.java)
                    .putExtra(EXTRA_URL, url)
                    .putExtra(EXTRA_TITLE, game.title),
            )
        }

        /** xbox.com/play to sign in to the Microsoft account, from settings. */
        fun signIn(context: Context) {
            context.startActivity(
                Intent(context, XcloudActivity::class.java)
                    .putExtra(EXTRA_URL, "$ORIGIN/${XcloudAccount.pageLocale()}/play")
                    .putExtra(EXTRA_SIGN_IN, true),
            )
        }
    }

    private lateinit var webView: WebView
    private lateinit var hintText: TextView
    private lateinit var menuView: ComposeView
    private lateinit var root: FrameLayout

    /** The page's full-screen element (xCloud's stream asks for full screen), shown over the page. */
    private var fullScreenView: View? = null
    private var fullScreenCallback: WebChromeClient.CustomViewCallback? = null
    private var menuOpen by mutableStateOf(false)
    private var signInMode = false
    private val handler = Handler(Looper.getMainLooper())
    private val hideHint = Runnable { hintText.visibility = View.GONE }
    private var documentStartScript = false
    private var hintShown = false

    /** Gamepad buttons held right now, to see Select + Start together. */
    private val held = mutableSetOf<Int>()

    /** Buttons whose press the page saw: their release goes to the page even while the panel is open. */
    private val pressedInPage = mutableSetOf<Int>()

    /** A button chosen in Rift's settings that also opens the panel, besides Home. */
    private var menuKey = KeyEvent.KEYCODE_UNKNOWN

    /** Whether the game's stream showed up, so leaving the game's page means the game is over. */
    private var streamShown = false

    /** The gamepad last used, to press its Home for the page ([openXboxMenu]). */
    private var lastGamepad: Int? = null

    private val pollSignIn = object : Runnable {
        override fun run() {
            webView.evaluateJavascript(SIGNED_IN_CHECK) { result ->
                if (result == "true") {
                    onSignedIn()
                } else {
                    handler.postDelayed(this, SIGN_IN_POLL_MS)
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A debuggable build can be inspected from chrome://inspect on a computer.
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) WebView.setWebContentsDebuggingEnabled(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        signInMode = intent.getBooleanExtra(EXTRA_SIGN_IN, false)
        menuKey = RiftControls.streamMenuKey(this)
        val url = intent.getStringExtra(EXTRA_URL) ?: return finish()
        val title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.rift_cloud_service_xcloud)

        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            isFocusable = true
            isFocusableInTouchMode = true
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.setSupportMultipleWindows(false)
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            // xbox.com should see Chrome for Android, not an embedded WebView.
            settings.userAgentString = settings.userAgentString
                .replace("; wv)", ")")
                .replace(Regex("Version/[\\d.]+ "), "")
            webViewClient = XcloudClient()
            webChromeClient = XcloudChromeClient()
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            // The Microsoft sign-in hops between live.com and xbox.com.
            setAcceptThirdPartyCookies(webView, true)
        }
        documentStartScript = addBetterXcloud()

        hintText = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(0xCC000000.toInt())
            textSize = 18f
            setPadding(40, 20, 40, 20)
            text = getString(R.string.rift_cloud_menu_hint)
            visibility = View.GONE
        }
        root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(webView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            menuView = ComposeView(context).apply {
                setContent {
                    StreamMenu(
                        isOpen = menuOpen,
                        appName = title,
                        pcName = getString(R.string.rift_cloud_service_xcloud),
                        onDismiss = ::closeMenu,
                        onEndStream = ::finish,
                        focusRow = StreamMenuRow.CLOUD_FIRST,
                        extraSettings = if (signInMode) null else { requester -> CloudQuickSettings(requester, ::openXboxMenu, ::applyLive) },
                        // The page behind the panel, copied as it opens, for the panel's glass to blur.
                        backdrop = rememberWindowBackdrop(menuOpen),
                    )
                }
            }
            addView(menuView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(
                hintText,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
                    .apply { bottomMargin = 64 },
            )
        }
        setContentView(root)
        // In a game Back (the edge swipe) opens the panel; anywhere else on xbox.com it goes back to Rift.
        onBackPressedDispatcher.addCallback(this) {
            when {
                menuOpen -> closeMenu()
                inGame() -> openMenu()
                else -> finish()
            }
        }
        webView.loadUrl(url)
        webView.requestFocus()
        handler.postDelayed(pollSignIn, SIGN_IN_POLL_MS)
    }

    /**
     * Better xCloud has to run before the page's own scripts. The script only runs where its userscript
     * header says (xbox.com's /play pages and the sign-in return page), in the top frame. Rift's settings
     * go into the page's storage first, and the script is changed so its name and its own settings
     * buttons never show: Rift's settings and side panel take their place.
     */
    private fun addBetterXcloud(): Boolean {
        // Without the script xCloud still plays, just without Better xCloud.
        val script = runCatching { assets.open(SCRIPT_ASSET).bufferedReader().use { it.readText() } }
            .onFailure { Timber.e(it, "Better xCloud script missing") }
            .getOrNull()
            ?.let(::unbranded) ?: return true
        val flags = JSONObject()
            .put(
                "DeviceInfo",
                JSONObject()
                    // A handheld with a gamepad: no touch controls, the controller-friendly interface.
                    .put("deviceType", "android-handheld")
                    .put("userAgent", webView.settings.userAgentString)
                    .put(
                        "androidInfo",
                        JSONObject().put("brand", Build.BRAND).put("board", Build.BOARD).put("model", Build.MODEL),
                    ),
            )
            // The script checks for its own updates on GitHub; Rift ships a fixed version.
            .put("CheckForUpdate", false)
        val guard = "if (window.top !== window || !/^\\/[a-zA-Z]{2}-[a-zA-Z]{2}\\/(play|auth\\/msa)/.test(window.location.pathname)) " +
            "throw new Error('[Rift] Better xCloud runs on xbox.com/play only');\n"
        val full = guard + "window.BX_FLAGS = $flags;\n" + CloudPlaySettings.pageScript(this) + script
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, full, setOf(ORIGIN))
            return true
        }
        // Old WebView: injected when each page starts loading instead, which is usually early enough.
        pendingScript = full
        return false
    }

    private var pendingScript: String? = null

    /**
     * The script without its name in what the page shows and without its settings buttons (in the
     * page's header, in xCloud's guide, in the stream's menu), and without its settings opening by
     * themselves where xCloud is not offered. The bundled file stays as published.
     */
    private fun unbranded(script: String): String {
        // The name in the script's own English texts first; translations it downloads go through t below.
        var text = script.replace("Better xCloud", "Rift")
        fun patch(from: String, to: String) {
            if (from in text) text = text.replace(from, to) else Timber.w("Cloud script changed, not found: %s", from.take(60))
        }
        patch(
            "var t = Translations.get;",
            "var t = (key, values) => { let text = Translations.get(key, values); " +
                "return typeof text === \"string\" ? text.replace(/Better xCloud/g, \"Rift\") : text; };",
        )
        patch("\$btnStreamSettings = StreamUiHandler.cloneStreamHudButton(\$orgButton, t(\"better-xcloud\"), BxIcon.BETTER_XCLOUD)", "\$btnStreamSettings = null")
        patch("buttonsLayout = [buttons.scriptSettings,", "buttonsLayout = [")
        patch("SettingsDialog.getInstance().show();}\$btnSettings", ";}\$btnSettings")
        return text
    }

    /**
     * xCloud's own guide (friends, quitting the game inside xCloud), which Home opens on a console. Home
     * opens Rift's panel here, so the panel presses Home for the page: down and, a moment later so the
     * page's gamepad polling sees it, up.
     */
    private fun openXboxMenu() {
        closeMenu()
        val device = lastGamepad ?: return
        val source = InputDevice.getDevice(device)?.sources ?: InputDevice.SOURCE_GAMEPAD
        val now = android.os.SystemClock.uptimeMillis()
        fun press(action: Int, time: Long) =
            KeyEvent(now, time, action, KeyEvent.KEYCODE_BUTTON_MODE, 0, 0, device, 0, 0, source)
        (fullScreenView ?: webView).dispatchKeyEvent(press(KeyEvent.ACTION_DOWN, now))
        handler.postDelayed({ (fullScreenView ?: webView).dispatchKeyEvent(press(KeyEvent.ACTION_UP, android.os.SystemClock.uptimeMillis())) }, XBOX_MENU_PRESS_MS)
    }

    /** A setting changed in the side panel: kept for the next game and applied to this one now. */
    private fun applyLive(setting: CloudSetting, value: Any) {
        CloudPlaySettings.set(this, setting, value)
        webView.evaluateJavascript(CloudPlaySettings.liveScript(setting, value)) { result ->
            if (result != "\"ok\"") Log.w(LOG_TAG, "Setting ${setting.key} not applied: $result")
        }
    }

    private inner class XcloudClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            // Store links (ms-windows-store:, xbox:, intent:) have nowhere to go here.
            val scheme = request.url.scheme
            return scheme != "http" && scheme != "https"
        }

        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
            if (!documentStartScript && url.startsWith(ORIGIN)) pendingScript?.let { view.evaluateJavascript(it, null) }
            closeIfGameLeft(url)
        }

        // xbox.com changes its address without loading a page when xCloud's own "Quit game" ends the session.
        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            closeIfGameLeft(url)
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) Log.w(LOG_TAG, "Page failed: ${error.errorCode} ${error.description} ${request.url}")
        }

        // The page's renderer died (out of memory, a GPU fault): the session cannot go on, so the
        // browser closes back to Rift instead of taking the whole app down.
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            Log.e(LOG_TAG, "Renderer gone, crashed=${detail.didCrash()}")
            if (!isFinishing) finish()
            return true
        }

        override fun onPageFinished(view: WebView, url: String) {
            Log.i(LOG_TAG, "Page finished: $url")
            // What was set in the page before Rift kept the settings is taken over once.
            if (url.startsWith(ORIGIN) && CloudPlaySettings.needsImport(this@XcloudActivity)) {
                view.evaluateJavascript(CloudPlaySettings.READ_PAGE_SCRIPT) { CloudPlaySettings.importFromPage(this@XcloudActivity, it) }
            }
            if (!signInMode && !hintShown && url.contains("/play/launch/")) {
                hintShown = true
                hintText.visibility = View.VISIBLE
                handler.removeCallbacks(hideHint)
                handler.postDelayed(hideHint, HINT_MS)
            }
        }
    }

    /**
     * xCloud puts its stream in full screen as soon as the game starts. A WebView hands the full-screen
     * element to the app as a separate view; until it is on screen the stream is drawn nowhere and
     * the page stays black. It goes right above the page, under the side panel and the hint.
     */
    private inner class XcloudChromeClient : WebChromeClient() {
        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            if (fullScreenView != null) {
                callback.onCustomViewHidden()
                return
            }
            Log.i(LOG_TAG, "Page went full screen")
            streamShown = true
            fullScreenView = view
            fullScreenCallback = callback
            view.setBackgroundColor(Color.BLACK)
            root.addView(view, 1, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            // The gamepad and the keys go where the stream is.
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            if (!menuOpen) view.requestFocus()
            hideSystemBars()
        }

        override fun onHideCustomView() {
            val view = fullScreenView ?: return
            root.removeView(view)
            fullScreenView = null
            fullScreenCallback = null
            // The stream leaves full screen when the game is over (quit in xCloud's menu, the session
            // ended): back to Rift, not to xbox.com's pages.
            if (!signInMode && !isFinishing) {
                Log.i(LOG_TAG, "Stream left full screen, closing")
                finish()
                return
            }
            if (!menuOpen) webView.requestFocus()
        }

        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
            val priority = when (message.messageLevel()) {
                ConsoleMessage.MessageLevel.ERROR -> Log.ERROR
                ConsoleMessage.MessageLevel.WARNING -> Log.WARN
                else -> Log.INFO
            }
            Log.println(priority, LOG_TAG, "${message.message()} (${message.sourceId()}:${message.lineNumber()})")
            return true
        }

        // Without a poster of its own the WebView draws its stock one (an Android robot) over a video that
        // has no picture yet; the stream's own black background is what should show.
        override fun getDefaultVideoPoster(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }

    /**
     * The game was quit inside xCloud (its guide's "Quit game", the Xbox menu's "Back to home"): xbox.com
     * then shows its Game Pass pages, which have no way back to Rift. Like "End stream", the browser
     * closes and Rift is back where the game was started from.
     */
    private fun closeIfGameLeft(url: String) {
        if (signInMode || !streamShown || isFinishing) return
        if (!url.startsWith(ORIGIN) || url.contains("/play/launch/")) return
        Log.i(LOG_TAG, "Game left for $url, closing")
        finish()
    }

    /** Whether a game is on screen: the stream is full screen. */
    private fun inGame() = fullScreenView != null

    /** Where the page's input goes: the full-screen stream when there is one, else the page. */
    private fun focusPage() {
        (fullScreenView ?: webView).requestFocus()
    }

    private fun onSignedIn() {
        if (!XcloudAccount.isSignedIn(this)) XcloudAccount.setSignedIn(this, true)
        // From settings the browser was only for signing in: back to settings, which now show the account.
        if (signInMode) handler.postDelayed({ if (!isFinishing) finish() }, SIGNED_IN_CLOSE_MS)
    }

    private fun hideSystemBars() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun openMenu() {
        // The panel's buttons live in the Compose layer; it takes the focus from the page.
        menuView.requestFocus()
        menuOpen = true
    }

    private fun closeMenu() {
        menuOpen = false
        focusPage()
    }

    private fun isGamepad(source: Int) =
        source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val gamepad = isGamepad(event.source)
        if (gamepad) {
            lastGamepad = event.deviceId
            when (event.action) {
                KeyEvent.ACTION_DOWN -> held += event.keyCode
                KeyEvent.ACTION_UP -> held -= event.keyCode
            }
        }
        // Home opens and closes the panel; the page never sees it, so xCloud's own menu stays shut.
        if (gamepad && (event.keyCode == KeyEvent.KEYCODE_BUTTON_MODE || event.keyCode == menuKey)) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                if (menuOpen) closeMenu() else openMenu()
            }
            return true
        }
        // With the panel open the pad drives the panel: B closes it, A and the d-pad reach Compose.
        if (menuOpen) {
            if (event.action == KeyEvent.ACTION_UP && pressedInPage.remove(event.keyCode)) (fullScreenView ?: webView).dispatchKeyEvent(event)
            if (event.keyCode == KeyEvent.KEYCODE_BUTTON_B) {
                if (event.action == KeyEvent.ACTION_UP) closeMenu()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
        if (gamepad && event.action == KeyEvent.ACTION_DOWN &&
            KeyEvent.KEYCODE_BUTTON_SELECT in held && KeyEvent.KEYCODE_BUTTON_START in held
        ) {
            openMenu()
            return true
        }
        // Out of a game (loading, xbox.com's own pages) B goes back to Rift; in a game it is the game's.
        if (gamepad && !signInMode && !inGame() && event.keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            if (event.action == KeyEvent.ACTION_UP) finish()
            return true
        }
        if (gamepad) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> pressedInPage += event.keyCode
                KeyEvent.ACTION_UP -> pressedInPage -= event.keyCode
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        CookieManager.getInstance().flush()
        // Like a stream from a PC: leaving the app ends the session, so the game is not left running.
        if (!signInMode) finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        fullScreenCallback?.onCustomViewHidden()
        if (::webView.isInitialized) {
            webView.stopLoading()
            (webView.parent as? android.view.ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        super.onDestroy()
    }
}
