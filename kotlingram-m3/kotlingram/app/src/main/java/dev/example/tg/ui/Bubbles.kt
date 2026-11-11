package dev.example.tg.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import dev.example.tg.data.Media
import dev.example.tg.data.UiMsg
import dev.example.tg.media.Waveform
import java.io.File

@Composable
fun MessageItem(m: UiMsg, onOpen: (Media) -> Unit) {
    val media = m.media
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        contentAlignment = if (m.out) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        when {
            media is Media.Sticker -> StickerMessage(m, media)
            media is Media.Video && media.round -> RoundVideoMessage(m, media, onOpen)
            else -> Bubble(m, onOpen)
        }
    }
}

@Composable
private fun Bubble(m: UiMsg, onOpen: (Media) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val media = m.media
    val visual = media is Media.Photo || media is Media.Video
    val overlayMeta = visual && m.text.isBlank()
    val shape = RoundedCornerShape(
        topStart = 18.dp, topEnd = 18.dp,
        bottomStart = if (m.out) 18.dp else 4.dp,
        bottomEnd = if (m.out) 4.dp else 18.dp,
    )
    Surface(
        color = if (m.out) cs.primaryContainer else cs.surfaceContainerHigh,
        contentColor = if (m.out) cs.onPrimaryContainer else cs.onSurface,
        shape = shape,
        modifier = if (visual) Modifier.width(260.dp) else Modifier.widthIn(max = 320.dp),
    ) {
        Column {
            if (!m.out && m.from.isNotBlank()) {
                Text(
                    m.from, style = MaterialTheme.typography.labelLarge, color = cs.primary,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = if (visual) 6.dp else 0.dp),
                )
            }
            Box {
                Column {
                    when (media) {
                        is Media.Photo -> PhotoContent(media) { onOpen(media) }
                        is Media.Video -> VideoContent(media) { onOpen(media) }
                        is Media.Voice -> VoiceContent(m, media)
                        is Media.Audio -> AudioContent(media)
                        is Media.Doc -> DocContent(media)
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

@Composable
private fun MetaRow(
    m: UiMsg, modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current.copy(alpha = 0.6f),
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
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

@Composable
private fun PhotoContent(p: Media.Photo, onClick: () -> Unit) {
    val f = rememberFile(p.file)
    val ratio = if (p.width > 0 && p.height > 0) (p.width.toFloat() / p.height).coerceIn(0.6f, 1.8f) else 1f
    Box(Modifier.fillMaxWidth().aspectRatio(ratio).background(Color.Black.copy(alpha = 0.1f)).clickable(onClick = onClick)) {
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

@Composable
private fun VideoContent(v: Media.Video, onClick: () -> Unit) {
    val ratio = if (v.width > 0 && v.height > 0) (v.width.toFloat() / v.height).coerceIn(0.6f, 1.8f) else 1.33f
    Box(Modifier.fillMaxWidth().aspectRatio(ratio).clickable(onClick = onClick)) {
        VideoThumb(v, Modifier.fillMaxSize())
        PlayOverlay(v.file)
        DurationChip(v, Modifier.align(Alignment.TopStart).padding(6.dp))
    }
}

@Composable
private fun RoundVideoMessage(m: UiMsg, v: Media.Video, onOpen: (Media) -> Unit) {
    Box(Modifier.size(200.dp).clip(CircleShape).clickable { onOpen(v) }) {
        VideoThumb(v, Modifier.fillMaxSize())
        PlayOverlay(v.file)
        DurationChip(v, Modifier.align(Alignment.TopCenter).padding(top = 10.dp))
        MetaPill(m, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
    }
}

@Composable
private fun StickerMessage(m: UiMsg, s: Media.Sticker) {
    val f = rememberFile(if (s.renderable) s.file else s.thumb)
    Box(Modifier.size(160.dp)) {
        val path = f?.path
        if (path != null) {
            AsyncImage(File(path), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } else {
            Text(s.emoji, fontSize = 64.sp, modifier = Modifier.align(Alignment.Center))
        }
        MetaPill(m, Modifier.align(Alignment.BottomEnd))
    }
}

// ---------- Голосовые / аудио ----------

@Composable
private fun PlayButton(fileId: Int, playing: Boolean, onToggle: (path: String) -> Unit) {
    val repo = LocalRepo.current
    val f = rememberFile(fileId)
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(cs.primary).clickable {
            val p = f?.path
            if (p != null) onToggle(p) else repo.download(fileId)
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
private fun WaveformBar(data: ByteArray, progress: Float, modifier: Modifier, onSeek: (Float) -> Unit) {
    val bars = remember(data) { Waveform.display(data, 40) }
    val active = MaterialTheme.colorScheme.primary
    val inactive = LocalContentColor.current.copy(alpha = 0.35f)
    val seek by rememberUpdatedState(onSeek)
    Canvas(modifier.pointerInput(Unit) { detectTapGestures { seek(it.x / size.width) } }) {
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
private fun VoiceContent(m: UiMsg, v: Media.Voice) {
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
    val active = ps.fileId == a.file
    val total = if (active && ps.durationMs > 0) ps.durationMs else a.duration * 1000L
    val progress = if (active && total > 0) (ps.positionMs / total.toFloat()).coerceIn(0f, 1f) else 0f

    Row(
        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PlayButton(a.file, active && ps.playing) { path -> player.toggle(a.file, path, a.duration * 1000L) }
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

@Composable
private fun DocContent(d: Media.Doc) {
    val ctx = LocalContext.current
    val repo = LocalRepo.current
    val f = rememberFile(d.file, auto = false)
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth()
            .clickable {
                val p = f?.path
                if (p != null) openFile(ctx, p, d.mime) else repo.download(d.file)
            }
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
