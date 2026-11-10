package dev.example.tg

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- Палитра (Telegram Dark) ----------
val BgMain = Color(0xFF0E1621)
val BgPanel = Color(0xFF17212B)
val BgInput = Color(0xFF242F3D)
val Accent = Color(0xFF5288C1)
val MsgOut = Color(0xFF2B5278)
val MsgIn = Color(0xFF182533)
val Muted = Color(0xFF708499)
val NameBlue = Color(0xFF64B5F6)

@Composable
fun TgTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(
        primary = Accent,
        onPrimary = Color.White,
        background = BgMain,
        surface = BgPanel,
        onSurface = Color.White,
        onBackground = Color.White,
        surfaceVariant = BgInput,
    ),
    content = content,
)

private fun fmt(t: Int): String =
    if (t == 0) "" else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t * 1000L))

// ---------- Корень ----------

@Composable
fun TgRoot(repo: TgRepo) {
    val auth by repo.auth.collectAsState()
    val error by repo.error.collectAsState()
    val busy by repo.busy.collectAsState()
    val openId by repo.openChatId.collectAsState()

    BackHandler(enabled = openId != null) { repo.close() }

    Column(
        Modifier
            .fillMaxSize()
            .background(BgMain)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        error?.let { msg ->
            Row(
                Modifier.fillMaxWidth().background(Color(0xFF96282D)).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("⚠ $msg", Modifier.weight(1f), fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Text("✕", Modifier.clickable { repo.error.value = null }.padding(8.dp))
            }
        }

        when (auth) {
            Auth.Starting -> CenterCard {
                CircularProgressIndicator()
                Text("Подключение к Telegram…")
            }
            Auth.NeedKeys -> KeysScreen(repo)
            Auth.NeedPhone -> StepScreen(
                "Вход в Telegram", "Номер телефона в международном формате", "+79991234567",
                false, KeyboardType.Phone, busy, repo::submitPhone,
                footer = { TextButton({ repo.resetKeys() }) { Text("Сбросить API ключи", color = Muted, fontSize = 12.sp) } },
            )
            Auth.NeedCode -> StepScreen(
                "Код подтверждения", "Код пришёл в Telegram или по SMS", "12345",
                false, KeyboardType.Number, busy, repo::submitCode,
            )
            Auth.NeedPassword -> StepScreen(
                "Облачный пароль", "Включена двухфакторная защита", "Пароль",
                true, KeyboardType.Password, busy, repo::submitPassword,
            )
            Auth.Ready -> if (openId == null) ChatsScreen(repo) else ChatScreen(repo, openId!!)
        }
    }
}

// ---------- Экраны входа ----------

@Composable
fun CenterCard(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .widthIn(max = 380.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(BgPanel)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun TgField(
    value: String, onChange: (String) -> Unit, hint: String,
    secret: Boolean = false, kb: KeyboardType = KeyboardType.Text, modifier: Modifier = Modifier.fillMaxWidth(),
) = OutlinedTextField(
    value = value,
    onValueChange = onChange,
    modifier = modifier,
    placeholder = { Text(hint, color = Muted) },
    singleLine = true,
    visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
    keyboardOptions = KeyboardOptions(keyboardType = kb),
    shape = RoundedCornerShape(10.dp),
)

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) = Button(
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier.fillMaxWidth().height(46.dp),
    shape = RoundedCornerShape(10.dp),
    colors = ButtonDefaults.buttonColors(containerColor = Accent),
) { Text(text, fontWeight = FontWeight.Bold) }

@Composable
fun KeysScreen(repo: TgRepo) {
    var id by remember { mutableStateOf("") }
    var hash by remember { mutableStateOf("") }
    CenterCard {
        Text("Настройка Telegram API", fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text(
            "Ключи берутся на my.telegram.org → API development tools",
            color = Muted, fontSize = 12.5.sp, textAlign = TextAlign.Center,
        )
        TgField(id, { id = it }, "API ID", kb = KeyboardType.Number)
        TgField(hash, { hash = it }, "API Hash", secret = true)
        PrimaryButton("Сохранить и подключиться") { repo.saveKeys(id, hash) }
    }
}

@Composable
fun StepScreen(
    title: String, subtitle: String, hint: String, secret: Boolean, kb: KeyboardType,
    busy: Boolean, onSubmit: (String) -> Unit, footer: @Composable () -> Unit = {},
) {
    var v by remember { mutableStateOf("") }
    CenterCard {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
        TgField(v, { v = it }, hint, secret, kb)
        PrimaryButton(if (busy) "Подождите…" else "Продолжить", enabled = !busy && v.isNotBlank()) { onSubmit(v) }
        footer()
    }
}

// ---------- Список чатов ----------

private val avatarColors = listOf(
    Color(0xFFE17076), Color(0xFFFAA774), Color(0xFFA695E7),
    Color(0xFF7BC862), Color(0xFF6EC9CB), Color(0xFF65AADD), Color(0xFFEE7AAE),
)

@Composable
fun Avatar(id: Long, title: String, size: Int = 48) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(avatarColors[(id % avatarColors.size).toInt().let { if (it < 0) -it else it }]),
        contentAlignment = Alignment.Center,
    ) {
        Text(title.firstOrNull()?.uppercase() ?: "?", fontSize = (size / 2.3).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ChatsScreen(repo: TgRepo) {
    val chats by repo.chats.collectAsState()
    var query by remember { mutableStateOf("") }
    val shown = remember(chats, query) {
        if (query.isBlank()) chats else chats.filter { it.title.contains(query, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(BgPanel).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Telegram", Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.Bold)
            TextButton({ repo.logout() }) { Text("Выйти", color = Muted) }
            TextButton({ repo.resetKeys() }) { Text("API ключи", color = Muted) }
        }
        TgField(query, { query = it }, "🔍 Поиск чатов", modifier = Modifier.fillMaxWidth().padding(10.dp))

        if (chats.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.id }) { c ->
                    Row(
                        Modifier.fillMaxWidth().clickable { repo.open(c.id) }.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Avatar(c.id, c.title)
                        Column(Modifier.weight(1f)) {
                            Text(c.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(c.preview, color = Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(fmt(c.time), color = Muted, fontSize = 12.sp)
                            if (c.unread > 0) {
                                Box(
                                    Modifier.padding(top = 4.dp).clip(RoundedCornerShape(10.dp)).background(Accent)
                                        .padding(horizontal = 7.dp, vertical = 1.dp)
                                ) { Text("${c.unread}", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- Экран переписки ----------

@Composable
fun ChatScreen(repo: TgRepo, chatId: Long) {
    val msgs by repo.messages.collectAsState()
    val chats by repo.chats.collectAsState()
    val title = chats.firstOrNull { it.id == chatId }?.title ?: ""
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(msgs.size) {
        if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.lastIndex)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(BgPanel).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton({ repo.close() }) { Text("←", fontSize = 22.sp) }
            Avatar(chatId, title, 36)
            Spacer(Modifier.size(10.dp))
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(msgs, key = { it.id }) { m ->
                Box(Modifier.fillMaxWidth(), contentAlignment = if (m.out) Alignment.CenterEnd else Alignment.CenterStart) {
                    Column(
                        Modifier
                            .widthIn(max = 300.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (m.out) MsgOut else MsgIn)
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        if (!m.out && m.from.isNotBlank()) {
                            Text(m.from, color = NameBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Text(m.text, fontSize = 15.sp)
                        Text(fmt(m.time), color = Muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.End))
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().background(BgPanel).padding(8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Сообщение", color = Muted) },
                maxLines = 5,
                shape = RoundedCornerShape(22.dp),
            )
            Button(
                onClick = { repo.send(text); text = "" },
                enabled = text.isNotBlank(),
                modifier = Modifier.height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
            ) { Text("➤", fontSize = 18.sp) }
        }
    }
}
