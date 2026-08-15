package jp.appathy.meshihq.data.remote

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class SiteField(val fieldName: String, val label: String, val value: String)

/**
 * 店舗の公式サイトを1ページだけ取得して、住所・電話・営業時間・座標の候補を拾う。
 * まず schema.org の JSON-LD を見て、無ければ本文テキストの見出し周辺を拾う。
 * 一括クロールはしない前提（ユーザーが1店ずつ実行する）。
 */
object OfficialSiteClient {

    private val LD_JSON = Regex(
        "<script[^>]*type=\"application/ld\\+json\"[^>]*>(.*?)</script>",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    private val TAG = Regex("<[^>]+>")
    private val POSTAL = Regex("〒?\\d{3}-?\\d{4}\\s*([^\\s<>]{4,60})")
    private val TEL = Regex("0\\d{1,4}-\\d{1,4}-\\d{3,4}")
    private val GEO = Regex("q=(-?\\d{1,3}\\.\\d{4,}),(-?\\d{1,3}\\.\\d{4,})")
    private val GEO_PAIR = Regex("\"latitude\"\\s*:\\s*\"?(-?\\d{1,3}\\.\\d{3,})\"?[\\s\\S]{0,80}?\"longitude\"\\s*:\\s*\"?(-?\\d{1,3}\\.\\d{3,})\"?")
    private val HOURS_LABEL = Regex("(営業時間|営業案内|Open|OPEN)[^0-9]{0,20}([0-9]{1,2}[:：][0-9]{2}[\\s\\S]{0,80})")

    fun fetch(url: String): List<SiteField> {
        val html = get(url)
        val fields = linkedMapOf<String, SiteField>()

        for (match in LD_JSON.findAll(html)) {
            parseLd(match.groupValues[1]).forEach { fields.putIfAbsent(it.fieldName, it) }
        }

        val text = html
            .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
            .replace(TAG, "\n")
            .replace("&nbsp;", " ")
            .replace(Regex("[ \\t]+"), " ")

        if (!fields.containsKey("address")) {
            POSTAL.find(text)?.let {
                fields["address"] = SiteField("address", "住所", it.value.trim().take(80))
            }
        }
        if (!fields.containsKey("phone")) {
            TEL.find(text)?.let {
                fields["phone"] = SiteField("phone", "電話", it.value)
            }
        }
        if (!fields.containsKey("opening_hours_raw")) {
            HOURS_LABEL.find(text)?.let {
                val value = it.groupValues[2].replace("\n", " ").trim().take(120)
                fields["opening_hours_raw"] = SiteField("opening_hours_raw", "営業時間", value)
            }
        }
        if (!fields.containsKey("geo")) {
            val geo = GEO.find(html) ?: GEO_PAIR.find(html)
            geo?.let {
                fields["geo"] = SiteField(
                    "geo",
                    "座標",
                    it.groupValues[1] + "," + it.groupValues[2]
                )
            }
        }
        return fields.values.toList()
    }

    private fun parseLd(raw: String): List<SiteField> {
        val trimmed = raw.trim()
        val objects = mutableListOf<JSONObject>()
        runCatching {
            if (trimmed.startsWith("[")) {
                val array = JSONArray(trimmed)
                for (i in 0 until array.length()) objects.add(array.getJSONObject(i))
            } else {
                objects.add(JSONObject(trimmed))
            }
        }
        val fields = mutableListOf<SiteField>()
        for (obj in objects) {
            obj.optJSONObject("address")?.let { address ->
                val text = listOf(
                    address.optString("postalCode"),
                    address.optString("addressRegion"),
                    address.optString("addressLocality"),
                    address.optString("streetAddress")
                ).filter { it.isNotBlank() }.joinToString("")
                if (text.isNotBlank()) fields.add(SiteField("address", "住所", text))
            }
            obj.optString("telephone").takeIf { it.isNotBlank() }?.let {
                fields.add(SiteField("phone", "電話", it))
            }
            obj.optJSONObject("geo")?.let { geo ->
                val lat = geo.optDouble("latitude", 0.0)
                val lon = geo.optDouble("longitude", 0.0)
                if (lat != 0.0 && lon != 0.0) {
                    fields.add(SiteField("geo", "座標", "$lat,$lon"))
                }
            }
            val hours = obj.opt("openingHours")
            when (hours) {
                is String -> if (hours.isNotBlank()) {
                    fields.add(SiteField("opening_hours_raw", "営業時間", hours))
                }
                is JSONArray -> {
                    val joined = (0 until hours.length()).joinToString("; ") { hours.optString(it) }
                    if (joined.isNotBlank()) {
                        fields.add(SiteField("opening_hours_raw", "営業時間", joined))
                    }
                }
            }
        }
        return fields
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.setRequestProperty("User-Agent", "MeshiHQApp/1.3 (personal use)")
        val body = if (connection.responseCode in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            throw IllegalStateException("取得できませんでした（HTTP ${connection.responseCode}）")
        }
        connection.disconnect()
        return body
    }
}
