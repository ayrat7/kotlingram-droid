package dev.example.tg.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.viewinterop.AndroidView
import android.net.Uri
import android.view.TextureView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.example.tg.data.Btn
import dev.example.tg.data.Markup
import dev.example.tg.data.Media
import dev.example.tg.data.Track
import dev.example.tg.data.ReplyPreview
import dev.example.tg.data.UiMsg
import dev.example.tg.media.Waveform
import java.io.File

/** Состояние встроенного проигрывателя кружков: одновременно играет не больше одного. */
@Stable
class RoundState {
    var id by mutableStateOf<Long?>(null)
    var paused by mutableStateOf(false)
    var progress by mutableFloatStateOf(0f)

    fun tap(msgId: Long) {
        if (id == msgId) {
            paused = !paused
        } else {
            id = msgId
            paused = false
            progress = 0f
        }
    }

    fun stop() {
        id = null
        paused = false
        progress = 0f
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageItem(
    m: UiMsg,
    round: RoundState,
    onOpen: (Media) -> Unit,
    onLong: () -> Unit,
    onJump: (Long) -> Unit,
    onReply: () -> Unit,
    onProfile: (Long) -> Unit,
    tail: Boolean = true,
    onBtn: (UiMsg, Btn) -> Unit = { _, _ -> },
) {
    val media = m.media
    val cs = MaterialTheme.colorScheme
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val limit = with(LocalDensity.current) { 72.dp.toPx() }
    val reply by rememberUpdatedState(onReply)

    Box(
        Modifier.fillMaxWidth().pointerInput(m.id) {
            // свайп влево по сообщению — ответить, как в Telegram
            detectHorizontalDragGestures(
                onDragEnd = {
                    if (offset.value <= -limit * 0.8f) reply()
                    scope.launch { offset.animateTo(0f) }
                },
                onDragCancel = { scope.launch { offset.animateTo(0f) } },
            ) { change, dx ->
                change.consume()
                scope.launch { offset.snapTo((offset.value + dx).coerceIn(-limit, 0f)) }
            }
        },
    ) {
        Box(
            Modifier.align(Alignment.CenterEnd).padding(end = 16.dp).size(32.dp).clip(CircleShape)
                .background(cs.surfaceContainerHighest)
                .graphicsLayer { alpha = (-offset.value / limit).coerceIn(0f, 1f) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.Reply, null, Modifier.size(18.dp), tint = cs.primary)
        }
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp).offset { IntOffset(offset.value.roundToInt(), 0) },
            contentAlignment = if (m.out) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Column(horizontalAlignment = if (m.out) Alignment.End else Alignment.Start) {
                when {
                    media is Media.Sticker -> StickerMessage(m, media, onLong)
                    media is Media.Video && media.round -> RoundVideoMessage(m, media, round, onLong)
                    else -> Bubble(m, tail, onOpen, onLong, onJump, onProfile)
                }
                (m.markup as? Markup.Inline)?.let { mk ->
                    InlineKeyboard(mk, Modifier.widthIn(max = 320.dp).padding(top = 3.dp)) { b -> onBtn(m, b) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(m: UiMsg, tail: Boolean, onOpen: (Media) -> Unit, onLong: () -> Unit, onJump: (Long) -> Unit, onProfile: (Long) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val pal = chatPalette()
    val media = m.media
    val visual = media is Media.Photo || media is Media.Video
    val overlayMeta = visual && m.text.isBlank()
    val bubble = BubbleShape(m.out, tail)
    val shape = RoundedCornerShape(
        topStart = 18.dp, topEnd = 18.dp,
        bottomStart = if (!m.out && tail) 3.dp else 18.dp,
        bottomEnd = if (m.out && tail) 3.dp else 18.dp,
    )
    androidx.compose.runtime.CompositionLocalProvider(
        LocalContentColor provides (if (m.out) pal.outText else pal.inText),
    ) {
    Box(
        modifier = (if (visual) Modifier.width(260.dp) else Modifier.widthIn(max = 320.dp))
            .background(if (m.out) pal.outBubble else pal.inBubble, bubble)
            .clip(shape)
            .combinedClickable(onClick = {}, onLongClick = onLong),
    ) {
        Column {
            if (!m.out && m.from.isNotBlank()) {
                Text(
                    m.from, style = MaterialTheme.typography.labelLarge, color = pal.inAccent,
                    modifier = Modifier
                        .clickable(enabled = m.senderId != 0L) { onProfile(m.senderId) }
                        .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = if (visual) 6.dp else 0.dp),
                )
            }
            m.reply?.let { ReplyBlock(it) { onJump(it.id) } }
            Box {
                Column {
                    when (media) {
                        is Media.Photo -> PhotoContent(media, { onOpen(media) }, onLong)
                        is Media.Video -> VideoContent(media, { onOpen(media) }, onLong)
                        is Media.Voice -> VoiceContent(m, media, onLong)
                        is Media.Audio -> AudioContent(media)
                        is Media.Doc -> DocContent(media, onLong)
                        else -> {}
                    }
                    if (m.text.isNotBlank()) {
                        Text(
                            m.text,
                            modifier = Modifier.padding(
                                start = 12.dp, end = 12.dp,
                                top = if (media == null && (m.out || m.from.isBlank())) 8.dp else 6.dp,
                            ),
                        )
                    }
                    if (!overlayMeta) {
                        MetaRow(m, Modifier.align(Alignment.End).padding(end = 10.dp, bottom = 5.dp, start = 12.dp))
                    }
                }
                if (overlayMeta) MetaPill(m, Modifier.align(Alignment.BottomEnd).padding(6.dp))
            }
        }
    }
    }
}

@Composable
private fun ReplyBlock(r: ReplyPreview, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(cs.primary.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .height(IntrinsicSize.Min),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(cs.primary))
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            if (r.author.isNotBlank()) {
                Text(
                    r.author, style = MaterialTheme.typography.labelMedium, color = cs.primary,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(r.text, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MetaRow(
    m: UiMsg, modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current.copy(alpha = 0.6f),
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (m.edited) {
            Text("изм.", style = MaterialTheme.typography.labelSmall, color = tint)
            Spacer(Modifier.width(4.dp))
        }
        Text(formatClock(m.time), style = MaterialTheme.typography.labelSmall, color = tint)
        if (m.out) {
            Spacer(Modifier.width(3.dp))
            Icon(
                if (m.pending) Icons.Filled.Schedule else if (m.read) Icons.Filled.DoneAll else Icons.Filled.Done,
                contentDescription = null, modifier = Modifier.size(14.dp), tint = tint,
            )
        }
    }
}

@Composable
private fun MetaPill(m: UiMsg, modifier: Modifier = Modifier) {
    Surface(color = Color.Black.copy(alpha = 0.45f), shape = RoundedCornerShape(10.dp), modifier = modifier) {
        MetaRow(m, Modifier.padding(horizontal = 7.dp, vertical = 2.dp), Color.White)
    }
}

// ---------- Фото / видео ----------

@Composable
fun MiniThumb(bytes: ByteArray?, modifier: Modifier = Modifier) {
    val bmp = remember(bytes) {
        bytes?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull() }
    }
    if (bmp != null) {
        Image(bmp, null, modifier.blur(8.dp), contentScale = ContentScale.Crop)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoContent(p: Media.Photo, onClick: () -> Unit, onLong: () -> Unit) {
    val f = rememberFile(p.file)
    val ratio = if (p.width > 0 && p.height > 0) (p.width.toFloat() / p.height).coerceIn(0.6f, 1.8f) else 1f
    Box(Modifier.fillMaxWidth().aspectRatio(ratio).background(Color.Black.copy(alpha = 0.1f)).combinedClickable(onClick = onClick, onLongClick = onLong)) {
        MiniThumb(p.mini, Modifier.fillMaxSize())
        val path = f?.path
        if (path != null) {
            AsyncImage(File(path), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        if (f != null && (f.uploading || f.downloading)) {
            ProgressBadge(f.progress, Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun ProgressBadge(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
        if (progress > 0f) {
            CircularProgressIndicator({ progress }, Modifier.size(32.dp), color = Color.White, strokeWidth = 3.dp)
        } else {
            CircularProgressIndicator(Modifier.size(32.dp), color = Color.White, strokeWidth = 3.dp)
        }
    }
}

@Composable
private fun VideoThumb(v: Media.Video, modifier: Modifier = Modifier) {
    val t = rememberFile(v.thumb)
    Box(modifier.background(Color.Black.copy(alpha = 0.35f))) {
        MiniThumb(v.mini, Modifier.fillMaxSize())
        val path = t?.path
        if (path != null) AsyncImage(File(path), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun BoxScope.PlayOverlay(fileId: Int) {
    val f = rememberFile(fileId, auto = false)
    if (f != null && (f.uploading || f.downloading)) {
        ProgressBadge(f.progress, Modifier.align(Alignment.Center))
    } else {
        Box(
            Modifier.align(Alignment.Center).size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun DurationChip(v: Media.Video, modifier: Modifier) {
    Surface(color = Color.Black.copy(alpha = 0.45f), shape = RoundedCornerShape(10.dp), modifier = modifier) {
        Text(
            if (v.gif) "GIF" else formatDuration(v.duration),
            color = Color.White, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoContent(v: Media.Video, onClick: () -> Unit, onLong: () -> Unit) {
    val ratio = if (v.width > 0 && v.height > 0) (v.width.toFloat() / v.height).coerceIn(0.6f, 1.8f) else 1.33f
    Box(Modifier.fillMaxWidth().aspectRatio(ratio).combinedClickable(onClick = onClick, onLongClick = onLong)) {
        VideoThumb(v, Modifier.fillMaxSize())
        PlayOverlay(v.file)
        DurationChip(v, Modifier.align(Alignment.TopStart).padding(6.dp))
    }
}

/** Кружок проигрывается прямо в кружке (не на весь экран): тап — играть/пауза, кольцо — прогресс. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoundVideoMessage(m: UiMsg, v: Media.Video, round: RoundState, onLong: () -> Unit) {
    val repo = LocalRepo.current
    val player = LocalPlayer.current
    val f = rememberFile(v.file, auto = false)
    val path = f?.path
    val active = round.id == m.id
    val ring = MaterialTheme.colorScheme.primary

    DisposableEffect(m.id) { onDispose { if (round.id == m.id) round.stop() } }

    Box(Modifier.size(200.dp)) {
        Box(
            Modifier.fillMaxSize().clip(CircleShape).combinedClickable(
                onClick = {
                    if (path == null) {
                        repo.download(v.file)
                    } else {
                        player.pause()
                        if (round.id != m.id && !m.out) repo.markOpened(m.chatId, m.id)
                        round.tap(m.id)
                    }
                },
                onLongClick = onLong,
            ),
        ) {
            VideoThumb(v, Modifier.fillMaxSize())
            if (active && path != null) RoundPlayer(path, round)
            if (!active || round.paused) PlayOverlay(v.file)
            DurationChip(v, Modifier.align(Alignment.TopCenter).padding(top = 10.dp))
            MetaPill(m, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
        }
        if (active) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 3.dp.toPx()
                drawArc(
                    ring, -90f, 360f * round.progress, false,
                    topLeft = Offset(w / 2, w / 2),
                    size = Size(size.width - w, size.height - w),
                    style = Stroke(w, cap = StrokeCap.Round),
                )
            }
        }
    }
}

@Composable
private fun RoundPlayer(path: String, round: RoundState) {
    val ctx = LocalContext.current
    val exo = remember(path) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(exo) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) round.stop()
            }
        }
        exo.addListener(l)
        onDispose {
            exo.removeListener(l)
            exo.release()
        }
    }
    LaunchedEffect(exo, round.paused) { exo.playWhenReady = !round.paused }
    LaunchedEffect(exo) {
        while (true) {
            val d = exo.duration
            if (d > 0) round.progress = (exo.currentPosition.toFloat() / d).coerceIn(0f, 1f)
            delay(50)
        }
    }
    // TextureView, а не SurfaceView — только он честно обрезается в круг
    AndroidView(
        factory = { c -> TextureView(c).also { exo.setVideoTextureView(it) } },
        modifier = Modifier.fillMaxSize(),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerMessage(m: UiMsg, s: Media.Sticker, onLong: () -> Unit) {
    val f = rememberFile(if (s.renderable || s.animated) s.file else s.thumb)
    Box(Modifier.size(160.dp).combinedClickable(onClick = {}, onLongClick = onLong)) {
        val path = f?.path
        if (path != null && s.animated) {
            TgsSticker(path, Modifier.fillMaxSize())
        } else if (path != null) {
            AsyncImage(File(path), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } else {
            Text(s.emoji, fontSize = 64.sp, modifier = Modifier.align(Alignment.Center))
        }
        MetaPill(m, Modifier.align(Alignment.BottomEnd))
    }
}

// ---------- Голосовые / аудио ----------

@Composable
private fun PlayButton(fileId: Int, playing: Boolean, autoStart: Boolean = false, onToggle: (path: String) -> Unit) {
    val repo = LocalRepo.current
    val f = rememberFile(fileId)
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(cs.primary).clickable {
            val p = f?.path
            if (p != null) onToggle(p) else if (autoStart) onToggle("") else repo.download(fileId)
        },
        contentAlignment = Alignment.Center,
    ) {
        when {
            f?.path == null -> {
                val pr = f?.progress ?: 0f
                if (f?.downloading == true && pr > 0f) {
                    CircularProgressIndicator({ pr }, Modifier.size(26.dp), color = cs.onPrimary, strokeWidth = 2.5.dp)
                } else if (f?.downloading == true) {
                    CircularProgressIndicator(Modifier.size(26.dp), color = cs.onPrimary, strokeWidth = 2.5.dp)
                } else {
                    Icon(Icons.Filled.Download, null, tint = cs.onPrimary)
                }
            }
            else -> Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = cs.onPrimary, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun WaveformBar(data: ByteArray, progress: Float, modifier: Modifier, onSeek: (Float) -> Unit, onLong: () -> Unit) {
    val bars = remember(data) { Waveform.display(data, 40) }
    val active = MaterialTheme.colorScheme.primary
    val inactive = LocalContentColor.current.copy(alpha = 0.35f)
    val seek by rememberUpdatedState(onSeek)
    val long by rememberUpdatedState(onLong)
    Canvas(modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { long() }, onTap = { seek(it.x / size.width) }) }) {
        val n = bars.size
        val gap = 2.dp.toPx()
        val bw = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
        bars.forEachIndexed { i, v ->
            val h = maxOf(3.dp.toPx(), v * size.height)
            drawRoundRect(
                color = if ((i + 0.5f) / n <= progress) active else inactive,
                topLeft = Offset(i * (bw + gap), (size.height - h) / 2f),
                size = Size(bw, h),
                cornerRadius = CornerRadius(bw / 2f),
            )
        }
    }
}

@Composable
private fun VoiceContent(m: UiMsg, v: Media.Voice, onLong: () -> Unit) {
    val repo = LocalRepo.current
    val player = LocalPlayer.current
    val ps by player.state.collectAsState()
    val active = ps.fileId == v.file
    val playing = active && ps.playing
    val total = if (active && ps.durationMs > 0) ps.durationMs else v.duration * 1000L
    val progress = if (active && total > 0) (ps.positionMs / total.toFloat()).coerceIn(0f, 1f) else 0f
    val newVoice = !m.out && !v.listened

    Row(
        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PlayButton(v.file, playing) { path ->
            player.toggle(v.file, path, v.duration * 1000L)
            if (newVoice) repo.markOpened(m.chatId, m.id)
        }
        Column(Modifier.width(170.dp)) {
            WaveformBar(
                v.waveform, progress, Modifier.fillMaxWidth().height(30.dp),
                onSeek = { player.seekToFraction(v.file, it) },
                onLong = onLong,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatDurationMs(if (active && ps.positionMs > 0) ps.positionMs else total),
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                )
                if (newVoice) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
    }
}

@Composable
private fun AudioContent(a: Media.Audio) {
    val player = LocalPlayer.current
    val ps by player.state.collectAsState()
    val queue = LocalAudioQueue.current
    val active = ps.fileId == a.file
    val total = if (active && ps.durationMs > 0) ps.durationMs else a.duration * 1000L
    val progress = if (active && total > 0) (ps.positionMs / total.toFloat()).coerceIn(0f, 1f) else 0f

    Row(
        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PlayButton(a.file, active && ps.playing && !ps.loading, autoStart = true) { _ ->
            val i = queue.indexOfFirst { it.fileId == a.file }
            if (i >= 0) player.playQueue(queue, i)
            else player.playQueue(listOf(Track(a.file, a.title, a.performer, a.duration, a.cover)), 0)
        }
        Column(Modifier.width(190.dp)) {
            Text(a.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = buildString {
                if (a.performer.isNotBlank()) append(a.performer).append(" · ")
                append(formatDurationMs(if (active && ps.positionMs > 0) ps.positionMs else total))
            }
            Text(
                sub, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = LocalContentColor.current.copy(alpha = 0.7f),
            )
            if (active) {
                LinearProgressIndicator({ progress }, Modifier.fillMaxWidth().padding(top = 4.dp))
            }
        }
    }
}

// ---------- Документы ----------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocContent(d: Media.Doc, onLong: () -> Unit) {
    val ctx = LocalContext.current
    val repo = LocalRepo.current
    val f = rememberFile(d.file, auto = false)
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(
                onClick = {
                    val p = f?.path
                    if (p != null) openFile(ctx, p, d.mime) else repo.download(d.file)
                },
                onLongClick = onLong,
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(cs.primary), contentAlignment = Alignment.Center) {
            when {
                f != null && (f.downloading || f.uploading) -> {
                    if (f.progress > 0f) CircularProgressIndicator({ f.progress }, Modifier.size(26.dp), color = cs.onPrimary, strokeWidth = 2.5.dp)
                    else CircularProgressIndicator(Modifier.size(26.dp), color = cs.onPrimary, strokeWidth = 2.5.dp)
                }
                f?.done == true -> Icon(Icons.Filled.Description, null, tint = cs.onPrimary)
                else -> Icon(Icons.Filled.Download, null, tint = cs.onPrimary)
            }
        }
        Column(Modifier.weight(1f, fill = false)) {
            Text(d.name, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (f != null && f.size > 0) {
                Text(
                    formatSize(f.size), style = MaterialTheme.typography.labelSmall,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                )
            }
        }
    }
}
