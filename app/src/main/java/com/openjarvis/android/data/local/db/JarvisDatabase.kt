package com.openjarvis.android.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        MemoryEntity::class,
        MemoryFtsEntity::class,
        AgentTaskEntity::class,
        AgentStepEntity::class,
        ExecutionTraceEntity::class,
        RoutingStatEntity::class,
        LocalModelEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class JarvisDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun memoryDao(): MemoryDao
    abstract fun agentTaskDao(): AgentTaskDao
    abstract fun learningDao(): LearningDao
    abstract fun localModelDao(): LocalModelDao
}
