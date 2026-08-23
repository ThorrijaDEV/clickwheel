package dev.thorrija.clickwheel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── hyperpop / y2k palette ──────────────────────────────────────────────────
val BgDeep = Color(0xFF140A26)
val BgDeeper = Color(0xFF0B0514)
val HotPink = Color(0xFFFF2EC4)
val CyberCyan = Color(0xFF00F0FF)
val AcidLime = Color(0xFFC6FF00)
val UltraViolet = Color(0xFF7B2EFF)
val WarnYellow = Color(0xFFFFE600)
val ChromeLight = Color(0xFFF6F7FF)
val ChromeDark = Color(0xFF9AA2BC)
val InkText = Color(0xFF17062E)

private val ChromeBar =
    Brush.verticalGradient(listOf(Color.White, ChromeLight, ChromeDark))
private val SelectionBrush =
    Brush.horizontalGradient(listOf(HotPink, UltraViolet))
private val ProgressBrush =
    Brush.horizontalGradient(listOf(HotPink, CyberCyan))
private val PanelBg = Color(0xFF1D1038)

data class MenuItem(val label: String, val action: () -> Unit)

/** Chrome/gloss title bar, the Y2K signature. */
@Composable
private fun TitleBar(title: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
            .background(ChromeBar),
    ) {
        // hairline gloss on top of the bar
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Color.White.copy(alpha = 0.9f)),
        )
        Text(
            "✦ ${title.uppercase()} ✦",
            color = InkText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 3.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

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
    Column(Modifier.fillMaxSize().background(BgDeep)) {
        Row(
            Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text("canción o artista…", fontSize = 14.sp, color = Color(0xFF8B84B0)) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = PanelBg,
                    unfocusedContainerColor = PanelBg,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = HotPink,
                    focusedIndicatorColor = HotPink,
                    unfocusedIndicatorColor = UltraViolet,
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "GO▸",
                color = InkText,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.horizontalGradient(listOf(AcidLime, CyberCyan)))
                    .clickable { onSubmit() }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            )
        }
        if (loading) LinearProgressIndicator(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            color = HotPink,
            trackColor = PanelBg,
        )
        error?.let {
            Text(
                "⚠ $it",
                color = WarnYellow,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Column(Modifier.fillMaxSize()) {
            results.forEachIndexed { i, song ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPlayResult(song) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "%02d".format(i + 1),
                        color = CyberCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.height(0.dp))
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(
                            song.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${song.artist} · ${formatTime(song.durationMs)}",
                            color = Color(0xFF9A90C8),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text("▶", color = HotPink, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun MenuScreen(title: String, items: List<MenuItem>, selected: Int) {
    Column(Modifier.fillMaxSize().background(BgDeep)) {
        TitleBar(title)
        items.forEachIndexed { i, item ->
            val isSel = i == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .then(if (isSel) Modifier.clip(RoundedCornerShape(8.dp)).background(SelectionBrush) else Modifier)
                    .clickable { item.action() }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
            ) {
                if (isSel) {
                    Text(
                        "★ ",
                        color = AcidLime,
                        fontSize = 16.sp,
                    )
                }
                Text(
                    item.label,
                    color = if (isSel) Color.White else Color(0xFFCFC7EE),
                    fontSize = 16.sp,
                    fontWeight = if (isSel) FontWeight.Black else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(ProgressBrush),
        )
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
            .background(BgDeep)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                "✧ AHORA SUENA ✧",
                color = CyberCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                song.title,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 28.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                song.artist,
                color = HotPink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Column {
            if (state.loading) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth(),
                    color = CyberCyan,
                    trackColor = PanelBg,
                )
                Spacer(Modifier.height(6.dp))
            }
            state.error?.let {
                Text(
                    "⚠ $it",
                    color = WarnYellow,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(6.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    formatTime(state.positionMs),
                    color = Color(0xFFCFC7EE),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    "-" + formatTime(state.durationMs - state.positionMs),
                    color = Color(0xFFCFC7EE),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(PanelBg)
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(99.dp)),
            ) {
                val frac =
                    if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
                Box(
                    Modifier
                        .fillMaxWidth(frac.coerceIn(0.01f, 1f))
                        .height(8.dp)
                        .background(ProgressBrush),
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    if (state.isPlaying) "▶" else "❙❙",
                    color = AcidLime,
                    fontSize = 20.sp,
                )
            }
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
