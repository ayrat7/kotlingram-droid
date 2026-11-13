package dev.example.tg.ui

import android.annotation.SuppressLint
import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import java.io.File
import java.util.concurrent.Executor

private sealed interface Shot {
    class Photo(val file: File) : Shot
    class Video(val file: File) : Shot
}

/**
 * Встроенная камера: нажать — фото, удерживать — видео. После съёмки — просмотр и «Отправить».
 * Нужны разрешения CAMERA (и RECORD_AUDIO для звука в видео); их запрашивает вызывающий экран.
 */
@SuppressLint("MissingPermission")
@Composable
fun CameraCapture(onDismiss: () -> Unit, onCaptured: (File) -> Unit) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor: Executor = remember { ContextCompat.getMainExecutor(ctx) }
    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var front by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var recMs by remember { mutableStateOf(0L) }
    var shot by remember { mutableStateOf<Shot?>(null) }
    var cam by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    val videoCapture = remember {
        VideoCapture.withOutput(
            Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HD, androidx.camera.video.FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))).build(),
        )
    }
    val cs = MaterialTheme.colorScheme

    LaunchedEffect(Unit) {
        ProcessCameraProvider.getInstance(ctx).also { f -> f.addListener({ provider = f.get() }, executor) }
    }
    // (пере)привязка камеры
    LaunchedEffect(provider, front, shot) {
        val p = provider ?: return@LaunchedEffect
        if (shot != null) { p.unbindAll(); return@LaunchedEffect }
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val sel = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        runCatching {
            p.unbindAll()
            cam = p.bindToLifecycle(lifecycleOwner, sel, preview, imageCapture, videoCapture)
        }
    }
    LaunchedEffect(flash, front) {
        imageCapture.flashMode = if (flash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }
    LaunchedEffect(recording) {
        val t0 = System.currentTimeMillis()
        while (recording != null) {
            recMs = System.currentTimeMillis() - t0
            kotlinx.coroutines.delay(200)
        }
        recMs = 0
    }
    DisposableEffect(Unit) {
        onDispose {
            recording?.stop()
            provider?.unbindAll()
        }
    }

    fun outFile(ext: String): File {
        val dir = File(ctx.cacheDir, "outgoing").apply { mkdirs() }
        return File(dir, "cam_${System.currentTimeMillis()}.$ext")
    }

    fun takePhoto() {
        val f = outFile("jpg")
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(f).build(), executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(r: ImageCapture.OutputFileResults) { shot = Shot.Photo(f) }
                override fun onError(e: ImageCaptureException) {}
            },
        )
    }

    fun startVideo() {
        if (recording != null) return
        val f = outFile("mp4")
        cam?.cameraControl?.enableTorch(flash)
        var pending = videoCapture.output.prepareRecording(ctx, FileOutputOptions.Builder(f).build())
        if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pending = pending.withAudioEnabled()
        }
        recording = pending.start(executor) { ev ->
            if (ev is VideoRecordEvent.Finalize) {
                cam?.cameraControl?.enableTorch(false)
                recording = null
                if (!ev.hasError() && f.length() > 0) shot = Shot.Video(f)
            }
        }
    }

    Dialog(onDismissRequest = { if (recording == null) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val s = shot
            if (s == null) {
                AndroidView({ previewView }, Modifier.fillMaxSize())
                Row(Modifier.fillMaxWidth().systemBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ if (recording == null) onDismiss() }) { Icon(Icons.Filled.Close, "Закрыть", tint = Color.White) }
                    Spacer(Modifier.weight(1f))
                    if (recording != null) {
                        Surface(shape = CircleShape, color = Color.Red.copy(alpha = 0.85f)) {
                            Text(formatDurationMs(recMs), Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = Color.White)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton({ flash = !flash }) {
                        Icon(if (flash) Icons.Filled.FlashOn else Icons.Filled.FlashOff, "Вспышка", tint = Color.White)
                    }
                }
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 28.dp, start = 32.dp, end = 32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Spacer(Modifier.size(48.dp))
                    // затвор: тап — фото, удержание — видео
                    Box(
                        Modifier.size(78.dp).clip(CircleShape)
                            .border(4.dp, Color.White, CircleShape)
                            .padding(7.dp).clip(CircleShape)
                            .background(if (recording != null) Color.Red else Color.White)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { takePhoto() },
                                    onLongPress = { startVideo() },
                                    onPress = {
                                        tryAwaitRelease()
                                        recording?.stop()
                                    },
                                )
                            },
                    )
                    IconButton({ if (recording == null) front = !front }, Modifier.size(48.dp)) {
                        Icon(Icons.Filled.FlipCameraAndroid, "Сменить камеру", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                }
                if (recording == null) {
                    Text(
                        "Нажмите — фото, удерживайте — видео", color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 6.dp),
                    )
                }
            } else {
                when (s) {
                    is Shot.Photo -> AsyncImage(s.file, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    is Shot.Video -> LoopingVideo(ctx, s.file)
                }
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton({
                        when (s) { is Shot.Photo -> s.file.delete(); is Shot.Video -> s.file.delete() }
                        shot = null
                    }) { Icon(Icons.Filled.Replay, null); Spacer(Modifier.width(8.dp)); Text("Переснять") }
                    Button({
                        onCaptured(when (s) { is Shot.Photo -> s.file; is Shot.Video -> s.file })
                    }) { Icon(Icons.Filled.Send, null); Spacer(Modifier.width(8.dp)); Text("Отправить") }
                }
                IconButton({ onDismiss() }, Modifier.align(Alignment.TopStart).systemBarsPadding().padding(8.dp)) {
                    Icon(Icons.Filled.Close, "Закрыть", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun LoopingVideo(ctx: Context, file: File) {
    val player = remember {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    AndroidView(
        { c -> PlayerView(c).apply { this.player = player; useController = false } },
        Modifier.fillMaxSize(),
    )
}
