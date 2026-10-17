package dev.rift.launcher.assistant.ai

/** The two request formats Rift speaks: Anthropic's Messages API and the OpenAI-compatible Chat Completions. */
enum class AiProtocol { ANTHROPIC, OPENAI }

/**
 * Providers the user can bring a key for. Everything except Anthropic goes through the OpenAI-compatible
 * format, which these services publish themselves. [Custom] is any such address: Ollama or LM Studio on the
 * user's PC, or their own proxy, where a key may not be needed.
 */
enum class AiProvider(
    val id: String,
    val label: String,
    val protocol: AiProtocol,
    val defaultBaseUrl: String,
    /** Where the user creates a key. */
    val keysUrl: String?,
    /** The model picked when the user has not chosen one; null lets them pick from the provider's list. */
    val recommendedModel: String? = null,
    val needsKey: Boolean = true,
) {
    Anthropic(
        "anthropic", "Anthropic (Claude)", AiProtocol.ANTHROPIC, "https://api.anthropic.com",
        "https://console.anthropic.com/settings/keys", recommendedModel = "claude-sonnet-5-5",
    ),
    OpenAI("openai", "OpenAI", AiProtocol.OPENAI, "https://api.openai.com/v1", "https://platform.openai.com/api-keys"),
    Gemini(
        "gemini", "Google Gemini", AiProtocol.OPENAI, "https://generativelanguage.googleapis.com/v1beta/openai",
        "https://aistudio.google.com/apikey",
    ),
    OpenRouter("openrouter", "OpenRouter", AiProtocol.OPENAI, "https://openrouter.ai/api/v1", "https://openrouter.ai/keys"),
    DeepSeek("deepseek", "DeepSeek", AiProtocol.OPENAI, "https://api.deepseek.com", "https://platform.deepseek.com/api_keys"),
    Qwen(
        "qwen", "Qwen (DashScope)", AiProtocol.OPENAI, "https://dashscope-intl.aliyuncs.com/compatible-mode/v1",
        "https://bailian.console.alibabacloud.com/?apiKey=1",
    ),
    Custom("custom", "OpenAI-compatible", AiProtocol.OPENAI, "", null, needsKey = false),
    ;

    companion object {
        fun byId(id: String?): AiProvider? = entries.firstOrNull { it.id == id }
    }
}
