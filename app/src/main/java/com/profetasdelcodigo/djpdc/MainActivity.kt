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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.media3.common.PlaybackParameters
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope

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
    private var showAdvancedControls by mutableStateOf(false)
    private val queueTracks = mutableStateListOf<Track>()
    private val playlistNames = mutableStateListOf<String>()
    private var selectedPlaylistName by mutableStateOf<String?>(null)
    private var playlistNameInput by mutableStateOf("")
    private val playlistRepository by lazy { PlaylistRepository(this) }
    private var libraryMessage by mutableStateOf("Your local tracks will appear here.")
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var deckBController: MediaController? = null
    private var deckBControllerFuture: ListenableFuture<MediaController>? = null
    private var deckBTrack by mutableStateOf<Track?>(null)
    private var deckBIsPlaying by mutableStateOf(false)
    private var crossfadePosition by mutableStateOf(0.0f)
    private var deckAVolume by mutableStateOf(1.0f)
    private var deckBVolume by mutableStateOf(1.0f)
    private val tapTimestampsMs = mutableStateListOf<Long>()
    private val deckATapTimestampsMs = mutableStateListOf<Long>()
    private var tapTempoBpm by mutableStateOf<Float?>(null)
    private var deckATapTempoBpm by mutableStateOf<Float?>(null)
    private var autoBpmA by mutableStateOf<Float?>(null)
    private var autoBpmB by mutableStateOf<Float?>(null)
    private var bpmAnalysisA by mutableStateOf(false)
    private var bpmAnalysisB by mutableStateOf(false)
    private var bpmMessageA by mutableStateOf("")
    private var bpmMessageB by mutableStateOf("")
    private var beatmatchSpeed by mutableStateOf<Float?>(null)
    private var deckACueMs by mutableStateOf<Long?>(null)
    private var deckBCueMs by mutableStateOf<Long?>(null)
    private var autoDjMode by mutableStateOf(AutoDJMode.OFF)
    private var autoDjMessage by mutableStateOf("Auto DJ is off. Start it when a local track is playing.")
    private var autoDjSessionState = AutoDJSessionState()
    private var autoDjJob: Job? = null
    private var bpmLibraryWarmupJob: Job? = null
    private val measuredBpmByTrackId = mutableMapOf<Long, Float>()

    private val deckBListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            deckBIsPlaying = playing
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val uri = mediaItem?.localConfiguration?.uri ?: return
            deckBTrack = tracks.firstOrNull { it.contentUri == uri } ?: deckBTrack
            deckBCueMs = null
            tapTimestampsMs.clear()
            tapTempoBpm = null
            autoBpmB = null
            bpmMessageB = ""
            beatmatchSpeed = null
            deckBController?.setPlaybackParameters(PlaybackParameters(1f, 1f))
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
            deckACueMs = null
            deckATapTimestampsMs.clear()
            deckATapTempoBpm = null
            autoBpmA = null
            bpmMessageA = ""
            beatmatchSpeed = null
            deckBController?.setPlaybackParameters(PlaybackParameters(1f, 1f))
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
                primary = Color(0xFF9B8CFF),
                onPrimary = Color(0xFF100D20),
                secondary = Color(0xFF62D9E8),
                onSecondary = Color(0xFF06171A),
                background = Color(0xFF090A12),
                onBackground = Color(0xFFF4F3FA),
                surface = Color(0xFF141522),
                onSurface = Color(0xFFF4F3FA),
                surfaceVariant = Color(0xFF202136),
                onSurfaceVariant = Color(0xFFA7A9BC)
            )) {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF090A12)) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("DJPDC", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                            Spacer(Modifier.weight(1f))
                            Text("SMART MIX · BETA", color = Color(0xFF62D9E8), style = MaterialTheme.typography.labelMedium)
                        }
                        Text("TU MÚSICA · TU MEZCLA · CONTROL INTELIGENTE", color = Color(0xFFA7A9BC), style = MaterialTheme.typography.labelMedium)

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF141522),
                            shape = RoundedCornerShape(22.dp),
                            border = BorderStroke(1.dp, Color(0xFF2B2D45))
                        ) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("REPRODUCIENDO AHORA", color = Color(0xFF62D9E8), style = MaterialTheme.typography.labelLarge)
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
                                            color = Color(0xFF24243A),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.fillMaxSize(),
                                                verticalArrangement = Arrangement.Center,
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text("DJPDC", color = Color(0xFF9B8CFF), style = MaterialTheme.typography.titleMedium)
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
                                            color = Color(0xFF9B8CFF),
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
                                                if (isPlaying) {
                                                    stopAutoDJ("Auto DJ stopped because playback was paused manually.")
                                                    activePlayer.pause()
                                                } else if (currentTrack != null) activePlayer.play()
                                            },
                                            enabled = controllerReady && currentTrack != null
                                        ) { Text(if (isPlaying) "Pause" else "Play") }
                                        Button(
                                            onClick = { skipToNextTrack() },
                                            enabled = controllerReady && (
                                                (controller?.mediaItemCount ?: 0) > 1 ||
                                                    (autoDjMode == AutoDJMode.ACTIVE && tracks.size > 1)
                                                )
                                        ) { Text("Next") }
                                        Button(
                                            onClick = { stopPlayback() },
                                            enabled = controllerReady && currentTrack != null
                                        ) { Text("Stop") }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Button(
                                            onClick = { if (autoDjMode == AutoDJMode.ACTIVE) stopAutoDJ("Auto DJ stopped by you.") else startAutoDJ() },
                                            enabled = controllerReady && deckBController != null && currentTrack != null && tracks.isNotEmpty()
                                        ) { Text(if (autoDjMode == AutoDJMode.ACTIVE) "Stop Auto DJ" else "Start Auto DJ") }
                                        Text(autoDjMessage, color = Color(0xFF9AA39A), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { showAdvancedControls = !showAdvancedControls }) {
                                            Text(if (showAdvancedControls) "Hide advanced controls" else "Advanced controls")
                                        }
                                    }
                                    if (showAdvancedControls) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("DECK A TAP TEMPO", color = Color(0xFF9AA39A), style = MaterialTheme.typography.labelSmall)
                                            Text(deckATapTempoBpm?.let { "${it.toInt()} BPM" } ?: "Tap 3+ times", color = Color.White)
                                        }
                                        Button(
                                            onClick = {
                                                val now = android.os.SystemClock.elapsedRealtime()
                                                if (deckATapTimestampsMs.lastOrNull()?.let { now - it > 2000L } == true) deckATapTimestampsMs.clear()
                                                deckATapTimestampsMs.add(now)
                                                while (deckATapTimestampsMs.size > 7) deckATapTimestampsMs.removeAt(0)
                                                deckATapTempoBpm = TapTempo.bpmFromTimestamps(deckATapTimestampsMs)
                                            },
                                            enabled = currentTrack != null
                                        ) { Text("TAP A") }
                                        Button(
                                            onClick = { deckATapTimestampsMs.clear(); deckATapTempoBpm = null },
                                            enabled = deckATapTimestampsMs.isNotEmpty()
                                        ) { Text("Reset") }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(autoBpmA?.let { "AUTO: ${it.toInt()} BPM" } ?: bpmMessageA.ifBlank { "Automatic BPM: not analyzed" }, color = Color(0xFF9AA39A), modifier = Modifier.weight(1f))
                                        Button(onClick = { currentTrack?.let { analyzeBpm(it, true) } }, enabled = currentTrack != null && !bpmAnalysisA) {
                                            Text(if (bpmAnalysisA) "Analyzing…" else "AUTO BPM A")
                                        }
                                    }
                                    val beatA = BeatPulseMath.at(playbackPositionMs, deckATapTempoBpm)
                                    Text(
                                        beatA?.let { "PULSE A · ${it.beatInBar}/4 · ${(it.phase * 100).toInt()}%" }
                                            ?: "PULSE A · awaiting BPM",
                                        color = Color(0xFF9B8CFF),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { deckACueMs = controller?.currentPosition?.coerceAtLeast(0L) },
                                            enabled = controllerReady && currentTrack != null
                                        ) { Text("Set A CUE") }
                                        Button(
                                            onClick = { deckACueMs?.let { controller?.seekTo(it) } },
                                            enabled = controllerReady && deckACueMs != null
                                        ) { Text("Jump A CUE") }
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
                        }

                        if (showAdvancedControls) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF141522),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("DECK B", color = Color(0xFF9B8CFF), style = MaterialTheme.typography.labelLarge)
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
                                        enabled = autoDjMode != AutoDJMode.ACTIVE && deckBController != null && deckBTrack != null
                                    ) { Text(if (deckBIsPlaying) "Pause B" else "Play B") }
                                    Button(
                                        onClick = {
                                            deckBController?.pause()
                                            deckBController?.seekTo(0L)
                                        },
                                        enabled = autoDjMode != AutoDJMode.ACTIVE && deckBController != null && deckBTrack != null
                                    ) { Text("Return B") }
                                }
                                Slider(
                                    value = if (deckBDurationMs > 0L) (deckBPositionMs.toFloat() / deckBDurationMs).coerceIn(0f, 1f) else 0f,
                                    onValueChange = { fraction ->
                                        if (deckBDurationMs > 0L) deckBController?.seekTo((fraction * deckBDurationMs).toLong())
                                    },
                                    enabled = autoDjMode != AutoDJMode.ACTIVE && deckBController != null && deckBDurationMs > 0L
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(formatTime(deckBPositionMs), color = Color(0xFF9AA39A), style = MaterialTheme.typography.labelSmall)
                                    Text(formatTime(deckBDurationMs), color = Color(0xFF9AA39A), style = MaterialTheme.typography.labelSmall)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { deckBCueMs = deckBController?.currentPosition?.coerceAtLeast(0L) },
                                        enabled = autoDjMode != AutoDJMode.ACTIVE && deckBController != null && deckBTrack != null
                                    ) { Text("Set B CUE") }
                                    Button(
                                        onClick = { deckBCueMs?.let { deckBController?.seekTo(it) } },
                                        enabled = autoDjMode != AutoDJMode.ACTIVE && deckBController != null && deckBCueMs != null
                                    ) { Text("Jump B CUE") }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("PHASE NUDGE B", color = Color(0xFF9AA39A), style = MaterialTheme.typography.labelSmall)
                                    Button(
                                        onClick = {
                                            val player = deckBController ?: return@Button
                                            val target = PhaseNudgeMath.adjust(player.currentPosition, -20L, player.duration)
                                            player.seekTo(target)
                                        },
                                        enabled = autoDjMode != AutoDJMode.ACTIVE && deckBController != null && deckBTrack != null
                                    ) { Text("−20 ms") }
                                    Button(
                                        onClick = {
                                            val player = deckBController ?: return@Button
                                            val target = PhaseNudgeMath.adjust(player.currentPosition, 20L, player.duration)
                                            player.seekTo(target)
                                        },
                                        enabled = autoDjMode != AutoDJMode.ACTIVE && deckBController != null && deckBTrack != null
                                    ) { Text("+20 ms") }
                                }
                                Text(
                                    "Manual seek adjustment only · listen and fine-tune by ear",
                                    color = Color(0xFF777A91),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("MANUAL TAP TEMPO", color = Color(0xFF9AA39A), style = MaterialTheme.typography.labelSmall)
                                        Text(
                                            tapTempoBpm?.let { "${it.toInt()} BPM" } ?: "Tap 3+ times to measure",
                                            color = Color.White,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            val now = android.os.SystemClock.elapsedRealtime()
                                            val previous = tapTimestampsMs.lastOrNull()
                                            if (previous != null && now - previous > 2000L) tapTimestampsMs.clear()
                                            tapTimestampsMs.add(now)
                                            while (tapTimestampsMs.size > 7) tapTimestampsMs.removeAt(0)
                                            tapTempoBpm = TapTempo.bpmFromTimestamps(tapTimestampsMs)
                                        },
                                        enabled = deckBTrack != null
                                    ) { Text("TAP") }
                                    Button(
                                        onClick = {
                                            tapTimestampsMs.clear()
                                            tapTempoBpm = null
                                        },
                                        enabled = tapTimestampsMs.isNotEmpty()
                                    ) { Text("Reset") }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(autoBpmB?.let { "AUTO: ${it.toInt()} BPM" } ?: bpmMessageB.ifBlank { "Automatic BPM: not analyzed" }, color = Color(0xFF9AA39A), modifier = Modifier.weight(1f))
                                    Button(onClick = { deckBTrack?.let { analyzeBpm(it, false) } }, enabled = deckBTrack != null && !bpmAnalysisB) {
                                        Text(if (bpmAnalysisB) "Analyzing…" else "AUTO BPM B")
                                    }
                                }
                                val beatB = BeatPulseMath.at(deckBPositionMs, tapTempoBpm)
                                Text(
                                    beatB?.let { "PULSE B · ${it.beatInBar}/4 · ${(it.phase * 100).toInt()}%" }
                                        ?: "PULSE B · awaiting BPM",
                                    color = Color(0xFF9B8CFF),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            val masterBpm = deckATapTempoBpm
                                            val sourceBpm = tapTempoBpm
                                            val speed = BeatmatchMath.matchingSpeed(masterBpm, sourceBpm)
                                            if (speed != null) {
                                                deckBController?.setPlaybackParameters(PlaybackParameters(speed, 1f))
                                                beatmatchSpeed = speed
                                            }
                                        },
                                        enabled = autoDjMode != AutoDJMode.ACTIVE && deckBController != null && deckATapTempoBpm != null && tapTempoBpm != null
                                    ) { Text("MATCH B TO A") }
                                    Button(
                                        onClick = {
                                            deckBController?.setPlaybackParameters(PlaybackParameters(1f, 1f))
                                            beatmatchSpeed = null
                                        },
                                        enabled = autoDjMode != AutoDJMode.ACTIVE && deckBController != null
                                    ) { Text("RESET SPEED") }
                                }
                                Text(
                                    beatmatchSpeed?.let { "B speed: ${"%.1f".format(it * 100f)}% · pitch preserved" }
                                        ?: "Beatmatch: measure both decks, then match B to A",
                                    color = Color(0xFF9AA39A),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("DECK B VOLUME", color = Color(0xFF9AA39A), style = MaterialTheme.typography.labelSmall)
                                    Spacer(Modifier.weight(1f))
                                    Text("${(deckBVolume * 100).toInt()}%", color = Color.White, style = MaterialTheme.typography.labelSmall)
                                }
                                Slider(
                                    value = deckBVolume,
                                    onValueChange = { deckBVolume = it; applyCrossfade() },
                                    valueRange = 0f..1f,
                                    enabled = autoDjMode != AutoDJMode.ACTIVE && deckBController != null
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
                                    enabled = controllerReady && autoDjMode != AutoDJMode.ACTIVE
                                )
                                Text("CROSSFADER  ·  A  ↔  B", color = Color(0xFF9AA39A), style = MaterialTheme.typography.labelSmall)
                                Slider(
                                    value = crossfadePosition,
                                    onValueChange = { position ->
                                        crossfadePosition = position
                                        applyCrossfade()
                                    },
                                    valueRange = 0f..1f,
                                    enabled = controllerReady && deckBController != null && autoDjMode != AutoDJMode.ACTIVE
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("A", color = Color(0xFF9AA39A))
                                    Text("CENTER", color = Color(0xFF9AA39A))
                                    Text("B", color = Color(0xFF9AA39A))
                                }
                            }
                        }

                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("BIBLIOTECA MUSICAL", color = Color(0xFF62D9E8), style = MaterialTheme.typography.labelLarge)
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
                            Text("UP NEXT  ·  ${queueTracks.size} tracks", color = Color(0xFF9B8CFF), style = MaterialTheme.typography.labelLarge)
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
                                focusedBorderColor = Color(0xFF9B8CFF),
                                cursorColor = Color(0xFF9B8CFF),
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
                                        onLoadDeckB = { stopAutoDJ("Auto DJ stopped because you loaded Deck B manually."); loadDeckB(track) }
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

    private fun restoreBpmCache(library: List<Track>) {
        val preferences = getSharedPreferences("djpdc_bpm_cache", Context.MODE_PRIVATE)
        measuredBpmByTrackId.clear()
        library.forEach { track ->
            val cachedUri = preferences.getString("uri_${track.id}", null)
            val cachedBpm = preferences.getFloat("bpm_${track.id}", -1f)
            if (cachedUri == track.contentUri.toString() && cachedBpm in 40f..240f) {
                measuredBpmByTrackId[track.id] = cachedBpm
            }
        }
    }

    private fun saveMeasuredBpm(track: Track, bpm: Float) {
        if (!bpm.isFinite() || bpm !in 40f..240f) return
        measuredBpmByTrackId[track.id] = bpm
        getSharedPreferences("djpdc_bpm_cache", Context.MODE_PRIVATE).edit()
            .putString("uri_${track.id}", track.contentUri.toString())
            .putFloat("bpm_${track.id}", bpm)
            .apply()
    }

    private suspend fun measureBpm(track: Track): Float? = withContext(Dispatchers.IO) {
        runCatching {
            val decoded = AudioBpmDecoder.decodeMono(this@MainActivity, track.contentUri, maxSeconds = 20)
                ?: return@runCatching null
            AudioBpmAnalyzer.estimateBpm(decoded.first, decoded.second)
        }.getOrNull()
    }

    private fun warmBpmCandidates(currentTrackId: Long) {
        bpmLibraryWarmupJob?.cancel()
        val snapshot = LocalTrackLibrarySnapshot.fromTracks(tracks.toList())
        val candidates = LocalTrackSelector.rank(
            snapshot.entries,
            LocalTrackSelectionRequest(
                favoriteTrackIds = favoriteTrackIds.toSet(),
                excludedTrackIds = setOf(currentTrackId),
                limit = 6
            )
        ).mapNotNull { snapshot.trackFor(it.track) }
            .filter { measuredBpmByTrackId[it.id] == null }
        bpmLibraryWarmupJob = lifecycleScope.launch {
            for (candidate in candidates) {
                if (!isActive || autoDjMode != AutoDJMode.ACTIVE) break
                val bpm = measureBpm(candidate)
                if (bpm != null && tracks.any {
                        it.id == candidate.id && it.contentUri == candidate.contentUri
                    }
                ) {
                    saveMeasuredBpm(candidate, bpm)
                }
                delay(250L)
            }
        }
    }

    private fun analyzeBpm(track: Track, deckA: Boolean) {
        if (deckA) {
            bpmAnalysisA = true
            bpmMessageA = "Decoding audio…"
        } else {
            bpmAnalysisB = true
            bpmMessageB = "Decoding audio…"
        }
        lifecycleScope.launch {
            val result = measureBpm(track)
            if (result != null) saveMeasuredBpm(track, result)
            if (deckA) {
                bpmAnalysisA = false
                if (currentTrack?.contentUri == track.contentUri) {
                    autoBpmA = result
                    bpmMessageA = if (result == null) "Could not estimate BPM" else "Automatic analysis complete · estimate"
                    if (result != null) deckATapTempoBpm = result
                }
            } else {
                bpmAnalysisB = false
                if (deckBTrack?.contentUri == track.contentUri) {
                    autoBpmB = result
                    bpmMessageB = if (result == null) "Could not estimate BPM" else "Automatic analysis complete · estimate"
                    if (result != null) tapTempoBpm = result
                }
            }
        }
    }

    private fun connectDeckBService() {
        try {
            val token = SessionToken(this, ComponentName(this, DeckBPlaybackService::class.java))
            val future = MediaController.Builder(this, token).buildAsync()
            deckBControllerFuture = future
            future.addListener({
                try {
                    val mediaController = future.get()
                    if (isFinishing || isDestroyed) {
                        mediaController.release()
                        return@addListener
                    }
                    deckBController = mediaController.also {
                        it.addListener(deckBListener)
                        applyCrossfade()
                    }
                } catch (error: Exception) {
                    deckBController = null
                    libraryMessage = "The second audio deck could not connect. The main player can still be used."
                }
            }, ContextCompat.getMainExecutor(this))
        } catch (error: Exception) {
            deckBController = null
            libraryMessage = "The second audio deck is unavailable on this device."
        }
    }

    private fun applyCrossfade() {
        val gains = MixerMath.equalPowerCrossfade(crossfadePosition)
        controller?.volume = MixerMath.applyChannelVolume(gains.deckA, deckAVolume)
        deckBController?.volume = MixerMath.applyChannelVolume(gains.deckB, deckBVolume)
    }

    private fun loadDeckB(track: Track) {
        val deck = deckBController ?: return
        deckBTrack = track
        deckBCueMs = null
        tapTimestampsMs.clear()
        tapTempoBpm = null
        beatmatchSpeed = null
        deck.setPlaybackParameters(PlaybackParameters(1f, 1f))
        deck.setMediaItem(MediaItem.fromUri(track.contentUri))
        deck.prepare()
        applyCrossfade()
    }

    private fun connectPlaybackService() {
        try {
            val token = SessionToken(this, ComponentName(this, DJPlaybackService::class.java))
            val future = MediaController.Builder(this, token).buildAsync()
            controllerFuture = future
            future.addListener({
                try {
                    val mediaController = future.get()
                    if (isFinishing || isDestroyed) {
                        mediaController.release()
                        return@addListener
                    }
                    controller = mediaController.also {
                        it.addListener(playerListener)
                        controllerReady = true
                        applyCrossfade()
                    }
                } catch (error: Exception) {
                    controller = null
                    controllerReady = false
                    libraryMessage = "The audio engine could not connect. Close and reopen DJPDC."
                }
            }, ContextCompat.getMainExecutor(this))
        } catch (error: Exception) {
            controller = null
            controllerReady = false
            libraryMessage = "DJPDC could not start its audio engine on this device."
        }
    }

    private fun loadLibrary() {
        if (!hasAudioPermission()) return
        try {
            val found = TrackRepository(this).loadTracks()
            tracks.clear()
            tracks.addAll(found)
            restoreBpmCache(found)
            libraryMessage = if (found.isEmpty()) {
                "No music files were found. Add audio files to your device and rescan."
            } else {
                "Library scanned successfully."
            }
        } catch (_: SecurityException) {
            libraryMessage = "DJPDC could not access the audio library. Check app permissions."
        } catch (_: Exception) {
            libraryMessage = "The music library could not be read. Try rescanning or checking audio permissions."
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
        stopAutoDJ("Auto DJ stopped because you selected a track manually.")
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

    private fun stopPlayback() {
        stopAutoDJ("Auto DJ stopped because playback was stopped.")
        controller?.pause()
        controller?.seekTo(0L)
    }

    private fun skipToNextTrack() {
        val activePlayer = controller ?: return
        if (autoDjMode == AutoDJMode.ACTIVE) {
            val activeQueue = queueTracks.ifEmpty { tracks.toList() }
            val currentIndex = activeQueue.indexOfFirst { it.id == currentTrack?.id }
            val nextTrack = if (currentIndex >= 0 && currentIndex + 1 < activeQueue.size) {
                activeQueue[currentIndex + 1]
            } else {
                activeQueue.firstOrNull { it.id != currentTrack?.id }
            }
            if (nextTrack != null) {
                playTrack(nextTrack)
            } else {
                stopAutoDJ("No other local track is available.")
            }
            return
        }
        stopAutoDJ("Auto DJ stopped because you selected Next.")
        activePlayer.seekToNextMediaItem()
    }

    private fun startAutoDJ() {
        val activePlayer = controller
        val deck = deckBController
        val selected = currentTrack
        if (activePlayer == null || deck == null || selected == null || !tracks.any { it.id == selected.id }) {
            autoDjMessage = "Start a song from your phone library first."
            return
        }
        autoDjJob?.cancel()
        val enabled = AutoDJSessionPolicy.apply(
            AutoDJSessionState(currentTrackId = selected.id),
            AutoDJUserCommand.ENABLE
        ) as AutoDJGateDecision.Updated
        autoDjSessionState = enabled.state
        autoDjSessionState = AutoDJCoordinator.attachCurrentTrack(
            autoDjSessionState,
            selected.id,
            tracks.map { it.id }.toSet()
        ) ?: run {
            stopAutoDJ("The current track is not available in the local library.")
            return
        }
        // Auto DJ owns the queue while active; retain the current playback position.
        activePlayer.setMediaItem(MediaItem.fromUri(selected.contentUri), activePlayer.currentPosition.coerceAtLeast(0L))
        activePlayer.prepare()
        activePlayer.play()
        autoDjMode = AutoDJMode.ACTIVE
        autoDjMessage = "Analyzing the current track and choosing only from music stored on this phone."
        // Analyze in the background; playback continues if tempo detection fails.
        if (autoBpmA == null && !bpmAnalysisA) {
            analyzeBpm(selected, deckA = true)
        }
        warmBpmCandidates(selected.id)
        autoDjJob = lifecycleScope.launch {
            runAutoDJLoop()
        }
    }

    private fun stopAutoDJ(message: String) {
        autoDjJob?.cancel()
        autoDjJob = null
        bpmLibraryWarmupJob?.cancel()
        bpmLibraryWarmupJob = null
        autoDjSessionState = (AutoDJSessionPolicy.apply(
            autoDjSessionState,
            AutoDJUserCommand.DISABLE
        ) as AutoDJGateDecision.Updated).state
        autoDjMode = AutoDJMode.OFF
        autoDjMessage = message
        // Restore ordinary deck gains and silence the standby deck.
        deckBController?.pause()
        deckBController?.volume = 0f
        controller?.volume = deckAVolume.coerceIn(0f, 1f)
        crossfadePosition = 0f
        deckBTrack = null
    }

    private suspend fun runAutoDJLoop() {
        while (autoDjSessionState.mode == AutoDJMode.ACTIVE) {
            val playerA = controller ?: break
            val playerB = deckBController ?: break
            val activeTrack = currentTrack ?: break
            if (playerA.currentMediaItem == null || playerA.mediaItemCount == 0) break

            val duration = playerA.duration.takeIf { it > 0L } ?: activeTrack.durationMs
            val remaining = duration - playerA.currentPosition.coerceAtLeast(0L)
            if (duration > 0L && remaining <= 7_500L) {
                val snapshot = LocalTrackLibrarySnapshot.fromTracks(tracks.toList())
                val decision = AutoDJCoordinator.proposeNext(
                    state = autoDjSessionState,
                    library = snapshot.entries,
                    request = LocalTrackSelectionRequest(
                        favoriteTrackIds = favoriteTrackIds.toSet(),
                        limit = 1
                    ),
                    transitionDurationMs = 6_000L,
                    currentBpm = autoBpmA ?: measuredBpmByTrackId[activeTrack.id],
                    measuredBpms = measuredBpmByTrackId.toMap()
                )
                if (decision !is AutoDJCoordinatorDecision.Ready) {
                    val reason = (decision as? AutoDJCoordinatorDecision.Blocked)?.reason
                        ?: "No next local track is available."
                    stopAutoDJ(reason)
                    return
                }
                val next = snapshot.trackFor(decision.nextTrack)
                if (next == null) {
                    stopAutoDJ("The selected local track is no longer available.")
                    return
                }
                val approval = AutoDJTransitionApproval(
                    currentTrackId = activeTrack.id,
                    nextTrackId = next.id,
                    plan = decision.plan
                )
                if (!AutoDJCoordinator.approveHandoff(
                        state = autoDjSessionState,
                        approval = approval,
                        availableLocalTrackIds = snapshot.entries.map { it.id }.toSet(),
                        nextTrackDurationMs = next.durationMs
                    )
                ) {
                    stopAutoDJ("The transition was blocked by a safety check.")
                    return
                }

                // Prepare Deck B silently before beginning the gain ramp.
                playerB.pause()
                playerB.setPlaybackParameters(
                    PlaybackParameters(decision.plan.playbackSpeedRatio, 1f)
                )
                playerB.setMediaItem(MediaItem.fromUri(next.contentUri), 0L)
                playerB.prepare()
                playerB.volume = 0f
                playerB.play()
                deckBTrack = next
                // Give Media3 time to buffer Deck B before starting the fade.
                var readyWaitMs = 0L
                while (!playerB.isPlaying && readyWaitMs < 5_000L &&
                    autoDjSessionState.mode == AutoDJMode.ACTIVE &&
                    currentTrack?.id == activeTrack.id
                ) {
                    delay(100L)
                    readyWaitMs += 100L
                }
                if (!playerB.isPlaying) {
                    stopAutoDJ("Deck B could not start the next local track; the current song was left alone.")
                    return
                }
                val startedAt = android.os.SystemClock.elapsedRealtime()
                var cancelled = false
                while (true) {
                    val stillValid = AutoDJCrossfadeTimeline.mayContinue(
                        session = autoDjSessionState,
                        expectedCurrentTrackId = activeTrack.id,
                        expectedNextTrackId = next.id,
                        actualCurrentTrackId = currentTrack?.id,
                        actualNextTrackId = deckBTrack?.id,
                        userOverride = autoDjSessionState.mode != AutoDJMode.ACTIVE
                    )
                    if (!stillValid || !playerA.isPlaying || !playerB.isPlaying) {
                        cancelled = true
                        break
                    }
                    val frame = AutoDJCrossfadeTimeline.frameAt(
                        android.os.SystemClock.elapsedRealtime() - startedAt,
                        decision.plan.transitionDurationMs
                    )
                    if (frame == null) {
                        cancelled = true
                        break
                    }
                    playerA.volume = MixerMath.applyChannelVolume(frame.gains.deckA, deckAVolume)
                    playerB.volume = MixerMath.applyChannelVolume(frame.gains.deckB, deckBVolume)
                    if (frame.isComplete) break
                    delay(100L)
                }
                if (cancelled) {
                    stopAutoDJ("Auto DJ transition cancelled; manual playback has priority.")
                    return
                }

                // Promote the selected song to Deck A and return Deck B to standby.
                playerB.pause()
                playerA.volume = 0f
                playerA.setPlaybackParameters(PlaybackParameters(1f, 1f))
                playerA.setMediaItem(MediaItem.fromUri(next.contentUri), 0L)
                playerA.prepare()
                playerA.play()
                currentTrack = next
                autoDjSessionState = autoDjSessionState.copy(currentTrackId = next.id)
                // The old estimate belongs to the previous song; analyze the new deck.
                autoBpmA = null
                bpmAnalysisA = false
                bpmMessageA = "Analyzing next track…"
                analyzeBpm(next, deckA = true)
                warmBpmCandidates(next.id)
                playerB.clearMediaItems()
                deckBTrack = null
                crossfadePosition = 0f
                applyCrossfade()
                autoDjMessage = "Now playing: ${next.title}"
            } else {
                delay(250L)
            }
        }
        if (autoDjMode == AutoDJMode.ACTIVE) {
            stopAutoDJ("Auto DJ stopped because playback is no longer available.")
        }
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
        autoDjJob?.cancel()
        bpmLibraryWarmupJob?.cancel()
        controller?.removeListener(playerListener)
        deckBController?.removeListener(deckBListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        deckBControllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        deckBControllerFuture = null
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
        color = Color(0xFF141522),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF25273C))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (track.albumArtUri != null) {
                AsyncImage(
                    model = track.albumArtUri,
                    contentDescription = "Album artwork",
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.size(48.dp),
                    color = Color(0xFF24243A),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("♪", color = Color(0xFF62D9E8), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
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
