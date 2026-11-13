package dev.example.tg.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.example.tg.data.Media
import dev.example.tg.data.UiMsg
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.derivedStateOf
import dev.example.tg.data.PresenceKind
import java.time.LocalDate
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.MutableState
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import dev.chrisbanes.haze.HazeState
import dev.example.tg.data.Btn
import dev.example.tg.data.BtnAction
import dev.example.tg.data.BotUi
import dev.example.tg.data.Markup
import dev.example.tg.data.Track

/** Что сейчас «прикреплено» к полю ввода: ответ или редактирование. */
sealed interface Composing {
    class Reply(val msg: UiMsg) : Composing
    class Edit(val msg: UiMsg) : Composing
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(chatId: Long) {
    val repo = LocalRepo.current
    val recorder = LocalRecorder.current
    val player = LocalPlayer.current
    val msgs by repo.messages.collectAsState()
    val chats by repo.chats.collectAsState()
    val preparing by repo.preparing.collectAsState()
    val chat = chats.firstOrNull { it.id == chatId } ?: repo.chatItem(chatId)
    val typing by repo.typing.collectAsState()
    val presence by repo.presence.collectAsState()
    val ps by player.state.collectAsState()
    val bot by repo.currentBot.collectAsState()
    val inlineRes by repo.inline.collectAsState()
    val recordingNow by recorder.active.collectAsState()
    val round = remember { RoundState() }
    val peerId = chat?.userId ?: 0L
    val title = chat?.title ?: ""
    var viewer by remember { mutableStateOf<Media?>(null) }
    var menuFor by remember { mutableStateOf<UiMsg?>(null) }
    var deleteFor by remember { mutableStateOf<UiMsg?>(null) }
    var forwardFor by remember { mutableStateOf<UiMsg?>(null) }
    var composing by remember { mutableStateOf<Composing?>(null) }
    var topMenu by remember { mutableStateOf(false) }
    val draft = rememberSaveable { mutableStateOf("") }
    var hiddenKb by remember { mutableStateOf(0L) }
    val clipboard = LocalClipboardManager.current
    val uiScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val hazeState = remember { HazeState() }
    val density = LocalDensity.current
    val pal = chatPalette()
    var topH by remember { mutableStateOf(0) }
    var botH by remember { mutableStateOf(0) }
    // reverseLayout: индекс 0 — самое новое, подгрузка старых не сдвигает прокрутку
    val reversed = remember(msgs) { msgs.asReversed() }
    // очередь музыки чата (от старых к новым) для «следующий/предыдущий»
    val audioQueue = remember(msgs) {
        msgs.mapNotNull { m -> (m.media as? Media.Audio)?.let { Track(it.file, it.title, it.performer, it.duration, it.cover) } }
    }
    // клавиатура бота: последняя клавиатура ответа от собеседника, если её не убрали
    val kbMsg = remember(msgs) { msgs.lastOrNull { !it.out && (it.markup is Markup.Keyboard || it.markup is Markup.Remove) } }
    val kb = (kbMsg?.markup as? Markup.Keyboard)?.takeIf { kbMsg?.id != hiddenKb }

    DisposableEffect(chatId) {
        onDispose {
            recorder.cancel()
            player.stopVoice()
            round.stop()
        }
    }

    // голосовое и кружок не играют одновременно
    LaunchedEffect(ps.playing) { if (ps.playing && ps.track == null) round.stop() }
    val showDown by remember { derivedStateOf { listState.firstVisibleItemIndex > 2 } }

    LaunchedEffect(reversed.firstOrNull()?.id) {
        if (listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0)
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) to info.totalItemsCount
        }.collect { (last, total) ->
            if (total > 0 && last >= total - 6) repo.loadOlder()
        }
    }

    fun onBotButton(m: UiMsg, b: Btn) {
        val a = b.action
        if (a is BtnAction.SwitchInline) {
            val u = bot?.username.orEmpty()
            draft.value = if (u.isNotBlank()) "@$u ${a.query}" else a.query
        } else repo.pressInline(m, b)
    }

    Box(Modifier.fillMaxSize().imePadding()) {
        Box(Modifier.fillMaxSize().glassSource(hazeState)) {
            BackgroundLayer()
            androidx.compose.runtime.CompositionLocalProvider(LocalAudioQueue provides audioQueue) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    reverseLayout = true,
                    contentPadding = PaddingValues(
                        top = with(density) { topH.toDp() } + 8.dp,
                        bottom = with(density) { botH.toDp() } + 8.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(reversed, key = { _, m -> m.id }) { i, m ->
                        val older = reversed.getOrNull(i + 1)
                        val newer = reversed.getOrNull(i - 1)
                        val tail = newer == null || newer.out != m.out || newer.senderId != m.senderId ||
                            dayOf(newer.time) != dayOf(m.time)
                        val groupStart = older == null || older.out != m.out || older.senderId != m.senderId
                        Column(Modifier.padding(top = if (groupStart) 6.dp else 0.dp)) {
                            if (older == null || dayOf(older.time) != dayOf(m.time)) DaySeparator(dayOf(m.time))
                            MessageItem(
                                m,
                                round = round,
                                onOpen = { viewer = it },
                                onLong = { menuFor = m },
                                onJump = { id ->
                                    val idx = reversed.indexOfFirst { it.id == id }
                                    if (idx >= 0) uiScope.launch { listState.animateScrollToItem(idx) }
                                },
                                onReply = { composing = Composing.Reply(m) },
                                onProfile = { repo.openUser(it) },
                                tail = tail,
                                onBtn = { mm, b -> onBotButton(mm, b) },
                            )
                        }
                    }
                }
            }
            val b = bot
            if (msgs.isEmpty() && b != null) {
                BotStartCard(b, Modifier.align(Alignment.Center)) { repo.send("/start") }
            }
        }

        // верхняя «стеклянная» панель
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .onSizeChanged { topH = it.height }
                .glassBar(hazeState, pal.glass),
        ) {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent,
                ),
                title = {
                    Row(
                        Modifier.clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = peerId != 0L) { repo.openUser(peerId) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(chat?.avatar, chatId, title, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val typingLabel = typing[chatId]
                            val sub = typingLabel
                                ?: if (bot != null) "бот" else if (chat?.isPrivate == true) presenceText(presence[peerId]) else ""
                            if (sub.isNotEmpty()) {
                                val online = typingLabel != null || (bot == null && presence[peerId]?.kind == PresenceKind.Online)
                                Text(
                                    sub, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (online) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton({ repo.close() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
                actions = {
                    if (chat?.isPrivate == true && bot == null && peerId != 0L) {
                        IconButton({ repo.calls.start(peerId) }) { Icon(Icons.Filled.Call, "Позвонить") }
                    }
                    Box {
                        IconButton({ topMenu = true }) { Icon(Icons.Filled.MoreVert, "Меню") }
                        DropdownMenu(expanded = topMenu, onDismissRequest = { topMenu = false }) {
                            val muted = chat?.muted == true
                            DropdownMenuItem(
                                text = { Text(if (muted) "Включить уведомления" else "Отключить уведомления") },
                                leadingIcon = {
                                    Icon(if (muted) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff, null)
                                },
                                onClick = { topMenu = false; repo.setMuted(chatId, !muted) },
                            )
                        }
                    }
                },
            )
            MiniPlayer()
            if (preparing) LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        // нижняя панель: inline-подсказки, клавиатура бота, поле ввода
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .onSizeChanged { botH = it.height }
                .glassBar(hazeState, pal.glass)
                .navigationBarsPadding(),
        ) {
            inlineRes?.let { r ->
                if (r.items.isNotEmpty()) InlineResultsPanel(r) { repo.sendInlineResult(r, it); draft.value = "" }
            }
            if (kb != null && !recordingNow) {
                ReplyKeyboardPanel(kb) { b ->
                    repo.pressKeyboard(b)
                    if (kb.oneTime) hiddenKb = kbMsg?.id ?: 0L
                }
            }
            InputBar(
                chatId = chatId,
                draftState = draft,
                composing = composing,
                onComposingChange = { composing = it },
                onVideoStart = { round.stop() },
                bot = bot,
                placeholder = kb?.placeholder.orEmpty(),
            )
        }

        if (showDown) {
            SmallFloatingActionButton(
                onClick = { uiScope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = with(density) { botH.toDp() } + 12.dp),
            ) {
                Icon(Icons.Filled.KeyboardArrowDown, "Вниз")
            }
        }
    }

    viewer?.let { MediaViewer(it) { viewer = null } }

    menuFor?.let { m ->
        MessageActionsSheet(
            m = m,
            onReply = { composing = Composing.Reply(m) },
            onCopy = { clipboard.setText(AnnotatedString(m.text)) },
            onEdit = { composing = Composing.Edit(m) },
            onForward = { forwardFor = m },
            onDelete = { deleteFor = m },
            onDismiss = { menuFor = null },
        )
    }
    deleteFor?.let { m ->
        DeleteDialog(
            m = m,
            canRevoke = m.out || chat?.isPrivate == true,
            onConfirm = { forAll -> repo.delete(m, forAll); deleteFor = null },
            onDismiss = { deleteFor = null },
        )
    }
    forwardFor?.let { m -> ForwardSheet(m) { forwardFor = null } }
}

@Composable
private fun DaySeparator(d: LocalDate) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f), shape = RoundedCornerShape(12.dp)) {
            Text(
                dayLabel(d), style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun PulsingDot() {
    val a by rememberInfiniteTransition(label = "rec").animateFloat(
        initialValue = 1f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "dot",
    )
    Box(Modifier.size(10.dp).alpha(a).clip(CircleShape).background(MaterialTheme.colorScheme.error))
}

@Composable
private fun InputBar(
    chatId: Long,
    draftState: MutableState<String>,
    composing: Composing?,
    onComposingChange: (Composing?) -> Unit,
    onVideoStart: () -> Unit,
    bot: BotUi?,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val repo = LocalRepo.current
    val recorder = LocalRecorder.current
    val player = LocalPlayer.current
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val recording by recorder.active.collectAsState()
    val elapsed by recorder.elapsedMs.collectAsState()
    val level by recorder.level.collectAsState()
    var text by draftState
    var attachMenu by remember { mutableStateOf(false) }
    var commandsOpen by remember { mutableStateOf(false) }
    var cameraOpen by remember { mutableStateOf(false) }

    // «@бот запрос» — inline-режим
    LaunchedEffect(text) {
        val m = Regex("^@([A-Za-z0-9_]{3,})\\s(.*)$", RegexOption.DOT_MATCHES_ALL).find(text)
        if (m != null && composing !is Composing.Edit) {
            kotlinx.coroutines.delay(350)
            repo.inlineQuery(m.groupValues[1], m.groupValues[2])
        } else repo.clearInline()
    }
    val cameraPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.CAMERA] == true) { player.pause(); cameraOpen = true }
        else repo.error.value = "Для камеры нужен доступ к ней"
    }
    fun onCamera() {
        val ok = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (ok) { player.pause(); cameraOpen = true }
        else cameraPermissions.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        repo.sendUris(uris, asFile = false)
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        repo.sendUris(uris, asFile = true)
    }

    // при входе в режим редактирования подставляем старый текст
    LaunchedEffect(composing) {
        (composing as? Composing.Edit)?.let { text = it.msg.text }
    }

    fun cancelComposing() {
        if (composing is Composing.Edit) text = ""
        onComposingChange(null)
    }

    fun startRecording() {
        player.stop()
        if (!recorder.start()) repo.error.value = "Не удалось начать запись"
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else repo.error.value = "Нет доступа к микрофону"
    }

    var videoOpen by remember { mutableStateOf(false) }
    fun openVideo() {
        player.stop()
        onVideoStart()
        videoOpen = true
    }
    val videoPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.all { it }) openVideo() else repo.error.value = "Для видеосообщений нужен доступ к камере и микрофону"
    }
    fun onVideo() {
        if (hasVideoNotePermissions(ctx)) openVideo()
        else videoPermissions.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }

    fun onMic() {
        val ok = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (ok) startRecording() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(modifier) {
    if (composing != null && !recording) {
        val (icon, title, msg) = when (composing) {
            is Composing.Reply -> Triple(Icons.AutoMirrored.Filled.Reply, "Ответ", composing.msg)
            is Composing.Edit -> Triple(Icons.Filled.Edit, "Редактирование", composing.msg)
        }
        Surface(Modifier.fillMaxWidth(), color = cs.surfaceContainerLow) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = cs.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.labelLarge, color = cs.primary, fontWeight = FontWeight.Bold)
                    Text(
                        msg.text.ifBlank { mediaLabel(msg.media) },
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton({ cancelComposing() }) { Icon(Icons.Filled.Close, "Отмена") }
            }
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (recording) {
            Surface(
                Modifier.weight(1f).heightIn(min = 52.dp),
                shape = RoundedCornerShape(26.dp), color = cs.surfaceContainerHigh,
            ) {
                Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ recorder.cancel() }) { Icon(Icons.Filled.Delete, "Отмена", tint = cs.error) }
                    PulsingDot()
                    Spacer(Modifier.width(8.dp))
                    Text(formatDurationMs(elapsed), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(16.dp))
                    LinearProgressIndicator(
                        { level },
                        Modifier.weight(1f).padding(end = 16.dp).clip(RoundedCornerShape(50)),
                    )
                }
            }
        } else {
            Surface(
                Modifier.weight(1f),
                shape = RoundedCornerShape(26.dp), color = cs.surfaceContainerHigh,
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Box {
                        IconButton({ attachMenu = true }, Modifier.heightIn(min = 52.dp)) {
                            Icon(Icons.Filled.AttachFile, "Прикрепить")
                        }
                        DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Фото или видео") },
                                leadingIcon = { Icon(Icons.Filled.PhotoLibrary, null) },
                                onClick = {
                                    attachMenu = false
                                    pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Камера") },
                                leadingIcon = { Icon(Icons.Filled.CameraAlt, null) },
                                onClick = { attachMenu = false; onCamera() },
                            )
                            DropdownMenuItem(
                                text = { Text("Файл") },
                                leadingIcon = { Icon(Icons.Filled.Description, null) },
                                onClick = { attachMenu = false; pickFile.launch("*/*") },
                            )
                        }
                    }
                    TextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(placeholder.ifBlank { "Сообщение" }) },
                        maxLines = 6,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                    )
                    if (text.isBlank()) {
                        if (bot != null && bot.menuUrl.isNotBlank()) {
                            TextButton(
                                { repo.openWebApp(chatId, bot.userId, bot.menuUrl, bot.menuText) },
                                Modifier.heightIn(min = 52.dp),
                            ) { Text(bot.menuText.ifBlank { "Меню" }, maxLines = 1) }
                        }
                        if (bot != null && bot.commands.isNotEmpty()) {
                            IconButton({ commandsOpen = true }, Modifier.heightIn(min = 52.dp)) {
                                Text("/", style = MaterialTheme.typography.titleLarge, color = cs.primary)
                            }
                        }
                        IconButton({ onCamera() }, Modifier.heightIn(min = 52.dp)) {
                            Icon(Icons.Filled.CameraAlt, "Камера")
                        }
                        if (bot == null) {
                            IconButton({ onVideo() }, Modifier.heightIn(min = 52.dp)) {
                                Icon(Icons.Filled.Videocam, "Видеосообщение")
                            }
                        }
                    }
                }
            }
        }

        val canSend = recording || text.isNotBlank()
        FilledIconButton(
            onClick = {
                when {
                    recording -> {
                        val replyId = (composing as? Composing.Reply)?.msg?.id ?: 0L
                        recorder.stop()?.let { repo.sendVoice(it.path, it.durationSec, it.waveform, replyId) }
                        onComposingChange(null)
                    }
                    text.isNotBlank() -> {
                        when (val c = composing) {
                            is Composing.Edit -> repo.edit(c.msg, text)
                            is Composing.Reply -> repo.send(text, c.msg.id)
                            null -> repo.send(text)
                        }
                        text = ""
                        onComposingChange(null)
                    }
                    else -> onMic()
                }
            },
            modifier = Modifier.size(52.dp),
        ) {
            Icon(
                if (canSend) Icons.AutoMirrored.Filled.Send else Icons.Filled.Mic,
                contentDescription = if (canSend) "Отправить" else "Записать голосовое",
            )
        }
    }
    if (commandsOpen && bot != null) {
        BotCommandsSheet(bot, onDismiss = { commandsOpen = false }) { cmd ->
            commandsOpen = false
            repo.send("/$cmd")
        }
    }
    if (cameraOpen) {
        CameraCapture(
            onDismiss = { cameraOpen = false },
            onCaptured = { file ->
                cameraOpen = false
                repo.sendUris(listOf(android.net.Uri.fromFile(file)), asFile = false)
                onComposingChange(null)
            },
        )
    }
    if (videoOpen) {
        VideoNoteRecorder(
            onDismiss = { videoOpen = false },
            onSend = { path, dur, len ->
                val replyId = (composing as? Composing.Reply)?.msg?.id ?: 0L
                repo.sendVideoNote(path, dur, len, replyId)
                onComposingChange(null)
                videoOpen = false
            },
        )
    }
    }
}
