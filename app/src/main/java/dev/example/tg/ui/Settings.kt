package dev.example.tg.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.example.tg.TgApplication
import dev.example.tg.data.ThemeMode
import dev.example.tg.notify.BackgroundService

private fun notificationsAllowed(ctx: Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun ignoringBattery(ctx: Context): Boolean =
    ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

@Composable
private fun SectionTitle(t: String) {
    Text(
        t, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onChange(!checked) },
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) ({ Text(subtitle) }) else null,
        trailingContent = { Switch(checked, onChange) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onProfile: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as TgApplication
    val repo = app.repo
    val st = app.settings
    val me by repo.me.collectAsState()
    val notifications by st.notifications.collectAsState()
    val preview by st.preview.collectAsState()
    val background by st.background.collectAsState()
    val dynamic by st.dynamicColor.collectAsState()
    val blur by st.blur.collectAsState()
    val pattern by st.pattern.collectAsState()
    val theme by st.theme.collectAsState()
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    // состояния системных разрешений обновляем при возврате на экран
    var permOk by remember { mutableStateOf(notificationsAllowed(ctx)) }
    var batteryOk by remember { mutableStateOf(ignoringBattery(ctx)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                permOk = notificationsAllowed(ctx)
                batteryOk = ignoringBattery(ctx)
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permOk = it }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            ListItem(
                modifier = Modifier.clickable(onClick = onProfile),
                leadingContent = { Avatar(me?.avatarSmall, me?.id ?: 0L, me?.fullName.orEmpty(), 56.dp) },
                headlineContent = {
                    Text(me?.fullName.orEmpty().ifBlank { "Профиль" }, fontWeight = FontWeight.Bold)
                },
                supportingContent = {
                    val sub = me?.username?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: "Изменить имя, фото и описание"
                    Text(sub)
                },
            )
            HorizontalDivider()

            SectionTitle("Уведомления")
            if (!permOk) {
                ListItem(
                    modifier = Modifier.clickable {
                        if (Build.VERSION.SDK_INT >= 33) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    leadingContent = { Icon(Icons.Filled.Notifications, null) },
                    headlineContent = { Text("Разрешить уведомления") },
                    supportingContent = { Text("Без разрешения сообщения в фоне не будут показываться") },
                )
            }
            SwitchRow("Уведомления о сообщениях", null, notifications) { st.setNotifications(it) }
            SwitchRow("Показывать текст сообщений", "Выключите, чтобы в уведомлении было только «Новое сообщение»", preview) {
                st.setPreview(it)
            }
            ListItem(
                modifier = Modifier.clickable {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName),
                    )
                },
                headlineContent = { Text("Звук и вибрация") },
                supportingContent = { Text("Системные настройки каналов уведомлений") },
            )

            SectionTitle("Фоновая работа")
            SwitchRow(
                "Получать сообщения в фоне",
                "Постоянное тихое уведомление и работающее соединение, пока вы вошли в аккаунт",
                background,
            ) {
                st.setBackground(it)
                if (it && repo.isLoggedIn()) BackgroundService.start(ctx) else BackgroundService.stop(ctx)
            }
            ListItem(
                modifier = Modifier.clickable {
                    val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                    runCatching { ctx.startActivity(i) }.onFailure {
                        ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                },
                leadingContent = { Icon(Icons.Filled.BatteryChargingFull, null) },
                headlineContent = { Text("Не ограничивать батарею") },
                supportingContent = {
                    Text(if (batteryOk) "Включено — система не будет усыплять приложение" else "Рекомендуется, иначе на некоторых телефонах сообщения приходят с задержкой")
                },
            )

            SectionTitle("Оформление")
            val modes = listOf(ThemeMode.System to "Системная", ThemeMode.Light to "Светлая", ThemeMode.Dark to "Тёмная")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                modes.forEachIndexed { i, (mode, label) ->
                    SegmentedButton(
                        selected = theme == mode,
                        onClick = { st.setTheme(mode) },
                        shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                    ) { Text(label) }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SwitchRow("Цвета из обоев", "Material You: палитра подстраивается под систему", dynamic) { st.setDynamicColor(it) }
            }

            SwitchRow("Размытие панелей", "Эффект стекла у верхней и нижней панелей (Android 12+)", blur) { st.setBlur(it) }
            SwitchRow("Узор на фоне чата", "Лёгкие рисунки на градиентном фоне", pattern) { st.setPattern(it) }

            SectionTitle("Аккаунт")
            ListItem(
                modifier = Modifier.clickable { confirmLogout = true },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.Logout, null, tint = MaterialTheme.colorScheme.error) },
                headlineContent = { Text("Выйти", color = MaterialTheme.colorScheme.error) },
            )
            ListItem(
                modifier = Modifier.clickable { confirmReset = true },
                leadingContent = { Icon(Icons.Filled.VpnKey, null) },
                headlineContent = { Text("Сбросить API ключи") },
                supportingContent = { Text("Удалит ключи и локальную базу, потребуется новый вход") },
            )
            Text(
                "Kotlingram 3.0",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp).align(Alignment.CenterHorizontally),
            )
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Выйти из аккаунта?") },
            text = { Text("Локальные данные будут удалены, понадобится повторный вход.") },
            confirmButton = {
                TextButton({ confirmLogout = false; repo.logout() }) { Text("Выйти", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton({ confirmLogout = false }) { Text("Отмена") } },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Сбросить API ключи?") },
            text = { Text("Ключи и локальная база TDLib будут удалены.") },
            confirmButton = {
                TextButton({ confirmReset = false; repo.resetKeys() }) { Text("Сбросить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton({ confirmReset = false }) { Text("Отмена") } },
        )
    }
}
