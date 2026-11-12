package dev.example.tg.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import dev.example.tg.TgApplication
import dev.example.tg.notify.BackgroundService
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.example.tg.data.Auth

private enum class Screen { Chats, Profile, Settings }

@Composable
fun TgRoot() {
    val repo = LocalRepo.current
    val auth by repo.auth.collectAsState()
    val error by repo.error.collectAsState()
    val busy by repo.busy.collectAsState()
    val openId by repo.openChatId.collectAsState()
    val info by repo.info.collectAsState()
    val pending by repo.pendingOpen.collectAsState()
    val ctx = LocalContext.current
    val app = ctx.applicationContext as TgApplication
    val bgEnabled by app.settings.background.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var screen by remember { mutableStateOf(Screen.Chats) }

    BackHandler(enabled = openId != null) { repo.close() }
    BackHandler(enabled = openId == null && screen != Screen.Chats) {
        screen = Screen.Chats
    }
    // регистрируется последним, поэтому закрывает чужой профиль раньше, чем чат
    val viewedUser by repo.viewedUserId.collectAsState()
    BackHandler(enabled = viewedUser != null) { repo.closeUser() }

    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it, withDismissAction = true)
            repo.error.value = null
        }
    }
    LaunchedEffect(info) {
        info?.let {
            snackbar.showSnackbar(it, duration = SnackbarDuration.Short)
            repo.info.value = null
        }
    }

    // открыть чат по нажатию на уведомление
    LaunchedEffect(auth, pending) {
        val id = pending
        if (auth == Auth.Ready && id != null) {
            screen = Screen.Chats
            repo.open(id)
            repo.pendingOpen.value = null
        }
    }

    // фоновая служба живёт, пока мы вошли в аккаунт и это не отключено в настройках
    LaunchedEffect(auth, bgEnabled) {
        if (auth == Auth.Ready && bgEnabled) BackgroundService.start(ctx) else BackgroundService.stop(ctx)
    }

    // один раз просим разрешение на уведомления (Android 13+)
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(auth) {
        if (auth == Auth.Ready && Build.VERSION.SDK_INT >= 33) {
            val prefs = ctx.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
            val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted && !prefs.getBoolean("asked_notif", false)) {
                prefs.edit().putBoolean("asked_notif", true).apply()
                askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            if (auth != Auth.Ready) {
                AuthScreen(auth)
            } else {
                val id = openId
                when {
                    id != null -> ChatScreen(id)
                    screen == Screen.Profile -> ProfileScreen(onBack = { screen = Screen.Chats })
                    screen == Screen.Settings -> SettingsScreen(
                        onBack = { screen = Screen.Chats },
                        onProfile = { screen = Screen.Profile },
                    )
                    else -> ChatsScreen(
                        onProfile = { screen = Screen.Profile },
                        onSettings = { screen = Screen.Settings },
                    )
                }
                // чужой профиль поверх чата — чтобы не терять черновик и положение ленты
                UserProfileHost()
            }
            SnackbarHost(
                snackbar,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding(),
            ) { data ->
                if (data.visuals.withDismissAction) {
                    Snackbar(
                        data,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        dismissActionContentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                } else {
                    Snackbar(data)
                }
            }
        }
    }
}
