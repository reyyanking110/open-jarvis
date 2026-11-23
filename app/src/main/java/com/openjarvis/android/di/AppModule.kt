package com.openjarvis.android.di

import com.openjarvis.android.core.config.ApiKeyProvider
import com.openjarvis.android.core.config.SecretVault
import com.openjarvis.android.core.telemetry.DeviceStateMonitor
import com.openjarvis.android.core.telemetry.DeviceTelemetryProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindingsModule {
    @Binds
    @Singleton
    abstract fun bindApiKeyProvider(impl: SecretVault): ApiKeyProvider

    @Binds
    @Singleton
    abstract fun bindDeviceTelemetryProvider(impl: DeviceStateMonitor): DeviceTelemetryProvider
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
}
