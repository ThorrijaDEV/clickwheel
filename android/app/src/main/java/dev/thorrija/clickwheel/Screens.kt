package dev.thorrija.clickwheel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

val ScreenBg = Color(0xFFB8CBD6)      // iPod LCD blue-gray
val ScreenText = Color(0xFF1A2430)
val SelectionBar = Color(0xFF3D5AFE)

data class MenuItem(val label: String, val action: () -> Unit)

@Composable
fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<Song>,
    loading: Boolean,
    error: String?,
    onSubmit: () -> Unit,
    onPlayResult: (Song) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(ScreenBg).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text("Canción o artista…", fontSize = 14.sp) },
                modifier = Modifier.weight(1f),
            )
            Text(
                "IR",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { onSubmit() }
                    .background(SelectionBar)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
        error?.let {
            Text(it, color = Color(0xFFB00020), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
        Column(Modifier.fillMaxSize()) {
            results.forEachIndexed { i, song ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPlayResult(song) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        song.title,
                        color = ScreenText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${song.artist} · ${formatTime(song.durationMs)}",
                        color = ScreenText.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun MenuScreen(title: String, items: List<MenuItem>, selected: Int) {
    Column(Modifier.fillMaxSize().background(ScreenBg)) {
        Text(
            title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .background(SelectionBar)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
        items.forEachIndexed { i, item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (i == selected) Modifier.background(SelectionBar)
                        else Modifier,
                    )
                    .clickable { item.action() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    item.label,
                    color = if (i == selected) Color.White else ScreenText,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun SongListScreen(
    title: String,
    songs: List<Song>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    MenuScreen(
        title = title,
        items = songs.mapIndexed { i, song ->
            MenuItem("${song.title} — ${song.artist}") { onSelect(i) }
        },
        selected = selected,
    )
}

@Composable
fun NowPlayingScreen(state: PlayerState) {
    val song = state.song ?: return
    Column(
        Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("AHORA SUENA", color = SelectionBar, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(
                song.title,
                color = ScreenText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(song.artist, color = ScreenText.copy(alpha = 0.7f), fontSize = 15.sp)
        }
        Column {
            if (state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
            }
            state.error?.let {
                Text(it, color = Color(0xFFB00020), fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(state.positionMs), color = ScreenText, fontSize = 12.sp)
                Text("-" + formatTime(state.durationMs - state.positionMs), color = ScreenText, fontSize = 12.sp)
            }
            LinearProgressIndicator(
                progress = {
                    if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
                },
                modifier = Modifier.fillMaxWidth(),
                color = ScreenText,
                trackColor = ScreenBg.copy(alpha = 0.4f),
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(if (state.isPlaying) "▶" else "❙❙", color = ScreenText, fontSize = 18.sp)
            }
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
