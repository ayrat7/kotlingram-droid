package dev.example.tg.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.example.tg.data.BotCommandUi
import dev.example.tg.data.BotUi
import dev.example.tg.data.Btn
import dev.example.tg.data.BtnAction
import dev.example.tg.data.InlineResultUi
import dev.example.tg.data.InlineResults
import dev.example.tg.data.Markup
import dev.example.tg.data.WebAppSession

/** Inline-кнопки под сообщением бота: ряды на всю ширину пузыря. */
@Composable
fun InlineKeyboard(markup: Markup.Inline, modifier: Modifier = Modifier, onClick: (Btn) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        markup.rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                row.forEach { b ->
                    Surface(
                        onClick = { onClick(b) },
                        modifier = Modifier.weight(1f).heightIn(min = 36.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = cs.surfaceContainerHighest.copy(alpha = 0.75f),
                    ) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 7.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                b.text, style = MaterialTheme.typography.labelLarge, color = cs.primary,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (b.action is BtnAction.Url || b.action is BtnAction.WebApp) {
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Filled.OpenInNew, null, Modifier.size(12.dp), tint = cs.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Клавиатура ответа под полем ввода. */
@Composable
fun ReplyKeyboardPanel(kb: Markup.Keyboard, onPress: (Btn) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        kb.rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { b ->
                    Surface(
                        onClick = { onPress(b) },
                        modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = cs.surfaceContainerHigh.copy(alpha = 0.85f),
                    ) {
                        Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                            Text(b.text, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

/** Подсказки inline-режима над полем ввода. */
@Composable
fun InlineResultsPanel(res: InlineResults, onPick: (InlineResultUi) -> Unit) {
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
        items(res.items, key = { it.id }) { r ->
            ListItem(
                modifier = Modifier.clickable { onPick(r) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(r.title.ifBlank { "Результат" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = if (r.subtitle.isNotBlank()) {
                    { Text(r.subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                } else null,
            )
        }
    }
}

/** Список команд бота («/»). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotCommandsSheet(bot: BotUi, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.padding(bottom = 16.dp)) {
            items(bot.commands, key = { it.command }) { c: BotCommandUi ->
                ListItem(
                    modifier = Modifier.clickable { onPick(c.command) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("/${c.command}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(c.description) },
                )
            }
        }
    }
}

/** Пустой чат с ботом: описание и кнопка «Запустить». */
@Composable
fun BotStartCard(bot: BotUi, modifier: Modifier = Modifier, onStart: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier.padding(32.dp), shape = RoundedCornerShape(20.dp),
        color = cs.surfaceContainerHigh.copy(alpha = 0.85f),
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Что умеет этот бот?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (bot.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(bot.description, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(16.dp))
            Button(onStart) { Text("Запустить") }
        }
    }
}

/**
 * Мини-приложение бота в WebView. Telegram-скрипт сообщает о событиях через
 * TelegramWebviewProxy.postEvent — реализованы закрытие, тема, размеры и открытие ссылок.
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun WebAppDialog(session: WebAppSession, onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val cs = MaterialTheme.colorScheme
    var web by remember { mutableStateOf<WebView?>(null) }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = cs.surface) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(session.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClose) { Icon(Icons.Filled.Close, "Закрыть") }
                }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { c ->
                        WebView(c).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            webViewClient = WebViewClient()
                            addJavascriptInterface(object {
                                @JavascriptInterface
                                fun postEvent(type: String?, data: String?) {
                                    post {
                                        when (type) {
                                            "web_app_close" -> onClose()
                                            "web_app_request_theme" -> reply("theme_changed", """{"theme_params":${themeJson()}}""")
                                            "web_app_request_viewport" ->
                                                reply("viewport_changed", """{"height":${height / resources.displayMetrics.density},"is_state_stable":true,"is_expanded":true}""")
                                            "web_app_open_link", "web_app_open_tg_link" -> {
                                                val u = Regex("\"url\"\\s*:\\s*\"([^\"]+)\"").find(data.orEmpty())?.groupValues?.get(1)?.replace("\\/", "/")
                                                if (u != null) runCatching {
                                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                                }
                                            }
                                        }
                                    }
                                }

                                private fun WebView.reply(event: String, json: String) {
                                    evaluateJavascript("window.Telegram&&Telegram.WebView&&Telegram.WebView.receiveEvent('$event',$json)", null)
                                }
                            }, "TelegramWebviewProxy")
                            loadUrl(session.url)
                            web = this
                        }
                    },
                )
            }
        }
    }
    DisposableEffect(Unit) { onDispose { web?.destroy() } }
}

private fun themeJson() =
    """{"bg_color":"#17212b","text_color":"#ffffff","hint_color":"#708499","link_color":"#6ab3f3","button_color":"#5288c1","button_text_color":"#ffffff","secondary_bg_color":"#0e1621","header_bg_color":"#17212b","accent_text_color":"#6ab3f3","section_bg_color":"#1f2b38","section_header_text_color":"#6ab3f3","subtitle_text_color":"#7d8e98","destructive_text_color":"#ec3942"}"""
