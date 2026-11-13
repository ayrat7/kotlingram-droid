package dev.example.tg.media

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dev.example.tg.TgApplication

/**
 * Показывает системное уведомление плеера с кнопками (шторка, экран блокировки, наушники).
 * Сам звук играет AudioPlayer из приложения — сервис только держит MediaSession.
 */
class MusicService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = (application as TgApplication).player
        session = MediaSession.Builder(this, player.sessionPlayer).setId("kotlingram_music").build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }

    companion object {
        fun start(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, MusicService::class.java))
            } catch (e: Exception) {
                // из фона запуск может быть запрещён — тогда музыка просто играет без уведомления
            }
        }
    }
}
