package dev.example.tg

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface Auth {
    data object Starting : Auth
    data object NeedKeys : Auth
    data object NeedPhone : Auth
    data object NeedCode : Auth
    data object NeedPassword : Auth
    data object Ready : Auth
}

data class ChatItem(val id: Long, val title: String, val preview: String, val unread: Int, val time: Int)
data class UiMsg(val id: Long, val out: Boolean, val from: String, val text: String, val time: Int)

class TdException(msg: String) : Exception(msg)

/** Вся работа с TDLib. Состояние отдаётся наружу через StateFlow. */
class TgRepo(private val app: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val prefs = app.getSharedPreferences("tg", Context.MODE_PRIVATE)

    @Volatile private var client: Client? = null
    @Volatile private var currentChatId = 0L
    @Volatile private var pendingReset = false

    private val chatMap = ConcurrentHashMap<Long, TdApi.Chat>()
    private val userNames = ConcurrentHashMap<Long, String>()

    val auth = MutableStateFlow<Auth>(Auth.Starting)
    val chats = MutableStateFlow<List<ChatItem>>(emptyList())
    val messages = MutableStateFlow<List<UiMsg>>(emptyList())
    val openChatId = MutableStateFlow<Long?>(null)
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)

    // ---------- Запуск и ключи ----------

    fun start() {
        if (client != null) return
        val id = prefs.getInt("api_id", 0)
        val hash = prefs.getString("api_hash", "").orEmpty()
        if (id == 0 || hash.isBlank()) auth.value = Auth.NeedKeys else createClient()
    }

    fun saveKeys(apiId: String, apiHash: String) {
        val id = apiId.trim().toIntOrNull()
        if (id == null || apiHash.isBlank()) {
            error.value = "Введите числовой API ID и непустой API Hash"
            return
        }
        error.value = null
        prefs.edit().putInt("api_id", id).putString("api_hash", apiHash.trim()).apply()
        auth.value = Auth.Starting
        if (client == null) createClient()
    }

    /** Стереть ключи и локальную базу TDLib. */
    fun resetKeys() {
        pendingReset = true
        prefs.edit().clear().apply()
        val c = client
        if (c != null) c.send(TdApi.Close(), null) else {
            File(app.filesDir, "tdlib").deleteRecursively()
            pendingReset = false
            auth.value = Auth.NeedKeys
        }
    }

    fun logout() {
        scope.launch { runCatching { call(TdApi.LogOut()) }.onFailure { error.value = it.message } }
    }

    private fun createClient() {
        client = Client.create(Client.ResultHandler { onUpdate(it) }, null, null)
    }

    // ---------- Авторизация ----------

    fun submitPhone(phone: String) = authCall(TdApi.SetAuthenticationPhoneNumber().apply { phoneNumber = phone.trim() })
    fun submitCode(value: String) = authCall(TdApi.CheckAuthenticationCode().apply { code = value.trim() })
    fun submitPassword(value: String) = authCall(TdApi.CheckAuthenticationPassword().apply { password = value })

    private fun authCall(f: TdApi.Function<*>) {
        scope.launch {
            busy.value = true
            try {
                call(f)
                error.value = null
            } catch (e: Exception) {
                error.value = e.message
            } finally {
                busy.value = false
            }
        }
    }

    private fun handleAuth(s: TdApi.AuthorizationState) {
        when (s) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                val p = TdApi.SetTdlibParameters().apply {
                    databaseDirectory = File(app.filesDir, "tdlib").absolutePath
                    filesDirectory = File(app.filesDir, "tdlib_files").absolutePath
                    databaseEncryptionKey = ByteArray(0)
                    useFileDatabase = true
                    useChatInfoDatabase = true
                    useMessageDatabase = true
                    useSecretChats = false
                    apiId = prefs.getInt("api_id", 0)
                    apiHash = prefs.getString("api_hash", "").orEmpty()
                    systemLanguageCode = Locale.getDefault().language
                    deviceModel = Build.MODEL
                    systemVersion = Build.VERSION.RELEASE
                    applicationVersion = "1.0"
                }
                client?.send(p) { r -> if (r is TdApi.Error) error.value = "Ошибка параметров TDLib: ${r.message}" }
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> auth.value = Auth.NeedPhone
            is TdApi.AuthorizationStateWaitCode -> auth.value = Auth.NeedCode
            is TdApi.AuthorizationStateWaitPassword -> auth.value = Auth.NeedPassword
            is TdApi.AuthorizationStateReady -> {
                auth.value = Auth.Ready
                loadChats()
            }
            is TdApi.AuthorizationStateClosed -> {
                client = null
                chatMap.clear()
                chats.value = emptyList()
                messages.value = emptyList()
                openChatId.value = null
                currentChatId = 0
                if (pendingReset) {
                    pendingReset = false
                    File(app.filesDir, "tdlib").deleteRecursively()
                    auth.value = Auth.NeedKeys
                } else {
                    auth.value = Auth.Starting
                    createClient()
                }
            }
            else -> error.value = "Этот способ входа пока не поддерживается (${s.javaClass.simpleName})"
        }
    }

    // ---------- Чаты ----------

    private fun loadChats() {
        scope.launch {
            runCatching {
                call(TdApi.LoadChats().apply { chatList = TdApi.ChatListMain(); limit = 100 })
            } // 404 = всё уже загружено, это нормально
            runCatching {
                call(TdApi.GetChats().apply { chatList = TdApi.ChatListMain(); limit = 100 })
            }
            publishChats()
        }
    }

    private fun mainOrder(c: TdApi.Chat): Long =
        c.positions?.firstOrNull { it.list is TdApi.ChatListMain }?.order ?: 0L

    private fun publishChats() {
        chats.value = chatMap.values
            .filter { mainOrder(it) > 0 }
            .sortedByDescending { mainOrder(it) }
            .map {
                ChatItem(
                    id = it.id,
                    title = it.title.ifBlank { "Без названия" },
                    preview = it.lastMessage?.let { m -> describe(m.content) } ?: "",
                    unread = it.unreadCount,
                    time = it.lastMessage?.date ?: 0,
                )
            }
    }

    // ---------- Переписка ----------

    fun open(id: Long) {
        currentChatId = id
        openChatId.value = id
        messages.value = emptyList()
        scope.launch {
            try {
                call(TdApi.OpenChat().apply { chatId = id })
                val raw = mutableListOf<TdApi.Message>()
                var from = 0L
                var attempts = 0
                // TDLib на первом запросе часто отдаёт лишь то, что есть локально — дозапрашиваем
                while (raw.size < 40 && attempts++ < 3) {
                    val r = call(TdApi.GetChatHistory().apply {
                        chatId = id; fromMessageId = from; offset = 0; limit = 50 - raw.size; onlyLocal = false
                    }) as TdApi.Messages
                    if (r.messages.isEmpty()) break
                    raw += r.messages
                    from = r.messages.last().id
                }
                val ui = raw.reversed().map { toUi(it) }
                if (currentChatId == id) {
                    messages.value = ui
                    markRead(id, raw.map { it.id }.toLongArray())
                }
            } catch (e: Exception) {
                error.value = e.message
            }
        }
    }

    fun close() {
        val id = currentChatId
        currentChatId = 0
        openChatId.value = null
        messages.value = emptyList()
        if (id != 0L) scope.launch { runCatching { call(TdApi.CloseChat().apply { chatId = id }) } }
    }

    fun send(msg: String) {
        val id = currentChatId
        if (id == 0L || msg.isBlank()) return
        val ft = TdApi.FormattedText().apply { this.text = msg; this.entities = emptyArray() }
        val content = TdApi.InputMessageText().apply { this.text = ft }
        val req = TdApi.SendMessage().apply { this.chatId = id; this.inputMessageContent = content }
        scope.launch { runCatching { call(req) }.onFailure { error.value = "Не отправлено: ${it.message}" } }
    }

    private suspend fun markRead(id: Long, ids: LongArray) {
        if (ids.isEmpty()) return
        runCatching {
            call(TdApi.ViewMessages().apply { chatId = id; messageIds = ids; forceRead = true })
        }
    }

    // ---------- Обновления от TDLib (приходят в потоке TDLib, не блокировать!) ----------

    private fun onUpdate(u: TdApi.Object) {
        when (u) {
            is TdApi.UpdateAuthorizationState -> handleAuth(u.authorizationState)
            is TdApi.UpdateNewChat -> { chatMap[u.chat.id] = u.chat; publishChats() }
            is TdApi.UpdateChatTitle -> chatMap[u.chatId]?.let { it.title = u.title; publishChats() }
            is TdApi.UpdateChatLastMessage -> chatMap[u.chatId]?.let {
                it.lastMessage = u.lastMessage
                it.positions = u.positions
                publishChats()
            }
            is TdApi.UpdateChatPosition -> chatMap[u.chatId]?.let { c ->
                c.positions = (c.positions.filter { it.list::class != u.position.list::class } + u.position).toTypedArray()
                publishChats()
            }
            is TdApi.UpdateChatReadInbox -> chatMap[u.chatId]?.let { it.unreadCount = u.unreadCount; publishChats() }
            is TdApi.UpdateNewMessage -> if (u.message.chatId == currentChatId) {
                scope.launch {
                    val ui = toUi(u.message)
                    messages.update { l -> if (l.any { it.id == ui.id }) l else l + ui }
                    if (!u.message.isOutgoing) markRead(u.message.chatId, longArrayOf(u.message.id))
                }
            }
            is TdApi.UpdateMessageSendSucceeded -> if (u.message.chatId == currentChatId) {
                scope.launch {
                    val ui = toUi(u.message)
                    messages.update { l -> l.map { if (it.id == u.oldMessageId) ui else it } }
                }
            }
            is TdApi.UpdateMessageContent -> if (u.chatId == currentChatId) {
                val t = describe(u.newContent)
                messages.update { l -> l.map { if (it.id == u.messageId) it.copy(text = t) else it } }
            }
            else -> {}
        }
    }

    // ---------- Хелперы ----------

    private suspend fun call(f: TdApi.Function<*>): TdApi.Object = suspendCancellableCoroutine { cont ->
        val c = client
        if (c == null) {
            cont.resumeWithException(TdException("Клиент не запущен"))
            return@suspendCancellableCoroutine
        }
        c.send(f) { r ->
            if (r is TdApi.Error) cont.resumeWithException(TdException("${r.code}: ${r.message}"))
            else cont.resume(r)
        }
    }

    private suspend fun toUi(m: TdApi.Message): UiMsg =
        UiMsg(m.id, m.isOutgoing, if (m.isOutgoing) "" else senderName(m), describe(m.content), m.date)

    private suspend fun senderName(m: TdApi.Message): String {
        if (chatMap[m.chatId]?.type is TdApi.ChatTypePrivate) return ""
        return when (val s = m.senderId) {
            is TdApi.MessageSenderUser -> userNames[s.userId] ?: try {
                val user = call(TdApi.GetUser().apply { userId = s.userId }) as TdApi.User
                "${user.firstName} ${user.lastName}".trim().also { userNames[s.userId] = it }
            } catch (e: Exception) {
                ""
            }
            is TdApi.MessageSenderChat -> chatMap[s.chatId]?.title ?: ""
            else -> ""
        }
    }

    private fun cap(t: String) = if (t.isBlank()) "" else ": $t"

    private fun describe(c: TdApi.MessageContent): String = when (c) {
        is TdApi.MessageText -> c.text.text
        is TdApi.MessagePhoto -> "📷 Фото" + cap(c.caption.text)
        is TdApi.MessageVideo -> "🎬 Видео" + cap(c.caption.text)
        is TdApi.MessageAnimation -> "GIF"
        is TdApi.MessageVoiceNote -> "🎤 Голосовое сообщение"
        is TdApi.MessageVideoNote -> "⏺ Видеосообщение"
        is TdApi.MessageAudio -> "🎵 " + c.audio.title.ifBlank { "Аудио" }
        is TdApi.MessageDocument -> "📄 " + c.document.fileName
        is TdApi.MessageSticker -> c.sticker.emoji + " Стикер"
        is TdApi.MessagePoll -> "📊 Опрос"
        is TdApi.MessageLocation -> "📍 Геопозиция"
        is TdApi.MessageContact -> "👤 Контакт"
        else -> "[Вложение]"
    }
}
