package dev.rift.launcher.assistant.ai

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.time.YearMonth
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import timber.log.Timber

/**
 * The assistant's settings and the user's API keys, in their own SharedPreferences file ("rift_assistant").
 * The app does not allow backup, so none of it leaves the device. Keys are encrypted with an AES-GCM key that
 * lives in the Android Keystore and never leaves it; only their last four characters are ever shown.
 */
class AiSettings private constructor(private val prefs: SharedPreferences) {

    var provider: AiProvider?
        get() = AiProvider.byId(prefs.getString(PROVIDER, null))
        set(value) = prefs.edit().putString(PROVIDER, value?.id).apply()

    fun hasKey(provider: AiProvider): Boolean = prefs.contains(keyName(provider))

    /** The saved key, or "" when there is none or it can no longer be decrypted (for example after a Keystore reset). */
    fun apiKey(provider: AiProvider): String {
        val stored = prefs.getString(keyName(provider), null) ?: return ""
        return try {
            KeyCipher.decrypt(stored)
        } catch (e: Exception) {
            Timber.w("AiSettings: saved key for ${provider.id} can't be decrypted")
            ""
        }
    }

    fun setApiKey(provider: AiProvider, key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) {
            prefs.edit().remove(keyName(provider)).apply()
        } else {
            prefs.edit().putString(keyName(provider), KeyCipher.encrypt(trimmed)).apply()
        }
    }

    /** The address requests go to: the provider's own, or one the user set (their proxy, Ollama, LM Studio). */
    fun baseUrl(provider: AiProvider): String = prefs.getString("base_url_${provider.id}", null) ?: provider.defaultBaseUrl

    fun setBaseUrl(provider: AiProvider, url: String) {
        val trimmed = url.trim().trimEnd('/')
        val editor = prefs.edit()
        if (trimmed.isEmpty() || trimmed == provider.defaultBaseUrl) {
            editor.remove("base_url_${provider.id}")
        } else {
            editor.putString("base_url_${provider.id}", trimmed)
        }
        editor.apply()
    }

    fun model(provider: AiProvider): String? = prefs.getString("model_${provider.id}", null) ?: provider.recommendedModel

    fun setModel(provider: AiProvider, model: String) = prefs.edit().putString("model_${provider.id}", model).apply()

    /** Longest answer one request may return, in tokens. */
    var maxOutputTokens: Int
        get() = prefs.getInt(MAX_OUTPUT, DEFAULT_MAX_OUTPUT)
        set(value) = prefs.edit().putInt(MAX_OUTPUT, value).apply()

    /** Tokens (in and out together) allowed per calendar month; 0 means no limit. */
    var monthlyTokenLimit: Long
        get() = prefs.getLong(MONTHLY_LIMIT, 0L)
        set(value) = prefs.edit().putLong(MONTHLY_LIMIT, value).apply()

    /** Lets suggestions change environment variables and launch arguments. Off by default. */
    var allowEnvAndArgs: Boolean
        get() = prefs.getBoolean(ALLOW_ENV, false)
        set(value) = prefs.edit().putBoolean(ALLOW_ENV, value).apply()

    data class Usage(val month: String, val requests: Int, val inputTokens: Long, val outputTokens: Long) {
        val totalTokens: Long get() = inputTokens + outputTokens
    }

    fun usage(month: YearMonth = YearMonth.now()): Usage {
        val key = month.toString()
        if (prefs.getString(USAGE_MONTH, null) != key) return Usage(key, 0, 0, 0)
        return Usage(key, prefs.getInt(USAGE_REQUESTS, 0), prefs.getLong(USAGE_IN, 0), prefs.getLong(USAGE_OUT, 0))
    }

    fun recordUsage(reply: AiReply, month: YearMonth = YearMonth.now()) {
        val current = usage(month)
        prefs.edit()
            .putString(USAGE_MONTH, current.month)
            .putInt(USAGE_REQUESTS, current.requests + 1)
            .putLong(USAGE_IN, current.inputTokens + reply.inputTokens)
            .putLong(USAGE_OUT, current.outputTokens + reply.outputTokens)
            .apply()
    }

    fun resetUsage() = prefs.edit().remove(USAGE_MONTH).remove(USAGE_REQUESTS).remove(USAGE_IN).remove(USAGE_OUT).apply()

    fun overMonthlyLimit(month: YearMonth = YearMonth.now()): Boolean =
        monthlyTokenLimit > 0 && usage(month).totalTokens >= monthlyTokenLimit

    /** A client for the chosen provider, or null until a provider (and its key, where needed) is set. */
    fun client(): AiClient? {
        val chosen = provider ?: return null
        return runCatching { AiClients.create(chosen, baseUrl(chosen), apiKey(chosen)) }.getOrNull()
    }

    /** A provider and a model are chosen, and the key is there where the provider needs one. */
    val isReady: Boolean
        get() {
            val chosen = provider ?: return false
            return model(chosen) != null && (!chosen.needsKey || hasKey(chosen))
        }

    /** Calls [listener] after any assistant setting is saved; call the returned function to stop. */
    fun onChange(listener: () -> Unit): () -> Unit {
        // SharedPreferences keeps listeners weakly; the returned function holds this one.
        val callback = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> listener() }
        prefs.registerOnSharedPreferenceChangeListener(callback)
        return { prefs.unregisterOnSharedPreferenceChangeListener(callback) }
    }

    private fun keyName(provider: AiProvider) = "api_key_${provider.id}"

    companion object {
        const val DEFAULT_MAX_OUTPUT = 4000
        private const val FILE = "rift_assistant"
        private const val PROVIDER = "provider"
        private const val MAX_OUTPUT = "max_output_tokens"
        private const val MONTHLY_LIMIT = "monthly_token_limit"
        private const val ALLOW_ENV = "allow_env_and_args"
        private const val USAGE_MONTH = "usage_month"
        private const val USAGE_REQUESTS = "usage_requests"
        private const val USAGE_IN = "usage_input_tokens"
        private const val USAGE_OUT = "usage_output_tokens"

        fun of(context: Context): AiSettings =
            AiSettings(context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE))
    }
}

/** AES-256-GCM with a non-exportable Keystore key of Rift's own (not GameNative's "pluvia_secret"). */
private object KeyCipher {
    private const val ALIAS = "rift_ai_keys"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    fun encrypt(text: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    fun decrypt(stored: String): String {
        val sealed = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
        }
        return String(cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES), Charsets.UTF_8)
    }
}
