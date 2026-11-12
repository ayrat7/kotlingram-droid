package dev.example.tg.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.example.tg.data.Auth
import dev.example.tg.data.COUNTRIES
import dev.example.tg.data.Country
import dev.example.tg.data.DEFAULT_COUNTRY
import dev.example.tg.data.countryByIso
import dev.example.tg.data.matchCountry
import kotlinx.coroutines.delay
import java.util.Locale

private val FieldShape = RoundedCornerShape(12.dp)

private const val PLANE_PATH =
    "M9.78 18.65l.28-4.23 7.68-6.92c.34-.31-.07-.46-.52-.19L7.74 13.3 3.64 12c-.88-.25-.89-.86.2-1.3" +
        "l15.97-6.16c.73-.33 1.43.18 1.15 1.3l-2.72 12.81c-.19.91-.74 1.13-1.5.71L12.6 16.3l-1.99 1.93" +
        "c-.23.23-.42.42-.83.42z"

/** Все шаги входа (всё, кроме Auth.Ready). Цвета берутся из темы: Material You + светлая/тёмная системная. */
@Composable
fun AuthScreen(auth: Auth) {
    when (auth) {
        Auth.Starting -> AuthShell("Telegram", "Подключение…") { CircularProgressIndicator() }
        Auth.NeedKeys -> KeysStep()
        Auth.NeedPhone -> PhoneStep()
        Auth.NeedCode -> CodeStep()
        Auth.NeedPassword -> PasswordStep()
        Auth.Ready -> {}
    }
}

@Composable
private fun TelegramLogo(size: androidx.compose.ui.unit.Dp = 112.dp) {
    val cs = MaterialTheme.colorScheme
    val plane = remember { PathParser().parsePathString(PLANE_PATH).toPath() }
    Canvas(Modifier.size(size)) {
        drawCircle(cs.primary)
        val s = this.size.minDimension * 0.6f / 24f
        withTransform({
            translate((this.size.width - 24f * s) / 2f, (this.size.height - 24f * s) / 2f)
            scale(s, s, pivot = Offset.Zero)
        }) { drawPath(plane, cs.onPrimary) }
    }
}

@Composable
private fun AuthShell(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        TelegramLogo()
        Spacer(Modifier.height(28.dp))
        Text(
            title, style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle, style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 340.dp),
        )
        Spacer(Modifier.height(32.dp))
        Column(
            Modifier.widthIn(max = 420.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PrimaryButton(text: String, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick, Modifier.fillMaxWidth().height(52.dp),
        enabled = enabled && !busy, shape = FieldShape,
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text(text)
    }
}

/** Ссылка-действие в стиле «ВХОД ПО QR-КОДУ» из Telegram. */
@Composable
private fun LinkButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick, enabled = enabled) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun TgField(
    value: String, onChange: (String) -> Unit, label: String,
    kb: KeyboardType = KeyboardType.Text, ime: ImeAction = ImeAction.Done,
    onDone: () -> Unit = {}, modifier: Modifier = Modifier,
    secret: Boolean = false, supporting: String? = null,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        shape = FieldShape,
        visualTransformation = if (secret && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (secret) ({
            IconButton({ visible = !visible }) {
                Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Показать")
            }
        }) else null,
        supportingText = supporting?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = kb, imeAction = ime),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
    )
}

// ---------- Шаг 1: API ----------

@Composable
private fun KeysStep() {
    val repo = LocalRepo.current
    var id by rememberSaveable { mutableStateOf("") }
    var hash by rememberSaveable { mutableStateOf("") }
    val ok = id.isNotBlank() && hash.isNotBlank()
    AuthShell(
        "Настройка API",
        "Введите API ID и API Hash — их выдаёт my.telegram.org в разделе API development tools.",
    ) {
        TgField(id, { id = it.filter(Char::isDigit).take(12) }, "API ID", KeyboardType.Number, ImeAction.Next)
        TgField(
            hash, { hash = it.trim() }, "API Hash", secret = true,
            onDone = { if (ok) repo.saveKeys(id, hash) },
        )
        PrimaryButton("Продолжить", busy = false, enabled = ok) { repo.saveKeys(id, hash) }
    }
}

// ---------- Шаг 2: номер ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneStep() {
    val repo = LocalRepo.current
    val busy by repo.busy.collectAsState()
    val initial = remember { countryByIso(Locale.getDefault().country) ?: DEFAULT_COUNTRY }
    var iso by rememberSaveable { mutableStateOf(initial.iso) }
    var phone by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue("+" + initial.code, TextRange(initial.code.length + 1)))
    }
    var picker by remember { mutableStateOf(false) }
    val country = countryByIso(iso) ?: DEFAULT_COUNTRY
    val digits = phone.text.filter(Char::isDigit)
    val valid = digits.length >= country.code.length + 5
    val focus = remember { FocusRequester() }
    val cs = MaterialTheme.colorScheme

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun submit() { if (valid && !busy) repo.submitPhone("+$digits") }

    AuthShell("Telegram", "Проверьте код страны и введите свой номер телефона.") {
        Box(
            Modifier.fillMaxWidth().clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
            ) { picker = true },
        ) {
            OutlinedTextField(
                value = country.name, onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                enabled = false, readOnly = true, singleLine = true,
                label = { Text("Страна") },
                trailingIcon = { Icon(Icons.Filled.KeyboardArrowDown, null) },
                shape = FieldShape,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = cs.onSurface,
                    disabledBorderColor = cs.outline,
                    disabledLabelColor = cs.onSurfaceVariant,
                    disabledTrailingIconColor = cs.onSurfaceVariant,
                    disabledContainerColor = Color.Transparent,
                ),
            )
        }
        OutlinedTextField(
            value = phone,
            onValueChange = { v ->
                val d = v.text.filter(Char::isDigit).take(15)
                val t = "+$d"
                phone = if (t == v.text) v else TextFieldValue(t, TextRange(t.length))
                matchCountry(d)?.let { if (it.code != country.code) iso = it.iso }
            },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            label = { Text("Номер телефона") },
            singleLine = true,
            shape = FieldShape,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        AnimatedVisibility(valid || busy) {
            PrimaryButton("Продолжить", busy = busy, enabled = valid) { submit() }
        }
        LinkButton("Сбросить API ключи") { repo.resetKeys() }
    }

    if (picker) {
        CountrySheet(
            selected = country,
            onPick = { c ->
                val old = phone.text.filter(Char::isDigit)
                val rest = if (old.startsWith(country.code)) old.drop(country.code.length) else ""
                val t = "+" + c.code + rest
                iso = c.iso
                phone = TextFieldValue(t, TextRange(t.length))
                picker = false
                runCatching { focus.requestFocus() }
            },
            onDismiss = { picker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountrySheet(selected: Country, onPick: (Country) -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var q by remember { mutableStateOf("") }
    val shown = remember(q) {
        val s = q.trim().removePrefix("+")
        if (s.isEmpty()) COUNTRIES
        else COUNTRIES.filter { it.name.contains(s, ignoreCase = true) || it.code.startsWith(s) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        OutlinedTextField(
            q, { q = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            label = { Text("Поиск страны") }, singleLine = true, shape = FieldShape,
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxWidth()) {
            items(shown, key = { it.iso }) { c ->
                ListItem(
                    modifier = Modifier.clickable { onPick(c) },
                    headlineContent = { Text(c.name) },
                    trailingContent = {
                        if (c.iso == selected.iso) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                        else Text("+${c.code}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                )
            }
        }
    }
}

// ---------- Шаг 3: код ----------

@Composable
private fun CodeStep() {
    val repo = LocalRepo.current
    val busy by repo.busy.collectAsState()
    val info by repo.codeInfo.collectAsState()
    var code by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    var left by remember(info?.stamp) { mutableIntStateOf(info?.timeout ?: 0) }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(info?.stamp) {
        while (left > 0) {
            delay(1000)
            left--
        }
    }

    val title = info?.phone?.takeIf { it.isNotBlank() }?.let { "+$it" } ?: "Код подтверждения"
    AuthShell(title, info?.hint ?: "Введите код, который мы вам отправили.") {
        TgField(
            code,
            { v ->
                code = v.filter(Char::isDigit).take(8)
                val len = info?.length ?: 0
                if (len > 0 && code.length == len && !busy) repo.submitCode(code)
            },
            "Код", KeyboardType.Number,
            onDone = { if (code.isNotBlank() && !busy) repo.submitCode(code) },
            modifier = Modifier.focusRequester(focus),
        )
        AnimatedVisibility(code.isNotBlank() || busy) {
            PrimaryButton("Продолжить", busy = busy, enabled = code.isNotBlank()) { repo.submitCode(code) }
        }
        if (info?.canResend == true) {
            if (left > 0) {
                Text(
                    "Отправить код ещё раз через ${formatDuration(left)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LinkButton("Отправить код ещё раз") { repo.resendCode() }
            }
        }
    }
}

// ---------- Шаг 4: облачный пароль ----------

@Composable
private fun PasswordStep() {
    val repo = LocalRepo.current
    val busy by repo.busy.collectAsState()
    val hint by repo.passwordHint.collectAsState()
    var pass by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    AuthShell("Введите пароль", "Ваш аккаунт защищён облачным паролем.") {
        TgField(
            pass, { pass = it }, "Пароль", secret = true,
            supporting = hint.takeIf { it.isNotBlank() }?.let { "Подсказка: $it" },
            onDone = { if (pass.isNotEmpty() && !busy) repo.submitPassword(pass) },
            modifier = Modifier.focusRequester(focus),
        )
        PrimaryButton("Продолжить", busy = busy, enabled = pass.isNotEmpty()) { repo.submitPassword(pass) }
    }
}
