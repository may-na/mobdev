package io.github.mobdev.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE channel = :channel ORDER BY id ASC")
    fun observeByChannel(channel: String): Flow<List<MessageEntity>>

    @Query("SELECT MAX(id) FROM messages WHERE channel = :channel")
    suspend fun maxId(channel: String): Long?

    @Query("SELECT MIN(id) FROM messages WHERE channel = :channel")
    suspend fun minId(channel: String): Long?

    @Query("SELECT COUNT(*) FROM messages WHERE channel = :channel")
    suspend fun countByChannel(channel: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(messages: List<MessageEntity>)

    @Query("DELETE FROM messages")
    suspend fun clear()
}

@Dao
interface ChannelDao {
    @Query("SELECT name FROM channels ORDER BY ordering ASC")
    fun observeNames(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM channels")
    suspend fun count(): Int

    @Transaction
    suspend fun replaceAll(names: List<String>) {
        clear()
        upsertAll(names.mapIndexed { index, name -> ChannelEntity(name = name, ordering = index) })
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels")
    suspend fun clear()
}

@Dao
interface OutboxDao {
    @Query("SELECT * FROM outbox WHERE channel = :channel ORDER BY localId ASC")
    fun observeByChannel(channel: String): Flow<List<OutboxEntity>>

    @Query("SELECT * FROM outbox ORDER BY localId ASC")
    suspend fun pending(): List<OutboxEntity>

    @Insert
    suspend fun insert(entry: OutboxEntity): Long

    @Query("DELETE FROM outbox WHERE localId = :localId")
    suspend fun deleteById(localId: Long)

    @Query("UPDATE outbox SET attempts = attempts + 1 WHERE localId = :localId")
    suspend fun incrementAttempts(localId: Long)

    @Query("DELETE FROM outbox")
    suspend fun clear()
}
