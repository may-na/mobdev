package io.github.mobdev.data

import io.github.mobdev.data.db.MessageEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequestDto(val name: String, val pwd: String)

@Serializable
data class MessageDto(
    val id: Long,
    val from: String,
    val to: String? = null,
    val data: MessageDataDto,
    val time: Long? = null,
)

@Serializable
data class MessageDataDto(
    @SerialName("Text") val text: TextDataDto? = null,
    @SerialName("Image") val image: ImageDataDto? = null,
)

@Serializable
data class TextDataDto(val text: String)

@Serializable
data class ImageDataDto(val link: String? = null)

@Serializable
data class SendMessageDto(
    val from: String,
    val to: String,
    val data: MessageDataDto,
)

data class Message(
    val id: Long,
    val from: String,
    val to: String?,
    val content: MessageContent,
    val time: Long?,
)

sealed interface MessageContent {
    data class Text(val text: String) : MessageContent
    data class Image(val link: String) : MessageContent
    data object Unknown : MessageContent
}

fun MessageDto.toDomain(): Message {
    val content = when {
        data.text != null -> MessageContent.Text(data.text.text)
        data.image != null -> MessageContent.Image(data.image.link.orEmpty())
        else -> MessageContent.Unknown
    }
    return Message(
        id = id,
        from = from,
        to = to,
        content = content,
        time = time,
    )
}

private const val KIND_TEXT = "text"
private const val KIND_IMAGE = "image"
private const val KIND_UNKNOWN = "unknown"

fun MessageDto.toEntity(channel: String): MessageEntity {
    val (kind, text, link) = when {
        data.text != null -> Triple(KIND_TEXT, data.text.text, null)
        data.image != null -> Triple(KIND_IMAGE, null, data.image.link.orEmpty())
        else -> Triple(KIND_UNKNOWN, null, null)
    }
    return MessageEntity(
        id = id,
        channel = channel,
        sender = from,
        recipient = to,
        kind = kind,
        text = text,
        imageLink = link,
        time = time,
    )
}

fun MessageEntity.toDomain(): Message {
    val content: MessageContent = when (kind) {
        KIND_TEXT -> MessageContent.Text(text.orEmpty())
        KIND_IMAGE -> MessageContent.Image(imageLink.orEmpty())
        else -> MessageContent.Unknown
    }
    return Message(
        id = id,
        from = sender,
        to = recipient,
        content = content,
        time = time,
    )
}

sealed interface ChatItem {
    val sortKey: Long

    data class Server(val message: Message) : ChatItem {
        override val sortKey: Long get() = message.id
    }

    data class Pending(
        val localId: Long,
        val from: String,
        val text: String,
        val createdAt: Long,
    ) : ChatItem {
        override val sortKey: Long get() = Long.MAX_VALUE - 1_000_000L + localId
    }
}
