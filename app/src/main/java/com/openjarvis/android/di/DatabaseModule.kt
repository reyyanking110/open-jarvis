package com.openjarvis.android.di

import android.content.Context
import androidx.room.Room
import com.openjarvis.android.data.local.db.AgentTaskDao
import com.openjarvis.android.data.local.db.ConversationDao
import com.openjarvis.android.data.local.db.JarvisDatabase
import com.openjarvis.android.data.local.db.LearningDao
import com.openjarvis.android.data.local.db.LocalModelDao
import com.openjarvis.android.data.local.db.MemoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): JarvisDatabase =
        Room.databaseBuilder(
            context,
            JarvisDatabase::class.java,
            "open_jarvis.db",
        ).build()

    @Provides
    fun provideConversationDao(db: JarvisDatabase): ConversationDao = db.conversationDao()

    @Provides
    fun provideMemoryDao(db: JarvisDatabase): MemoryDao = db.memoryDao()

    @Provides
    fun provideAgentTaskDao(db: JarvisDatabase): AgentTaskDao = db.agentTaskDao()

    @Provides
    fun provideLearningDao(db: JarvisDatabase): LearningDao = db.learningDao()

    @Provides
    fun provideLocalModelDao(db: JarvisDatabase): LocalModelDao = db.localModelDao()
}
