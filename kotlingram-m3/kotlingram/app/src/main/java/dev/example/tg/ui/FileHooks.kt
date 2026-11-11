package dev.example.tg.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.example.tg.data.FileState
import kotlinx.coroutines.flow.flowOf
import java.io.File

/** Подписка на состояние файла TDLib; при auto = true заодно запускает скачивание. */
@Composable
fun rememberFile(id: Int?, auto: Boolean = true): FileState? {
    val repo = LocalRepo.current
    val flow = remember(id) { if (id == null) flowOf(null) else repo.fileFlow(id) }
    val state by flow.collectAsState(initial = id?.let { repo.files.value[it] })
    LaunchedEffect(id, auto) { if (id != null && auto) repo.download(id) }
    return state
}

private val avatarColors = listOf(
    Color(0xFFE17076), Color(0xFFFAA774), Color(0xFFA695E7),
    Color(0xFF7BC862), Color(0xFF6EC9CB), Color(0xFF65AADD), Color(0xFFEE7AAE),
)

@Composable
fun Avatar(fileId: Int?, id: Long, title: String, size: Dp = 48.dp) {
    val f = rememberFile(fileId)
    val path = f?.path
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(avatarColors[Math.floorMod(id, avatarColors.size.toLong()).toInt()]),
        contentAlignment = Alignment.Center,
    ) {
        if (path != null) {
            AsyncImage(File(path), null, Modifier.size(size), contentScale = ContentScale.Crop)
        } else {
            Text(
                title.firstOrNull()?.uppercase() ?: "?",
                color = Color.White,
                fontSize = (size.value / 2.3f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
