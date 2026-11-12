package dev.example.tg.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import dev.example.tg.data.Media
import dev.example.tg.data.Presence
import dev.example.tg.data.PresenceKind
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

fun formatDuration(sec: Int): String = String.format(Locale.ROOT, "%d:%02d", sec / 60, sec % 60)

fun formatDurationMs(ms: Long): String = formatDuration((ms / 1000).toInt())

fun formatSize(b: Long): String = when {
    b < 1024 -> "$b Б"
    b < 1024 * 1024 -> String.format(Locale.ROOT, "%.0f КБ", b / 1024.0)
    b < 1024L * 1024 * 1024 -> String.format(Locale.ROOT, "%.1f МБ", b / 1024.0 / 1024.0)
    else -> String.format(Locale.ROOT, "%.2f ГБ", b / 1024.0 / 1024.0 / 1024.0)
}

fun formatClock(t: Int): String =
    if (t == 0) "" else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t * 1000L))

fun dayOf(t: Int): LocalDate = Instant.ofEpochSecond(t.toLong()).atZone(ZoneId.systemDefault()).toLocalDate()

fun formatListTime(t: Int): String {
    if (t == 0) return ""
    val d = dayOf(t)
    return if (d == LocalDate.now()) formatClock(t)
    else d.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
}

fun dayLabel(d: LocalDate): String {
    val today = LocalDate.now()
    return when {
        d == today -> "Сегодня"
        d == today.minusDays(1) -> "Вчера"
        d.year == today.year -> d.format(DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault()))
        else -> d.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()))
    }
}

fun openFile(ctx: Context, path: String, mime: String?) {
    try {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", File(path))
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime?.takeIf { it.isNotBlank() } ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, null))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, "Нет приложения для открытия файла", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(ctx, "Не удалось открыть файл", Toast.LENGTH_SHORT).show()
    }
}

/** Короткая подпись вложения (для баннеров ответа/редактирования). */
fun mediaLabel(m: Media?): String = when (m) {
    is Media.Photo -> "Фото"
    is Media.Video -> if (m.round) "Видеосообщение" else if (m.gif) "GIF" else "Видео"
    is Media.Voice -> "Голосовое сообщение"
    is Media.Audio -> m.title
    is Media.Doc -> m.name
    is Media.Sticker -> m.emoji + " Стикер"
    null -> ""
}

private fun lastSeenLabel(t: Int): String {
    val now = (System.currentTimeMillis() / 1000).toInt()
    val diff = now - t
    return when {
        diff < 60 -> "только что"
        diff < 3600 -> "${diff / 60} мин. назад"
        else -> {
            val d = dayOf(t)
            val today = LocalDate.now()
            when {
                d == today -> "сегодня в ${formatClock(t)}"
                d == today.minusDays(1) -> "вчера в ${formatClock(t)}"
                else -> d.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())) + " в " + formatClock(t)
            }
        }
    }
}

/** «в сети», «был(а) недавно» и т.п. */
fun presenceText(p: Presence?): String = when (p?.kind) {
    PresenceKind.Online -> "в сети"
    PresenceKind.Offline -> "был(а) " + lastSeenLabel(p.lastSeen)
    PresenceKind.Recently -> "был(а) недавно"
    PresenceKind.LastWeek -> "был(а) на этой неделе"
    PresenceKind.LastMonth -> "был(а) в этом месяце"
    PresenceKind.Long -> "был(а) давно"
    PresenceKind.Bot -> "бот"
    else -> ""
}
