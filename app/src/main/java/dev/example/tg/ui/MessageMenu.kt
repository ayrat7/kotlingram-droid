package dev.example.tg.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.example.tg.data.UiMsg

/** Можно ли изменить сообщение: своё, уже отправленное, не стикер и не кружок. */
fun canEdit(m: UiMsg): Boolean {
    if (!m.out || m.pending) return false
    val media = m.media
    return when (media) {
        null, is dev.example.tg.data.Media.Photo, is dev.example.tg.data.Media.Doc,
        is dev.example.tg.data.Media.Audio, is dev.example.tg.data.Media.Voice -> true
        is dev.example.tg.data.Media.Video -> !media.round
        is dev.example.tg.data.Media.Sticker -> false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActionsSheet(
    m: UiMsg,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onForward: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState()
    val preview = m.text.ifBlank { mediaLabel(m.media) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Text(
            preview,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
        if (!m.pending) {
            ActionRow(Icons.AutoMirrored.Filled.Reply, "Ответить") { onDismiss(); onReply() }
        }
        if (m.text.isNotBlank()) {
            ActionRow(Icons.Filled.ContentCopy, "Копировать текст") { onDismiss(); onCopy() }
        }
        if (canEdit(m)) {
            ActionRow(Icons.Filled.Edit, "Изменить") { onDismiss(); onEdit() }
        }
        if (!m.pending) {
            ActionRow(Icons.AutoMirrored.Filled.Send, "Переслать") { onDismiss(); onForward() }
        }
        ActionRow(Icons.Filled.Delete, "Удалить", danger = true) { onDismiss(); onDelete() }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(icon, null, tint = color) },
        headlineContent = { Text(label, color = color) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    )
}

@Composable
fun DeleteDialog(m: UiMsg, canRevoke: Boolean, onConfirm: (forAll: Boolean) -> Unit, onDismiss: () -> Unit) {
    var forAll by remember { mutableStateOf(canRevoke) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Удалить сообщение?") },
        text = {
            if (canRevoke) {
                Row(
                    Modifier.fillMaxWidth().clickable { forAll = !forAll },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = forAll, onCheckedChange = { forAll = it })
                    Text("Удалить также для собеседника")
                }
            } else {
                Text("Сообщение будет удалено только у вас.")
            }
        },
        confirmButton = {
            TextButton({ onConfirm(canRevoke && forAll) }) {
                Text("Удалить", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("Отмена") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForwardSheet(m: UiMsg, onDismiss: () -> Unit) {
    val repo = LocalRepo.current
    val ctx = LocalContext.current
    val chats by repo.chats.collectAsState()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Text(
            "Переслать в…", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn {
            items(chats, key = { it.id }) { c ->
                ListItem(
                    modifier = Modifier.clickable {
                        repo.forward(m.chatId, m.id, c.id)
                        Toast.makeText(ctx, "Пересылаю…", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    leadingContent = { Avatar(c.avatar, c.id, c.title, 40.dp) },
                    headlineContent = { Text(c.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                )
            }
        }
    }
}
