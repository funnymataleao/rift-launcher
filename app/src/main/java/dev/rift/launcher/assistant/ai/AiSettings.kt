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
 * One provider the user brought a key for. There can be any number, several of the same kind too (two Anthropic
 * accounts, an Ollama on the PC); one is active. [id] names its stored values, [type] is the service it talks to.
 */
data class AiConnection(val id: String, val type: AiProvider, val name: String)

/**
 * The assistant's settings, its providers and the user's API keys, in their own SharedPreferences file ("rift_assistant").
 * The app does not allow backup, so none of it leaves the device. Keys are encrypted with an AES-GCM key that
 * lives in the Android Keystore and never leaves it; only their last four characters are ever shown.
 */
class AiSettings private constructor(private val prefs: SharedPreferences) {

    init {
        migrate()
    }

    /** Every provider the user added, in the order they were added. */
    val connections: List<AiConnection>
        get() = prefs.getString(CONNECTIONS, null).orEmpty().split(',').filter { it.isNotEmpty() }.mapNotNull { id ->
            val type = AiProvider.byId(prefs.getString("type_$id", null)) ?: return@mapNotNull null
            AiConnection(id, type, prefs.getString("name_$id", null) ?: type.label)
        }

    /** The provider requests go to: the one chosen, or the first when none is. */
    val active: AiConnection?
        get() {
            val all = connections
            val id = prefs.getString(ACTIVE, null)
            return all.firstOrNull { it.id == id } ?: all.firstOrNull()
        }

    fun activate(connection: AiConnection) = prefs.edit().putString(ACTIVE, connection.id).apply()

    /** Adds a provider of [type] named [name] and makes it the active one. */
    fun add(type: AiProvider, name: String): AiConnection {
        val ids = connections.map { it.id }
        var id = type.id
        var n = 2
        while (id in ids || prefs.contains("type_$id") || prefs.contains(keyName(id))) id = "${type.id}_${n++}"
        val connection = AiConnection(id, type, name)
        prefs.edit()
            .putString("type_$id", type.id)
            .putString("name_$id", name)
            .putString(CONNECTIONS, (ids + id).joinToString(","))
            .putString(ACTIVE, id)
            .apply()
        return connection
    }

    fun rename(connection: AiConnection, name: String) {
        if (name.isNotBlank()) prefs.edit().putString("name_${connection.id}", name.trim()).apply()
    }

    /** Forgets a provider with its key, address and model. The first one left becomes active if it was. */
    fun remove(connection: AiConnection) {
        val left = connections.filterNot { it.id == connection.id }
        val id = connection.id
        val editor = prefs.edit()
            .remove("type_$id").remove("name_$id").remove(keyName(id)).remove("base_url_$id").remove("model_$id")
            .putString(CONNECTIONS, left.joinToString(",") { it.id })
        if (prefs.getString(ACTIVE, null) == id) {
            left.firstOrNull()?.let { editor.putString(ACTIVE, it.id) } ?: editor.remove(ACTIVE)
        }
        editor.apply()
    }

    fun hasKey(connection: AiConnection): Boolean = prefs.contains(keyName(connection.id))

    /** The saved key, or "" when there is none or it can no longer be decrypted (for example after a Keystore reset). */
    fun apiKey(connection: AiConnection): String {
        val stored = prefs.getString(keyName(connection.id), null) ?: return ""
        return try {
            KeyCipher.decrypt(stored)
        } catch (e: Exception) {
            Timber.w("AiSettings: saved key for ${connection.id} can't be decrypted")
            ""
        }
    }

    fun setApiKey(connection: AiConnection, key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) {
            prefs.edit().remove(keyName(connection.id)).apply()
        } else {
            prefs.edit().putString(keyName(connection.id), KeyCipher.encrypt(trimmed)).apply()
        }
    }

    /** The address requests go to: the provider's own, or one the user set (their proxy, Ollama, LM Studio). */
    fun baseUrl(connection: AiConnection): String =
        prefs.getString("base_url_${connection.id}", null) ?: connection.type.defaultBaseUrl

    fun setBaseUrl(connection: AiConnection, url: String) {
        val trimmed = url.trim().trimEnd('/')
        val editor = prefs.edit()
        if (trimmed.isEmpty() || trimmed == connection.type.defaultBaseUrl) {
            editor.remove("base_url_${connection.id}")
        } else {
            editor.putString("base_url_${connection.id}", trimmed)
        }
        editor.apply()
    }

    fun model(connection: AiConnection): String? = prefs.getString("model_${connection.id}", null) ?: connection.type.recommendedModel

    fun setModel(connection: AiConnection, model: String) = prefs.edit().putString("model_${connection.id}", model).apply()

    /** A client for [connection] as it is saved now; throws [AiException] (NO_KEY, NOT_FOUND) when it can't be made. */
    fun clientFor(connection: AiConnection): AiClient = AiClients.create(connection.type, baseUrl(connection), apiKey(connection))

    /**
     * Before several providers, there was one chosen provider and values per provider kind ("api_key_anthropic").
     * Each kind that has anything saved becomes a provider of its own whose id is that kind, so its key and the
     * rest stay where they are.
     */
    private fun migrate() {
        if (prefs.contains(CONNECTIONS)) return
        val chosen = prefs.getString(OLD_PROVIDER, null)
        val ids = AiProvider.entries.filter { type ->
            type.id == chosen || prefs.contains(keyName(type.id)) || prefs.contains("base_url_${type.id}") ||
                prefs.contains("model_${type.id}")
        }.map { it.id }
        val editor = prefs.edit()
        ids.forEach { editor.putString("type_$it", it) }
        editor.putString(CONNECTIONS, ids.joinToString(","))
        if (chosen != null && chosen in ids) editor.putString(ACTIVE, chosen)
        editor.remove(OLD_PROVIDER).apply()
    }

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

    /** A provider is active, its model is chosen, and the key is there where the provider needs one. */
    val isReady: Boolean
        get() {
            val chosen = active ?: return false
            return model(chosen) != null && (!chosen.type.needsKey || hasKey(chosen))
        }

    /** Calls [listener] after any assistant setting is saved; call the returned function to stop. */
    fun onChange(listener: () -> Unit): () -> Unit {
        // SharedPreferences keeps listeners weakly; the returned function holds this one.
        val callback = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> listener() }
        prefs.registerOnSharedPreferenceChangeListener(callback)
        return { prefs.unregisterOnSharedPreferenceChangeListener(callback) }
    }

    private fun keyName(id: String) = "api_key_$id"

    companion object {
        const val DEFAULT_MAX_OUTPUT = 4000
        private const val FILE = "rift_assistant"
        /** Before several providers: the id of the one chosen kind. */
        private const val OLD_PROVIDER = "provider"
        private const val CONNECTIONS = "connections"
        private const val ACTIVE = "active_connection"
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
