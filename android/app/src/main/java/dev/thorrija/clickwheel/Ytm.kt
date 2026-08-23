package dev.thorrija.clickwheel

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * YouTube Music search + stream resolution.
 *
 * Search goes through NewPipeExtractor (works reliably). Stream resolution
 * talks directly to the innertube player endpoint with an ANDROID_VR client,
 * because web/TV clients are currently answered with UNPLAYABLE
 * "The page needs to be reloaded." anti-bot walls.
 */
object Ytm {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    private val http = okhttp3.OkHttpClient.Builder()
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .build(),
            )
        }
        .build()

    private class OkHttpDownloader : Downloader() {
        override fun execute(request: Request): Response {
            val builder = okhttp3.Request.Builder().url(request.url())
            request.headers().forEach { entry ->
                builder.header(entry.key, entry.value.firstOrNull() ?: "")
            }
            if (request.httpMethod() == "POST") {
                val payload = request.dataToSend() ?: ByteArray(0)
                builder.post(okhttp3.RequestBody.create(null, payload))
            }
            http.newCall(builder.build()).execute().use { resp ->
                val bodyText = resp.body?.string() ?: ""
                val headers: MutableMap<String, MutableList<String>> =
                    resp.headers.toMultimap().mapValues { e -> e.value.toMutableList() }
                        .toMutableMap()
                return Response(resp.code, resp.message, headers, bodyText, resp.request.url.toString())
            }
        }
    }

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        NewPipe.init(OkHttpDownloader())
        initialized = true
    }

    suspend fun searchSongs(query: String): List<Song> = withContext(Dispatchers.IO) {
        val service = ServiceList.YouTube
        val handler = service.searchQHFactory.fromQuery(query)
        val extractor = service.getSearchExtractor(handler).apply { fetchPage() }
        extractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .map { item ->
                Song(
                    id = "ytm-${item.url.hashCode()}",
                    title = item.name ?: "",
                    artist = item.uploaderName ?: "",
                    durationMs = item.duration * 1000L,
                    source = Source.YTM,
                    uri = item.url, // watch URL; resolved to audio on play
                )
            }
    }

    /**
     * Resolve a direct audio URL for a YTM track via the innertube player API.
     *
     * YouTube rotates anti-bot flags across internal clients ("Sign in to
     * confirm you're not a bot"), so we try a fallback chain: ANDROID_VR
     * first, then IOS. Each is verified live before shipping; the chain keeps
     * working while at least one client passes.
     */
    suspend fun resolveAudio(song: Song): Pair<String, Map<String, String>> =
        withContext(Dispatchers.IO) {
            val videoId = VIDEO_ID_RE.find(song.uri ?: "")?.groupValues?.get(1)
                ?: throw IllegalArgumentException("pista sin identificador de vídeo")

            var lastError: Exception? = null
            for (client in CLIENT_CHAIN) {
                try {
                    val url = innertubeAudioUrl(videoId, client)
                    return@withContext Pair(url, emptyMap())
                } catch (e: Exception) {
                    Log.w(TAG, "cliente ${client.name} falló: ${e.message}")
                    lastError = e
                }
            }
            throw lastError ?: IllegalStateException("sin clientes disponibles")
        }

    /** Single innertube /player call; returns the best direct audio URL. */
    private fun innertubeAudioUrl(videoId: String, client: InnertubeClient): String {
        val payload = JSONObject().apply {
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            put(
                "context",
                JSONObject().put(
                    "client",
                    JSONObject()
                        .put("clientName", client.name)
                        .put("clientVersion", client.version),
                ),
            )
        }.toString()

        val request = okhttp3.Request.Builder()
            .url(INNERTUBE_PLAYER_URL)
            .header("User-Agent", client.userAgent)
            .post(
                okhttp3.RequestBody.create(
                    "application/json; charset=utf-8".toMediaTypeOrNull(),
                    payload.toByteArray(),
                ),
            )
            .build()

        http.newCall(request).execute().use { resp ->
            val rawBody = resp.body?.string() ?: "{}"
            if (!resp.isSuccessful) {
                throw IllegalStateException("YouTube respondió ${resp.code}: ${rawBody.take(160)}")
            }
            val json = JSONObject(rawBody)
            val playStatus = json.optJSONObject("playabilityStatus")
            val status = playStatus?.optString("status") ?: "UNKNOWN"
            if (status != "OK") {
                val reason = playStatus?.optString("reason") ?: ""
                throw IllegalStateException(if (reason.isBlank()) status else reason)
            }

            val formats = json.optJSONObject("streamingData")
                ?.optJSONArray("adaptiveFormats")
                ?: throw IllegalStateException("respuesta sin formatos")

            var bestUrl: String? = null
            var bestBitrate = -1
            for (i in 0 until formats.length()) {
                val f = formats.getJSONObject(i)
                if (!f.optString("mimeType").startsWith("audio")) continue
                val url = f.optString("url")
                if (url.isEmpty()) continue
                val bitrate = f.optInt("bitrate", 0)
                if (bitrate > bestBitrate) {
                    bestBitrate = bitrate
                    bestUrl = url
                }
            }
            return bestUrl ?: throw IllegalStateException("sin audio reproducible")
        }
    }

    private class InnertubeClient(val name: String, val version: String, val userAgent: String)

    private val CLIENT_CHAIN = listOf(
        InnertubeClient(
            "ANDROID_VR", "1.61.48",
            "com.google.android.apps.youtube.vr.oculus/1.61.48 " +
                "(Linux; U; Android 12; eureka-user Build/SQ3A.220605.009.A1) gzip",
        ),
        InnertubeClient(
            "IOS", "20.10.4",
            "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)",
        ),
    )

    private const val TAG = "Clickwheel"
    private const val INNERTUBE_PLAYER_URL = "https://www.youtube.com/youtubei/v1/player"
    private val VIDEO_ID_RE = Regex("[?&]v=([A-Za-z0-9_-]{6,})")
}
