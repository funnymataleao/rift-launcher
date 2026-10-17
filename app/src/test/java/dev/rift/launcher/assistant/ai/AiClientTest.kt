package dev.rift.launcher.assistant.ai

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class AiClientTest {

    private val key = "sk-ant-api03-SECRETSECRETSECRET-tail1234"
    private lateinit var server: MockWebServer
    private val http = OkHttpClient()

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun base() = server.url("/").toString().trimEnd('/')

    @Test
    fun anthropicSendsItsHeadersAndReadsTextAndUsage() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"model":"claude-sonnet-5-5","stop_reason":"end_turn",
                   "content":[{"type":"text","text":"Hello"},{"type":"text","text":" there"}],
                   "usage":{"input_tokens":10,"cache_read_input_tokens":5,"output_tokens":3}}""",
            ),
        )
        val client = AiClients.create(AiProvider.Anthropic, base(), key, http)
        val reply = client.complete(
            AiRequest("claude-sonnet-5-5", "Be brief", listOf(AiMessage(AiMessage.Role.USER, "Hi")), 100),
        )
        assertEquals("Hello there", reply.text)
        assertEquals(15L, reply.inputTokens)
        assertEquals(3L, reply.outputTokens)
        assertFalse(reply.truncated)
        val recorded = server.takeRequest()
        assertEquals("/v1/messages", recorded.path)
        assertEquals(key, recorded.getHeader("x-api-key"))
        assertEquals("2023-06-01", recorded.getHeader("anthropic-version"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"system\":\"Be brief\""))
        assertTrue(body.contains("\"max_tokens\":100"))
    }

    @Test
    fun anthropicModelsFollowPages() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"data":[{"id":"a","display_name":"A"}],"has_more":true,"last_id":"a"}"""))
        server.enqueue(MockResponse().setBody("""{"data":[{"id":"b"}],"has_more":false,"last_id":"b"}"""))
        val models = AiClients.create(AiProvider.Anthropic, base(), key, http).listModels()
        assertEquals(listOf(AiModel("a", "A"), AiModel("b", "b")), models)
        server.takeRequest()
        assertEquals("/v1/models?limit=100&after_id=a", server.takeRequest().path)
    }

    @Test
    fun openAiCompatibleUsesBearerAndSystemMessage() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"model":"deepseek-chat","choices":[{"message":{"role":"assistant","content":"ok"},"finish_reason":"length"}],
                   "usage":{"prompt_tokens":7,"completion_tokens":2}}""",
            ),
        )
        val reply = AiClients.create(AiProvider.DeepSeek, base(), "sk-deep", http)
            .complete(AiRequest("deepseek-chat", "sys", listOf(AiMessage(AiMessage.Role.USER, "q")), 50))
        assertEquals("ok", reply.text)
        assertTrue(reply.truncated)
        val recorded = server.takeRequest()
        assertEquals("/chat/completions", recorded.path)
        assertEquals("Bearer sk-deep", recorded.getHeader("Authorization"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("{\"role\":\"system\",\"content\":\"sys\"}"))
        assertTrue(body.contains("\"max_tokens\":50"))
    }

    @Test
    fun openAiItselfGetsMaxCompletionTokens() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"x"}}]}"""))
        AiClients.create(AiProvider.OpenAI, base(), "sk-openai", http)
            .complete(AiRequest("m", "", listOf(AiMessage(AiMessage.Role.USER, "q")), 10))
        assertTrue(server.takeRequest().body.readUtf8().contains("\"max_completion_tokens\":10"))
    }

    @Test
    fun localServerNeedsNoKey() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"data":[{"id":"models/qwen3:8b"}]}"""))
        val models = AiClients.create(AiProvider.Custom, base(), "", http).listModels()
        assertEquals("qwen3:8b", models.single().id)
        assertEquals(null, server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun missingKeyFailsBeforeAnyRequest() {
        try {
            AiClients.create(AiProvider.Anthropic, base(), " ", http)
            fail()
        } catch (e: AiException) {
            assertEquals(AiException.Kind.NO_KEY, e.kind)
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun errorsAreSortedAndNeverCarryTheKey() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"invalid x-api-key $key"}}"""))
        val error = runCatching { AiClients.create(AiProvider.Anthropic, base(), key, http).listModels() }.exceptionOrNull() as AiException
        assertEquals(AiException.Kind.AUTH, error.kind)
        assertFalse(error.detail.contains("SECRET"))
        assertFalse(error.message!!.contains(key))
    }

    @Test
    fun classifiesRegionQuotaAndLimits() {
        fun kind(code: Int, body: String) = AiErrors.classify(code, body, key).kind
        assertEquals(
            AiException.Kind.REGION,
            kind(403, """{"error":{"code":"unsupported_country_region_territory","message":"Country, region, or territory not supported"}}"""),
        )
        assertEquals(AiException.Kind.REGION, kind(400, """[{"error":{"code":400,"message":"User location is not supported for the API use."}}]"""))
        assertEquals(AiException.Kind.QUOTA, kind(400, """{"error":{"message":"Your credit balance is too low to access the Anthropic API."}}"""))
        assertEquals(AiException.Kind.QUOTA, kind(429, """{"error":{"type":"insufficient_quota","message":"You exceeded your current quota"}}"""))
        assertEquals(AiException.Kind.RATE_LIMIT, kind(429, """{"error":{"message":"rate limited"}}"""))
        assertEquals(AiException.Kind.SERVER, kind(529, """{"error":{"type":"overloaded_error","message":"Overloaded"}}"""))
        assertEquals(AiException.Kind.NOT_FOUND, kind(404, "not here"))
    }

    @Test
    fun redactsShortenedKeys() {
        assertEquals("key ••••", AiSecrets.redact("key $key", key))
        assertEquals("key ••••…", AiSecrets.redact("key ${key.take(12)}…", key))
        assertEquals("••••1234", AiSecrets.lastFour(key))
    }
}
