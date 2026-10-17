package dev.rift.launcher.community

import android.content.Context
import android.os.Build
import app.gamenative.BuildConfig
import app.gamenative.api.ApiResult
import app.gamenative.api.CommunityConfigService
import app.gamenative.api.GameRunApi
import app.gamenative.api.sanitizeCommunityConfig
import app.gamenative.utils.BestConfigService
import app.gamenative.utils.Net
import com.winlator.container.Container
import dev.rift.launcher.settings.RiftPrefs
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * Rift's own community database (server/rift-community): the "How did the game run?" answers go
 * there instead of api.gamenative.app, whose API serves official GameNative builds only.
 *
 * Every answer is also kept next to the game's container, so it stays on the tablet when sharing
 * is off or the server cannot be reached.
 */
object RiftCommunity {

    /** The deployed Worker; empty until it is, and then answers are only kept on the tablet. */
    const val BASE_URL = ""

    private const val LOCAL_DIR = ".rift/feedback"
    private const val LOCAL_KEPT = 30

    /** Reads Rift's database with GameNative's own client (same routes and JSON); null until deployed. */
    val configService: CommunityConfigService? by lazy {
        BASE_URL.takeIf { it.isNotEmpty() }?.let { CommunityConfigService(baseUrl = it) }
    }

    private val bestConfigCache = ConcurrentHashMap<String, BestConfigService.BestConfigResponse>()

    /**
     * Rift's best config for the game on this GPU (same request and answer as GameNative's
     * /api/best-config), or null when the server is not set up, cannot be reached or has nothing.
     */
    suspend fun fetchBestConfig(
        gameName: String,
        gpuName: String,
        gameStore: String,
    ): BestConfigService.BestConfigResponse? = withContext(Dispatchers.IO) {
        if (BASE_URL.isEmpty()) return@withContext null
        val cacheKey = "${gameName}_${gpuName}_$gameStore"
        bestConfigCache[cacheKey]?.let { return@withContext it }
        val request = JSONObject().apply {
            put("gameName", gameName)
            put("gpuName", gpuName)
            put("game_store", gameStore)
            put("model", Build.MODEL)
            put("modernBuild", BuildConfig.MODERN_ANDROID)
        }
        val call = Request.Builder()
            .url("$BASE_URL/api/best-config")
            .post(request.toString().toRequestBody("application/json".toMediaType()))
            .build()
        try {
            Net.http.newCall(call).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.tag("RiftCommunity").w("Best config: HTTP ${response.code}")
                    return@withContext null
                }
                val json = JSONObject(response.body?.string().orEmpty())
                val matchType = json.optString("matchType", "no_match")
                if (matchType == "no_match") return@withContext null
                BestConfigService.BestConfigResponse(
                    bestConfig = Json.parseToJsonElement(json.getJSONObject("bestConfig").toString()).jsonObject,
                    matchType = matchType,
                    matchedGpu = json.optString("matchedGpu"),
                    matchedDeviceId = json.optInt("matchedDeviceId"),
                    matchedStore = json.optString("matchedStore", gameStore),
                ).also { bestConfigCache[cacheKey] = it }
            }
        } catch (e: Exception) {
            Timber.tag("RiftCommunity").w(e, "Best config: ${e.message}")
            null
        }
    }

    suspend fun submitGameRun(
        context: Context,
        container: Container,
        gameStore: String,
        storeGameId: String,
        gameName: String,
        deviceModel: String,
        deviceManufacturer: String,
        gpuName: String,
        socName: String?,
        androidVersion: String,
        appVersion: String,
        configs: JSONObject,
        rating: Int,
        tags: List<String>,
        notes: String?,
        avgFps: Float?,
        sessionLengthSec: Int?,
    ): ApiResult<GameRunApi.GameRunResponse> = withContext(Dispatchers.IO) {
        val prefs = RiftPrefs.of(context)
        val body = JSONObject().apply {
            put("gameName", gameName)
            put("gameStore", gameStore)
            put("storeGameId", storeGameId)
            put("deviceModel", deviceModel)
            put("deviceManufacturer", deviceManufacturer)
            put("gpuName", gpuName)
            put("socName", socName ?: JSONObject.NULL)
            put("androidVersion", androidVersion)
            put("appVersion", appVersion)
            // Only the settings a community config can carry: no container id, drives or paths.
            put("configs", communityConfig(configs))
            put("rating", rating)
            put("tags", JSONArray(tags))
            put("notes", notes ?: JSONObject.NULL)
            put("avgFps", avgFps?.toDouble() ?: JSONObject.NULL)
            put("sessionLengthSec", sessionLengthSec ?: JSONObject.NULL)
            put("installId", prefs.communityInstallId)
        }
        keepOnDevice(container, body)

        if (BASE_URL.isEmpty() || !prefs.communityShare) {
            return@withContext ApiResult.Success(GameRunApi.GameRunResponse(id = null, created = false))
        }
        val request = Request.Builder()
            .url("$BASE_URL/api/game-run")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        try {
            Net.http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = runCatching { JSONObject(text).optJSONObject("error")?.optString("message") }
                        .getOrNull() ?: text
                    Timber.tag("RiftCommunity").w("HTTP ${response.code}: $message")
                    return@withContext ApiResult.HttpError(response.code, message)
                }
                val json = JSONObject(text)
                ApiResult.Success(
                    GameRunApi.GameRunResponse(
                        id = if (json.isNull("id")) null else json.optLong("id"),
                        created = json.optBoolean("created", false),
                    ),
                )
            }
        } catch (e: IOException) {
            Timber.tag("RiftCommunity").e(e, "Network error: ${e.message}")
            ApiResult.NetworkError(e)
        }
    }

    private fun communityConfig(configs: JSONObject): JSONObject {
        val config = Json.parseToJsonElement(configs.toString()).jsonObject
        return JSONObject(sanitizeCommunityConfig(config).toString())
    }

    private fun keepOnDevice(container: Container, body: JSONObject) {
        runCatching {
            val dir = File(container.rootDir, LOCAL_DIR).also { it.mkdirs() }
            val local = JSONObject(body.toString()).apply {
                remove("installId")
                put("savedAt", System.currentTimeMillis())
            }
            File(dir, "${System.currentTimeMillis()}.json").writeText(local.toString())
            dir.listFiles { file -> file.extension == "json" }
                ?.sortedByDescending { it.name }
                ?.drop(LOCAL_KEPT)
                ?.forEach { it.delete() }
        }.onFailure { Timber.tag("RiftCommunity").w(it, "Could not keep the answer on the device") }
    }
}
