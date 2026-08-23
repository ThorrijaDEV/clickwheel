package dev.thorrija.clickwheel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class Source { LOCAL, YTM }

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0,
    val source: Source,
    /** Local content Uri string, or remote stream URL resolved lazily for YTM. */
    val uri: String? = null,
)

data class PlayerState(
    val song: Song? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val loading: Boolean = false,
    val error: String? = null,
)

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val player: ExoPlayer = ExoPlayer.Builder(app).build()

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    private var queue: List<Song> = emptyList()
    private var queueIndex = -1

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updateProgress()
                if (playbackState == Player.STATE_ENDED) next()
            }
        })
        player.addListener(object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) = Unit
        })
    }

    fun playQueue(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty()) return
        queue = songs
        loadIndex(startIndex)
        player.play()
    }

    fun togglePlayPause() {
        when {
            player.isPlaying -> player.pause()
            else -> player.play()
        }
    }

    fun next() {
        if (queue.isNotEmpty()) {
            loadIndex((queueIndex + 1) % queue.size)
        }
    }

    fun previous() {
        if (_state.value.positionMs > 3000 || queueIndex <= 0) {
            player.seekTo(0)
        } else {
            loadIndex(queueIndex - 1)
        }
    }

    fun seekTo(fraction: Float) {
        val duration = player.duration
        if (duration > 0) player.seekTo((duration * fraction).toLong())
    }

    private fun loadIndex(index: Int) {
        queueIndex = index
        val song = queue[index]
        _state.value = PlayerState(song = song, loading = true)
        val item = MediaItem.Builder()
            .setUri(song.uri ?: throw IllegalArgumentException("unresolved song"))
            .build()
        // YTM streams are resolved before reaching here.
        player.setMediaItem(item)
        player.prepare()
    }

    /** Called by the YTM resolver once a direct audio URL is known. */
    fun startResolved(song: Song, url: String, headers: Map<String, String>) {
        queue = listOf(song)
        queueIndex = 0
        _state.value = PlayerState(song = song, loading = true)
        val item = MediaItem.Builder().setUri(url).build()
        player.setMediaItem(item)
        player.prepare()
        player.play()
    }

    fun tickProgress() {
        if (_state.value.song != null) updateProgress()
    }

    private fun updateProgress() {
        _state.value = _state.value.copy(
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = if (player.duration > 0) player.duration else _state.value.durationMs,
            loading = player.playbackState == Player.STATE_BUFFERING,
        )
    }

    override fun onCleared() {
        player.release()
    }
}
