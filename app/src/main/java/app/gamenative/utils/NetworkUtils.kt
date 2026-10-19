package app.gamenative.utils

import app.gamenative.BuildConfig
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Dispatcher
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.dnsoverhttps.DnsOverHttps
import timber.log.Timber
import java.net.InetAddress
import java.util.concurrent.TimeUnit

object Net {

    private val gameNativeServiceHosts = setOf("api.gamenative.app", "relay.gamenative.app")

    /** Clean build: GameNative's own API serves official GameNative builds only, so never call it.
     *  Callers already treat a failed response as "no data". */
    val gameNativeApiBlock = Interceptor { chain ->
        val request = chain.request()
        if (BuildConfig.CLEAN_BUILD && request.url.host in gameNativeServiceHosts) {
            Timber.d("Clean build: skipped ${request.url.host}${request.url.encodedPath}")
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(503)
                .message("GameNative API disabled in this build")
                .body("".toResponseBody("text/plain".toMediaType()))
                .build()
        } else {
            chain.proceed(request)
        }
    }

    private val bootstrapClient = OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .build()

    private val doh: DnsOverHttps = DnsOverHttps.Builder()
        .client(bootstrapClient)
        .url("https://dns.google/dns-query".toHttpUrl())
        .bootstrapDnsHosts(
            InetAddress.getByName("8.8.8.8"),
            InetAddress.getByName("8.8.4.4"),
        )
        .build()

    val fallbackDns: Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return try {
                doh.lookup(hostname)
            } catch (e: Exception) {
                Timber.w(e, "DoH lookup failed for $hostname, falling back to system DNS")
                Dns.SYSTEM.lookup(hostname)
            }
        }
    }

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(fallbackDns)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.MINUTES)
            .pingInterval(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(gameNativeApiBlock)
            .build()
    }

    fun httpForParallelDownloads(parallelDownloads: Int): OkHttpClient {
        val hostConcurrency = parallelDownloads.coerceIn(4, 32)
        val dispatcher = Dispatcher().apply {
            maxRequestsPerHost = hostConcurrency
            maxRequests = maxOf(64, hostConcurrency * 2)
        }
        return http.newBuilder()
            .dispatcher(dispatcher)
            .readTimeout(5, TimeUnit.MINUTES)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .protocols(listOf(Protocol.HTTP_1_1))
            .build()
    }
}
