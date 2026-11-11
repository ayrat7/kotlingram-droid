package dev.example.tg.data

import android.content.Context
import android.net.Uri
import android.os.Build
import dev.example.tg.media.Attachments
import dev.example.tg.media.Prepared
import dev.example.tg.notify.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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

/** Вся работа с TDLib. Состояние отдаётся наружу через StateFlow. */
class TgRepo(private val app: Context, val settings: AppSettings) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val prefs = app.getSharedPreferences("tg", Context.MODE_PRIVATE)

    @Volatile private var client: Client? = null
    @Volatile private var currentChatId = 0L
    @Volatile private var pendingReset = false
    @Volatile private var loadingOlder = false
    @Volatile private var reachedStart = false
    @Volatile private var emptyOlderTries = 0
    @Volatile private var appForeground = false
    @Volatile private var myId = 0L

    val notifier = Notifier(app)
    private val scopeMute = ConcurrentHashMap<String, Int>()
    private val replyCache = ConcurrentHashMap<String, ReplyPreview>()

    private val chatMap = ConcurrentHashMap<Long, TdApi.Chat>()
    private val userNames = ConcurrentHashMap<Long, String>()

    val auth = MutableStateFlow<Auth>(Auth.Starting)
    val chats = MutableStateFlow<List<ChatItem>>(emptyList())
    /** От старых к новым. */
    val messages = MutableStateFlow<List<UiMsg>>(emptyList())
    val openChatId = MutableStateFlow<Long?>(null)
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    /** Идёт подготовка вложений к отправке (копирование, сжатие). */
    val preparing = MutableStateFlow(false)
    val files = MutableStateFlow<Map<Int, FileState>>(emptyMap())
    val me = MutableStateFlow<Profile?>(null)
    val profileBusy = MutableStateFlow(false)
    /** Подпись состояния соединения («Подключение…») или null, когда всё в порядке. */
    val connection = MutableStateFlow<String?>(null)
    /** Нейтральные сообщения для снэкбара (не ошибки). */
    val info = MutableStateFlow<String?>(null)
    /** Чат, который нужно открыть (из уведомления). */
    val pendingOpen = MutableStateFlow<Long?>(null)

    // ---------- Запуск и ключи ----------

    fun isLoggedIn(): Boolean = prefs.getBoolean("logged_in", false)

    /** Приложение на экране или в фоне: влияет на уведомления и статус «в сети». */
    fun setForeground(fg: Boolean) {
        appForeground = fg
        if (auth.value == Auth.Ready) sendOnline(fg)
        if (fg) {
            val cid = currentChatId
            if (cid != 0L) {
                val ids = messages.value.filter { !it.out }.takeLast(20).map { it.id }.toLongArray()
                scope.launch { markRead(cid, ids) }
                notifier.cancel(cid)
            }
        }
    }

    private fun sendOnline(on: Boolean) {
        client?.send(TdApi.SetOption().apply { name = "online"; value = TdApi.OptionValueBoolean(on) }, null)
    }

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
            File(app.filesDir, "tdlib_files").deleteRecursively()
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
                    applicationVersion = "2.0"
                }
                client?.send(p) { r -> if (r is TdApi.Error) error.value = "Ошибка параметров TDLib: ${r.message}" }
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                prefs.edit().putBoolean("logged_in", false).apply()
                me.value = null
                notifier.cancelAll()
                auth.value = Auth.NeedPhone
            }
            is TdApi.AuthorizationStateWaitCode -> auth.value = Auth.NeedCode
            is TdApi.AuthorizationStateWaitPassword -> auth.value = Auth.NeedPassword
            is TdApi.AuthorizationStateReady -> {
                prefs.edit().putBoolean("logged_in", true).apply()
                auth.value = Auth.Ready
                sendOnline(appForeground)
                loadMe()
                loadScopeSettings()
                loadChats()
            }
            is TdApi.AuthorizationStateClosed -> {
                client = null
                chatMap.clear()
                chats.value = emptyList()
                messages.value = emptyList()
                files.value = emptyMap()
                openChatId.value = null
                currentChatId = 0
                if (pendingReset) {
                    pendingReset = false
                    File(app.filesDir, "tdlib").deleteRecursively()
                    File(app.filesDir, "tdlib_files").deleteRecursively()
                    auth.value = Auth.NeedKeys
                } else {
                    auth.value = Auth.Starting
                    createClient()
                }
            }
            else -> error.value = "Этот способ входа пока не поддерживается (${s.javaClass.simpleName})"
        }
    }

    // ---------- Файлы ----------

    fun fileFlow(id: Int): Flow<FileState?> = files.map { it[id] }.distinctUntilChanged()

    private fun registerFile(f: TdApi.File) {
        val l = f.local
        val r = f.remote
        val st = FileState(
            id = f.id,
            path = if (l.isDownloadingCompleted && l.path.isNotEmpty()) l.path else null,
            size = maxOf(f.size.toLong(), f.expectedSize.toLong()),
            downloaded = l.downloadedSize.toLong(),
            downloading = l.isDownloadingActive,
            uploading = r.isUploadingActive,
            uploaded = r.uploadedSize.toLong(),
        )
        files.update { m -> if (m[st.id] == st) m else m + (st.id to st) }
    }

    private fun reg(f: TdApi.File?) {
        if (f != null) registerFile(f)
    }

    /** Запускает скачивание, если файл ещё не скачан и не качается. */
    fun download(id: Int) {
        val st = files.value[id]
        if (st != null && (st.path != null || st.downloading)) return
        val req = TdApi.DownloadFile().apply {
            fileId = id
            priority = 16
            offset = 0
            limit = 0
            synchronous = false
        }
        client?.send(req, null)
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
            .map { c ->
                ChatItem(
                    id = c.id,
                    title = c.title.ifBlank { "Без названия" },
                    preview = c.lastMessage?.let { m -> (if (m.isOutgoing) "Вы: " else "") + describe(m.content) } ?: "",
                    unread = c.unreadCount,
                    time = c.lastMessage?.date ?: 0,
                    avatar = c.photo?.small?.id,
                    muted = isMuted(c),
                    isPrivate = c.type is TdApi.ChatTypePrivate,
                )
            }
    }

    // ---------- Переписка ----------

    fun open(id: Long) {
        currentChatId = id
        openChatId.value = id
        messages.value = emptyList()
        reachedStart = false
        emptyOlderTries = 0
        notifier.cancel(id)
        scope.launch {
            try {
                call(TdApi.OpenChat().apply { chatId = id })
                val raw = fetchHistory(id, 0L, 40)
                val ui = raw.sortedBy { it.id }.map { toUi(it) }
                if (currentChatId == id) {
                    messages.value = ui
                    markRead(id, raw.map { it.id }.toLongArray())
                }
            } catch (e: Exception) {
                error.value = e.message
            }
        }
    }

    /** TDLib на первом запросе часто отдаёт лишь локальное — дозапрашиваем. Сообщение from включается в ответ. */
    private suspend fun fetchHistory(cid: Long, fromId: Long, want: Int): List<TdApi.Message> {
        val out = LinkedHashMap<Long, TdApi.Message>()
        var from = fromId
        var attempts = 0
        while (out.size < want && attempts++ < 3) {
            val r = call(TdApi.GetChatHistory().apply {
                chatId = cid
                fromMessageId = from
                offset = 0
                limit = want + 10 - out.size
                onlyLocal = false
            }) as TdApi.Messages
            if (r.messages.isEmpty()) break
            val before = out.size
            r.messages.forEach { out.putIfAbsent(it.id, it) }
            if (out.size == before) break
            from = r.messages.last().id
        }
        return out.values.toList()
    }

    /** Подгрузка более старых сообщений при прокрутке вверх. */
    fun loadOlder() {
        val cid = currentChatId
        if (cid == 0L || loadingOlder || reachedStart) return
        val oldest = messages.value.firstOrNull()?.id ?: return
        loadingOlder = true
        scope.launch {
            try {
                val raw = fetchHistory(cid, oldest, 30).filter { it.id < oldest }
                if (raw.isEmpty()) {
                    if (++emptyOlderTries >= 2) reachedStart = true
                } else {
                    emptyOlderTries = 0
                    val ui = raw.sortedBy { it.id }.map { toUi(it) }
                    if (currentChatId == cid) messages.update { l -> (ui + l).distinctBy { it.id } }
                }
            } catch (e: Exception) {
                error.value = e.message
            } finally {
                loadingOlder = false
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

    private suspend fun markRead(cid: Long, ids: LongArray) {
        if (ids.isEmpty()) return
        runCatching {
            call(TdApi.ViewMessages().apply { chatId = cid; messageIds = ids; forceRead = true })
        }
    }

    /** Помечает голосовое/кружок как прослушанное. */
    fun markOpened(cid: Long, messageId: Long) {
        scope.launch {
            runCatching { call(TdApi.OpenMessageContent().apply { chatId = cid; this.messageId = messageId }) }
        }
    }

    // ---------- Отправка ----------

    private fun fmtText(t: String = ""): TdApi.FormattedText =
        TdApi.FormattedText().apply { text = t; entities = emptyArray() }

    private fun local(p: String): TdApi.InputFileLocal = TdApi.InputFileLocal().apply { path = p }

    private fun sendContent(cid: Long, content: TdApi.InputMessageContent, replyId: Long = 0L) {
        val req = TdApi.SendMessage().apply {
            chatId = cid
            inputMessageContent = content
            if (replyId != 0L) replyTo = TdApi.InputMessageReplyToMessage().apply { messageId = replyId }
        }
        scope.launch { runCatching { call(req) }.onFailure { error.value = "Не отправлено: ${it.message}" } }
    }

    fun send(msg: String, replyId: Long = 0L) {
        val cid = currentChatId
        if (cid == 0L || msg.isBlank()) return
        sendContent(cid, TdApi.InputMessageText().apply { text = fmtText(msg) }, replyId)
    }

    fun sendVoice(path: String, durationSec: Int, waveform: ByteArray, replyId: Long = 0L) {
        val cid = currentChatId
        if (cid == 0L) return
        sendContent(cid, TdApi.InputMessageVoiceNote().apply {
            voiceNote = local(path)
            duration = durationSec
            this.waveform = waveform
            caption = fmtText()
        }, replyId)
    }

    /** Фото/видео из галереи (asFile = false) или любые файлы как документы (asFile = true). */
    fun sendUris(uris: List<Uri>, asFile: Boolean) {
        val cid = currentChatId
        if (cid == 0L || uris.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            preparing.value = true
            try {
                for (u in uris) {
                    try {
                        when (val p = Attachments.prepare(app, u, asFile)) {
                            is Prepared.Photo -> sendContent(cid, TdApi.InputMessagePhoto().apply {
                                photo = local(p.path)
                                thumbnail = null
                                addedStickerFileIds = IntArray(0)
                                width = p.width
                                height = p.height
                                caption = fmtText()
                            })
                            is Prepared.Video -> sendContent(cid, TdApi.InputMessageVideo().apply {
                                video = local(p.path)
                                thumbnail = null
                                addedStickerFileIds = IntArray(0)
                                duration = p.durationSec
                                width = p.width
                                height = p.height
                                supportsStreaming = true
                                caption = fmtText()
                            })
                            is Prepared.Doc -> sendContent(cid, TdApi.InputMessageDocument().apply {
                                document = local(p.path)
                                thumbnail = null
                                disableContentTypeDetection = false
                                caption = fmtText()
                            })
                        }
                    } catch (e: Exception) {
                        error.value = "Не удалось подготовить файл: ${e.message}"
                    }
                }
            } finally {
                preparing.value = false
            }
        }
    }

    // ---------- Обновления от TDLib (приходят в потоке TDLib, не блокировать!) ----------

    private fun onUpdate(u: TdApi.Object) {
        when (u) {
            is TdApi.UpdateAuthorizationState -> handleAuth(u.authorizationState)
            is TdApi.UpdateFile -> registerFile(u.file)
            is TdApi.UpdateNewChat -> {
                chatMap[u.chat.id] = u.chat
                reg(u.chat.photo?.small)
                publishChats()
            }
            is TdApi.UpdateChatTitle -> chatMap[u.chatId]?.let { it.title = u.title; publishChats() }
            is TdApi.UpdateChatPhoto -> chatMap[u.chatId]?.let {
                it.photo = u.photo
                reg(u.photo?.small)
                publishChats()
            }
            is TdApi.UpdateChatLastMessage -> chatMap[u.chatId]?.let {
                it.lastMessage = u.lastMessage
                it.positions = u.positions
                publishChats()
            }
            is TdApi.UpdateChatPosition -> chatMap[u.chatId]?.let { c ->
                c.positions = (c.positions.filter { it.list::class != u.position.list::class } + u.position).toTypedArray()
                publishChats()
            }
            is TdApi.UpdateChatReadInbox -> chatMap[u.chatId]?.let {
                it.unreadCount = u.unreadCount
                if (u.unreadCount == 0) notifier.cancel(u.chatId)
                publishChats()
            }
            is TdApi.UpdateChatReadOutbox -> {
                chatMap[u.chatId]?.lastReadOutboxMessageId = u.lastReadOutboxMessageId
                if (u.chatId == currentChatId) {
                    messages.update { l ->
                        l.map { if (it.out && !it.read && it.id <= u.lastReadOutboxMessageId) it.copy(read = true) else it }
                    }
                }
            }
            is TdApi.UpdateNewMessage -> {
                val msg = u.message
                val isCurrent = msg.chatId == currentChatId
                if (isCurrent) {
                    scope.launch {
                        val ui = toUi(msg)
                        messages.update { l -> if (l.any { it.id == ui.id }) l else (l + ui).sortedBy { it.id } }
                        if (!msg.isOutgoing && appForeground) markRead(msg.chatId, longArrayOf(msg.id))
                    }
                }
                if (!msg.isOutgoing && !(isCurrent && appForeground)) maybeNotify(msg)
            }
            is TdApi.UpdateConnectionState -> connection.value = when (u.state) {
                is TdApi.ConnectionStateReady -> null
                is TdApi.ConnectionStateWaitingForNetwork -> "Ожидание сети…"
                is TdApi.ConnectionStateConnectingToProxy -> "Подключение к прокси…"
                is TdApi.ConnectionStateUpdating -> "Обновление…"
                else -> "Подключение…"
            }
            is TdApi.UpdateUser -> if (u.user.id == myId) {
                me.value = toProfile(u.user, me.value?.bio.orEmpty())
            }
            is TdApi.UpdateUserFullInfo -> if (u.userId == myId) {
                me.value = me.value?.copy(bio = u.userFullInfo.bio?.text.orEmpty())
            }
            is TdApi.UpdateChatNotificationSettings -> chatMap[u.chatId]?.let {
                it.notificationSettings = u.notificationSettings
                publishChats()
            }
            is TdApi.UpdateScopeNotificationSettings -> {
                scopeMute[scopeName(u.scope)] = u.notificationSettings.muteFor
                publishChats()
            }
            is TdApi.UpdateMessageEdited -> if (u.chatId == currentChatId) {
                messages.update { l -> l.map { if (it.id == u.messageId) it.copy(edited = u.editDate > 0) else it } }
            }
            is TdApi.UpdateMessageSendSucceeded -> if (u.message.chatId == currentChatId) {
                scope.launch {
                    val ui = toUi(u.message)
                    messages.update { l ->
                        (l.filter { it.id != u.oldMessageId && it.id != ui.id } + ui).sortedBy { it.id }
                    }
                }
            }
            is TdApi.UpdateMessageSendFailed -> if (u.message.chatId == currentChatId) {
                messages.update { l -> l.filter { it.id != u.oldMessageId } }
                error.value = "Не удалось отправить сообщение"
            }
            is TdApi.UpdateMessageContent -> if (u.chatId == currentChatId) {
                val (t, m) = parse(u.newContent)
                messages.update { l -> l.map { if (it.id == u.messageId) it.copy(text = t, media = m) else it } }
            }
            is TdApi.UpdateDeleteMessages -> if (u.chatId == currentChatId && u.isPermanent) {
                val gone = u.messageIds.toSet()
                messages.update { l -> l.filter { it.id !in gone } }
            }
            else -> {}
        }
    }

    // ---------- Действия над сообщениями ----------

    fun edit(m: UiMsg, newText: String) {
        if (newText.isBlank() || newText == m.text) return
        val req: TdApi.Function<*> = if (m.media == null) {
            TdApi.EditMessageText().apply {
                chatId = m.chatId
                messageId = m.id
                inputMessageContent = TdApi.InputMessageText().apply { text = fmtText(newText) }
            }
        } else {
            TdApi.EditMessageCaption().apply {
                chatId = m.chatId
                messageId = m.id
                caption = fmtText(newText)
            }
        }
        scope.launch { runCatching { call(req) }.onFailure { error.value = "Не удалось изменить: ${it.message}" } }
    }

    fun delete(m: UiMsg, forAll: Boolean) {
        val req = TdApi.DeleteMessages().apply {
            chatId = m.chatId
            messageIds = longArrayOf(m.id)
            revoke = forAll
        }
        scope.launch {
            runCatching { call(req) }.onFailure { error.value = "Не удалось удалить: ${it.message}" }
            // pending/локальные сообщения могут не прислать UpdateDeleteMessages
            messages.update { l -> l.filter { it.id != m.id } }
        }
    }

    fun forward(fromChat: Long, messageId: Long, toChat: Long) {
        val req = TdApi.ForwardMessages().apply {
            chatId = toChat
            this.fromChatId = fromChat
            messageIds = longArrayOf(messageId)
            options = null
            sendCopy = false
            removeCaption = false
        }
        scope.launch {
            runCatching { call(req) }
                .onSuccess { info.value = "Сообщение переслано" }
                .onFailure { error.value = "Не удалось переслать: ${it.message}" }
        }
    }

    // ---------- Уведомления ----------

    private fun scopeName(s: TdApi.NotificationSettingsScope): String = when (s) {
        is TdApi.NotificationSettingsScopeGroupChats -> "group"
        is TdApi.NotificationSettingsScopeChannelChats -> "channel"
        else -> "private"
    }

    private fun scopeOf(c: TdApi.Chat): String = when (val t = c.type) {
        is TdApi.ChatTypeBasicGroup -> "group"
        is TdApi.ChatTypeSupergroup -> if (t.isChannel) "channel" else "group"
        else -> "private"
    }

    private fun isMuted(c: TdApi.Chat): Boolean {
        val s = c.notificationSettings ?: return false
        val mute = if (s.useDefaultMuteFor) scopeMute[scopeOf(c)] ?: 0 else s.muteFor
        return mute > 0
    }

    private fun loadScopeSettings() {
        scope.launch {
            val scopes = listOf(
                "private" to TdApi.NotificationSettingsScopePrivateChats(),
                "group" to TdApi.NotificationSettingsScopeGroupChats(),
                "channel" to TdApi.NotificationSettingsScopeChannelChats(),
            )
            for ((name, sc) in scopes) {
                runCatching {
                    val r = call(TdApi.GetScopeNotificationSettings().apply { scope = sc }) as TdApi.ScopeNotificationSettings
                    scopeMute[name] = r.muteFor
                }
            }
            publishChats()
        }
    }

    fun setMuted(cid: Long, mute: Boolean) {
        val s = chatMap[cid]?.notificationSettings ?: return
        s.useDefaultMuteFor = false
        s.muteFor = if (mute) 367 * 24 * 3600 else 0
        client?.send(TdApi.SetChatNotificationSettings().apply { chatId = cid; notificationSettings = s }, null)
        publishChats()
    }

    private fun isNotifiable(c: TdApi.MessageContent) =
        c is TdApi.MessageText || c is TdApi.MessagePhoto || c is TdApi.MessageVideo ||
            c is TdApi.MessageAnimation || c is TdApi.MessageVoiceNote || c is TdApi.MessageVideoNote ||
            c is TdApi.MessageAudio || c is TdApi.MessageDocument || c is TdApi.MessageSticker ||
            c is TdApi.MessagePoll || c is TdApi.MessageLocation || c is TdApi.MessageContact

    private fun maybeNotify(m: TdApi.Message) {
        if (!settings.notifications.value || !isNotifiable(m.content)) return
        val chat = chatMap[m.chatId] ?: return
        if (isMuted(chat)) return
        scope.launch {
            val group = chat.type !is TdApi.ChatTypePrivate && chat.type !is TdApi.ChatTypeSecret
            val sender = if (group) senderName(m).ifBlank { null } else null
            val body = if (settings.preview.value) describe(m.content) else "Новое сообщение"
            val avatar = chat.photo?.small?.let { f -> files.value[f.id]?.path }
            notifier.incoming(m.chatId, chat.title.ifBlank { "Telegram" }, group, sender, body, m.id, avatar)
        }
    }

    /** Ответ прямо из уведомления. */
    fun replyFromNotification(cid: Long, lastMessageId: Long, text: String, done: () -> Unit) {
        scope.launch {
            try {
                call(TdApi.SendMessage().apply {
                    chatId = cid
                    inputMessageContent = TdApi.InputMessageText().apply { this.text = fmtText(text) }
                })
                notifier.ownReply(cid, text)
                if (lastMessageId != 0L) markRead(cid, longArrayOf(lastMessageId))
            } catch (e: Exception) {
                error.value = "Не отправлено: ${e.message}"
            } finally {
                done()
            }
        }
    }

    /** «Прочитано» из уведомления. */
    fun markChatRead(cid: Long, messageId: Long, done: () -> Unit) {
        scope.launch {
            try {
                if (messageId != 0L) markRead(cid, longArrayOf(messageId))
                notifier.cancel(cid)
            } finally {
                done()
            }
        }
    }

    // ---------- Профиль ----------

    private fun toProfile(u: TdApi.User, bio: String): Profile {
        reg(u.profilePhoto?.small)
        reg(u.profilePhoto?.big)
        return Profile(
            id = u.id,
            firstName = u.firstName.orEmpty(),
            lastName = u.lastName.orEmpty(),
            username = u.usernames?.editableUsername.orEmpty(),
            phone = u.phoneNumber.orEmpty(),
            bio = bio,
            avatarSmall = u.profilePhoto?.small?.id,
            avatarBig = u.profilePhoto?.big?.id,
            photoId = u.profilePhoto?.id ?: 0L,
        )
    }

    private fun loadMe() {
        scope.launch {
            try {
                val u = call(TdApi.GetMe()) as TdApi.User
                myId = u.id
                me.value = toProfile(u, me.value?.bio.orEmpty())
                runCatching {
                    val full = call(TdApi.GetUserFullInfo().apply { userId = u.id }) as TdApi.UserFullInfo
                    me.value = me.value?.copy(bio = full.bio?.text.orEmpty())
                }
            } catch (e: Exception) {
                error.value = e.message
            }
        }
    }

    private fun friendly(e: Throwable): String {
        val m = e.message.orEmpty()
        return when {
            "USERNAME_OCCUPIED" in m -> "Это имя пользователя занято"
            "USERNAME_INVALID" in m -> "Недопустимое имя пользователя"
            "USERNAME_NOT_MODIFIED" in m -> "Имя пользователя не изменилось"
            "FIRSTNAME_INVALID" in m -> "Недопустимое имя"
            "FLOOD" in m -> "Слишком часто, попробуйте позже"
            else -> m
        }
    }

    fun saveProfile(first: String, last: String, uname: String, newBio: String) {
        val p = me.value
        scope.launch {
            profileBusy.value = true
            try {
                if (p == null || first != p.firstName || last != p.lastName) {
                    call(TdApi.SetName().apply { firstName = first; lastName = last })
                }
                if (p == null || uname != p.username) {
                    call(TdApi.SetUsername().apply { username = uname })
                }
                if (p == null || newBio != p.bio) {
                    call(TdApi.SetBio().apply { bio = newBio })
                    me.value = me.value?.copy(bio = newBio)
                }
                info.value = "Профиль сохранён"
            } catch (e: Exception) {
                error.value = friendly(e)
            } finally {
                profileBusy.value = false
            }
        }
    }

    fun setAvatar(uri: Uri) {
        scope.launch(Dispatchers.IO) {
            profileBusy.value = true
            try {
                val path = Attachments.prepareAvatar(app, uri)
                val inner = TdApi.InputChatPhotoStatic().apply { photo = local(path) }
                call(TdApi.SetProfilePhoto().apply { photo = inner; isPublic = false })
                info.value = "Фото профиля обновлено"
            } catch (e: Exception) {
                error.value = "Не удалось обновить фото: ${e.message}"
            } finally {
                profileBusy.value = false
            }
        }
    }

    fun deleteAvatar() {
        val id = me.value?.photoId ?: return
        if (id == 0L) return
        scope.launch {
            profileBusy.value = true
            try {
                call(TdApi.DeleteProfilePhoto().apply { profilePhotoId = id })
                info.value = "Фото удалено"
            } catch (e: Exception) {
                error.value = e.message
            } finally {
                profileBusy.value = false
            }
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

    private suspend fun toUi(m: TdApi.Message): UiMsg {
        val (text, media) = parse(m.content)
        val chat = chatMap[m.chatId]
        return UiMsg(
            id = m.id,
            chatId = m.chatId,
            out = m.isOutgoing,
            from = if (m.isOutgoing) "" else senderName(m),
            text = text,
            time = m.date,
            media = media,
            pending = m.sendingState is TdApi.MessageSendingStatePending,
            read = m.isOutgoing && chat != null && m.id <= chat.lastReadOutboxMessageId,
            edited = m.editDate > 0,
            reply = replyPreview(m),
        )
    }

    private suspend fun replyPreview(m: TdApi.Message): ReplyPreview? {
        val r = m.replyTo as? TdApi.MessageReplyToMessage ?: return null
        if (r.messageId == 0L) return null
        val cid = if (r.chatId != 0L) r.chatId else m.chatId
        val key = "$cid:${r.messageId}"
        replyCache[key]?.let { return it }
        return try {
            val o = call(TdApi.GetMessage().apply { chatId = cid; messageId = r.messageId }) as TdApi.Message
            val author = if (o.isOutgoing) "Вы" else senderName(o).ifBlank { chatMap[o.chatId]?.title.orEmpty() }
            ReplyPreview(o.id, author, describe(o.content)).also { replyCache[key] = it }
        } catch (e: Exception) {
            ReplyPreview(r.messageId, "", "Сообщение недоступно")
        }
    }

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

    /** Содержимое сообщения → (текст/подпись, вложение). Заодно регистрирует файлы. */
    private fun parse(c: TdApi.MessageContent): Pair<String, Media?> {
        when (c) {
            is TdApi.MessageText -> return c.text.text to null
            is TdApi.MessagePhoto -> {
                val sizes = c.photo.sizes
                val full = sizes.lastOrNull()
                val disp = sizes.lastOrNull { maxOf(it.width, it.height) <= 1280 } ?: full
                if (full != null && disp != null) {
                    reg(full.photo)
                    reg(disp.photo)
                    return c.caption.text to Media.Photo(
                        disp.photo.id, full.photo.id, disp.width, disp.height, c.photo.minithumbnail?.data,
                    )
                }
            }
            is TdApi.MessageVideo -> {
                val v = c.video
                reg(v.video)
                reg(v.thumbnail?.file)
                return c.caption.text to Media.Video(
                    v.video.id, v.thumbnail?.file?.id, v.width, v.height, v.duration, v.minithumbnail?.data,
                )
            }
            is TdApi.MessageAnimation -> {
                val a = c.animation
                reg(a.animation)
                reg(a.thumbnail?.file)
                return c.caption.text to Media.Video(
                    a.animation.id, a.thumbnail?.file?.id, a.width, a.height, a.duration, a.minithumbnail?.data,
                    gif = true,
                )
            }
            is TdApi.MessageVideoNote -> {
                val v = c.videoNote
                reg(v.video)
                reg(v.thumbnail?.file)
                return "" to Media.Video(
                    v.video.id, v.thumbnail?.file?.id, v.length, v.length, v.duration, v.minithumbnail?.data,
                    round = true,
                )
            }
            is TdApi.MessageVoiceNote -> {
                val v = c.voiceNote
                reg(v.voice)
                return c.caption.text to Media.Voice(v.voice.id, v.duration, v.waveform ?: ByteArray(0), c.isListened)
            }
            is TdApi.MessageAudio -> {
                val a = c.audio
                reg(a.audio)
                val title = a.title.ifBlank { a.fileName.ifBlank { "Аудио" } }
                return c.caption.text to Media.Audio(a.audio.id, title, a.performer, a.duration)
            }
            is TdApi.MessageDocument -> {
                val d = c.document
                reg(d.document)
                return c.caption.text to Media.Doc(d.document.id, d.fileName.ifBlank { "Файл" }, d.mimeType)
            }
            is TdApi.MessageSticker -> {
                val s = c.sticker
                reg(s.sticker)
                reg(s.thumbnail?.file)
                return "" to Media.Sticker(s.sticker.id, s.thumbnail?.file?.id, s.emoji, s.format is TdApi.StickerFormatWebp)
            }
            else -> {}
        }
        return describe(c) to null
    }

    private fun dur(s: Int) = String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60)

    private fun cap(t: String) = if (t.isBlank()) "" else ": $t"

    /** Короткое описание для списка чатов. */
    private fun describe(c: TdApi.MessageContent): String = when (c) {
        is TdApi.MessageText -> c.text.text
        is TdApi.MessagePhoto -> "📷 Фото" + cap(c.caption.text)
        is TdApi.MessageVideo -> "🎬 Видео" + cap(c.caption.text)
        is TdApi.MessageAnimation -> "GIF"
        is TdApi.MessageVoiceNote -> "🎤 Голосовое " + dur(c.voiceNote.duration)
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
