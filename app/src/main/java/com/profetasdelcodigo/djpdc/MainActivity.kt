package com.profetasdelcodigo.djpdc

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private var permissionGranted by mutableStateOf(false)

    private val audioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionGranted = hasAudioPermission()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFF76B900),
                background = Color(0xFF0B0D0B),
                surface = Color(0xFF151815)
            )) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0B0D0B)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("DJPDC", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                            Spacer(Modifier.weight(1f))
                            Text("BETA 0.1", color = Color(0xFF76B900))
                        }
                        Text("YOUR MUSIC. YOUR MIX.", color = Color(0xFF9AA39A), style = MaterialTheme.typography.labelLarge)
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF151815),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("LIBRARY", color = Color(0xFF76B900), style = MaterialTheme.typography.labelLarge)
                                Text(
                                    if (permissionGranted) "Audio access granted" else "Connect your music library",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    if (permissionGranted) "The next milestone will index your local tracks, albums and artists." else "Allow access to audio files on this device to start building your DJ library.",
                                    color = Color(0xFFADB5AD)
                                )
                                if (!permissionGranted) {
                                    Button(onClick = { requestAudioPermission() }) {
                                        Text("Grant music access")
                                    }
                                }
                            }
                        }
                        Text("MIXING MODE", color = Color(0xFF76B900), style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ModeTile("AUTO DJ", "Smart transitions", Modifier.weight(1f))
                            ModeTile("MANUAL", "Two-deck control", Modifier.weight(1f))
                        }
                        Spacer(Modifier.weight(1f))
                        Text("LOCAL-FIRST AUDIO ENGINE · AI-ASSISTED", color = Color(0xFF6F786F), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
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
}

@androidx.compose.runtime.Composable
private fun ModeTile(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = Color(0xFF151815), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = Color(0xFF9AA39A), style = MaterialTheme.typography.bodySmall)
        }
    }
}
