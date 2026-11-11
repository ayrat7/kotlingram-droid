package dev.example.tg.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.net.toUri
import dev.example.tg.MainActivity
import dev.example.tg.R
import java.util.concurrent.ConcurrentHashMap

/** Уведомления о сообщениях (MessagingStyle, быстрый ответ, «прочитано») и уведомление фоновой службы. */
class Notifier(private val ctx: Context) {
    companion object {
        const val CH_MESSAGES = "messages"
        const val CH_SERVICE = "service"
        const val SERVICE_ID = 1001
        private const val SUMMARY_ID = 1
        private const val GROUP = "dev.example.tg.MESSAGES"

        const val ACTION_REPLY = "dev.example.tg.REPLY"
        const val ACTION_READ = "dev.example.tg.READ"
        const val EXTRA_CHAT = "chat_id"
        const val EXTRA_MESSAGE = "message_id"
        const val KEY_REPLY = "reply_text"
    }

    private class Line(val sender: String?, val text: String, val time: Long)

    private class Conv(
        val title: String,
        val group: Boolean,
        var avatarPath: String?,
        var lastMessageId: Long,
        val lines: ArrayList<Line> = ArrayList(),
    )

    private val nm = NotificationManagerCompat.from(ctx)
    private val convs = ConcurrentHashMap<Long, Conv>()

    init {
        val sys = ctx.getSystemService(NotificationManager::class.java)
        sys.createNotificationChannel(
            NotificationChannel(CH_MESSAGES, "Сообщения", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Новые сообщения"
            },
        )
        sys.createNotificationChannel(
            NotificationChannel(CH_SERVICE, "Фоновое соединение", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Постоянное уведомление, пока приложение получает сообщения в фоне"
                setShowBadge(false)
            },
        )
    }

    private fun nid(chatId: Long) = 10_000 + Math.floorMod(chatId, 1_000_000_000L).toInt()

    private fun openIntent(chatId: Long): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java)
            .setData("kotlingram://chat/$chatId".toUri())
            .putExtra(EXTRA_CHAT, chatId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(ctx, nid(chatId), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun serviceNotification(): Notification {
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(ctx, CH_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_send)
            .setContentTitle("Kotlingram")
            .setContentText("Получение сообщений в фоне")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .build()
    }

    /** Новое входящее сообщение. sender — имя автора для групп, null для личных. */
    fun incoming(chatId: Long, title: String, group: Boolean, sender: String?, text: String, messageId: Long, avatarPath: String?) {
        val c = convs.getOrPut(chatId) { Conv(title, group, avatarPath, messageId) }
        c.avatarPath = avatarPath ?: c.avatarPath
        c.lastMessageId = messageId
        c.lines += Line(sender ?: title, text, System.currentTimeMillis())
        while (c.lines.size > 8) c.lines.removeAt(0)
        post(chatId, c, alert = true)
    }

    /** Наш ответ из уведомления — дописываем в переписку, чтобы убрать индикатор отправки. */
    fun ownReply(chatId: Long, text: String) {
        val c = convs[chatId] ?: return
        c.lines += Line(null, text, System.currentTimeMillis())
        post(chatId, c, alert = false)
    }

    private fun post(chatId: Long, c: Conv, alert: Boolean) {
        val me = Person.Builder().setName("Вы").build()
        val style = NotificationCompat.MessagingStyle(me).setGroupConversation(c.group)
        if (c.group) style.setConversationTitle(c.title)
        for (l in c.lines) {
            val person: Person? = l.sender?.let { Person.Builder().setName(it).build() }
            style.addMessage(l.text, l.time, person)
        }

        val replyInput = RemoteInput.Builder(KEY_REPLY).setLabel("Ответить").build()
        val replyIntent = Intent(ctx, NotificationReceiver::class.java)
            .setAction(ACTION_REPLY)
            .setData("kotlingram://reply/$chatId".toUri())
            .putExtra(EXTRA_CHAT, chatId)
            .putExtra(EXTRA_MESSAGE, c.lastMessageId)
        val replyPi = PendingIntent.getBroadcast(
            ctx, nid(chatId), replyIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val replyAction = NotificationCompat.Action.Builder(R.drawable.ic_stat_send, "Ответить", replyPi)
            .addRemoteInput(replyInput)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()

        val readIntent = Intent(ctx, NotificationReceiver::class.java)
            .setAction(ACTION_READ)
            .setData("kotlingram://read/$chatId".toUri())
            .putExtra(EXTRA_CHAT, chatId)
            .putExtra(EXTRA_MESSAGE, c.lastMessageId)
        val readPi = PendingIntent.getBroadcast(
            ctx, nid(chatId), readIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val readAction = NotificationCompat.Action.Builder(R.drawable.ic_stat_send, "Прочитано", readPi)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()

        val b = NotificationCompat.Builder(ctx, CH_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_send)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(GROUP)
            .setAutoCancel(true)
            .setOnlyAlertOnce(!alert)
            .setContentIntent(openIntent(chatId))
            .addAction(replyAction)
            .addAction(readAction)
        c.avatarPath?.let { path ->
            runCatching { BitmapFactory.decodeFile(path) }.getOrNull()?.let { b.setLargeIcon(it) }
        }

        try {
            nm.notify(nid(chatId), b.build())
            if (convs.size >= 2) postSummary()
        } catch (e: SecurityException) {
            // нет разрешения POST_NOTIFICATIONS
        }
    }

    private fun postSummary() {
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CH_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_send)
            .setContentTitle("Kotlingram")
            .setContentText("Новые сообщения")
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .build()
        try { nm.notify(SUMMARY_ID, n) } catch (e: SecurityException) { }
    }

    fun cancel(chatId: Long) {
        if (convs.remove(chatId) != null) nm.cancel(nid(chatId))
        if (convs.isEmpty()) nm.cancel(SUMMARY_ID)
    }

    fun cancelAll() {
        convs.clear()
        nm.cancelAll()
    }
}
