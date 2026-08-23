package dev.thorrija.clickwheel

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Screen { MENU, LIBRARY, SEARCH, NOW_PLAYING }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ytm.init(this)
        setContent { ClickwheelApp() }
    }
}

@Composable
fun ClickwheelApp(viewModel: PlayerViewModel = viewModel()) {
    val context = LocalContext.current
    val playerState by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()

    var screen by remember { mutableStateOf(Screen.MENU) }
    var menuSelection by remember { mutableIntStateOf(0) }
    var listSelection by remember { mutableIntStateOf(0) }
    var librarySongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<Song>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    // Progress ticker while a song plays.
    LaunchedEffect(playerState.isPlaying, playerState.song?.id) {
        while (playerState.isPlaying) {
            delay(500)
            viewModel.tickProgress()
        }
    }

    // Load the device music library.
    val audioPermission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) librarySongs = MediaLibrary.scan(context)
    }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, audioPermission) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) librarySongs = MediaLibrary.scan(context) else permissionLauncher.launch(audioPermission)
    }

    val activeList: List<Any> = when (screen) {
        Screen.MENU -> listOf("Biblioteca", "Buscar en YouTube Music", "Ahora suena")
        Screen.LIBRARY -> librarySongs
        else -> emptyList()
    }

    fun move(delta: Int) {
        if (screen == Screen.NOW_PLAYING || screen == Screen.SEARCH) return
        val size = activeList.size
        if (size > 0) {
            listSelection = (listSelection + delta + size) % size
        }
    }

    fun select() {
        when (screen) {
            Screen.MENU -> when (listSelection % 3) {
                0 -> { screen = Screen.LIBRARY; listSelection = 0 }
                1 -> { screen = Screen.SEARCH; listSelection = 0 }
                else -> screen = Screen.NOW_PLAYING
            }
            Screen.LIBRARY -> {
                viewModel.playQueue(librarySongs, listSelection.coerceIn(0, (librarySongs.size - 1).coerceAtLeast(0)))
                screen = Screen.NOW_PLAYING
            }
            else -> viewModel.togglePlayPause()
        }
    }

    fun goMenu() {
        if (screen != Screen.NOW_PLAYING && screen != Screen.LIBRARY && screen != Screen.SEARCH) return
        if (screen == Screen.NOW_PLAYING) {
            screen = Screen.MENU
        } else {
            screen = Screen.MENU
        }
        listSelection = 0
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B0514), Color(0xFF1D1038))))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(320.dp),
        ) {
            when (screen) {
                Screen.MENU -> MenuScreen(
                    "iPod",
                    listOf(
                        MenuItem("Biblioteca") { screen = Screen.LIBRARY },
                        MenuItem("Buscar en YouTube Music") { screen = Screen.SEARCH },
                        MenuItem("Ahora suena") { screen = Screen.NOW_PLAYING },
                    ),
                    listSelection,
                )
                Screen.LIBRARY -> SongListScreen(
                    "Biblioteca",
                    librarySongs,
                    listSelection,
                    onSelect = { i ->
                        listSelection = i
                        viewModel.playQueue(librarySongs, i)
                        screen = Screen.NOW_PLAYING
                    },
                )
                Screen.SEARCH -> SearchScreen(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    results = searchResults,
                    loading = searching,
                    error = searchError,
                    onPlayResult = { song ->
                        scope.launch {
                            try {
                                searching = true
                                val (url, headers) = Ytm.resolveAudio(song)
                                viewModel.startResolved(song, url, headers)
                                screen = Screen.NOW_PLAYING
                            } catch (e: Exception) {
                                android.util.Log.e("Clickwheel", "resolveAudio falló", e)
                                searchError = "😵 no pude sacar el audio de esa pista — prueba otra"
                            } finally {
                                searching = false
                            }
                        }
                    },
                    onSubmit = {
                        if (searchQuery.isNotBlank()) {
                            searching = true
                            searchError = null
                            scope.launch {
                                try {
                                    searchResults = Ytm.searchSongs(searchQuery.trim())
                                    if (searchResults.isEmpty()) searchError = "Sin resultados"
                                } catch (e: Exception) {
                                    searchError = e.message ?: "fallo de red"
                                } finally {
                                    searching = false
                                }
                            }
                        }
                    },
                )
                Screen.NOW_PLAYING -> NowPlayingScreen(playerState)
            }
        }

        Text(
            "✧ clickwheel ✧",
            color = HotPink,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 4.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        ClickWheel(
            onSelect = ::select,
            onMenu = ::goMenu,
            onPrevious = {
                if (screen == Screen.NOW_PLAYING) viewModel.previous() else move(-1)
            },
            onNext = {
                if (screen == Screen.NOW_PLAYING) viewModel.next() else move(+1)
            },
            onPlayPause = {
                if (screen != Screen.NOW_PLAYING) screen = Screen.NOW_PLAYING
                viewModel.togglePlayPause()
            },
        )
    }
}
