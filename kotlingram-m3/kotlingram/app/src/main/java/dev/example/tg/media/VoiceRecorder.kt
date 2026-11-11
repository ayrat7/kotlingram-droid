package dev.example.tg.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.sqrt

/** Запись голосовых в OGG/Opus — родной формат голосовых Telegram. */
class VoiceRecorder(private val ctx: Context) {
    class Result(val path: String, val durationSec: Int, val waveform: ByteArray)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val active = MutableStateFlow(false)
    val elapsedMs = MutableStateFlow(0L)
    val level = MutableStateFlow(0f)

    private var rec: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L
    private val amps = ArrayList<Float>()
    private var job: Job? = null

    @Suppress("DEPRECATION")
    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()

    fun start(): Boolean {
        if (rec != null) return true
        val f = File(Attachments.root(ctx), "voice_${System.currentTimeMillis()}.ogg")
        val r = newRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.OGG)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(48000)
            r.setAudioEncodingBitRate(32000)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            rec = r
            file = f
            amps.clear()
            startedAt = SystemClock.elapsedRealtime()
            elapsedMs.value = 0
            active.value = true
            job = scope.launch {
                while (isActive) {
                    delay(80)
                    val a = runCatching { r.maxAmplitude }.getOrDefault(0)
                    val n = sqrt(a / 32767f).coerceIn(0f, 1f)
                    amps += n
                    level.value = n
                    elapsedMs.value = SystemClock.elapsedRealtime() - startedAt
                }
            }
            true
        } catch (e: Exception) {
            runCatching { r.release() }
            f.delete()
            false
        }
    }

    /** Останавливает запись. null — если запись слишком короткая или сломалась. */
    fun stop(): Result? {
        val r = rec ?: return null
        job?.cancel()
        val dur = SystemClock.elapsedRealtime() - startedAt
        val ok = runCatching { r.stop() }.isSuccess
        val f = finish(r)
        if (!ok || dur < 700 || f == null) {
            f?.delete()
            return null
        }
        return Result(f.absolutePath, ((dur + 500) / 1000).toInt().coerceAtLeast(1), Waveform.encode(amps))
    }

    fun cancel() {
        val r = rec ?: return
        job?.cancel()
        runCatching { r.stop() }
        finish(r)?.delete()
    }

    private fun finish(r: MediaRecorder): File? {
        runCatching { r.release() }
        rec = null
        active.value = false
        level.value = 0f
        elapsedMs.value = 0
        val f = file
        file = null
        return f
    }
}
