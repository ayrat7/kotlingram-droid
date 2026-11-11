package dev.example.tg.data

sealed interface Auth {
    data object Starting : Auth
    data object NeedKeys : Auth
    data object NeedPhone : Auth
    data object NeedCode : Auth
    data object NeedPassword : Auth
    data object Ready : Auth
}

class TdException(msg: String) : Exception(msg)

data class ChatItem(
    val id: Long,
    val title: String,
    val preview: String,
    val unread: Int,
    val time: Int,
    /** id файла аватарки (маленькая) */
    val avatar: Int?,
    val muted: Boolean = false,
    val isPrivate: Boolean = false,
)

/** Вложение сообщения. Везде хранятся только id файлов TDLib — состояние файла берётся из TgRepo.files. */
sealed interface Media {
    class Photo(
        val file: Int,
        val fullFile: Int,
        val width: Int,
        val height: Int,
        val mini: ByteArray?,
    ) : Media

    /** Видео, GIF-анимация (mp4) и кружок (round). */
    class Video(
        val file: Int,
        val thumb: Int?,
        val width: Int,
        val height: Int,
        val duration: Int,
        val mini: ByteArray?,
        val gif: Boolean = false,
        val round: Boolean = false,
    ) : Media

    class Voice(val file: Int, val duration: Int, val waveform: ByteArray, val listened: Boolean) : Media

    class Audio(val file: Int, val title: String, val performer: String, val duration: Int) : Media

    class Doc(val file: Int, val name: String, val mime: String) : Media

    class Sticker(
        val file: Int,
        val thumb: Int?,
        val emoji: String,
        /** true для webp — его умеем рисовать; tgs/webm показываем превью или эмодзи */
        val renderable: Boolean,
    ) : Media
}

data class UiMsg(
    val id: Long,
    val chatId: Long,
    val out: Boolean,
    val from: String,
    val text: String,
    val time: Int,
    val media: Media? = null,
    val pending: Boolean = false,
    val read: Boolean = false,
    val edited: Boolean = false,
    val reply: ReplyPreview? = null,
)

/** Превью сообщения, на которое отвечает данное. */
data class ReplyPreview(val id: Long, val author: String, val text: String)

/** Свой аккаунт. */
data class Profile(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val username: String,
    val phone: String,
    val bio: String,
    val avatarSmall: Int?,
    val avatarBig: Int?,
    val photoId: Long,
) {
    val fullName: String get() = "$firstName $lastName".trim()
}

/** Состояние файла TDLib (скачивание / загрузка). */
data class FileState(
    val id: Int,
    val path: String? = null,
    val size: Long = 0,
    val downloaded: Long = 0,
    val downloading: Boolean = false,
    val uploading: Boolean = false,
    val uploaded: Long = 0,
) {
    val done: Boolean get() = path != null
    val progress: Float
        get() {
            if (size <= 0) return 0f
            val v = if (uploading) uploaded else downloaded
            return (v.toFloat() / size).coerceIn(0f, 1f)
        }
}
