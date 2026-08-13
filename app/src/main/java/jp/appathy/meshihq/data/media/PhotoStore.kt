package jp.appathy.meshihq.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * 端末のギャラリーから選んだ画像をアプリ内ストレージへコピーする。
 * URIのままだと権限が切れて後から開けなくなるため、実体を持つ。
 */
object PhotoStore {

    private const val MAX_EDGE = 1600

    fun dir(context: Context): File = File(context.filesDir, "photos").apply { mkdirs() }

    fun save(context: Context, uri: Uri, shopId: Long): String? {
        val bitmap = decode(context, uri, MAX_EDGE) ?: return null
        val file = File(dir(context), "shop_${shopId}_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
        }
        bitmap.recycle()
        return file.absolutePath
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
