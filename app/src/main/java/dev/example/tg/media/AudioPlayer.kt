package dev.example.tg.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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

/** Один общий плеер для голосовых и аудио: одновременно играет только одно сообщение. Только главный поток. */
class AudioPlayer(ctx: Context) {
    data class State(
        val fileId: Int = -1,
        val playing: Boolean = false,
        val positionMs: Long = 0,
        val durationMs: Long = 0,
    )

    val state = MutableStateFlow(State())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ticker: Job? = null
    private var knownDuration = 0L

    private val player: ExoPlayer = ExoPlayer.Builder(ctx)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                state.update { it.copy(playing = isPlaying) }
                ticker?.cancel()
                if (isPlaying) {
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
                    player.pause()
                    player.seekTo(0)
                    state.update { it.copy(playing = false, positionMs = 0) }
                } else if (playbackState == Player.STATE_READY) {
                    sync()
                }
            }
        })
    }

    private fun sync() {
        val d = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: knownDuration
        state.update { it.copy(positionMs = player.currentPosition, durationMs = d) }
    }

    fun toggle(fileId: Int, path: String, durationMs: Long) {
        if (state.value.fileId == fileId) {
            if (player.isPlaying) player.pause() else player.play()
            return
        }
        knownDuration = durationMs
        state.value = State(fileId = fileId, durationMs = durationMs)
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
        player.prepare()
        player.play()
    }

    fun seekToFraction(fileId: Int, fraction: Float) {
        val s = state.value
        if (s.fileId != fileId || s.durationMs <= 0) return
        player.seekTo((fraction.coerceIn(0f, 1f) * s.durationMs).toLong())
        sync()
    }

    fun pause() {
        if (player.isPlaying) player.pause()
    }

    fun stop() {
        ticker?.cancel()
        player.stop()
        player.clearMediaItems()
        state.value = State()
    }
}
