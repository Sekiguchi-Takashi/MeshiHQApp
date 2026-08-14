package jp.appathy.meshihq.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 端末のギャラリーから選んだ画像をアプリ内ストレージへコピーする。
 * URIのままだと権限が切れて後から開けなくなるため、実体を持つ。
 */
object PhotoStore {

    private const val MAX_EDGE = 1600

    fun dir(context: Context): File = File(context.filesDir, "photos").apply { mkdirs() }

    data class Saved(val path: String, val takenAt: Long?)

    fun save(context: Context, uri: Uri, shopId: Long): Saved? {
        val takenAt = readTakenAt(context, uri)
        val bitmap = decode(context, uri, MAX_EDGE) ?: return null
        val file = File(dir(context), "shop_${shopId}_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
        }
        bitmap.recycle()
        return Saved(file.absolutePath, takenAt)
    }

    /** 縮小コピーを作る前に、元画像のEXIFから撮影日時を読む。 */
    fun readTakenAt(context: Context, uri: Uri): Long? {
        val raw = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            }
        }.getOrNull() ?: return null
        val format = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
        return runCatching { format.parse(raw)?.time }.getOrNull()
    }

    fun delete(path: String) {
        runCatching { File(path).delete() }
    }

    fun thumbnail(path: String, maxEdge: Int = 320): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
        }
        return BitmapFactory.decodeFile(path, options)
    }

    fun decode(context: Context, uri: Uri, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }
        if (bounds.outWidth <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
        }
        return context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
    }

    private fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        var longest = maxOf(width, height)
        while (longest / sample > maxEdge) sample *= 2
        return sample
    }
}
