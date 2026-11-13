package dev.example.tg.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import dev.example.tg.data.GiftUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.GZIPInputStream

/** Анимированный стикер .tgs (gzip-Lottie). */
@Composable
fun TgsSticker(path: String, modifier: Modifier = Modifier, animate: Boolean = true) {
    val json by produceState<String?>(null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching { GZIPInputStream(File(path).inputStream()).bufferedReader().use { it.readText() } }.getOrNull()
        }
    }
    val j = json ?: return
    val comp by rememberLottieComposition(LottieCompositionSpec.JsonString(j))
    if (animate) LottieAnimation(comp, modifier, iterations = LottieConstants.IterateForever)
    else LottieAnimation(comp, progress = { 0.35f }, modifier = modifier)
}

@Composable
private fun GiftImage(g: GiftUi, modifier: Modifier, animate: Boolean) {
    val f = rememberFile(g.sticker)
    val p = f?.path
    Box(modifier, contentAlignment = Alignment.Center) {
        when {
            p == null -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            g.animated -> TgsSticker(p, Modifier.fillMaxSize(), animate)
            else -> AsyncImage(File(p), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    }
}

private fun rgb(c: Int) = Color(0xFF000000.toInt() or (c and 0xFFFFFF))

/** Сетка подарков профиля. Ничего не показывает, пока подарков нет. */
@Composable
fun GiftsSection(userId: Long) {
    val repo = LocalRepo.current
    val all by repo.gifts.collectAsState()
    LaunchedEffect(userId) { repo.loadGifts(userId) }
    val list = all[userId].orEmpty()
    if (list.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf<GiftUi?>(null) }

    Text(
        "Подарки · ${list.size}", style = MaterialTheme.typography.titleSmall, color = cs.primary,
        fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
    list.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { g ->
                GiftCard(g, Modifier.weight(1f)) { open = g }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    open?.let { g -> GiftDialog(g) { open = null } }
}

@Composable
private fun GiftCard(g: GiftUi, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg = if (g.upgraded && g.bgCenter != 0) {
        Brush.radialGradient(listOf(rgb(g.bgCenter), rgb(g.bgEdge)))
    } else Brush.verticalGradient(listOf(cs.surfaceContainerHigh, cs.surfaceContainer))
    Column(
        modifier.aspectRatio(0.82f).clip(RoundedCornerShape(16.dp)).background(bg).clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GiftImage(g, Modifier.weight(1f).fillMaxWidth(), animate = false)
        val onBg = if (g.upgraded && g.bgCenter != 0) Color.White else cs.onSurface
        Text(
            if (g.upgraded) "#${g.number}" else if (g.stars > 0) "★ ${g.stars}" else "",
            style = MaterialTheme.typography.labelMedium, color = onBg, maxLines = 1,
        )
    }
}

@Composable
private fun GiftDialog(g: GiftUi, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onDismiss) { Text("Закрыть") } },
        title = { Text(g.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val bg = if (g.upgraded && g.bgCenter != 0) Brush.radialGradient(listOf(rgb(g.bgCenter), rgb(g.bgEdge))) else null
                Box(
                    Modifier.size(180.dp).clip(RoundedCornerShape(20.dp)).let { if (bg != null) it.background(bg) else it },
                    contentAlignment = Alignment.Center,
                ) { GiftImage(g, Modifier.fillMaxSize().padding(12.dp), animate = true) }
                Spacer(Modifier.height(12.dp))
                if (g.upgraded) Text("№ ${g.number} из ${g.total}")
                else if (g.stars > 0) Text("Стоимость: ${g.stars} ★")
                if (g.fromName.isNotBlank()) Text("От: ${g.fromName}", style = MaterialTheme.typography.bodyMedium)
                if (g.text.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(g.text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                }
                if (g.date > 0) Text(dayLabel(dayOf(g.date)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}
