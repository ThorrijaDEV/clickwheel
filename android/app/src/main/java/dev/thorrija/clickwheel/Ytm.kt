package dev.thorrija.clickwheel

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
     * Resolve a direct audio URL for a YTM track via the innertube player API
     * using the ANDROID_VR client (still returns unencrypted adaptive URLs).
     */
    suspend fun resolveAudio(song: Song): Pair<String, Map<String, String>> =
        withContext(Dispatchers.IO) {
            val videoId = VIDEO_ID_RE.find(song.uri ?: "")?.groupValues?.get(1)
                ?: throw IllegalArgumentException("pista sin identificador de vídeo")

            val payload = JSONObject().apply {
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
                put(
                    "context",
                    JSONObject().put(
                        "client",
                        JSONObject()
                            .put("clientName", "ANDROID_VR")
                            .put("clientVersion", "1.61.48")
                            .put("deviceMake", "Oculus")
                            .put("deviceModel", "Quest 3")
                            .put("osName", "Android")
                            .put("osVersion", "12")
                            .put("androidSdkVersion", 32),
                    ),
                )
            }.toString()

            val request = okhttp3.Request.Builder()
                .url(INNERTUBE_PLAYER_URL)
                .header("User-Agent", VR_USER_AGENT)
                .post(okhttp3.RequestBody.create(null, payload.toByteArray()))
                .build()

            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("YouTube respondió ${resp.code}")
                val json = JSONObject(resp.body?.string() ?: "{}")
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
                Pair(
                    bestUrl ?: throw IllegalStateException("sin audio reproducible"),
                    emptyMap(),
                )
            }
        }

    private const val INNERTUBE_PLAYER_URL = "https://www.youtube.com/youtubei/v1/player"
    private const val VR_USER_AGENT =
        "com.google.android.apps.youtube.vr.oculus/1.61.48 " +
            "(Linux; U; Android 12; eureka-user Build/SQ3A.220605.009.A1) gzip"
    private val VIDEO_ID_RE = Regex("[?&]v=([A-Za-z0-9_-]{6,})")
}
