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
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.example.tg.data.Media
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(chatId: Long) {
    val repo = LocalRepo.current
    val recorder = LocalRecorder.current
    val player = LocalPlayer.current
    val msgs by repo.messages.collectAsState()
    val chats by repo.chats.collectAsState()
    val preparing by repo.preparing.collectAsState()
    val chat = chats.firstOrNull { it.id == chatId }
    val title = chat?.title ?: ""
    var viewer by remember { mutableStateOf<Media?>(null) }
    val listState = rememberLazyListState()
    // reverseLayout: индекс 0 — самое новое, подгрузка старых не сдвигает прокрутку
    val reversed = remember(msgs) { msgs.asReversed() }

    DisposableEffect(chatId) {
        onDispose {
            recorder.cancel()
            player.stop()
        }
    }

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

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).imePadding()) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(chat?.avatar, chatId, title, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            navigationIcon = {
                IconButton({ repo.close() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            },
        )
        if (preparing) LinearProgressIndicator(Modifier.fillMaxWidth())

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(reversed, key = { _, m -> m.id }) { i, m ->
                val older = reversed.getOrNull(i + 1)
                Column {
                    if (older == null || dayOf(older.time) != dayOf(m.time)) DaySeparator(dayOf(m.time))
                    MessageItem(m, onOpen = { viewer = it })
                }
            }
        }

        InputBar(Modifier.navigationBarsPadding())
    }

    viewer?.let { MediaViewer(it) { viewer = null } }
}

@Composable
private fun DaySeparator(d: LocalDate) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(12.dp)) {
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
private fun InputBar(modifier: Modifier = Modifier) {
    val repo = LocalRepo.current
    val recorder = LocalRecorder.current
    val player = LocalPlayer.current
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val recording by recorder.active.collectAsState()
    val elapsed by recorder.elapsedMs.collectAsState()
    val level by recorder.level.collectAsState()
    var text by rememberSaveable { mutableStateOf("") }
    var attachMenu by remember { mutableStateOf(false) }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        repo.sendUris(uris, asFile = false)
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        repo.sendUris(uris, asFile = true)
    }

    fun startRecording() {
        player.stop()
        if (!recorder.start()) repo.error.value = "Не удалось начать запись"
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else repo.error.value = "Нет доступа к микрофону"
    }

    fun onMic() {
        val ok = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (ok) startRecording() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    Row(
        modifier.fillMaxWidth().padding(8.dp),
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
                        placeholder = { Text("Сообщение") },
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
                }
            }
        }

        val canSend = recording || text.isNotBlank()
        FilledIconButton(
            onClick = {
                when {
                    recording -> recorder.stop()?.let { repo.sendVoice(it.path, it.durationSec, it.waveform) }
                    text.isNotBlank() -> { repo.send(text); text = "" }
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
}
