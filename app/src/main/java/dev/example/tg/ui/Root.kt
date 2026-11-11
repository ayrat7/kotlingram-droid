package dev.example.tg.ui

import androidx.activity.compose.BackHandler
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

@Composable
fun TgRoot() {
    val repo = LocalRepo.current
    val auth by repo.auth.collectAsState()
    val error by repo.error.collectAsState()
    val busy by repo.busy.collectAsState()
    val openId by repo.openChatId.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    BackHandler(enabled = openId != null) { repo.close() }

    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it, withDismissAction = true)
            repo.error.value = null
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            when (auth) {
                Auth.Starting -> AuthCard("Kotlingram", "Подключение к Telegram…") {
                    CircularProgressIndicator()
                }
                Auth.NeedKeys -> KeysScreen()
                Auth.NeedPhone -> StepScreen(
                    "Вход в Telegram", "Номер телефона в международном формате", "Телефон", "+79991234567",
                    false, KeyboardType.Phone, busy, repo::submitPhone,
                    footer = { TextButton({ repo.resetKeys() }) { Text("Сбросить API ключи") } },
                )
                Auth.NeedCode -> StepScreen(
                    "Код подтверждения", "Код пришёл в Telegram или по SMS", "Код", "12345",
                    false, KeyboardType.Number, busy, repo::submitCode,
                )
                Auth.NeedPassword -> StepScreen(
                    "Облачный пароль", "Включена двухфакторная защита", "Пароль", "",
                    true, KeyboardType.Password, busy, repo::submitPassword,
                )
                Auth.Ready -> {
                    val id = openId
                    if (id == null) ChatsScreen() else ChatScreen(id)
                }
            }
            SnackbarHost(
                snackbar,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding(),
            ) { data ->
                Snackbar(
                    data,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    dismissActionContentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun AuthCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ElevatedCard(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
            Column(
                Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send, null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(
                    subtitle, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
                content()
            }
        }
    }
}

@Composable
private fun Field(
    value: String, onChange: (String) -> Unit, label: String, hint: String = "",
    secret: Boolean = false, kb: KeyboardType = KeyboardType.Text, done: () -> Unit = {},
) = OutlinedTextField(
    value = value,
    onValueChange = onChange,
    modifier = Modifier.fillMaxWidth(),
    label = { Text(label) },
    placeholder = if (hint.isNotEmpty()) ({ Text(hint) }) else null,
    singleLine = true,
    visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
    keyboardOptions = KeyboardOptions(keyboardType = kb, imeAction = ImeAction.Done),
    keyboardActions = KeyboardActions(onDone = { done() }),
)

@Composable
private fun KeysScreen() {
    val repo = LocalRepo.current
    var id by rememberSaveable { mutableStateOf("") }
    var hash by rememberSaveable { mutableStateOf("") }
    AuthCard("Настройка Telegram API", "Ключи берутся на my.telegram.org → API development tools") {
        Field(id, { id = it }, "API ID", kb = KeyboardType.Number)
        Field(hash, { hash = it }, "API Hash", secret = true, done = { repo.saveKeys(id, hash) })
        Button({ repo.saveKeys(id, hash) }, Modifier.fillMaxWidth().height(50.dp)) { Text("Сохранить и подключиться") }
    }
}

@Composable
private fun StepScreen(
    title: String, subtitle: String, label: String, hint: String, secret: Boolean, kb: KeyboardType,
    busy: Boolean, onSubmit: (String) -> Unit, footer: @Composable () -> Unit = {},
) {
    var v by rememberSaveable(title) { mutableStateOf("") }
    AuthCard(title, subtitle) {
        Field(v, { v = it }, label, hint, secret, kb, done = { if (!busy && v.isNotBlank()) onSubmit(v) })
        Button(
            { onSubmit(v) },
            Modifier.fillMaxWidth().height(50.dp),
            enabled = !busy && v.isNotBlank(),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Продолжить")
        }
        footer()
    }
}
