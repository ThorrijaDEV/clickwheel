package dev.thorrija.clickwheel

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * YouTube Music search + stream resolution via NewPipeExtractor
 * (the same community-maintained extraction layer used by NewPipe).
 */
object Ytm {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    private class OkHttpDownloader : Downloader() {
        private val client = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .build()
                chain.proceed(req)
            }
            .build()

        override fun execute(request: Request): Response {
            val builder = okhttp3.Request.Builder().url(request.url())
            request.headers().forEach { entry ->
                builder.header(entry.key, entry.value.firstOrNull() ?: "")
            }
            if (request.httpMethod() == "POST") {
                val payload = request.dataToSend() ?: ByteArray(0)
                builder.post(okhttp3.RequestBody.create(null, payload))
            }
            val raw = client.newCall(builder.build()).execute()
            raw.use { resp ->
                val bodyText = resp.body?.string() ?: ""
                val code = resp.code
                val message = resp.message
                val headers: MutableMap<String, MutableList<String>> =
                    resp.headers.toMultimap().mapValues { e -> e.value.toMutableList() }
                        .toMutableMap()
                val latest = resp.request.url.toString()
                return Response(code, message, headers, bodyText, latest)
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
            .filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>()
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

    /** Resolve the best audio-only stream for a YTM track. */
    suspend fun resolveAudio(song: Song): Pair<String, Map<String, String>> =
        withContext(Dispatchers.IO) {
            val info: StreamInfo = StreamInfo.getInfo(ServiceList.YouTube, song.uri)
            val best: AudioStream? = info.audioStreams
                .maxByOrNull { stream -> stream.averageBitrate }
            requireNotNull(best) { "no audio streams found" }
            val headers = mapOf("User-Agent" to "Mozilla/5.0")
            Pair(best.content, headers)
        }
}
