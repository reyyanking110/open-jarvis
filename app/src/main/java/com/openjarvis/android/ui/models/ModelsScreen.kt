package com.openjarvis.android.ui.models

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import com.openjarvis.android.core.domain.model.LocalAccelerator
import com.openjarvis.android.ui.theme.JarvisEmerald

@Composable
fun ModelsScreen(
    viewModel: ModelsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingImportModelId by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        val targetId = pendingImportModelId
        pendingImportModelId = null
        if (uri != null && targetId != null) {
            viewModel.importLocalModel(targetId, uri)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text("Models & Inference Hardware", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "Device RAM: ${state.telemetry.availableRamMb} MB free / ${state.telemetry.totalRamMb} MB total",
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
            state.statusMessage?.let { msg ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(msg, modifier = Modifier.weight(1f))
                            TextButton(onClick = { viewModel.clearStatus() }) { Text("OK") }
                        }
                    }
                }
            }

            item {
                Text("Online Cloud Models (Gemini)", style = MaterialTheme.typography.titleMedium)
            }

            items(state.geminiModels, key = { it.id }) { spec ->
                val selected = state.settings.selectedGeminiModelId == spec.id
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { viewModel.selectGeminiModel(spec.id) },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(spec.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                spec.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text("Local Accelerator (LiteRT-LM)", style = MaterialTheme.typography.titleMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    LocalAccelerator.entries.forEach { acc ->
                        FilterChip(
                            selected = state.settings.localAccelerator == acc,
                            onClick = { viewModel.selectAccelerator(acc) },
                            label = { Text(acc.label) },
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text("Offline On-Device Models (.litertlm)", style = MaterialTheme.typography.titleMedium)
            }

            items(state.localCatalog, key = { it.id }) { spec ->
                val installed = state.installedModels[spec.id]
                val download = state.downloads[spec.id]
                val isSelected = state.settings.selectedLocalModelId == spec.id
                val isDownloading = download?.state == WorkInfo.State.RUNNING ||
                    download?.state == WorkInfo.State.ENQUEUED

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.selectLocalModel(spec.id) },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(spec.displayName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "${spec.approxSizeMb} MB · Min ${spec.minRamMb} MB RAM",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (installed != null) {
                                Text(
                                    text = "Installed",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = JarvisEmerald,
                                )
                            }
                        }

                        Text(
                            text = spec.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        if (installed != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "SHA-256: ${installed.sha256.take(16)}...",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        if (isDownloading && download != null) {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { (download.progress / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = "Downloading: ${download.progress}%",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }

                        download?.error?.let { err ->
                            if (download.state == WorkInfo.State.FAILED) {
                                Text(
                                    text = "Download failed: $err",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isDownloading) {
                                OutlinedButton(onClick = { viewModel.cancelDownload(spec.id) }) {
                                    Text("Cancel")
                                }
                            } else {
                                Button(onClick = { viewModel.startDownload(spec) }) {
                                    Text(if (installed == null) "Download" else "Re-download")
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    pendingImportModelId = spec.id
                                    importLauncher.launch(arrayOf("*/*"))
                                },
                            ) {
                                Text("Import File")
                            }
                            if (installed != null) {
                                TextButton(onClick = { viewModel.deleteLocalModel(spec.id) }) {
                                    Text("Delete", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
