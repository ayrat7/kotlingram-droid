package dev.example.tg.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.example.tg.MainActivity
import dev.example.tg.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

/**
 * Голосовой движок звонков. Telegram-звонки шифруются и идут через tgcalls (нативная библиотека),
 * которой нет в предсобранном TDLib. Здесь — точка подключения: реализуйте интерфейс поверх tgcalls
 * и присвойте TgApplication.repo.calls.engine. Пока стоит [NoCallEngine], сигнализация и экран звонка
 * работают, но звук не идёт, поэтому звонки не запускаются (см. [CallManager.start]).
 */
interface CallEngine {
    val available: Boolean

    /** Звонок установлен: ключи и серверы готовы. */
    fun start(callId: Int, outgoing: Boolean, ready: TdApi.CallStateReady)

    /** Служебные данные от собеседника (UpdateNewCallSignalingData). */
    fun onSignaling(callId: Int, data: ByteArray)

    fun setMuted(muted: Boolean)
    fun setSpeaker(on: Boolean)
    fun stop()
}

object NoCallEngine : CallEngine {
    override val available = false
    override fun start(callId: Int, outgoing: Boolean, ready: TdApi.CallStateReady) {}
    override fun onSignaling(callId: Int, data: ByteArray) {}
    override fun setMuted(muted: Boolean) {}
    override fun setSpeaker(on: Boolean) {}
    override fun stop() {}
}

/** Состояние звонка по данным TDLib + рингтон и уведомление о входящем. */
class CallManager(private val repo: TgRepo, private val app: Context) {
    val call = MutableStateFlow<UiCall?>(null)
    val muted = MutableStateFlow(false)
    val speaker = MutableStateFlow(false)

    @Volatile var engine: CallEngine = NoCallEngine

    private var ringtone: Ringtone? = null
    private var engineStarted = false
    private val nm = NotificationManagerCompat.from(app)

    companion object {
        const val CH_CALLS = "calls"
        private const val NOTIF_ID = 2001
    }

    init {
        app.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CH_CALLS, "Звонки", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Входящие звонки"
            },
        )
    }

    private fun protocol() = TdApi.CallProtocol().apply {
        udpP2p = true
        udpReflector = true
        minLayer = 65
        maxLayer = 92
        libraryVersions = arrayOf("11.0.0", "10.0.0", "9.0.0", "8.0.0", "7.0.0", "6.0.0", "5.0.0", "4.0.3", "4.0.2", "4.0.1", "4.0.0")
    }

    val canCall: Boolean get() = engine.available

    fun start(userId: Long) {
        if (call.value != null) return
        if (!engine.available) {
            repo.info.value = "Звонки: не подключён голосовой движок tgcalls (см. README)"
            return
        }
        repo.scope.launch {
            try {
                repo.call(TdApi.CreateCall().apply {
                    this.userId = userId
                    protocol = protocol()
                    isVideo = false
                })
            } catch (e: Exception) {
                repo.error.value = "Не удалось позвонить: ${e.message}"
            }
        }
    }

    fun accept() {
        val c = call.value ?: return
        if (!engine.available) return
        repo.scope.launch {
            try {
                repo.call(TdApi.AcceptCall().apply { callId = c.id; protocol = protocol() })
            } catch (e: Exception) {
                repo.error.value = "Не удалось принять звонок: ${e.message}"
            }
        }
    }

    /** «Отклонить» для входящего и «Завершить» для остальных. */
    fun hangup() {
        val c = call.value ?: return
        val secs = if (c.startedAt > 0) ((System.currentTimeMillis() - c.startedAt) / 1000).toInt() else 0
        repo.scope.launch {
            runCatching {
                repo.call(TdApi.DiscardCall().apply {
                    callId = c.id
                    isDisconnected = false
                    duration = secs
                    isVideo = false
                    connectionId = 0L
                })
            }
            finish(c.id, "Завершён")
        }
    }

    fun toggleMute() {
        muted.value = !muted.value
        engine.setMuted(muted.value)
    }

    fun toggleSpeaker() {
        speaker.value = !speaker.value
        engine.setSpeaker(speaker.value)
    }

    fun sendSignaling(callId: Int, data: ByteArray) {
        repo.scope.launch {
            runCatching { repo.call(TdApi.SendCallSignalingData().apply { this.callId = callId; this.data = data }) }
        }
    }

    fun onSignaling(u: TdApi.UpdateNewCallSignalingData) = engine.onSignaling(u.callId, u.data)

    fun onCall(c: TdApi.Call) {
        repo.scope.launch {
            val prev = call.value
            // уже идёт другой звонок — новый входящий отклоняем
            if (prev != null && prev.id != c.id && prev.phase != CallPhase.Ended) {
                if (c.state is TdApi.CallStatePending && !c.isOutgoing) {
                    runCatching {
                        repo.call(TdApi.DiscardCall().apply { callId = c.id; isDisconnected = false; duration = 0; isVideo = false; connectionId = 0L })
                    }
                }
                return@launch
            }
            val name = prev?.name?.takeIf { it.isNotBlank() } ?: repo.userName(c.userId)
            val avatar = prev?.avatar ?: runCatching {
                val u = repo.call(TdApi.GetUser().apply { userId = c.userId }) as TdApi.User
                repo.reg(u.profilePhoto?.small)
                u.profilePhoto?.small?.id
            }.getOrNull()

            when (val s = c.state) {
                is TdApi.CallStatePending -> {
                    val phase = if (c.isOutgoing) CallPhase.Calling else CallPhase.Ringing
                    val note = if (c.isOutgoing && s.isReceived) "Звонок…" else ""
                    call.value = UiCall(c.id, c.userId, name, avatar, c.isOutgoing, phase, note = note)
                    if (phase == CallPhase.Ringing) onRinging(name)
                }
                is TdApi.CallStateExchangingKeys -> {
                    stopRinging()
                    call.value = UiCall(c.id, c.userId, name, avatar, c.isOutgoing, CallPhase.Connecting)
                }
                is TdApi.CallStateReady -> {
                    stopRinging()
                    val started = prev?.startedAt?.takeIf { it > 0 } ?: System.currentTimeMillis()
                    call.value = UiCall(
                        c.id, c.userId, name, avatar, c.isOutgoing, CallPhase.Active,
                        emojis = s.emojis?.toList().orEmpty(), startedAt = started,
                    )
                    if (!engineStarted) {
                        engineStarted = true
                        engine.setMuted(muted.value)
                        engine.start(c.id, c.isOutgoing, s)
                    }
                }
                is TdApi.CallStateHangingUp -> call.value = prev?.copy(phase = CallPhase.Ended, note = "Завершение…")
                is TdApi.CallStateDiscarded -> finish(c.id, when (s.reason) {
                    is TdApi.CallDiscardReasonMissed -> "Пропущен"
                    is TdApi.CallDiscardReasonDeclined -> "Отклонён"
                    is TdApi.CallDiscardReasonDisconnected -> "Соединение потеряно"
                    else -> "Завершён"
                })
                is TdApi.CallStateError -> finish(c.id, "Ошибка: ${s.error.message}")
                else -> {}
            }
        }
    }

    private suspend fun finish(id: Int, note: String) {
        val cur = call.value
        if (cur != null && cur.id != id) return
        stopRinging()
        if (engineStarted) { engine.stop(); engineStarted = false }
        nm.cancel(NOTIF_ID)
        if (cur != null) call.value = cur.copy(phase = CallPhase.Ended, note = note)
        muted.value = false
        speaker.value = false
        delay(1500)
        if (call.value?.id == id) call.value = null
    }

    private fun onRinging(name: String) {
        runCatching {
            if (ringtone == null) {
                ringtone = RingtoneManager.getRingtone(app, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
                    ?.also { it.isLooping = true; it.play() }
            }
        }
        if (!repo.appForeground) {
            val open = PendingIntent.getActivity(
                app, 0, Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val n = NotificationCompat.Builder(app, CH_CALLS)
                .setSmallIcon(R.drawable.ic_stat_send)
                .setContentTitle(name.ifBlank { "Telegram" })
                .setContentText("Входящий звонок")
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .setContentIntent(open)
                .build()
            try { nm.notify(NOTIF_ID, n) } catch (e: SecurityException) { }
        }
    }

    private fun stopRinging() {
        runCatching { ringtone?.stop() }
        ringtone = null
        nm.cancel(NOTIF_ID)
    }
}
