package com.openjarvis.android.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openjarvis.android.core.domain.model.InferenceMode
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    onOpenPermissions: () -> Unit,
    onOpenModels: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var apiKeyInput by remember { mutableStateOf("") }

    val safFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        viewModel.saveSafWorkspaceTree(uri)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text("Settings & Security", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "Keystore-encrypted credentials, routing policy, voice, and SAF workspace.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.statusNotice?.let { notice ->
                item {
                    Card {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(notice, modifier = Modifier.weight(1f))
                            TextButton(onClick = { viewModel.clearNotice() }) { Text("OK") }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Gemini API Key", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (state.hasGeminiKey) {
                                "Active key stored securely: ${state.maskedKeyHint}"
                            } else {
                                "No key configured. Enter your Google AI Studio Gemini API key below (stored AES-256 encrypted in Android Keystore)."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            label = { Text("Enter Gemini API Key") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    viewModel.saveApiKey(apiKeyInput)
                                    apiKeyInput = ""
                                },
                                enabled = apiKeyInput.isNotBlank(),
                            ) {
                                Text("Save Key")
                            }
                            if (state.hasGeminiKey) {
                                OutlinedButton(onClick = { viewModel.clearApiKey() }) {
                                    Text("Clear Key")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Inference Routing Mode", style = MaterialTheme.typography.titleMedium)
                        InferenceMode.entries.forEach { mode ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = state.settings.inferenceMode == mode,
                                    onClick = { viewModel.setInferenceMode(mode) },
                                )
                                Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Strict Privacy (On-Device Only)", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Blocks all cloud Gemini requests and forces local LiteRT-LM inference.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = state.settings.privacyLocalOnly,
                                onCheckedChange = { viewModel.setPrivacyLocalOnly(it) },
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-Speak Responses (TTS)", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Automatically read JARVIS responses aloud.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = state.settings.autoSpeakResponses,
                                onCheckedChange = { viewModel.setAutoSpeak(it) },
                            )
                        }

                        Column {
                            Text(
                                text = "Max Autonomous Agent Steps: ${state.settings.maxAgentSteps}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Slider(
                                value = state.settings.maxAgentSteps.toFloat(),
                                onValueChange = { viewModel.setMaxSteps(it.roundToInt()) },
                                valueRange = 2f..16f,
                                steps = 13,
                            )
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Storage Access Framework (SAF) Workspace", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = state.settings.safTreeUri
                                ?: "No shared folder linked yet. Link a folder to allow scoped file read/write tools.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { safFolderLauncher.launch(null) }) {
                                Text("Choose Folder")
                            }
                            if (state.settings.safTreeUri != null) {
                                OutlinedButton(onClick = { viewModel.clearSafWorkspace() }) {
                                    Text("Unlink")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onOpenPermissions,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Permissions")
                    }
                    OutlinedButton(
                        onClick = onOpenModels,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Model Manager")
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
