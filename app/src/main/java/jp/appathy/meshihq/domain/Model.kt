package jp.appathy.meshihq.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Categories {
    val ALL = listOf(
        "和食", "中華", "寿司", "焼肉", "しゃぶしゃぶ", "イタリアン", "ピザ専門",
        "ファミレス", "ラーメン", "たこ焼き", "うどん", "ハンバーガー",
        "アイス", "カフェ", "居酒屋", "その他"
    )

    fun colorOf(category: String): Int = when (category) {
        "和食" -> 0xFF6D8B74.toInt()
        "中華" -> 0xFFC0504D.toInt()
        "寿司" -> 0xFF3F7CAC.toInt()
        "焼肉" -> 0xFF8E3B46.toInt()
        "しゃぶしゃぶ" -> 0xFFB07A5A.toInt()
        "イタリアン" -> 0xFF4F8A5B.toInt()
        "ピザ専門" -> 0xFFD9782D.toInt()
        "ファミレス" -> 0xFF7A7FB5.toInt()
        "ラーメン" -> 0xFFCE7B3B.toInt()
        "たこ焼き" -> 0xFFB5651D.toInt()
        "うどん" -> 0xFF9C8B4B.toInt()
        "ハンバーガー" -> 0xFFA65E2E.toInt()
        "アイス" -> 0xFF6FA8C7.toInt()
        "カフェ" -> 0xFF7B5E48.toInt()
        "居酒屋" -> 0xFFB03A48.toInt()
        else -> 0xFF757575.toInt()
    }
}

object Budget {
    val STEPS = listOf(1000, 1500, 2000, 2500, 3000)

    fun label(min: Int?, max: Int?): String {
        if (min == null && max == null) return "未設定"
        val lo = min ?: max!!
        val hi = max ?: min!!
        return if (lo == hi) "1人 ${lo}円" else "1人 ${lo}〜${hi}円"
    }

    fun total(min: Int?, max: Int?, people: Int): String {
        if (min == null && max == null) return "-"
        val lo = (min ?: max!!) * people
        val hi = (max ?: min!!) * people
        return if (lo == hi) "${lo}円" else "${lo}〜${hi}円"
    }
}

object SourceType {
    const val SELF_VISIT = "self_visit"
    const val OFFICIAL = "official"
    const val OSM = "osm"
    const val AI_ESTIMATE = "ai_estimate"
    const val OCR = "ocr"
    const val HEARSAY = "hearsay"

    fun confidenceOf(type: String): Double = when (type) {
        SELF_VISIT -> 1.0
        OFFICIAL -> 0.9
        OCR -> 0.7
        OSM -> 0.6
        AI_ESTIMATE -> 0.4
        else -> 0.3
    }

    fun labelOf(type: String): String = when (type) {
        SELF_VISIT -> "自分で確認"
        OFFICIAL -> "公式情報"
        OCR -> "写真OCR"
        OSM -> "OSM取込"
        AI_ESTIMATE -> "AI推定"
        else -> "伝聞"
    }
}

object ShopStatus {
    val ALL = listOf("active", "closed", "unknown")

    fun labelOf(status: String): String = when (status) {
        "active" -> "営業中"
        "closed" -> "閉店"
        else -> "未確認"
    }
}

object Geo {
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    fun distanceLabel(meters: Double): String =
        if (meters < 1000) "${meters.toInt()}m" else String.format("%.1fkm", meters / 1000)
}
