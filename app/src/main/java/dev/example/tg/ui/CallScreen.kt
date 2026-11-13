package dev.example.tg.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.example.tg.data.CallPhase
import dev.example.tg.data.UiCall
import kotlinx.coroutines.delay

/** Полноэкранный экран звонка поверх всего приложения. */
@Composable
fun CallOverlay() {
    val repo = LocalRepo.current
    val mgr = repo.calls
    val call by mgr.call.collectAsState()
    val muted by mgr.muted.collectAsState()
    val speaker by mgr.speaker.collectAsState()
    val c = call ?: return

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        CallContent(c, muted, speaker, mgr.canCall, mgr::accept, mgr::hangup, mgr::toggleMute, mgr::toggleSpeaker)
    }
}

@Composable
private fun CallContent(
    c: UiCall, muted: Boolean, speaker: Boolean, canAccept: Boolean,
    onAccept: () -> Unit, onHangup: () -> Unit, onMute: () -> Unit, onSpeaker: () -> Unit,
) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(c.phase) { while (c.phase == CallPhase.Active) { now = System.currentTimeMillis(); delay(500) } }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0E1B2C), Color(0xFF1F3A5C), Color(0xFF0A1220))))) {
        // размытая аватарка как фон
        val f = rememberFile(c.avatar)
        f?.path?.let { p ->
            coil.compose.AsyncImage(
                java.io.File(p), null,
                Modifier.fillMaxSize().blur(48.dp).background(Color.Black.copy(alpha = 0.0f)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop, alpha = 0.35f,
            )
        }
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            Avatar(c.avatar, c.userId, c.name, 140.dp)
            Spacer(Modifier.height(20.dp))
            Text(c.name, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                when (c.phase) {
                    CallPhase.Ringing -> "Входящий звонок"
                    CallPhase.Calling -> c.note.ifBlank { "Вызов…" }
                    CallPhase.Connecting -> "Соединение…"
                    CallPhase.Active -> formatDurationMs((now - c.startedAt).coerceAtLeast(0))
                    CallPhase.Ended -> c.note.ifBlank { "Звонок завершён" }
                },
                color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp,
            )
            if (c.emojis.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                // эмодзи ключа: должны совпасть у обоих собеседников
                Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.12f)) {
                    Text(c.emojis.joinToString(" "), Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 26.sp)
                }
            }
            if (c.phase == CallPhase.Ringing && !canAccept) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Принять звонок нельзя: в приложении не подключён голосовой движок (tgcalls).",
                    color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center, fontSize = 13.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            when (c.phase) {
                CallPhase.Ringing -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RoundButton(Icons.Filled.CallEnd, "Отклонить", Color(0xFFE53935), onHangup)
                    if (canAccept) RoundButton(Icons.Filled.Call, "Принять", Color(0xFF43A047), onAccept)
                }
                CallPhase.Ended -> {}
                else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RoundButton(if (muted) Icons.Filled.MicOff else Icons.Filled.Mic, "Микрофон", if (muted) Color.White else Color.White.copy(alpha = 0.18f), onMute, dark = muted)
                    RoundButton(Icons.Filled.CallEnd, "Завершить", Color(0xFFE53935), onHangup)
                    RoundButton(Icons.Filled.VolumeUp, "Динамик", if (speaker) Color.White else Color.White.copy(alpha = 0.18f), onSpeaker, dark = speaker)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, bg: Color, onClick: () -> Unit, dark: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick, Modifier.size(68.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = bg, contentColor = if (dark) Color.Black else Color.White),
            shape = CircleShape,
        ) { Icon(icon, label, Modifier.size(30.dp)) }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
    }
}
