package dev.example.tg.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.example.tg.data.Media
import dev.example.tg.data.PresenceKind
import dev.example.tg.data.UserCard

/** Оверлей с чужим профилем: показывается, пока репозиторий хранит viewedUserId. */
@Composable
fun UserProfileHost() {
    val repo = LocalRepo.current
    val id by repo.viewedUserId.collectAsState()
    val card by repo.viewedUser.collectAsState()
    // последняя карточка нужна, чтобы анимация закрытия не показывала пустой экран
    var last by remember { mutableStateOf<UserCard?>(null) }
    if (card != null) last = card
    AnimatedVisibility(
        visible = id != null,
        enter = slideInHorizontally { it } + fadeIn(),
        exit = slideOutHorizontally { it } + fadeOut(),
    ) {
        UserProfileScreen(if (id == null) last else card, onBack = { repo.closeUser() })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserProfileScreen(card: UserCard?, onBack: () -> Unit) {
    val repo = LocalRepo.current
    val chats by repo.chats.collectAsState()
    val clipboard = LocalClipboardManager.current
    var viewer by remember { mutableStateOf<Media?>(null) }

    fun copy(text: String) {
        clipboard.setText(AnnotatedString(text))
        repo.info.value = "Скопировано"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Профиль") },
                navigationIcon = {
                    IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
            )
        },
    ) { pad ->
        if (card == null) {
            Column(Modifier.fillMaxSize().padding(pad), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(120.dp))
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        val muted = chats.firstOrNull { it.id == card.chatId }?.muted == true
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier.clip(CircleShape).clickable(enabled = card.avatarBig != null) {
                    card.avatarBig?.let { viewer = Media.Photo(card.avatarSmall ?: it, it, 0, 0, null) }
                },
            ) { Avatar(card.avatarBig ?: card.avatarSmall, card.id, card.name, 120.dp) }
            Spacer(Modifier.height(16.dp))
            Text(
                card.name.ifBlank { "Без имени" },
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            val status = presenceText(card.presence)
            if (status.isNotEmpty()) {
                Text(
                    status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (card.presence.kind == PresenceKind.Online) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton({ repo.openPrivateChat(card.id) }, Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Filled.Chat, null)
                    Spacer(Modifier.padding(start = 8.dp))
                    Text("Написать")
                }
                if (!card.isBot) {
                    OutlinedButton({ repo.calls.start(card.id) }, Modifier.weight(1f)) {
                        Icon(Icons.Filled.Call, null)
                        Spacer(Modifier.padding(start = 8.dp))
                        Text("Звонок")
                    }
                }
                if (card.chatId != 0L) {
                    OutlinedButton({ repo.setMuted(card.chatId, !muted) }, Modifier.weight(1f)) {
                        Icon(if (muted) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff, null)
                        Spacer(Modifier.padding(start = 8.dp))
                        Text(if (muted) "Включить" else "Без звука")
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            val rows = buildList {
                if (card.phone.isNotBlank()) add(Triple(Icons.Filled.Phone, "Телефон", "+" + card.phone.removePrefix("+")))
                if (card.username.isNotBlank()) add(Triple(Icons.Filled.AlternateEmail, "Имя пользователя", "@" + card.username))
                if (card.bio.isNotBlank()) add(Triple(Icons.Filled.Info, if (card.isBot) "О боте" else "О себе", card.bio))
            }
            if (rows.isNotEmpty()) {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    rows.forEach { (icon, label, value) ->
                        ListItem(
                            modifier = Modifier.clickable { copy(value) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
                            overlineContent = { Text(label) },
                            headlineContent = { Text(value) },
                        )
                    }
                }
                Text(
                    "Нажмите на строку, чтобы скопировать",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Column(Modifier.fillMaxWidth()) {
                GiftsSection(card.id)
                ProfileMusicSection(card.id)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    viewer?.let { MediaViewer(it, onDismiss = { viewer = null }) }
}
