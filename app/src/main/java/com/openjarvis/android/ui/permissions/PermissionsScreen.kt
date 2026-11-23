package com.openjarvis.android.ui.permissions

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openjarvis.android.ui.settings.SettingsViewModel
import com.openjarvis.android.ui.theme.JarvisAmber
import com.openjarvis.android.ui.theme.JarvisEmerald

@Composable
fun PermissionsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var refreshTick by remember { mutableIntStateOf(0) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        refreshTick++
    }

    val recordGranted = remember(refreshTick) {
        viewModel.isPermissionGranted(Manifest.permission.RECORD_AUDIO)
    }
    val notifyGranted = remember(refreshTick) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            viewModel.isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }
    }
    val callGranted = remember(refreshTick) {
        viewModel.isPermissionGranted(Manifest.permission.CALL_PHONE)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Permissions & Capabilities", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Permissions are requested only when needed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = onBack) { Text("Back") }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                PermissionRowCard(
                    title = "Microphone (RECORD_AUDIO)",
                    description = "Required for voice commands via Android SpeechRecognizer.",
                    granted = recordGranted,
                    onRequest = { permLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                item {
                    PermissionRowCard(
                        title = "Notifications (POST_NOTIFICATIONS)",
                        description = "Required for JARVIS reminder notifications, agent progress, and model downloads.",
                        granted = notifyGranted,
                        onRequest = { permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    )
                }
            }

            item {
                PermissionRowCard(
                    title = "Direct Phone Calls (CALL_PHONE)",
                    description = "Optional. Without it, JARVIS opens the dialer instead of placing calls directly (always asks confirmation).",
                    granted = callGranted,
                    onRequest = { permLauncher.launch(Manifest.permission.CALL_PHONE) },
                )
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Platform Capability Status", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "SpeechRecognizer available: ${state.speechRecognitionAvailable}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "SAF Workspace linked: ${state.settings.safTreeUri != null}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            },
                        ) {
                            Text("Open Android App Settings")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRowCard(
    title: String,
    description: String,
    granted: Boolean,
    onRequest: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    text = if (granted) "Granted" else "Not Granted",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (granted) JarvisEmerald else JarvisAmber,
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!granted) {
                Button(onClick = onRequest) {
                    Text("Grant Permission")
                }
            }
        }
    }
}
