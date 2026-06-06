package io.github.mobdev.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MessageEntity::class, ChannelEntity::class, OutboxEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun channelDao(): ChannelDao
    abstract fun outboxDao(): OutboxDao

    companion object {
        @Volatile
        private var instance: ChatDatabase? = null

        fun get(context: Context): ChatDatabase = instance ?: synchronized(this) {
            instance ?: build(context).also { instance = it }
        }

        private fun build(context: Context): ChatDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                ChatDatabase::class.java,
                "chat.db"
            )
                .fallbackToDestructiveMigration()
                .build()
    }
}
