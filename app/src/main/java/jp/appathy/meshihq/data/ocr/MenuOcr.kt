package jp.appathy.meshihq.data.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

data class MenuCandidate(
    val name: String,
    val price: Int?,
    val raw: String
)

/**
 * メニュー写真から品名と価格の候補を拾う。
 * 端末内で完結するML Kit日本語モデルを使い、通信は発生しない。
 */
object MenuOcr {

    private val PRICE = Regex("([0-9０-９,，]{2,6})\\s*(円|¥|￥)")
    private val YEN_PREFIX = Regex("(¥|￥)\\s*([0-9０-９,，]{2,6})")
    private val NOISE = Regex("^[\\s\\-—・:：|]+|[\\s\\-—・:：|]+$")

    suspend fun recognize(bitmap: Bitmap): List<MenuCandidate> {
        val text = runRecognizer(bitmap) ?: return emptyList()
        return text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { toCandidate(it) }
            .distinctBy { it.name + "/" + it.price }
    }

    private suspend fun runRecognizer(bitmap: Bitmap): String? =
        suspendCancellableCoroutine { continuation ->
            val recognizer = TextRecognition.getClient(
                JapaneseTextRecognizerOptions.Builder().build()
            )
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { result ->
                    if (continuation.isActive) continuation.resume(result.text)
                }
                .addOnFailureListener {
                    if (continuation.isActive) continuation.resume(null)
                }
        }

    fun toCandidate(line: String): MenuCandidate? {
        val price = extractPrice(line)
        val name = line
            .replace(PRICE, "")
            .replace(YEN_PREFIX, "")
            .replace(NOISE, "")
            .trim()
        if (name.length < 2) return null
        if (price == null && !line.any { it in '\u3040'..'\u30ff' || it in '\u4e00'..'\u9fff' }) {
            return null
        }
        return MenuCandidate(name = name.take(40), price = price, raw = line)
    }

    private fun extractPrice(line: String): Int? {
        val match = PRICE.find(line)?.groupValues?.getOrNull(1)
            ?: YEN_PREFIX.find(line)?.groupValues?.getOrNull(2)
            ?: return null
        val normalized = match
            .replace(",", "")
            .replace("，", "")
            .map { ch -> if (ch in '０'..'９') ('0' + (ch - '０')) else ch }
            .joinToString("")
        val value = normalized.toIntOrNull() ?: return null
        return if (value in 50..50000) value else null
    }
}
