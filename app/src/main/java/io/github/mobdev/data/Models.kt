package io.github.mobdev.data

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
