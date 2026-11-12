package dev.example.tg.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.view.Surface
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import dev.example.tg.media.Attachments
import java.io.File

private const val MAX_MS = 60_000L

private fun Context.findActivity(): ComponentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is ComponentActivity) return c
        c = c.baseContext
    }
    return null
}

fun hasVideoNotePermissions(ctx: Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

/**
 * Запись видеосообщения (кружка). Круглое превью, до 60 секунд, переключение камер.
 * Видео обрезается в квадрат через ViewPort, поэтому в чате оно ложится в круг без искажений.
 * Разрешения CAMERA и RECORD_AUDIO должны быть выданы до показа.
 */
@SuppressLint("MissingPermission") // разрешения проверяются до показа (hasVideoNotePermissions)
@Composable
fun VideoNoteRecorder(onDismiss: () -> Unit, onSend: (path: String, durationSec: Int, length: Int) -> Unit) {
    val ctx = LocalContext.current
    val activity = remember { ctx.findActivity() }
    val executor = remember { ContextCompat.getMainExecutor(ctx) }
    val previewView = remember {
        PreviewView(ctx).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE // TextureView: нормально обрезается в круг
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var front by remember { mutableStateOf(true) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var cancelled by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf<String?>(null) }
    val provider = remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(Unit) {
        val f = ProcessCameraProvider.getInstance(ctx)
        f.addListener({ runCatching { provider.value = f.get() }.onFailure { failed = "Камера недоступна" } }, executor)
    }

    // (пере)привязка камеры при смене объектива
    LaunchedEffect(provider.value, front, activity) {
        val p = provider.value ?: return@LaunchedEffect
        val owner = activity ?: return@LaunchedEffect
        try {
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.SD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
                .setTargetVideoEncodingBitRate(1_200_000)
                .build()
            val vc = VideoCapture.withOutput(recorder)
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
            val viewPort = ViewPort.Builder(Rational(1, 1), rotation).setScaleType(ViewPort.FILL_CENTER).build()
            val group = UseCaseGroup.Builder().setViewPort(viewPort).addUseCase(preview).addUseCase(vc).build()
            p.unbindAll()
            p.bindToLifecycle(
                owner,
                if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA,
                group,
            )
            videoCapture = vc
        } catch (e: Exception) {
            failed = "Не удалось запустить камеру"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cancelled = true
            recording?.stop()
            provider.value?.unbindAll()
        }
    }

    fun start() {
        val vc = videoCapture ?: return
        if (recording != null) return
        val dir = File(ctx.cacheDir, "outgoing").apply { mkdirs() }
        val file = File(dir, "note_${System.currentTimeMillis()}.mp4")
        val opts = FileOutputOptions.Builder(file).setDurationLimitMillis(MAX_MS).build()
        try {
            recording = vc.output.prepareRecording(ctx, opts).withAudioEnabled().start(executor) { ev ->
                when (ev) {
                    is VideoRecordEvent.Status -> elapsed = ev.recordingStats.recordedDurationNanos / 1_000_000
                    is VideoRecordEvent.Finalize -> {
                        recording = null
                        if (cancelled || ev.hasError()) {
                            file.delete()
                            if (!cancelled && ev.hasError() && elapsed > 700) failed = "Не удалось записать видео"
                        } else {
                            val info = runCatching { Attachments.videoInfo(file.path) }.getOrNull()
                            if (info == null || info.durationSec < 1) {
                                file.delete()
                                onDismiss()
                            } else {
                                val side = minOf(info.width, info.height).let { if (it <= 0) 480 else it }
                                onSend(file.path, info.durationSec, side)
                            }
                        }
                    }
                    else -> {}
                }
            }
        } catch (e: SecurityException) {
            failed = "Нет доступа к камере или микрофону"
        }
    }

    LaunchedEffect(failed) {
        if (failed != null) {
            kotlinx.coroutines.delay(1800)
            onDismiss()
        }
    }

    val rec = recording != null
    Dialog(
        onDismissRequest = { if (!rec) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = Color.Black.copy(alpha = 0.82f)) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val ring = MaterialTheme.colorScheme.primary
                Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier.size(280.dp).clip(CircleShape).background(Color.DarkGray),
                    )
                    Canvas(Modifier.fillMaxSize()) {
                        val w = 4.dp.toPx()
                        drawCircle(Color.White.copy(alpha = 0.25f), radius = size.minDimension / 2 - w / 2, style = Stroke(w))
                        val p = (elapsed / MAX_MS.toFloat()).coerceIn(0f, 1f)
                        if (p > 0f) {
                            drawArc(
                                ring, -90f, 360f * p, false,
                                topLeft = Offset(w / 2, w / 2),
                                size = Size(size.width - w, size.height - w),
                                style = Stroke(w, cap = StrokeCap.Round),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    failed ?: if (rec) formatDuration((elapsed / 1000).toInt()) else "Видеосообщение",
                    color = if (failed != null) MaterialTheme.colorScheme.error else Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(28.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            cancelled = true
                            recording?.stop()
                            onDismiss()
                        },
                        modifier = Modifier.size(52.dp),
                    ) { Icon(Icons.Filled.Close, "Отмена") }

                    if (!rec) {
                        FilledIconButton(
                            onClick = { start() },
                            enabled = videoCapture != null && failed == null,
                            modifier = Modifier.size(72.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFE53935)),
                        ) { Icon(Icons.Filled.FiberManualRecord, "Записать", Modifier.size(36.dp), tint = Color.White) }
                    } else {
                        FilledIconButton(
                            onClick = { recording?.stop() },
                            modifier = Modifier.size(72.dp),
                        ) { Icon(Icons.AutoMirrored.Filled.Send, "Отправить", Modifier.size(32.dp)) }
                    }

                    FilledTonalIconButton(
                        onClick = { front = !front },
                        enabled = !rec,
                        modifier = Modifier.size(52.dp),
                    ) { Icon(Icons.Filled.Cameraswitch, "Сменить камеру") }
                }
            }
        }
    }
}
