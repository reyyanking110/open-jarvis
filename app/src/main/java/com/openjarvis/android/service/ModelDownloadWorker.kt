package com.openjarvis.android.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.openjarvis.android.R
import com.openjarvis.android.core.domain.model.ModelCatalog
import com.openjarvis.android.data.repository.ModelRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val okHttpClient: OkHttpClient,
    private val modelRepository: ModelRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val modelId = inputData.getString(KEY_MODEL_ID) ?: return@withContext Result.failure()
        val spec = ModelCatalog.findById(modelId) ?: return@withContext Result.failure()
        val url = inputData.getString(KEY_URL)?.takeIf { it.isNotBlank() } ?: spec.downloadUrl
        if (url.isBlank()) {
            return@withContext Result.failure(workDataOf(KEY_ERROR to "No download URL for $modelId"))
        }

        ensureChannel()
        setForeground(createForegroundInfo(spec.displayName, 0))

        val destFile = File(modelRepository.modelsDirectory(), spec.fileName)
        val tempFile = File(modelRepository.modelsDirectory(), "${spec.fileName}.part")

        val request = Request.Builder().url(url).build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        workDataOf(KEY_ERROR to "HTTP ${response.code} downloading ${spec.displayName}"),
                    )
                }
                val body = response.body ?: throw IOException("Empty download body.")
                val totalBytes = body.contentLength()
                var downloaded = 0L
                var lastPct = -1

                body.byteStream().use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            if (isStopped) {
                                return@withContext Result.failure(workDataOf(KEY_ERROR to "Cancelled"))
                            }
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloaded += read

                            val pct = if (totalBytes > 0L) {
                                ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                            } else {
                                0
                            }
                            if (pct != lastPct) {
                                lastPct = pct
                                setProgress(workDataOf(KEY_PROGRESS to pct))
                                setForeground(createForegroundInfo(spec.displayName, pct))
                            }
                        }
                    }
                }
            }

            if (destFile.exists()) destFile.delete()
            if (!tempFile.renameTo(destFile)) {
                tempFile.copyTo(destFile, overwrite = true)
                tempFile.delete()
            }
            modelRepository.registerInstalledFile(modelId, destFile)
            Result.success()
        } catch (e: Exception) {
            tempFile.delete()
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Download failed")))
        }
    }

    private fun ensureChannel() {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            applicationContext.getString(R.string.download_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = applicationContext.getString(R.string.download_channel_description)
        }
        nm.createNotificationChannel(channel)
    }

    private fun createForegroundInfo(modelName: String, progress: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading $modelName")
            .setContentText(if (progress > 0) "$progress% complete" else "Starting download...")
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_MODEL_ID = "model_id"
        const val KEY_URL = "download_url"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
        const val CHANNEL_ID = "jarvis_model_downloads"
        const val NOTIFICATION_ID = 1002
    }
}
