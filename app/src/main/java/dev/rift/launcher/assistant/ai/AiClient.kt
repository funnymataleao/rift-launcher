package dev.rift.launcher.assistant.ai

import app.gamenative.utils.Net
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class AiModel(val id: String, val name: String = id)

data class AiMessage(val role: Role, val text: String, val images: List<String> = emptyList()) {
    enum class Role(val wire: String) { USER("user"), ASSISTANT("assistant") }
}

data class AiRequest(
    val model: String,
    val system: String,
    val messages: List<AiMessage>,
    val maxOutputTokens: Int,
)

data class AiReply(
    val text: String,
    val model: String,
    val inputTokens: Long,
    val outputTokens: Long,
    /** True when the answer stopped at [AiRequest.maxOutputTokens] and is cut off. */
    val truncated: Boolean,
)

/** A failed call, sorted so the settings page can say what to do. [detail] never contains the key. */
class AiException(val kind: Kind, val httpCode: Int? = null, val detail: String = "") :
    IOException("$kind${httpCode?.let { " ($it)" }.orEmpty()}: $detail") {
    enum class Kind { NO_KEY, AUTH, REGION, QUOTA, RATE_LIMIT, NOT_FOUND, NETWORK, SERVER, BAD_RESPONSE }
}

interface AiClient {
    suspend fun listModels(): List<AiModel>
    suspend fun complete(request: AiRequest): AiReply
}

/**
 * Plain HTTP clients for both formats, on GameNative's shared OkHttp client (its DNS-over-HTTPS fallback
 * helps where a provider's name doesn't resolve). Nothing here logs requests, headers or bodies.
 */
object AiClients {

    private val http: OkHttpClient by lazy {
        Net.http.newBuilder().readTimeout(180, TimeUnit.SECONDS).build()
    }
    private val localHttp: OkHttpClient by lazy {
        // Local models can take several minutes to load before producing the first token.
        Net.http.newBuilder().readTimeout(15, TimeUnit.MINUTES).callTimeout(20, TimeUnit.MINUTES).build()
    }

    fun create(provider: AiProvider, baseUrl: String, apiKey: String, client: OkHttpClient? = null): AiClient {
        val base = baseUrl.ifBlank { provider.defaultBaseUrl }.trimEnd('/')
        if (base.isEmpty()) throw AiException(AiException.Kind.NOT_FOUND, detail = "no address")
        if (provider.needsKey && apiKey.isBlank()) throw AiException(AiException.Kind.NO_KEY)
        val effectiveClient = client ?: if (provider == AiProvider.Ollama) localHttp else http
        return when (provider.protocol) {
            AiProtocol.ANTHROPIC -> AnthropicClient(effectiveClient, base, apiKey)
            AiProtocol.OPENAI -> OpenAiCompatibleClient(effectiveClient, base, apiKey, provider)
        }
    }
}

private val JSON_MEDIA = "application/json".toMediaType()
private val json = Json { ignoreUnknownKeys = true }

private suspend fun OkHttpClient.call(request: Request, apiKey: String): JsonObject = withContext(Dispatchers.IO) {
    val response = try {
        newCall(request).execute()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        throw AiException(AiException.Kind.NETWORK, detail = AiSecrets.redact(e.message.orEmpty(), apiKey))
    }
    response.use {
        val body = it.body.string()
        if (!it.isSuccessful) throw AiErrors.classify(it.code, body, apiKey)
        try {
            json.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            throw AiException(AiException.Kind.BAD_RESPONSE, it.code, AiSecrets.redact(body.take(200), apiKey))
        }
    }
}

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.long(key: String): Long = (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L

/** Anthropic Messages API: `x-api-key` and `anthropic-version` headers, `/v1/models` and `/v1/messages`. */
private class AnthropicClient(
    private val http: OkHttpClient,
    private val base: String,
    private val apiKey: String,
) : AiClient {

    private fun request(path: String) = Request.Builder()
        .url("$base$path")
        .header("x-api-key", apiKey)
        .header("anthropic-version", "2023-06-01")

    override suspend fun listModels(): List<AiModel> {
        val models = ArrayList<AiModel>()
        var after: String? = null
        do {
            val page = http.call(request("/v1/models?limit=100" + after?.let { "&after_id=$it" }.orEmpty()).get().build(), apiKey)
            page["data"]?.jsonArray?.forEach { item ->
                val obj = item.jsonObject
                val id = obj.string("id") ?: return@forEach
                models.add(AiModel(id, obj.string("display_name") ?: id))
            }
            after = page.string("last_id")?.takeIf { (page["has_more"] as? JsonPrimitive)?.contentOrNull == "true" }
        } while (after != null && models.size < 1000)
        return models
    }

    override suspend fun complete(request: AiRequest): AiReply {
        val body = buildJsonObject {
            put("model", request.model)
            put("max_tokens", request.maxOutputTokens)
            if (request.system.isNotEmpty()) put("system", request.system)
            put(
                "messages",
                buildJsonArray {
                    request.messages.forEach { m ->
                        add(
                            buildJsonObject {
                                put("role", m.role.wire)
                                if (m.images.isEmpty()) {
                                    put("content", m.text)
                                } else {
                                    // Rift: pictures first, then the text, as Anthropic recommends.
                                    put(
                                        "content",
                                        buildJsonArray {
                                            m.images.forEach { data ->
                                                add(
                                                    buildJsonObject {
                                                        put("type", "image")
                                                        put("source", buildJsonObject { put("type", "base64"); put("media_type", "image/jpeg"); put("data", data) })
                                                    },
                                                )
                                            }
                                            add(buildJsonObject { put("type", "text"); put("text", m.text) })
                                        },
                                    )
                                }
                            },
                        )
                    }
                },
            )
        }
        val reply = http.call(request("/v1/messages").post(body.toString().toRequestBody(JSON_MEDIA)).build(), apiKey)
        val text = (reply["content"] as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
            .filter { it.string("type") == "text" }
            .joinToString("") { it.string("text").orEmpty() }
        val usage = reply["usage"] as? JsonObject ?: JsonObject(emptyMap())
        return AiReply(
            text = text,
            model = reply.string("model") ?: request.model,
            inputTokens = usage.long("input_tokens") + usage.long("cache_read_input_tokens") + usage.long("cache_creation_input_tokens"),
            outputTokens = usage.long("output_tokens"),
            truncated = reply.string("stop_reason") == "max_tokens",
        )
    }
}

/** OpenAI-compatible Chat Completions: `Authorization: Bearer`, `/models` and `/chat/completions`. */
private class OpenAiCompatibleClient(
    private val http: OkHttpClient,
    private val base: String,
    private val apiKey: String,
    private val provider: AiProvider,
) : AiClient {

    private fun request(path: String) = Request.Builder().url("$base$path").apply {
        if (apiKey.isNotBlank()) header("Authorization", "Bearer $apiKey")
        if (provider == AiProvider.OpenRouter) {
            header("HTTP-Referer", "https://github.com/funnymataleao/rift-launcher")
            header("X-Title", "Rift Launcher")
        }
    }

    override suspend fun listModels(): List<AiModel> {
        val page = http.call(request("/models").get().build(), apiKey)
        return (page["data"] as? JsonArray).orEmpty().mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val id = obj.string("id") ?: return@mapNotNull null
            AiModel(id.removePrefix("models/"), obj.string("name") ?: obj.string("display_name") ?: id.removePrefix("models/"))
        }
    }

    override suspend fun complete(request: AiRequest): AiReply {
        val body = buildJsonObject {
            put("model", request.model)
            // OpenAI's current models only take max_completion_tokens; the other services take max_tokens.
            put(if (provider == AiProvider.OpenAI) "max_completion_tokens" else "max_tokens", request.maxOutputTokens)
            put(
                "messages",
                buildJsonArray {
                    if (request.system.isNotEmpty()) add(buildJsonObject { put("role", "system"); put("content", request.system) })
                    request.messages.forEach { m ->
                        add(
                            buildJsonObject {
                                put("role", m.role.wire)
                                if (m.images.isEmpty()) {
                                    put("content", m.text)
                                } else {
                                    // Rift: OpenAI-style parts (Ollama and the other compatible servers read them too).
                                    put(
                                        "content",
                                        buildJsonArray {
                                            add(buildJsonObject { put("type", "text"); put("text", m.text) })
                                            m.images.forEach { data ->
                                                add(
                                                    buildJsonObject {
                                                        put("type", "image_url")
                                                        put("image_url", buildJsonObject { put("url", "data:image/jpeg;base64,$data") })
                                                    },
                                                )
                                            }
                                        },
                                    )
                                }
                            },
                        )
                    }
                },
            )
        }
        val reply = http.call(request("/chat/completions").post(body.toString().toRequestBody(JSON_MEDIA)).build(), apiKey)
        val choice = (reply["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
            ?: throw AiException(AiException.Kind.BAD_RESPONSE, detail = "no choices")
        val message = choice["message"] as? JsonObject
        val usage = reply["usage"] as? JsonObject ?: JsonObject(emptyMap())
        return AiReply(
            text = message?.string("content").orEmpty(),
            model = reply.string("model") ?: request.model,
            inputTokens = usage.long("prompt_tokens"),
            outputTokens = usage.long("completion_tokens"),
            truncated = choice.string("finish_reason") == "length",
        )
    }
}

internal object AiErrors {

    private val REGION = Regex(
        "unsupported_country|country, region, or territory|location is not supported|not available in your (country|region)|" +
            "request not allowed",
        RegexOption.IGNORE_CASE,
    )
    private val QUOTA = Regex("insufficient_quota|credit balance|billing|exceeded your current quota|insufficient credits", RegexOption.IGNORE_CASE)

    fun classify(code: Int, body: String, apiKey: String): AiException {
        val message = providerMessage(body)
        val detail = AiSecrets.redact(message.ifEmpty { body }.take(300), apiKey)
        val text = "$message $body"
        val kind = when {
            REGION.containsMatchIn(text) -> AiException.Kind.REGION
            QUOTA.containsMatchIn(text) || code == 402 -> AiException.Kind.QUOTA
            code == 401 || code == 403 -> AiException.Kind.AUTH
            code == 429 -> AiException.Kind.RATE_LIMIT
            code == 404 -> AiException.Kind.NOT_FOUND
            code >= 500 -> AiException.Kind.SERVER
            else -> AiException.Kind.BAD_RESPONSE
        }
        return AiException(kind, code, detail)
    }

    /** `{"error":{"message":…}}` in both formats; some OpenAI-compatible services send a list of those. */
    private fun providerMessage(body: String): String = runCatching {
        val root = json.parseToJsonElement(body)
        val obj = (root as? JsonArray)?.firstOrNull()?.jsonObject ?: root.jsonObject
        val error = obj["error"]
        when (error) {
            is JsonObject -> error.string("message").orEmpty()
            is JsonPrimitive -> error.contentOrNull.orEmpty()
            else -> obj.string("message").orEmpty()
        }
    }.getOrDefault("")
}

internal object AiSecrets {
    /** Removes the key, and anything that looks like one of its prefixes, from text shown or logged. */
    fun redact(text: String, apiKey: String): String {
        if (apiKey.length < 8) return text
        var result = text.replace(apiKey, "••••")
        // Providers sometimes echo a shortened key such as "sk-ant-api03-abc…xyz".
        result = result.replace(apiKey.take(12), "••••")
        return result.replace(apiKey.takeLast(8), "••••")
    }

    fun lastFour(apiKey: String): String = if (apiKey.length <= 4) "••••" else "••••" + apiKey.takeLast(4)
}
