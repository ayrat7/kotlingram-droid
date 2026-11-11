package dev.example.tg.media

import kotlin.math.max
import kotlin.math.roundToInt

/** Waveform голосовых Telegram: 5-битные значения, упакованные подряд (little-endian). */
object Waveform {
    fun encode(raw: List<Float>, count: Int = 100): ByteArray {
        if (raw.isEmpty()) return ByteArray(0)
        val peak = raw.max().coerceAtLeast(0.05f)
        val samples = IntArray(count) { i ->
            val from = i * raw.size / count
            val to = max(from + 1, (i + 1) * raw.size / count).coerceAtMost(raw.size)
            var m = 0f
            for (j in from until to) m = max(m, raw[j])
            ((m / peak) * 31f).roundToInt().coerceIn(0, 31)
        }
        val total = (count * 5 + 7) / 8
        val out = ByteArray(total + 1)
        for (i in 0 until count) {
            val bit = i * 5
            val idx = bit / 8
            val v = samples[i] shl (bit % 8)
            out[idx] = (out[idx].toInt() or (v and 0xFF)).toByte()
            out[idx + 1] = (out[idx + 1].toInt() or ((v shr 8) and 0xFF)).toByte()
        }
        return out.copyOf(total)
    }

    /** Значения 0..1. */
    fun decode(b: ByteArray): List<Float> {
        val n = b.size * 8 / 5
        return List(n) { i ->
            val bit = i * 5
            val idx = bit / 8
            var v = b[idx].toInt() and 0xFF
            if (idx + 1 < b.size) v = v or ((b[idx + 1].toInt() and 0xFF) shl 8)
            ((v shr (bit % 8)) and 31) / 31f
        }
    }

    /** Приводит к [count] столбикам для отрисовки. */
    fun display(b: ByteArray, count: Int = 40): List<Float> {
        val src = decode(b)
        if (src.isEmpty()) return List(count) { 0.25f }
        return List(count) { i ->
            val from = i * src.size / count
            val to = max(from + 1, (i + 1) * src.size / count).coerceAtMost(src.size)
            var s = 0f
            for (j in from until to) s += src[j]
            (s / (to - from)).coerceIn(0.08f, 1f)
        }
    }
}
