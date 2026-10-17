package dev.rift.launcher.cloud

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
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
import dev.rift.launcher.stream.StreamMenu
import org.json.JSONObject
import timber.log.Timber

/**
 * Xbox Cloud Gaming in Rift's own full-screen browser, with the Better xCloud userscript loaded before
 * xbox.com's own scripts. The gamepad reaches the game through the browser's Gamepad API.
 *
 * Exits like a stream from a PC: Select + Start (or the edge swipe) opens the side panel with
 * "End stream", B closes it. Opened with [EXTRA_SIGN_IN] it shows xbox.com/play for signing in and
 * closes by itself once xbox.com says the account is signed in.
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
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        signInMode = intent.getBooleanExtra(EXTRA_SIGN_IN, false)
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
            webChromeClient = WebChromeClient()
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
            text = getString(R.string.rift_stream_menu_hint)
            visibility = View.GONE
        }
        setContentView(
            FrameLayout(this).apply {
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
                        )
                    }
                }
                addView(menuView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                addView(
                    hintText,
                    FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
                        .apply { bottomMargin = 64 },
                )
            },
        )
        onBackPressedDispatcher.addCallback(this) {
            if (menuOpen) closeMenu() else openMenu()
        }
        webView.loadUrl(url)
        webView.requestFocus()
        handler.postDelayed(pollSignIn, SIGN_IN_POLL_MS)
    }

    /**
     * Better xCloud has to run before the page's own scripts. The script only runs where its userscript
     * header says (xbox.com's /play pages and the sign-in return page), in the top frame.
     */
    private fun addBetterXcloud(): Boolean {
        // Without the script xCloud still plays, just without Better xCloud.
        val script = runCatching { assets.open(SCRIPT_ASSET).bufferedReader().use { it.readText() } }
            .onFailure { Timber.e(it, "Better xCloud script missing") }
            .getOrNull() ?: return true
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
        val full = guard + "window.BX_FLAGS = $flags;\n" + script
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, full, setOf(ORIGIN))
            return true
        }
        // Old WebView: injected when each page starts loading instead, which is usually early enough.
        pendingScript = full
        return false
    }

    private var pendingScript: String? = null

    private inner class XcloudClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            // Store links (ms-windows-store:, xbox:, intent:) have nowhere to go here.
            val scheme = request.url.scheme
            return scheme != "http" && scheme != "https"
        }

        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
            if (!documentStartScript && url.startsWith(ORIGIN)) pendingScript?.let { view.evaluateJavascript(it, null) }
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (!signInMode && !hintShown && url.contains("/play/launch/")) {
                hintShown = true
                hintText.visibility = View.VISIBLE
                handler.removeCallbacks(hideHint)
                handler.postDelayed(hideHint, HINT_MS)
            }
        }
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
        webView.requestFocus()
    }

    private fun isGamepad(source: Int) =
        source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val gamepad = isGamepad(event.source)
        if (gamepad) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> held += event.keyCode
                KeyEvent.ACTION_UP -> held -= event.keyCode
            }
        }
        // With the panel open the pad drives the panel: B closes it, A and the d-pad reach Compose.
        if (menuOpen) {
            if (event.action == KeyEvent.ACTION_UP && pressedInPage.remove(event.keyCode)) webView.dispatchKeyEvent(event)
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
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }
}
