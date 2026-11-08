package com.profetasdelcodigo.djpdc

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class MainActivity : ComponentActivity() {
    private var permissionGranted by mutableStateOf(false)
    private val tracks = mutableStateListOf<Track>()
    private var currentTrack by mutableStateOf<Track?>(null)
    private var isPlaying by mutableStateOf(false)
    private var libraryMessage by mutableStateOf("Your local tracks will appear here.")
    private lateinit var player: ExoPlayer

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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build().also {
            it.addListener(playerListener)
        }
        permissionGranted = hasAudioPermission()
        if (permissionGranted) loadLibrary()

        setContent {
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
                            Text("BETA 0.2", color = Color(0xFF76B900))
                        }
                        Text("YOUR MUSIC. YOUR MIX.", color = Color(0xFF9AA39A), style = MaterialTheme.typography.labelLarge)

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF151815),
                            shape = RoundedCornerShape(22.dp)
                        ) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("NOW PLAYING", color = Color(0xFF76B900), style = MaterialTheme.typography.labelLarge)
                                Text(
                                    currentTrack?.title ?: "Choose a track",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    currentTrack?.artist ?: "DJPDC local player",
                                    color = Color(0xFF9AA39A),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = {
                                            if (isPlaying) player.pause() else if (currentTrack != null) player.play()
                                        },
                                        enabled = currentTrack != null
                                    ) { Text(if (isPlaying) "Pause" else "Play") }
                                    Button(
                                        onClick = { player.seekToNextMediaItem() },
                                        enabled = player.mediaItemCount > 1
                                    ) { Text("Next") }
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("YOUR LIBRARY", color = Color(0xFF76B900), style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.weight(1f))
                            Text("${tracks.size} tracks", color = Color(0xFF9AA39A), style = MaterialTheme.typography.labelSmall)
                        }

                        if (!permissionGranted) {
                            Text("Allow DJPDC to read audio files stored on this device.", color = Color(0xFFADB5AD))
                            Button(onClick = { requestAudioPermission() }) { Text("Grant music access") }
                        } else if (tracks.isEmpty()) {
                            Text(libraryMessage, color = Color(0xFFADB5AD))
                            Button(onClick = { loadLibrary() }) { Text("Rescan library") }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(tracks, key = { it.id }) { track ->
                                    TrackRow(track = track, onClick = { playTrack(track) })
                                }
                            }
                        }

                        Text("AUTO DJ  ·  MANUAL DECKS  ·  LOCAL AUDIO", color = Color(0xFF6F786F), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
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

    private fun playTrack(track: Track) {
        val index = tracks.indexOfFirst { it.id == track.id }
        if (index < 0) return
        currentTrack = track
        player.setMediaItems(tracks.map { MediaItem.fromUri(it.contentUri) }, index, 0L)
        player.prepare()
        player.play()
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
        player.removeListener(playerListener)
        player.release()
        super.onDestroy()
    }
}

@androidx.compose.runtime.Composable
private fun TrackRow(track: Track, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = Color(0xFF151815),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(track.title, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${track.artist}  ·  ${track.album}",
                color = Color(0xFF9AA39A),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
