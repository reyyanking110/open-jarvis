package com.openjarvis.android.data.repository

import android.content.Context
import android.net.Uri
import com.openjarvis.android.core.domain.model.InstalledLocalModel
import com.openjarvis.android.core.domain.model.ModelCatalog
import com.openjarvis.android.core.domain.model.ModelSpec
import com.openjarvis.android.data.local.db.LocalModelDao
import com.openjarvis.android.data.local.db.LocalModelEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class ModelRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localModelDao: LocalModelDao,
) {
    fun modelsDirectory(): File =
        File(context.filesDir, "models").apply { if (!exists()) mkdirs() }

    fun observeInstalledModels(): Flow<List<InstalledLocalModel>> =
        localModelDao.observeInstalledModels().map { list ->
            list.mapNotNull { entity ->
                val file = File(entity.localPath)
                if (!file.exists()) return@mapNotNull null
                val spec = ModelCatalog.findById(entity.modelId)
                    ?: ModelSpec(
                        id = entity.modelId,
                        displayName = file.nameWithoutExtension,
                        engineType = com.openjarvis.android.core.domain.model.EngineType.LOCAL_LITERT_LM,
                        description = "User-imported LiteRT-LM model",
                        minRamMb = 3072,
                        approxSizeMb = (entity.fileSizeBytes / (1024L * 1024L)).toInt(),
                        contextWindow = 4096,
                        supportsToolCalling = true,
                        fileName = file.name,
                    )
                InstalledLocalModel(
                    spec = spec,
                    localPath = entity.localPath,
                    fileSizeBytes = entity.fileSizeBytes,
                    sha256 = entity.sha256,
                    installedAtMillis = entity.installedAtMillis,
                )
            }
        }

    suspend fun getInstalledModel(modelId: String): InstalledLocalModel? {
        val entity = localModelDao.getInstalledModel(modelId) ?: return null
        val file = File(entity.localPath)
        if (!file.exists() || file.length() <= 0L) return null
        val spec = ModelCatalog.findById(modelId) ?: return null
        return InstalledLocalModel(
            spec = spec,
            localPath = entity.localPath,
            fileSizeBytes = entity.fileSizeBytes,
            sha256 = entity.sha256,
            installedAtMillis = entity.installedAtMillis,
        )
    }

    suspend fun registerInstalledFile(modelId: String, file: File): InstalledLocalModel =
        withContext(Dispatchers.IO) {
            val sha256 = computeSha256(file)
            val now = System.currentTimeMillis()
            localModelDao.upsertInstalledModel(
                LocalModelEntity(
                    modelId = modelId,
                    localPath = file.absolutePath,
                    fileSizeBytes = file.length(),
                    sha256 = sha256,
                    installedAtMillis = now,
                )
            )
            val spec = ModelCatalog.findById(modelId)
                ?: ModelSpec(
                    id = modelId,
                    displayName = file.nameWithoutExtension,
                    engineType = com.openjarvis.android.core.domain.model.EngineType.LOCAL_LITERT_LM,
                    description = "Imported LiteRT-LM model",
                    minRamMb = 3072,
                    approxSizeMb = (file.length() / (1024L * 1024L)).toInt(),
                    contextWindow = 4096,
                    supportsToolCalling = true,
                    fileName = file.name,
                )
            InstalledLocalModel(
                spec = spec,
                localPath = file.absolutePath,
                fileSizeBytes = file.length(),
                sha256 = sha256,
                installedAtMillis = now,
            )
        }

    /**
     * Imports a user-selected .litertlm or .task file via Storage Access Framework into internal storage.
     */
    suspend fun importModelFromUri(modelId: String, uri: Uri): Result<InstalledLocalModel> =
        withContext(Dispatchers.IO) {
            runCatching {
                val spec = ModelCatalog.findById(modelId)
                val targetName = spec?.fileName?.takeIf { it.isNotBlank() } ?: "$modelId.litertlm"
                val destFile = File(modelsDirectory(), targetName)
                val tempFile = File(modelsDirectory(), "$targetName.tmp")

                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                    }
                } ?: error("Unable to open selected model file.")

                if (tempFile.length() < 1024L) {
                    tempFile.delete()
                    error("Selected file is empty or too small to be a valid LLM model.")
                }
                if (destFile.exists()) destFile.delete()
                if (!tempFile.renameTo(destFile)) {
                    tempFile.copyTo(destFile, overwrite = true)
                    tempFile.delete()
                }
                registerInstalledFile(modelId, destFile)
            }
        }

    suspend fun deleteInstalledModel(modelId: String) = withContext(Dispatchers.IO) {
        val existing = localModelDao.getInstalledModel(modelId)
        if (existing != null) {
            File(existing.localPath).delete()
        }
        localModelDao.deleteInstalledModel(modelId)
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        file.inputStream().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
