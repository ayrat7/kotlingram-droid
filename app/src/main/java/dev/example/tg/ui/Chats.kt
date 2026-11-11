package dev.example.tg.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(onProfile: () -> Unit, onSettings: () -> Unit) {
    val repo = LocalRepo.current
    val chats by repo.chats.collectAsState()
    val me by repo.me.collectAsState()
    val connection by repo.connection.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val uiScope = rememberCoroutineScope()
    val shown = remember(chats, query) {
        if (query.isBlank()) chats else chats.filter { it.title.contains(query, ignoreCase = true) }
    }

    BackHandler(enabled = drawer.isOpen) { uiScope.launch { drawer.close() } }
    BackHandler(enabled = searching && !drawer.isOpen) { searching = false; query = "" }
    LaunchedEffect(searching) { if (searching) focus.requestFocus() }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = !searching,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
                    Avatar(me?.avatarSmall, me?.id ?: 0L, me?.fullName.orEmpty(), 64.dp)
                    Spacer(Modifier.size(12.dp))
                    Text(
                        me?.fullName.orEmpty().ifBlank { "Telegram" },
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    )
                    val sub = me?.username?.takeIf { it.isNotBlank() }?.let { "@$it" }
                        ?: me?.phone?.takeIf { it.isNotBlank() }?.let { "+$it" }
                    if (sub != null) {
                        Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
                Spacer(Modifier.size(8.dp))
                NavigationDrawerItem(
                    label = { Text("Мой профиль") },
                    icon = { Icon(Icons.Filled.AccountCircle, null) },
                    selected = false,
                    onClick = { uiScope.launch { drawer.close() }; onProfile() },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    label = { Text("Настройки") },
                    icon = { Icon(Icons.Filled.Settings, null) },
                    selected = false,
                    onClick = { uiScope.launch { drawer.close() }; onSettings() },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    label = { Text("Выйти") },
                    icon = { Icon(Icons.AutoMirrored.Filled.Logout, null) },
                    selected = false,
                    onClick = { uiScope.launch { drawer.close() }; confirmLogout = true },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                if (searching) {
                    TopAppBar(
                        title = {
                            TextField(
                                value = query,
                                onValueChange = { query = it },
                                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                                placeholder = { Text("Поиск чатов") },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                ),
                            )
                        },
                        navigationIcon = {
                            IconButton({ searching = false; query = "" }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                            }
                        },
                    )
                } else {
                    TopAppBar(
                        title = {
                            Text(connection ?: "Telegram", fontWeight = FontWeight.Bold, maxLines = 1)
                        },
                        navigationIcon = {
                            IconButton({ uiScope.launch { drawer.open() } }) { Icon(Icons.Filled.Menu, "Меню") }
                        },
                        actions = {
                            IconButton({ searching = true }) { Icon(Icons.Filled.Search, "Поиск") }
                        },
                    )
                }
            },
        ) { pad ->
            if (chats.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
                    items(shown, key = { it.id }) { c ->
                        ListItem(
                            modifier = Modifier.clickable { repo.open(c.id) },
                            leadingContent = { Avatar(c.avatar, c.id, c.title) },
                            headlineContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        c.title, fontWeight = FontWeight.SemiBold, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                                    )
                                    if (c.muted) {
                                        Spacer(Modifier.width(6.dp))
                                        Icon(
                                            Icons.Filled.NotificationsOff, "Без звука", Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                            supportingContent = { Text(c.preview, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingContent = {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        formatListTime(c.time),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (c.unread > 0) {
                                        Badge(
                                            Modifier.padding(top = 4.dp),
                                            containerColor = if (c.muted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                                        ) { Text("${c.unread}") }
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Выйти из аккаунта?") },
            text = { Text("Локальные данные будут удалены, понадобится повторный вход.") },
            confirmButton = {
                TextButton({ confirmLogout = false; repo.logout() }) {
                    Text("Выйти", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton({ confirmLogout = false }) { Text("Отмена") } },
        )
    }
}
