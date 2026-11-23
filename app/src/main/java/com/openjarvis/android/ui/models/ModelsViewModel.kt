package com.openjarvis.android.ui.models

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.openjarvis.android.core.config.JarvisPreferencesRepository
import com.openjarvis.android.core.config.JarvisSettings
import com.openjarvis.android.core.domain.model.DeviceTelemetry
import com.openjarvis.android.core.domain.model.InstalledLocalModel
import com.openjarvis.android.core.domain.model.LocalAccelerator
import com.openjarvis.android.core.domain.model.ModelCatalog
import com.openjarvis.android.core.domain.model.ModelSpec
import com.openjarvis.android.core.telemetry.DeviceTelemetryProvider
import com.openjarvis.android.data.repository.ModelRepository
import com.openjarvis.android.service.ModelDownloadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DownloadStatus(
    val modelId: String,
    val progress: Int,
    val state: WorkInfo.State,
    val error: String? = null,
)

data class ModelsUiState(
    val settings: JarvisSettings = JarvisSettings(),
    val telemetry: DeviceTelemetry = DeviceTelemetry(),
    val geminiModels: List<ModelSpec> = ModelCatalog.geminiModels,
    val localCatalog: List<ModelSpec> = ModelCatalog.localModels,
    val installedModels: Map<String, InstalledLocalModel> = emptyMap(),
    val downloads: Map<String, DownloadStatus> = emptyMap(),
    val statusMessage: String? = null,
)

@HiltViewModel
class ModelsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelRepository: ModelRepository,
    private val preferencesRepository: JarvisPreferencesRepository,
    telemetryProvider: DeviceTelemetryProvider,
) : ViewModel() {

    private val workManager = WorkManager.getInstance(context)
    private val statusMessage = MutableStateFlow<String?>(null)

    private val workFlow = workManager.getWorkInfosByTagFlow(TAG_MODEL_DOWNLOAD)

    val uiState: StateFlow<ModelsUiState> = combine(
        preferencesRepository.settingsFlow,
        telemetryProvider.telemetryFlow,
        modelRepository.observeInstalledModels(),
        workFlow,
        statusMessage,
    ) { settings, telemetry, installed, workInfos, msg ->
        val downloadMap = mutableMapOf<String, DownloadStatus>()
        workInfos.forEach { info ->
            val modelIdTag = info.tags.firstOrNull { it.startsWith("model:") }?.removePrefix("model:")
            if (modelIdTag != null) {
                downloadMap[modelIdTag] = DownloadStatus(
                    modelId = modelIdTag,
                    progress = info.progress.getInt(ModelDownloadWorker.KEY_PROGRESS, 0),
                    state = info.state,
                    error = info.outputData.getString(ModelDownloadWorker.KEY_ERROR),
                )
            }
        }
        ModelsUiState(
            settings = settings,
            telemetry = telemetry,
            installedModels = installed.associateBy { it.spec.id },
            downloads = downloadMap,
            statusMessage = msg,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ModelsUiState(),
    )

    fun selectGeminiModel(modelId: String) {
        viewModelScope.launch { preferencesRepository.setGeminiModel(modelId) }
    }

    fun selectLocalModel(modelId: String) {
        viewModelScope.launch { preferencesRepository.setLocalModel(modelId) }
    }

    fun selectAccelerator(accelerator: LocalAccelerator) {
        viewModelScope.launch { preferencesRepository.setLocalAccelerator(accelerator) }
    }

    fun startDownload(spec: ModelSpec) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    ModelDownloadWorker.KEY_MODEL_ID to spec.id,
                    ModelDownloadWorker.KEY_URL to spec.downloadUrl,
                )
            )
            .addTag(TAG_MODEL_DOWNLOAD)
            .addTag("model:${spec.id}")
            .build()

        workManager.enqueueUniqueWork(
            "download_${spec.id}",
            ExistingWorkPolicy.REPLACE,
            request,
        )
        statusMessage.value = "Queued download for ${spec.displayName}."
    }

    fun cancelDownload(modelId: String) {
        workManager.cancelUniqueWork("download_$modelId")
    }

    fun importLocalModel(modelId: String, uri: Uri) {
        viewModelScope.launch {
            statusMessage.value = "Importing model file..."
            val result = modelRepository.importModelFromUri(modelId, uri)
            result.onSuccess {
                preferencesRepository.setLocalModel(modelId)
                statusMessage.value = "Imported ${it.spec.displayName} (${it.fileSizeBytes / (1024 * 1024)} MB)."
            }.onFailure { err ->
                statusMessage.value = "Import failed: ${err.message}"
            }
        }
    }

    fun deleteLocalModel(modelId: String) {
        viewModelScope.launch {
            modelRepository.deleteInstalledModel(modelId)
            statusMessage.value = "Deleted local model files for $modelId."
        }
    }

    fun clearStatus() {
        statusMessage.value = null
    }

    companion object {
        const val TAG_MODEL_DOWNLOAD = "jarvis_model_download"
    }
}
