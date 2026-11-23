package com.openjarvis.android

import android.app.Application
import android.content.ComponentCallbacks2
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.openjarvis.android.ai.engine.LiteRtLmEngine
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class OpenJarvisApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var liteRtLmEngine: LiteRtLmEngine

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
        ) {
            appScope.launch {
                liteRtLmEngine.release()
            }
        }
    }
}
