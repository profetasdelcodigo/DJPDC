package com.profetasdelcodigo.djpdc

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var permissionGranted by mutableStateOf(false)
    private var controllerReady by mutableStateOf(false)
    private val tracks = mutableStateListOf<Track>()
    private val favoriteTrackIds = mutableStateListOf<Long>()
    private var showFavoritesOnly by mutableStateOf(false)
    private var currentTrack by mutableStateOf<Track?>(null)
    private var isPlaying by mutableStateOf(false)
    private var searchQuery by mutableStateOf("")
    private var shuffleEnabled by mutableStateOf(false)
    private var repeatMode by mutableStateOf(Player.REPEAT_MODE_OFF)
    private var playbackPositionMs by mutableStateOf(0L)
    private var playbackDurationMs by mutableStateOf(0L)
    private var deckBPositionMs by mutableStateOf(0L)
    private var deckBDurationMs by mutableStateOf(0L)
    private var showQueue by mutableStateOf(false)
    private val queueTracks = mutableStateListOf<Track>()
    private val playlistNames = mutableStateListOf<String>()
    private var selectedPlaylistName by mutableStateOf<String?>(null)
    private var playlistNameInput by mutableStateOf("")
    private val playlistRepository by lazy { PlaylistRepository(this) }
    private var libraryMessage by mutableStateOf("Your local tracks will appear here.")
    private var controller: MediaController? = null
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var deckBController: MediaController? = null
    private lateinit var deckBControllerFuture: ListenableFuture<MediaController>
    private var deckBTrack by mutableStateOf<Track?>(null)
    private var deckBIsPlaying by mutableStateOf(false)
    private var crossfadePosition by mutableStateOf(0.0f)
    private var deckAVolume by mutableStateOf(1.0f)
    private var deckBVolume by mutableStateOf(1.0f)

    private val deckBListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            deckBIsPlaying = playing
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val uri = mediaItem?.localConfiguration?.uri ?: return
            deckBTrack = tracks.firstOrNull { it.contentUri == uri } ?: deckBTrack
        }
    }

    private val audioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        if (granted) loadLibrary()
        else libraryMessage = "Music access is needed to scan your audio library."
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val uri = mediaItem?.localConfiguration?.uri ?: return
            currentTrack = tracks.firstOrNull { it.contentUri == uri }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            shuffleEnabled = shuffleModeEnabled
        }

        override fun onRepeatModeChanged(repeatModeValue: Int) {
            repeatMode = repeatModeValue
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connectPlaybackService()
        connectDeckBService()
        playlistNames.addAll(playlistRepository.names())
        favoriteTrackIds.addAll(getSharedPreferences("djpdc_library", Context.MODE_PRIVATE)
            .getStringSet("favorite_track_ids", emptySet())
            .orEmpty()
            .mapNotNull { it.toLongOrNull() })
        permissionGranted = hasAudioPermission()
        if (permissionGranted) loadLibrary()

        setContent {
            LaunchedEffect(controllerReady) {
                while (true) {
                    val player = controller
                    if (player != null) {
                        playbackPositionMs = player.currentPosition.coerceAtLeast(0L)
                        playbackDurationMs = player.duration.takeIf { it > 0L } ?: 0L
                    }
                    val deckBPlayer = deckBController
                    if (deckBPlayer != null) {
                        deckBPositionMs = deckBPlayer.currentPosition.coerceAtLeast(0L)
                        deckBDurationMs = deckBPlayer.duration.takeIf { it > 0L } ?: 0L
                    }
                    delay(500)
                }
            }
            val selectedPlaylistIds = selectedPlaylistName?.let { playlistRepository.trackIds(it) } ?: emptySet()
            val filteredTracks = tracks.filter { track ->
                val query = searchQuery.trim()
                (!showFavoritesOnly || track.id in favoriteTrackIds) &&
                    (selectedPlaylistName == null || track.id in selectedPlaylistIds) &&
                    (query.isEmpty() || track.title.contains(query, ignoreCase = true) ||
                        track.artist.contains(query, ignoreCase = true) ||
                        track.album.contains(query, ignoreCase = true))
            }
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFF76B900),
                background = Color(0xFF0B0D0B),
                surface = Color(0xFF151815)
            )) {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF0B0D0B)) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("DJPDC", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                            Spacer(Modifier.weight(1f))
                            Text("BETA 0.8", color = Color(0xFF76B900))
                        }
                        Text("YOUR MUSIC. YOUR MIX.", color = Color(0xFF9AA39A), style = MaterialTheme.typography.labelLarge)

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF151815),
                            shape = RoundedCornerShape(22.dp)
                        ) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("NOW PLAYING", color = Color(0xFF76B900), style = MaterialTheme.typography.labelLarge)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    if (currentTrack?.albumArtUri != null) {
                                        AsyncImage(
                                            model = currentTrack?.albumArtUri,
                                            contentDescription = "Album artwork",
                                            modifier = Modifier.size(104.dp),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Surface(
                                            modifier = Modifier.size(104.dp),
                                            color = Color(0xFF242B22),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.fillMaxSize(),
                                                verticalArrangement = Arrangement.Center,
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text("DJPDC", color = Color(0xFF76B900), style = MaterialTheme.typography.titleMedium)
                                                Text("♪", color = Color.White, style = MaterialTheme.typography.headlineLarge)
                                            }
                                        }
                                    }
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            currentTrack?.title ?: "Choose a track",
                                            color = Color.White,
                                            style = MaterialTheme.typography.titleLarge,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            currentTrack?.artist ?: if (controllerReady) "DJPDC playback engine ready" else "Connecting playback engine…",
                                            color = Color(0xFF9AA39A),
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            currentTrack?.album ?: "LOCAL MUSIC PLAYER",
                                            color = Color(0xFF76B900),
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Slider(
                                    value = if (playbackDurationMs > 0L) (playbackPositionMs.toFloat() / playbackDurationMs).coerceIn(0f, 1f) else 0f,
                                    onValueChange = { fraction ->
                                        if (playbackDurationMs > 0L) controller?.seekTo((fraction * playbackDurationMs).toLong())
                                    },
                                    enabled = controllerReady && playbackDurationMs > 0L
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(formatTime(playbackPositionMs), color = Color(0xFF9AA39A), style = MaterialTheme.typography.labelSmall)
                                    Text(formatTime(playbackDurationMs), color = Color(0xFF9AA39A), style = MaterialTheme.typography.labelSmall)
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                val activePlayer = controller ?: return@Button
                                                if (isPlaying) activePlayer.pause()
                                                else if (currentTrack != null) activePlayer.play()
                                            },
                                            enabled = controllerReady && currentTrack != null
                                        ) { Text(if (isPlaying) "Pause" else "Play") }
                                        Button(
                                            onClick = { controller?.seekToNextMediaItem() },
                                            enabled = controllerReady && (controller?.mediaItemCount ?: 0) > 1
                                        ) { Text("Next") }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                val activePlayer = controller ?: return@Button
                                                activePlayer.shuffleModeEnabled = !activePlayer.shuffleModeEnabled
                                            },
                                            enabled = controllerReady
                                        ) { Text(if (shuffleEnabled) "Shuffle ON" else "Shuffle") }
                                        Button(
                                            onClick = {
                                                val activePlayer = controller ?: return@Button
                                                activePlayer.repeatMode = when (activePlayer.repeatMode) {
                                                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                                    else -> Player.REPEAT_MODE_OFF
                                                }
                                            },
                                            enabled = controllerReady
                                        ) {
                                            Text(when (repeatMode) {
                                                Player.REPEAT_MODE_ALL -> "Repeat ALL"
                                                Player.REPEAT_MODE_ONE -> "Repeat ONE"
                                                else -> "Repeat OFF"
                                            })
                                        }
                                    }
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF151815),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("DECK B", color = Color(0xFF76B900), style = MaterialTheme.typography.labelLarge)
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        deckBTrack?.title ?: "No track loaded",
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Button(
                                        onClick = {
                                            val deck = deckBController ?: return@Button
                                            if (deckBIsPlaying) deck.pause() else deck.play()
                                        },
                                        enabled = deckBController != null && deckBTrack != null
                                    ) { Text(if (deckBIsPlaying) "Pause B" else "Play B") }
                                    Button(
                                        onClick = {
                                            deckBController?.pause()
                                            deckBController?.seekTo(0L)
                                        },
                                        enabled = deckBController != null && deckBTrack != null
                                    ) { Text("Return B") }
                                }
                                Slider(
                                    value = if (deckBDurationMs > 0L) (deckBPositionMs.toFloat() / deckBDurationMs).coerceIn(0f, 1f) else 0f,
                                    onValueChange = { fraction ->
                                        if (deckBDurationMs > 0L) deckBController?.seekTo((fraction * deckBDurationMs).toLong())
                                    },
                                    enabled = deckBController != null && deckBDurationMs > 0L
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(formatTime(deckBPositionMs), color = Color(0xFF9AA39A), style = MaterialTheme.typography.labelSmall)
                                    Text(formatTime(deckBDurationMs), color = Color(0xFF9AA39A), style = MaterialTheme.typography.labelSmall)
                                }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("DECK B VOLUME", color = Color(0xFF9AA39A), style = MaterialTheme.typography.labelSmall)
                                    Spacer(Modifier.weight(1f))
                                    Text("${(deckBVolume * 100).toInt()}%", color = Color.White, style = MaterialTheme.typography.labelSmall)
                                }
                                Slider(
                                    value = deckBVolume,
                                    onValueChange = { deckBVolume = it; applyCrossfade() },
                                    valueRange = 0f..1f,
                                    enabled = deckBController != null
                                )
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("DECK A VOLUME", color = Color(0xFF9AA39A), style = MaterialTheme.typography.labelSmall)
                                    Spacer(Modifier.weight(1f))
                                    Text("${(deckAVolume * 100).toInt()}%", color = Color.White, style = MaterialTheme.typography.labelSmall)
                                }
                                Slider(
                                    value = deckAVolume,
                                    onValueChange = { deckAVolume = it; applyCrossfade() },
                                    valueRange = 0f..1f,
                                    enabled = controllerReady
                                )
                                Text("CROSSFADER  ·  A  ↔  B", color = Color(0xFF9AA39A), style = MaterialTheme.typography.labelSmall)
                                Slider(
                                    value = crossfadePosition,
                                    onValueChange = { position ->
                                        crossfadePosition = position
                                        applyCrossfade()
                                    },
                                    valueRange = 0f..1f,
                                    enabled = controllerReady && deckBController != null
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("A", color = Color(0xFF9AA39A))
                                    Text("CENTER", color = Color(0xFF9AA39A))
                                    Text("B", color = Color(0xFF9AA39A))
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("YOUR LIBRARY", color = Color(0xFF76B900), style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.weight(1f))
                            Text("${tracks.size} tracks", color = Color(0xFF9AA39A), style = MaterialTheme.typography.labelSmall)
                            Button(onClick = { showQueue = !showQueue }, enabled = controllerReady) { Text(if (showQueue) "Hide queue" else "Queue") }
                            Button(onClick = { loadLibrary() }, enabled = permissionGranted) { Text("Scan") }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { showFavoritesOnly = false; selectedPlaylistName = null }) {
                                Text(if (!showFavoritesOnly && selectedPlaylistName == null) "All tracks ✓" else "All tracks")
                            }
                            Button(onClick = { showFavoritesOnly = true; selectedPlaylistName = null }) {
                                Text(if (showFavoritesOnly) "Favorites ✓" else "Favorites")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = playlistNameInput,
                                onValueChange = { playlistNameInput = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                placeholder = { Text("New playlist name") }
                            )
                            Button(
                                onClick = {
                                    val name = playlistNameInput.trim()
                                    if (name.isNotEmpty() && filteredTracks.isNotEmpty()) {
                                        playlistRepository.save(name, filteredTracks.map { it.id })
                                        playlistNames.clear()
                                        playlistNames.addAll(playlistRepository.names())
                                        selectedPlaylistName = name
                                        showFavoritesOnly = false
                                        playlistNameInput = ""
                                    }
                                },
                                enabled = playlistNameInput.isNotBlank() && filteredTracks.isNotEmpty()
                            ) { Text("Save") }
                        }
                        if (playlistNames.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(playlistNames) { name ->
                                    Button(onClick = {
                                        selectedPlaylistName = if (selectedPlaylistName == name) null else name
                                        showFavoritesOnly = false
                                    }) {
                                        Text(if (selectedPlaylistName == name) "$name ✓" else name)
                                    }
                                }
                            }
                        }

                        if (showQueue) {
                            Text("UP NEXT  ·  ${queueTracks.size} tracks", color = Color(0xFF76B900), style = MaterialTheme.typography.labelLarge)
                            val nextTracks = queueTracks.drop((controller?.currentMediaItemIndex ?: 0) + 1).take(5)
                            if (nextTracks.isEmpty()) {
                                Text("No upcoming tracks in the queue.", color = Color(0xFF9AA39A))
                            } else {
                                nextTracks.forEach { queuedTrack ->
                                    Text(
                                        "•  ${queuedTrack.title} — ${queuedTrack.artist}",
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = permissionGranted,
                            placeholder = { Text("Search songs, artists or albums") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF76B900),
                                cursorColor = Color(0xFF76B900),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        if (!permissionGranted) {
                            Text("Allow DJPDC to read audio files stored on this device.", color = Color(0xFFADB5AD))
                            Button(onClick = { requestAudioPermission() }) { Text("Grant music access") }
                        } else if (tracks.isEmpty()) {
                            Text(libraryMessage, color = Color(0xFFADB5AD))
                            Button(onClick = { loadLibrary() }) { Text("Rescan library") }
                        } else if (filteredTracks.isEmpty()) {
                            Text(if (showFavoritesOnly) "No favorites yet. Tap ☆ on a track to save it." else "No tracks match your search.", color = Color(0xFFADB5AD))
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredTracks, key = { it.id }) { track ->
                                    TrackRow(
                                        track = track,
                                        enabled = controllerReady,
                                        isFavorite = track.id in favoriteTrackIds,
                                        onFavoriteClick = { toggleFavorite(track) },
                                        onClick = { playTrack(track) },
                                        onLoadDeckB = { loadDeckB(track) }
                                    )
                                }
                            }
                        }

                        Text("BACKGROUND AUDIO  ·  AUTO DJ  ·  MANUAL DECKS", color = Color(0xFF6F786F), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    private fun connectDeckBService() {
        val token = SessionToken(this, ComponentName(this, DeckBPlaybackService::class.java))
        deckBControllerFuture = MediaController.Builder(this, token).buildAsync()
        deckBControllerFuture.addListener({
            try {
                deckBController = deckBControllerFuture.get().also { mediaController ->
                    mediaController.addListener(deckBListener)
                    applyCrossfade()
                }
            } catch (_: Exception) {
                libraryMessage = "Deck B could not start. Deck A remains available."
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun applyCrossfade() {
        val gains = MixerMath.equalPowerCrossfade(crossfadePosition)
        controller?.volume = MixerMath.applyChannelVolume(gains.deckA, deckAVolume)
        deckBController?.volume = MixerMath.applyChannelVolume(gains.deckB, deckBVolume)
    }

    private fun loadDeckB(track: Track) {
        val deck = deckBController ?: return
        deckBTrack = track
        deck.setMediaItem(MediaItem.fromUri(track.contentUri))
        deck.prepare()
        applyCrossfade()
    }

    private fun connectPlaybackService() {
        val token = SessionToken(this, ComponentName(this, DJPlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture.addListener({
            try {
                controller = controllerFuture.get().also { mediaController ->
                    mediaController.addListener(playerListener)
                    controllerReady = true
                    applyCrossfade()
                }
            } catch (_: Exception) {
                libraryMessage = "Playback service could not start. Restart DJPDC and try again."
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun loadLibrary() {
        if (!hasAudioPermission()) return
        try {
            val found = TrackRepository(this).loadTracks()
            tracks.clear()
            tracks.addAll(found)
            libraryMessage = if (found.isEmpty()) {
                "No music files were found. Add audio files to your device and rescan."
            } else {
                "Library scanned successfully."
            }
        } catch (_: SecurityException) {
            libraryMessage = "DJPDC could not access the audio library. Check app permissions."
        }
    }

    private fun toggleFavorite(track: Track) {
        if (track.id in favoriteTrackIds) favoriteTrackIds.remove(track.id)
        else favoriteTrackIds.add(track.id)
        getSharedPreferences("djpdc_library", Context.MODE_PRIVATE).edit()
            .putStringSet("favorite_track_ids", favoriteTrackIds.map { it.toString() }.toSet())
            .apply()
    }

    private fun playTrack(track: Track) {
        val activePlayer = controller ?: return
        val activeQueue = selectedPlaylistName?.let { name ->
            val ids = playlistRepository.trackIds(name)
            tracks.filter { it.id in ids }
        } ?: tracks.toList()
        val index = activeQueue.indexOfFirst { it.id == track.id }
        if (index < 0) return
        currentTrack = track
        queueTracks.clear()
        queueTracks.addAll(activeQueue)
        activePlayer.setMediaItems(activeQueue.map { MediaItem.fromUri(it.contentUri) }, index, 0L)
        activePlayer.prepare()
        activePlayer.play()
    }

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAudioPermission() {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        audioPermission.launch(permission)
    }

    override fun onDestroy() {
        controller?.removeListener(playerListener)
        deckBController?.removeListener(deckBListener)
        MediaController.releaseFuture(controllerFuture)
        MediaController.releaseFuture(deckBControllerFuture)
        controller = null
        deckBController = null
        super.onDestroy()
    }
}

private fun formatTime(milliseconds: Long): String {
    if (milliseconds <= 0L) return "0:00"
    val totalSeconds = milliseconds / 1000
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}

@androidx.compose.runtime.Composable
private fun TrackRow(
    track: Track,
    enabled: Boolean,
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit,
    onClick: () -> Unit,
    onLoadDeckB: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF151815),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f).clickable(enabled = enabled, onClick = onClick)
                    .padding(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(track.title, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${track.artist}  ·  ${track.album}",
                    color = Color(0xFF9AA39A),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(onClick = onLoadDeckB, enabled = enabled) { Text("Load B") }
            Button(onClick = onFavoriteClick) { Text(if (isFavorite) "★" else "☆") }
        }
    }
}
