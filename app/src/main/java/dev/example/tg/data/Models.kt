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
    val pinned: Boolean = false,
    /** id собеседника в личном чате, иначе 0 */
    val userId: Long = 0L,
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

    class Audio(
        val file: Int,
        val title: String,
        val performer: String,
        val duration: Int,
        val cover: Int? = null,
    ) : Media

    class Doc(val file: Int, val name: String, val mime: String) : Media

    class Sticker(
        val file: Int,
        val thumb: Int?,
        val emoji: String,
        /** true для webp — его умеем рисовать; tgs/webm показываем превью или эмодзи */
        val renderable: Boolean,
        /** tgs (gzip-Lottie) — рисуем через Lottie */
        val animated: Boolean = false,
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
    /** id отправителя-пользователя (для открытия профиля), 0 если неизвестно */
    val senderId: Long = 0L,
    /** Клавиатура бота (inline-кнопки под сообщением или клавиатура ответа). */
    val markup: Markup? = null,
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

enum class PresenceKind { Online, Offline, Recently, LastWeek, LastMonth, Long, Bot, Unknown }

/** «Был(а) в сети» пользователя. lastSeen — unix-время, только для Offline. */
data class Presence(val kind: PresenceKind, val lastSeen: Int = 0)

/** Чужой профиль. */
data class UserCard(
    val id: Long,
    val name: String,
    val username: String,
    val phone: String,
    val bio: String,
    val presence: Presence,
    val avatarSmall: Int?,
    val avatarBig: Int?,
    val isContact: Boolean,
    val isBot: Boolean,
    val premium: Boolean,
    val chatId: Long,
)

/** Подсказки для шага ввода кода. */
data class CodeInfo(
    val phone: String,
    val hint: String,
    val length: Int,
    val canResend: Boolean,
    val timeout: Int,
    val stamp: Long,
)


// ---------- Боты ----------

sealed interface BtnAction {
    data class Url(val url: String) : BtnAction
    class Callback(val data: ByteArray) : BtnAction
    data class SwitchInline(val query: String) : BtnAction
    data class WebApp(val url: String) : BtnAction
    data class Copy(val text: String) : BtnAction

    /** Кнопка клавиатуры ответа: просто отправить её текст. */
    data object SendText : BtnAction
    data object RequestPhone : BtnAction
    data object Unsupported : BtnAction
}

data class Btn(val text: String, val action: BtnAction)

sealed interface Markup {
    data class Inline(val rows: List<List<Btn>>) : Markup
    data class Keyboard(val rows: List<List<Btn>>, val oneTime: Boolean, val placeholder: String) : Markup
    data object Remove : Markup
}

data class BotCommandUi(val command: String, val description: String)

/** Бот, с которым открыт личный чат. */
data class BotUi(
    val userId: Long,
    val username: String,
    val description: String,
    val commands: List<BotCommandUi>,
    val menuText: String,
    val menuUrl: String,
)

data class InlineResultUi(val id: String, val title: String, val subtitle: String)
data class InlineResults(val queryId: Long, val query: String, val items: List<InlineResultUi>)
data class WebAppSession(val title: String, val url: String, val launchId: Long)

// ---------- Музыка ----------

data class Track(
    val fileId: Int,
    val title: String,
    val performer: String,
    val durationSec: Int,
    val cover: Int? = null,
)

// ---------- Подарки ----------

data class GiftUi(
    val key: String,
    val title: String,
    /** id файла стикера подарка */
    val sticker: Int?,
    /** tgs — Lottie, иначе картинка */
    val animated: Boolean,
    val stars: Long,
    val upgraded: Boolean,
    val number: Int,
    val total: Int,
    /** цвета фона для улучшенных подарков (RGB), 0 — нет */
    val bgCenter: Int,
    val bgEdge: Int,
    val fromId: Long,
    val fromName: String,
    val text: String,
    val date: Int,
)

// ---------- Звонки ----------

enum class CallPhase { Ringing, Calling, Connecting, Active, Ended }

data class UiCall(
    val id: Int,
    val userId: Long,
    val name: String,
    val avatar: Int?,
    val outgoing: Boolean,
    val phase: CallPhase,
    val emojis: List<String> = emptyList(),
    val startedAt: Long = 0L,
    val note: String = "",
)
