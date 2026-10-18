package dev.rift.launcher.assistant.ai

import android.content.Context
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
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

    suspend fun find(context: Context): String? = withContext(Dispatchers.IO) {
        val local = localIpv4(context)
        val candidates = linkedSetOf("127.0.0.1")
        local?.let { ip ->
            val prefix = ip.substringBeforeLast('.')
            // Likely hosts first, then the subnet. This normally returns before the full scan completes.
            listOf(1, 2, 10, 20, 50, 100, 254).forEach { candidates += "$prefix.$it" }
            candidates += ip
            (1..254).forEach { candidates += "$prefix.$it" }
        }
        coroutineScope {
            candidates.chunked(32).forEach { batch ->
                val found = batch.map { host -> async { if (isOllama(host)) host else null } }.awaitAll().firstOrNull { it != null }
                if (found != null) return@coroutineScope "http://$found:11434/v1"
            }
            null
        }
    }

    private fun isOllama(host: String): Boolean = runCatching {
        probe.newCall(Request.Builder().url("http://$host:11434/api/tags").get().build()).execute().use { response ->
            response.isSuccessful && response.header("Content-Type").orEmpty().contains("application/json")
        }
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun localIpv4(context: Context): String? {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val raw = wifi?.connectionInfo?.ipAddress ?: 0
        if (raw != 0) return listOf(raw and 255, raw shr 8 and 255, raw shr 16 and 255, raw shr 24 and 255).joinToString(".")
        return Collections.list(NetworkInterface.getNetworkInterfaces()).flatMap { Collections.list(it.inetAddresses) }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && it.isSiteLocalAddress }?.hostAddress
    }
}
