package dev.example.tg.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.io.InputStream

sealed interface Prepared {
    class Photo(val path: String, val width: Int, val height: Int) : Prepared
    class Video(val path: String, val width: Int, val height: Int, val durationSec: Int) : Prepared
    class Doc(val path: String) : Prepared
}

/** Подготовка выбранных пользователем файлов: TDLib нужен путь в файловой системе, а не content://. */
object Attachments {
    private const val MAX_SIDE = 2560

    fun root(ctx: Context): File = File(ctx.cacheDir, "outgoing").apply { mkdirs() }

    /** Удаляет старые временные файлы (вызывать вне главного потока). */
    fun cleanup(ctx: Context) {
        val limit = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        root(ctx).listFiles()?.filter { it.lastModified() < limit }?.forEach { it.deleteRecursively() }
    }

    fun prepare(ctx: Context, uri: Uri, asFile: Boolean): Prepared {
        val mime = ctx.contentResolver.getType(uri).orEmpty()
        return when {
            asFile -> Prepared.Doc(copy(ctx, uri))
            mime.startsWith("image/") && mime != "image/gif" -> photo(ctx, uri)
            mime.startsWith("video/") -> video(ctx, uri)
            else -> Prepared.Doc(copy(ctx, uri))
        }
    }

    private fun displayName(ctx: Context, uri: Uri): String? =
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()

    private fun copy(ctx: Context, uri: Uri): String {
        val name = (displayName(ctx, uri) ?: "file").replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val dir = File(root(ctx), System.nanoTime().toString()).apply { mkdirs() }
        val out = File(dir, name)
        val input = ctx.contentResolver.openInputStream(uri) ?: throw IOException("Не удалось открыть файл")
        input.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
        return out.absolutePath
    }

    private fun photo(ctx: Context, uri: Uri): Prepared.Photo {
        val cr = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Не удалось прочитать изображение")

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2

        var bmp: Bitmap = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IOException("Не удалось прочитать изображение")

        val rot = cr.openInputStream(uri)?.use { rotation(it) } ?: 0
        if (rot != 0) {
            val m = Matrix().apply { postRotate(rot.toFloat()) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        if (bmp.hasAlpha()) {
            val flat = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(flat)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(bmp, 0f, 0f, null)
            bmp = flat
        }
        val out = File(root(ctx), "photo_${System.nanoTime()}.jpg")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return Prepared.Photo(out.absolutePath, bmp.width, bmp.height)
    }

    private fun rotation(s: InputStream): Int =
        when (ExifInterface(s).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

    private fun video(ctx: Context, uri: Uri): Prepared.Video {
        val path = copy(ctx, uri)
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(path)
            fun num(key: Int): Long = r.extractMetadata(key)?.toLongOrNull() ?: 0L
            var w = num(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH).toInt()
            var h = num(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT).toInt()
            val rot = num(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION).toInt()
            if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
            val dur = ((num(MediaMetadataRetriever.METADATA_KEY_DURATION) + 500) / 1000).toInt()
            return Prepared.Video(path, w, h, dur)
        } finally {
            r.release()
        }
    }
}
