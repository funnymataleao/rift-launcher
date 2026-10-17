package dev.rift.launcher.cloud

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.core.content.edit
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * The Xbox Cloud Gaming account as Rift knows it: whether the person signed in to xbox.com in Rift's
 * browser (Settings > Cloud streaming), and which cloud games they started from Rift, for the home row.
 * The sign-in itself lives in the WebView's cookies and storage; this only remembers that it happened.
 * Kept in the same file as the other home settings, "rift_prefs".
 */
object XcloudAccount {
    private const val FILE = "rift_prefs"
    private const val SIGNED_IN = "xcloud_signed_in"
    private const val RECENT = "xcloud_recent"
    private const val MAX_RECENT = 50

    /** Market of the Game Pass catalog. xCloud is not sold in every country; the US catalog is the full one. */
    const val MARKET = "US"

    private val signedInFlow = MutableStateFlow<Boolean?>(null)
    private val recentFlow = MutableStateFlow<Map<String, Long>?>(null)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Signed in or not; the home's cloud tab exists only while this is true. */
    fun signedIn(context: Context): StateFlow<Boolean?> {
        if (signedInFlow.value == null) signedInFlow.value = prefs(context).getBoolean(SIGNED_IN, false)
        return signedInFlow.asStateFlow()
    }

    fun isSignedIn(context: Context): Boolean = signedIn(context).value == true

    fun setSignedIn(context: Context, signedIn: Boolean) {
        prefs(context).edit { putBoolean(SIGNED_IN, signedIn) }
        signedInFlow.value = signedIn
    }

    /** Forgets the Microsoft account: the browser's cookies and site storage go too. */
    fun signOut(context: Context) {
        setSignedIn(context, false)
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
    }

    /** xbox.com's locale path, in the device language: "ru-RU", "en-US". */
    fun pageLocale(): String {
        val locale = Locale.getDefault()
        val country = locale.country.ifEmpty { if (locale.language == "en") "US" else locale.language.uppercase() }
        return "${locale.language}-$country"
    }

    /** Catalog text language, with English as the fallback the catalog fills in. */
    fun catalogLanguages(): String = "${pageLocale().lowercase()},en-us"

    /** When each cloud game was last started from Rift, by product id. */
    fun recent(context: Context): StateFlow<Map<String, Long>?> {
        if (recentFlow.value == null) recentFlow.value = readRecent(context)
        return recentFlow.asStateFlow()
    }

    private fun readRecent(context: Context): Map<String, Long> {
        val raw = prefs(context).getString(RECENT, null) ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            json.keys().asSequence().associateWith { json.getLong(it) }
        }.getOrDefault(emptyMap())
    }

    fun markPlayed(context: Context, productId: String) {
        val updated = (readRecent(context) + (productId to System.currentTimeMillis()))
            .entries.sortedByDescending { it.value }.take(MAX_RECENT)
            .associate { it.key to it.value }
        prefs(context).edit { putString(RECENT, JSONObject(updated as Map<*, *>).toString()) }
        recentFlow.value = updated
    }
}
