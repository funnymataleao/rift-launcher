package dev.rift.launcher.assistant.ai

import android.content.Context
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Finds Ollama on this Android device or the current /24 LAN, then verifies its native /api/tags endpoint. */
object OllamaDiscovery {
    private val probe = OkHttpClient.Builder()
        .connectTimeout(220, TimeUnit.MILLISECONDS)
        .readTimeout(450, TimeUnit.MILLISECONDS)
        .callTimeout(700, TimeUnit.MILLISECONDS)
        .build()

    private data class Host(val ip: String, val modelCount: Int, val largestModelB: Double, val totalBytes: Long)

    /** Scan the whole subnet and prefer the host with the most capable local models, not the first responder. */
    suspend fun find(context: Context): String? = withContext(Dispatchers.IO) {
        val local = localIpv4(context)
        val candidates = linkedSetOf("127.0.0.1")
        local?.let { ip ->
            val prefix = ip.substringBeforeLast('.')
            listOf(1, 2, 10, 20, 50, 100, 140, 254).forEach { candidates += "$prefix.$it" }
            candidates += ip
            (1..254).forEach { candidates += "$prefix.$it" }
        }
        val hosts = coroutineScope {
            candidates.toList().chunked(48).flatMap { batch ->
                batch.map { host -> async { inspect(host) } }.awaitAll().filterNotNull()
            }
        }
        val best = hosts.maxWithOrNull(
            compareBy<Host> { it.largestModelB }
                .thenBy { it.modelCount }
                .thenBy { it.totalBytes },
        )
        best?.let { "http://${it.ip}:11434/v1" }
    }

    private fun inspect(host: String): Host? = runCatching {
        probe.newCall(Request.Builder().url("http://$host:11434/api/tags").get().build()).execute().use { response ->
            if (!response.isSuccessful || !response.header("Content-Type").orEmpty().contains("application/json")) return null
            val root = JSONObject(response.body?.string().orEmpty())
            val models = root.optJSONArray("models") ?: return null
            if (models.length() == 0) return Host(host, 0, 0.0, 0L)
            var maxB = 0.0
            var totalBytes = 0L
            for (i in 0 until models.length()) {
                val model = models.optJSONObject(i) ?: continue
                val bytes = model.optLong("size", 0L)
                totalBytes += bytes
                val details = model.optJSONObject("details")
                val paramSize = details?.optString("parameter_size", "").orEmpty()
                    .uppercase().replace("B", "").toDoubleOrNull() ?: 0.0
                if (paramSize > maxB) maxB = paramSize
            }
            Host(host, models.length(), maxB, totalBytes)
        }
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun localIpv4(context: Context): String? {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val raw = wifi?.connectionInfo?.ipAddress ?: 0
        if (raw != 0) return listOf(raw and 255, raw shr 8 and 255, raw shr 16 and 255, raw shr 24 and 255).joinToString(".")
        return Collections.list(NetworkInterface.getNetworkInterfaces()).flatMap { Collections.list(it.inetAddresses) }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && it.isSiteLocalAddress }?.hostAddress
    }
}
