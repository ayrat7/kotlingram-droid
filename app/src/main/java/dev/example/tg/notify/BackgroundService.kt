package dev.example.tg.notify

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.RemoteInput
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.example.tg.TgApplication

/**
 * Держит процесс живым, чтобы TDLib получал сообщения в фоне.
 * Без Google-пушей (нужен свой Firebase-проект) это единственный способ получать уведомления.
 */
class BackgroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val n = (application as TgApplication).repo.notifier.serviceNotification()
        try {
            ServiceCompat.startForeground(this, Notifier.SERVICE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } catch (e: Exception) {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    companion object {
        fun start(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, BackgroundService::class.java))
            } catch (e: Exception) {
                // запуск из фона может быть запрещён системой — повторим при открытии приложения
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, BackgroundService::class.java))
        }
    }
}

/** Кнопки «Ответить» и «Прочитано» в уведомлении. */
class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repo = (context.applicationContext as TgApplication).repo
        val chatId = intent.getLongExtra(Notifier.EXTRA_CHAT, 0L)
        val messageId = intent.getLongExtra(Notifier.EXTRA_MESSAGE, 0L)
        if (chatId == 0L) return
        val pending = goAsync()
        when (intent.action) {
            Notifier.ACTION_REPLY -> {
                val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Notifier.KEY_REPLY)?.toString().orEmpty()
                if (text.isBlank()) { pending.finish(); return }
                repo.replyFromNotification(chatId, messageId, text) { pending.finish() }
            }
            Notifier.ACTION_READ -> repo.markChatRead(chatId, messageId) { pending.finish() }
            else -> pending.finish()
        }
    }
}

/** После перезагрузки телефона поднимаем службу, если пользователь вошёл в аккаунт. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as TgApplication
        if (app.settings.background.value && app.repo.isLoggedIn()) BackgroundService.start(context)
    }
}
