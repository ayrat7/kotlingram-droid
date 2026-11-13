package dev.example.tg.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.example.tg.data.Track
import dev.example.tg.media.RepeatMode
import java.io.File

/** Очередь аудио текущего чата (от старых к новым): отсюда «следующий» и «предыдущий». */
val LocalAudioQueue = compositionLocalOf<List<Track>> { emptyList() }

@Composable
private fun Cover(fileId: Int?, size: androidx.compose.ui.unit.Dp, round: Int = 10) {
    val f = rememberFile(fileId)
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(size).clip(RoundedCornerShape(round.dp)).background(cs.primary.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center,
    ) {
        val p = f?.path
        if (p != null) AsyncImage(File(p), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Filled.MusicNote, null, tint = cs.primary, modifier = Modifier.size(size / 2))
    }
}

/** Тонкая полоса плеера под верхней панелью: показывается, пока выбран трек музыки. */
@Composable
fun MiniPlayer(modifier: Modifier = Modifier) {
    val player = LocalPlayer.current
    val ps by player.state.collectAsState()
    val t = ps.track ?: return
    val cs = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { player.expanded.value = true }.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(t.cover, 36.dp, 8)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (ps.loading) "Загрузка…" else t.performer.ifBlank { "Аудио" },
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
                )
            }
            IconButton({ player.prev() }) { Icon(Icons.Filled.SkipPrevious, "Назад") }
            IconButton({ player.playPause() }) {
                if (ps.loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Icon(if (ps.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Пауза")
            }
            IconButton({ player.next() }) { Icon(Icons.Filled.SkipNext, "Дальше") }
            IconButton({ player.stop() }) { Icon(Icons.Filled.Close, "Закрыть") }
        }
        val frac = if (ps.durationMs > 0) (ps.positionMs / ps.durationMs.toFloat()).coerceIn(0f, 1f) else 0f
        LinearProgressIndicator({ frac }, Modifier.fillMaxWidth().height(2.dp), trackColor = androidx.compose.ui.graphics.Color.Transparent)
    }
}

/** Полноэкранный плеер: обложка, перемотка, повтор, перемешивание, очередь. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingHost() {
    val player = LocalPlayer.current
    val open by player.expanded.collectAsState()
    val ps by player.state.collectAsState()
    val t = ps.track
    if (!open || t == null) return
    val cs = MaterialTheme.colorScheme
    var showQueue by remember { mutableStateOf(false) }
    var drag by remember { mutableStateOf<Float?>(null) }

    ModalBottomSheet(onDismissRequest = { player.expanded.value = false }) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showQueue) {
                Text("Очередь", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    itemsIndexed(ps.queue) { i, q ->
                        ListItem(
                            modifier = Modifier.clickable { player.playQueue(ps.queue, i) },
                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                            leadingContent = { Cover(q.cover, 44.dp, 8) },
                            headlineContent = { Text(q.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (i == ps.index) FontWeight.Bold else FontWeight.Normal) },
                            supportingContent = { Text(q.performer.ifBlank { formatDuration(q.durationSec) }, maxLines = 1) },
                            trailingContent = { if (i == ps.index) Icon(Icons.Filled.GraphicEq, null, tint = cs.primary) },
                        )
                    }
                }
            } else {
                Cover(t.cover, 260.dp, 24)
                Spacer(Modifier.height(20.dp))
                Text(t.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (t.performer.isNotBlank()) Text(t.performer, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                val total = if (ps.durationMs > 0) ps.durationMs else t.durationSec * 1000L
                val pos = drag ?: if (total > 0) ps.positionMs / total.toFloat() else 0f
                Slider(
                    value = pos.coerceIn(0f, 1f),
                    onValueChange = { drag = it },
                    onValueChangeFinished = { drag?.let { player.seekToMs((it * total).toLong()) }; drag = null },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatDurationMs((pos * total).toLong()), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    Text(formatDurationMs(total), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton({ player.toggleShuffle() }) {
                    Icon(Icons.Filled.Shuffle, "Перемешать", tint = if (ps.shuffle) cs.primary else cs.onSurfaceVariant)
                }
                IconButton({ player.prev() }) { Icon(Icons.Filled.SkipPrevious, "Назад", Modifier.size(32.dp)) }
                FilledIconButton({ player.playPause() }, Modifier.size(64.dp)) {
                    if (ps.loading) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = cs.onPrimary)
                    else Icon(if (ps.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Играть", Modifier.size(36.dp))
                }
                IconButton({ player.next() }) { Icon(Icons.Filled.SkipNext, "Дальше", Modifier.size(32.dp)) }
                IconButton({ player.toggleRepeat() }) {
                    Icon(
                        if (ps.repeat == RepeatMode.One) Icons.Filled.RepeatOne else Icons.Filled.Repeat, "Повтор",
                        tint = if (ps.repeat == RepeatMode.Off) cs.onSurfaceVariant else cs.primary,
                    )
                }
            }
            TextButton({ showQueue = !showQueue }) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, null)
                Spacer(Modifier.width(8.dp))
                Text(if (showQueue) "К плееру" else "Очередь (${ps.queue.size})")
            }
        }
    }
}

/** Музыка профиля: список треков, тап — играть всю подборку. */
@Composable
fun ProfileMusicSection(userId: Long) {
    val repo = LocalRepo.current
    val player = LocalPlayer.current
    val all by repo.profileAudios.collectAsState()
    val ps by player.state.collectAsState()
    LaunchedEffect(userId) { repo.loadProfileAudios(userId) }
    val tracks = all[userId].orEmpty()
    if (tracks.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    Text(
        "Музыка · ${tracks.size}", style = MaterialTheme.typography.titleSmall, color = cs.primary,
        fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
    )
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer.copy(alpha = 0.85f)),
    ) {
        tracks.forEachIndexed { i, t ->
            val current = ps.track?.fileId == t.fileId
            ListItem(
                modifier = Modifier.clickable { player.playQueue(tracks, i) },
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                leadingContent = { Cover(t.cover, 44.dp, 8) },
                headlineContent = { Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (current) FontWeight.Bold else FontWeight.Normal) },
                supportingContent = { Text(t.performer.ifBlank { formatDuration(t.durationSec) }, maxLines = 1) },
                trailingContent = {
                    Icon(if (current && ps.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = cs.primary)
                },
            )
        }
    }
}
