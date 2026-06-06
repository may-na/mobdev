package io.github.mobdev.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [Index("channel"), Index("channel", "id")]
)
data class MessageEntity(
    @PrimaryKey val id: Long,
    val channel: String,
    val sender: String,
    val recipient: String?,
    val kind: String,
    val text: String?,
    val imageLink: String?,
    val time: Long?,
)

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val name: String,
    val ordering: Int,
)

@Entity(
    tableName = "outbox",
    indices = [Index("channel")]
)
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val channel: String,
    val text: String,
    val createdAt: Long,
    val attempts: Int = 0,
)
