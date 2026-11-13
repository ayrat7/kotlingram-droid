package dev.example.tg.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dev.example.tg.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

enum class RepeatMode { Off, All, One }

/**
 * Один общий плеер: голосовые сообщения, аудио из чатов и музыка профилей. Только главный поток.
 * Музыка (track != null) играет очередью: следующий/предыдущий, повтор, перемешивание,
 * кнопки в шторке уведомлений через [MusicService].
 */
class AudioPlayer(private val ctx: Context) {
    data class State(
        val fileId: Int = -1,
        val playing: Boolean = false,
        val positionMs: Long = 0,
        val durationMs: Long = 0,
        /** текущий трек музыки; для голосовых null */
        val track: Track? = null,
        val queue: List<Track> = emptyList(),
        val index: Int = -1,
        val repeat: RepeatMode = RepeatMode.Off,
        val shuffle: Boolean = false,
        /** ждём, пока TDLib докачает файл трека */
        val loading: Boolean = false,
    )

    val state = MutableStateFlow(State())
    /** Раскрыт ли полноэкранный плеер. */
    val expanded = MutableStateFlow(false)

    /** Путь к скачанному файлу (null, если не скачан) и запрос на скачивание — подключается в TgApplication. */
    var pathOf: (Int) -> String? = { null }
    var download: (Int) -> Unit = {}

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ticker: Job? = null
    private var knownDuration = 0L
    private var pendingFile = -1
    private var order: List<Int> = emptyList() // порядок индексов очереди с учётом shuffle

    val exo: ExoPlayer = ExoPlayer.Builder(ctx)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    /** Для MediaSession: добавляет кнопки «следующий/предыдущий», ведущие в нашу очередь. */
    val sessionPlayer: Player = object : ForwardingPlayer(exo) {
        override fun getAvailableCommands(): Player.Commands {
            val b = super.getAvailableCommands().buildUpon()
            if (state.value.queue.size > 1) b.addAll(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_PREVIOUS)
            return b.build()
        }

        override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)
        override fun seekToNext() = this@AudioPlayer.next()
        override fun seekToNextMediaItem() = this@AudioPlayer.next()
        override fun seekToPrevious() = this@AudioPlayer.prev()
        override fun seekToPreviousMediaItem() = this@AudioPlayer.prev()
        override fun hasNextMediaItem(): Boolean = state.value.queue.size > 1
        override fun hasPreviousMediaItem(): Boolean = state.value.queue.size > 1
    }

    init {
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                state.update { it.copy(playing = isPlaying) }
                ticker?.cancel()
                if (isPlaying) {
                    if (state.value.track != null) MusicService.start(ctx)
                    ticker = scope.launch {
                        while (isActive) {
                            sync()
                            delay(100)
                        }
                    }
                }
                sync()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    val s = state.value
                    if (s.track != null && s.queue.isNotEmpty()) {
                        autoNext()
                    } else {
                        exo.pause()
                        exo.seekTo(0)
                        state.update { it.copy(playing = false, positionMs = 0) }
                    }
                } else if (playbackState == Player.STATE_READY) {
                    sync()
                }
            }
        })
    }

    private fun sync() {
        val d = exo.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: knownDuration
        state.update { it.copy(positionMs = exo.currentPosition, durationMs = d) }
    }

    // ---------- Голосовые ----------

    fun toggle(fileId: Int, path: String, durationMs: Long) {
        if (state.value.fileId == fileId && state.value.track == null) {
            if (exo.isPlaying) exo.pause() else exo.play()
            return
        }
        pendingFile = -1
        setSpeech()
        knownDuration = durationMs
        state.value = State(fileId = fileId, durationMs = durationMs)
        exo.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
        exo.prepare()
        exo.play()
    }

    // ---------- Музыка ----------

    /** Играть очередь [queue] начиная с [index]. Если файла ещё нет — скачиваем и стартуем сами. */
    fun playQueue(queue: List<Track>, index: Int) {
        if (queue.isEmpty() || index !in queue.indices) return
        val cur = state.value
        // тап по уже выбранному треку — пауза/продолжить
        if (cur.track?.fileId == queue[index].fileId && !cur.loading) {
            if (exo.isPlaying) exo.pause() else exo.play()
            return
        }
        state.value = cur.copy(queue = queue, index = index, track = queue[index], fileId = queue[index].fileId)
        rebuildOrder()
        startCurrent()
    }

    private fun rebuildOrder() {
        val s = state.value
        val idx = s.queue.indices.toMutableList()
        if (s.shuffle) {
            idx.remove(s.index)
            idx.shuffle()
            idx.add(0, s.index)
        }
        order = idx
    }

    private fun startCurrent() {
        val s = state.value
        val t = s.track ?: return
        val path = pathOf(t.fileId)
        if (path == null) {
            pendingFile = t.fileId
            exo.pause()
            state.update { it.copy(loading = true, playing = false, positionMs = 0, durationMs = t.durationSec * 1000L) }
            download(t.fileId)
            return
        }
        pendingFile = -1
        setMusic()
        knownDuration = t.durationSec * 1000L
        state.update { it.copy(loading = false, positionMs = 0, durationMs = knownDuration, fileId = t.fileId) }
        val meta = MediaMetadata.Builder().setTitle(t.title).setArtist(t.performer.ifBlank { null }).build()
        exo.setMediaItem(MediaItem.Builder().setUri(Uri.fromFile(File(path))).setMediaMetadata(meta).build())
        exo.prepare()
        exo.play()
        // заранее просим соседний трек, чтобы «следующий» срабатывал сразу
        neighbour(+1)?.let { if (pathOf(it.fileId) == null) download(it.fileId) }
    }

    /** Вызывать при обновлении файлов в TDLib: докачанный трек стартует автоматически. */
    fun checkPending() {
        val id = pendingFile
        if (id != -1 && pathOf(id) != null) startCurrent()
    }

    private fun neighbour(step: Int): Track? {
        val s = state.value
        if (s.queue.isEmpty()) return null
        val pos = order.indexOf(s.index).takeIf { it >= 0 } ?: return null
        val n = pos + step
        val j = when {
            n in order.indices -> order[n]
            s.repeat == RepeatMode.All -> order[Math.floorMod(n, order.size)]
            else -> return null
        }
        return s.queue[j]
    }

    private fun go(step: Int) {
        val s = state.value
        val pos = order.indexOf(s.index).takeIf { it >= 0 } ?: return
        val n = pos + step
        val j = when {
            n in order.indices -> order[n]
            else -> order[Math.floorMod(n, order.size)] // по кругу при ручном переключении
        }
        state.update { it.copy(index = j, track = it.queue[j], fileId = it.queue[j].fileId) }
        startCurrent()
    }

    private fun autoNext() {
        val s = state.value
        when {
            s.repeat == RepeatMode.One -> { exo.seekTo(0); exo.play() }
            neighbour(+1) != null -> go(+1)
            else -> {
                exo.pause()
                exo.seekTo(0)
                state.update { it.copy(playing = false, positionMs = 0) }
            }
        }
    }

    fun next() { if (state.value.queue.isNotEmpty()) go(+1) }

    /** Если трек играет больше 3 секунд — в начало, иначе предыдущий. */
    fun prev() {
        if (state.value.queue.isEmpty()) return
        if (exo.currentPosition > 3000) exo.seekTo(0) else go(-1)
    }

    fun toggleRepeat() {
        state.update { it.copy(repeat = when (it.repeat) { RepeatMode.Off -> RepeatMode.All; RepeatMode.All -> RepeatMode.One; RepeatMode.One -> RepeatMode.Off }) }
    }

    fun toggleShuffle() {
        state.update { it.copy(shuffle = !it.shuffle) }
        rebuildOrder()
    }

    fun playPause() {
        if (state.value.track == null && state.value.fileId == -1) return
        if (exo.isPlaying) exo.pause() else exo.play()
    }

    fun seekToMs(ms: Long) {
        exo.seekTo(ms.coerceAtLeast(0))
        sync()
    }

    private fun setMusic() {
        exo.setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true,
        )
    }

    private fun setSpeech() {
        exo.setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true,
        )
    }

    // ---------- Общее ----------

    fun seekToFraction(fileId: Int, fraction: Float) {
        val s = state.value
        if (s.fileId != fileId || s.durationMs <= 0) return
        exo.seekTo((fraction.coerceIn(0f, 1f) * s.durationMs).toLong())
        sync()
    }

    fun pause() {
        if (exo.isPlaying) exo.pause()
    }

    /** Остановить только голосовое: музыка продолжает играть при выходе из чата. */
    fun stopVoice() {
        if (state.value.track == null) stop()
    }

    fun stop() {
        ticker?.cancel()
        pendingFile = -1
        exo.stop()
        exo.clearMediaItems()
        expanded.value = false
        state.value = State(repeat = state.value.repeat, shuffle = state.value.shuffle)
    }
}
