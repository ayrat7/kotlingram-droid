package dev.example.tg.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

enum class ThemeMode { System, Light, Dark }

/** Локальные настройки приложения (не относятся к аккаунту Telegram). */
class AppSettings(ctx: Context) {
    private val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val notifications = MutableStateFlow(p.getBoolean("notifications", true))
    val preview = MutableStateFlow(p.getBoolean("preview", true))
    val background = MutableStateFlow(p.getBoolean("background", true))
    val dynamicColor = MutableStateFlow(p.getBoolean("dynamic", true))
    /** Размытие верхней и нижней панелей («стекло»). */
    val blur = MutableStateFlow(p.getBoolean("blur", true))
    /** Узор на фоне чата. */
    val pattern = MutableStateFlow(p.getBoolean("pattern", true))
    val theme = MutableStateFlow(
        runCatching { ThemeMode.valueOf(p.getString("theme", null) ?: "System") }.getOrDefault(ThemeMode.System),
    )

    fun setNotifications(v: Boolean) { notifications.value = v; p.edit().putBoolean("notifications", v).apply() }
    fun setPreview(v: Boolean) { preview.value = v; p.edit().putBoolean("preview", v).apply() }
    fun setBackground(v: Boolean) { background.value = v; p.edit().putBoolean("background", v).apply() }
    fun setDynamicColor(v: Boolean) { dynamicColor.value = v; p.edit().putBoolean("dynamic", v).apply() }
    fun setBlur(v: Boolean) { blur.value = v; p.edit().putBoolean("blur", v).apply() }
    fun setPattern(v: Boolean) { pattern.value = v; p.edit().putBoolean("pattern", v).apply() }
    fun setTheme(v: ThemeMode) { theme.value = v; p.edit().putString("theme", v.name).apply() }
}
