package dev.example.tg.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen() {
    val repo = LocalRepo.current
    val chats by repo.chats.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val shown = remember(chats, query) {
        if (query.isBlank()) chats else chats.filter { it.title.contains(query, ignoreCase = true) }
    }

    BackHandler(enabled = searching) { searching = false; query = "" }
    LaunchedEffect(searching) { if (searching) focus.requestFocus() }

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
                            keyboardOptions = KeyboardOptions.Default,
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
                    title = { Text("Telegram", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton({ searching = true }) { Icon(Icons.Filled.Search, "Поиск") }
                        Box {
                            IconButton({ menu = true }) { Icon(Icons.Filled.MoreVert, "Меню") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Выйти") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, null) },
                                    onClick = { menu = false; repo.logout() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Сбросить API ключи") },
                                    leadingIcon = { Icon(Icons.Filled.VpnKey, null) },
                                    onClick = { menu = false; repo.resetKeys() },
                                )
                            }
                        }
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
                            Text(c.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Text(c.preview, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        trailingContent = {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    formatListTime(c.time),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (c.unread > 0) {
                                    Badge(Modifier.padding(top = 4.dp)) { Text("${c.unread}") }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
