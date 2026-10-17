package dev.rift.launcher.assistant

import app.gamenative.api.SuggestionConfigKeys
import com.winlator.container.ContainerData
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.json.JSONObject

/**
 * The container settings the assistant reads and compares. It is exactly GameNative's own list of keys a
 * support suggestion may change ([SuggestionConfigKeys.APPLICABLE_CONFIG_KEYS]), so whatever the assistant
 * proposes later can go through GameNative's applier and its rollback unchanged.
 */
object GameSettings {

    val KEYS: Set<String> = SuggestionConfigKeys.APPLICABLE_CONFIG_KEYS.keys

    /** Keys that describe how a game runs rather than which file it starts; communities are grouped by these. */
    val RUNTIME_KEYS: List<String> = listOf(
        "containerVariant",
        "wineVersion",
        "graphicsDriver",
        "graphicsDriverVersion",
        "dxwrapper",
        "dxwrapperConfig",
        "emulator",
        "box64Version",
        "box64Preset",
        "fexcoreVersion",
        "fexcorePreset",
    )

    @Serializable
    data class Change(val key: String, val from: String?, val to: String?)

    fun fromContainerData(data: ContainerData): Map<String, String> =
        KEYS.mapNotNull { key -> SuggestionConfigKeys.configValueOf(data, key)?.let { key to it } }.toMap()

    /** Reads settings from a container's JSON, the form GameNative keeps in run records. */
    fun fromJson(json: JSONObject): Map<String, String> = buildMap {
        for (key in KEYS) {
            if (!json.has(key) || json.isNull(key)) continue
            put(key, json.get(key).toString())
        }
    }

    /** Reads settings from a community config, the form GameNative's community service returns. */
    fun fromJsonObject(json: JsonObject): Map<String, String> = buildMap {
        for (key in KEYS) {
            val element = json[key] ?: continue
            if (element is JsonNull) continue
            put(key, (element as? JsonPrimitive)?.content ?: element.toString())
        }
    }

    fun diff(from: Map<String, String>, to: Map<String, String>, keys: Collection<String> = KEYS): List<Change> =
        keys.mapNotNull { key ->
            val a = from[key]
            val b = to[key]
            if (a == b) null else Change(key, a, b)
        }
}
