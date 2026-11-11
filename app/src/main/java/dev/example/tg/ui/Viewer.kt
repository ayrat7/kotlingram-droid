package dev.example.tg.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import dev.example.tg.data.Media
import java.io.File

@Composable
fun MediaViewer(media: Media, onDismiss: () -> Unit) {
    // пока смотрим видео/фото — голосовое не должно играть поверх
    val player = LocalPlayer.current
    LaunchedEffect(Unit) { player.pause() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            when (media) {
                is Media.Photo -> ZoomableImage(media)
                is Media.Video -> VideoPlayer(media)
                else -> {}
            }
            IconButton(onDismiss, Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)) {
                Icon(Icons.Filled.Close, "Закрыть", tint = Color.White)
            }
        }
    }
}

@Composable
private fun ZoomableImage(p: Media.Photo) {
    val full = rememberFile(p.fullFile)
    val small = rememberFile(p.file)
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val path = full?.path ?: small?.path

    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset = if (scale <= 1f) Offset.Zero else offset + pan
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    scale = if (scale > 1f) 1f else 2.5f
                    offset = Offset.Zero
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        if (path != null) {
            AsyncImage(
                File(path), null,
                Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
                contentScale = ContentScale.Fit,
            )
        }
        if (full?.path == null) {
            val pr = full?.progress ?: 0f
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)) {
                if (pr > 0f) CircularProgressIndicator({ pr }, color = Color.White) else CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

@Composable
private fun VideoPlayer(v: Media.Video) {
    val ctx = LocalContext.current
    val f = rememberFile(v.file)
    val path = f?.path

    if (path == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val pr = f?.progress ?: 0f
            if (pr > 0f) CircularProgressIndicator({ pr }, color = Color.White) else CircularProgressIndicator(color = Color.White)
        }
        return
    }

    val exo = remember(path) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
            repeatMode = if (v.gif) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(exo) { onDispose { exo.release() } }

    AndroidView(
        factory = { c ->
            PlayerView(c).apply {
                player = exo
                useController = !v.gif
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}
