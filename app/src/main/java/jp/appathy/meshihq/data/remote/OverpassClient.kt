package jp.appathy.meshihq.data.remote

import jp.appathy.meshihq.domain.SourceType
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class OsmCandidate(
    val osmId: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val amenity: String?,
    val cuisine: String?,
    val phone: String?,
    val address: String?,
    val openingHoursRaw: String?
)

object OverpassClient {

    private const val ENDPOINT = "https://overpass-api.de/api/interpreter"

    /**
     * 指定座標の周囲 radiusMeters にある飲食店を取得する。
     * 名前のない要素は取り込み対象にならないため、ここで落とす。
     */
    fun fetchAround(lat: Double, lon: Double, radiusMeters: Int): List<OsmCandidate> {
        val filter = "[\"amenity\"~\"^(restaurant|cafe|fast_food|pub|bar|ice_cream)$\"][\"name\"]"
        val query = buildString {
            append("[out:json][timeout:25];(")
            append("node$filter(around:$radiusMeters,$lat,$lon);")
            append("way$filter(around:$radiusMeters,$lat,$lon);")
            append(");out center tags;")
        }
        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 15000
        connection.readTimeout = 40000
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        connection.setRequestProperty("User-Agent", "MeshiHQApp/0.2")
        connection.outputStream.use { out ->
            out.write(("data=" + java.net.URLEncoder.encode(query, "UTF-8")).toByteArray())
        }
        val body = if (connection.responseCode in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            val message = connection.errorStream?.bufferedReader()?.use { it.readText() }
            throw IllegalStateException("Overpass ${connection.responseCode}: ${message.orEmpty().take(120)}")
        }
        connection.disconnect()
        return parse(body)
    }

    fun toCandidates(list: List<OsmCandidate>): List<ImportCandidate> = list.map { candidate ->
        ImportCandidate(
            externalId = candidate.osmId,
            name = candidate.name,
            lat = candidate.lat,
            lon = candidate.lon,
            category = OsmCategory.of(candidate),
            address = candidate.address,
            phone = candidate.phone,
            openingHoursRaw = candidate.openingHoursRaw,
            sourceType = SourceType.OSM
        )
    }

    fun parse(body: String): List<OsmCandidate> {
        val elements = JSONObject(body).optJSONArray("elements") ?: return emptyList()
        val list = mutableListOf<OsmCandidate>()
        for (i in 0 until elements.length()) {
            val element = elements.getJSONObject(i)
            val tags = element.optJSONObject("tags") ?: continue
            val name = tags.optString("name").takeIf { it.isNotBlank() } ?: continue
            val lat = if (element.has("lat")) element.optDouble("lat")
            else element.optJSONObject("center")?.optDouble("lat") ?: continue
            val lon = if (element.has("lon")) element.optDouble("lon")
            else element.optJSONObject("center")?.optDouble("lon") ?: continue
            list.add(
                OsmCandidate(
                    osmId = element.optString("type", "node") + "/" + element.optLong("id"),
                    name = name,
                    lat = lat,
                    lon = lon,
                    amenity = tags.optString("amenity").takeIf { it.isNotBlank() },
                    cuisine = tags.optString("cuisine").takeIf { it.isNotBlank() },
                    phone = (tags.optString("phone").takeIf { it.isNotBlank() }
                        ?: tags.optString("contact:phone").takeIf { it.isNotBlank() }),
                    address = buildAddress(tags),
                    openingHoursRaw = tags.optString("opening_hours").takeIf { it.isNotBlank() }
                )
            )
        }
        return list
    }

    private fun buildAddress(tags: JSONObject): String? {
        val parts = listOf(
            tags.optString("addr:province"),
            tags.optString("addr:city"),
            tags.optString("addr:suburb"),
            tags.optString("addr:quarter"),
            tags.optString("addr:neighbourhood"),
            tags.optString("addr:block_number"),
            tags.optString("addr:housenumber")
        ).filter { it.isNotBlank() }
        return parts.joinToString("").takeIf { it.isNotBlank() }
    }
}

object OsmCategory {

    fun of(candidate: OsmCandidate): String {
        val cuisine = candidate.cuisine.orEmpty().lowercase()
        when {
            cuisine.contains("pizza") -> return "ピザ専門"
            cuisine.contains("ramen") || cuisine.contains("noodle") -> return "ラーメン"
            cuisine.contains("sushi") -> return "寿司"
            cuisine.contains("udon") || cuisine.contains("soba") -> return "うどん"
            cuisine.contains("yakiniku") || cuisine.contains("bbq") ||
                cuisine.contains("barbecue") -> return "焼肉"
            cuisine.contains("shabu") || cuisine.contains("sukiyaki") -> return "しゃぶしゃぶ"
            cuisine.contains("takoyaki") || cuisine.contains("okonomiyaki") -> return "たこ焼き"
            cuisine.contains("burger") -> return "ハンバーガー"
            cuisine.contains("ice_cream") -> return "アイス"
            cuisine.contains("italian") -> return "イタリアン"
            cuisine.contains("chinese") -> return "中華"
            cuisine.contains("japanese") || cuisine.contains("washoku") -> return "和食"
            cuisine.contains("coffee") || cuisine.contains("cafe") -> return "カフェ"
            cuisine.contains("family_restaurant") -> return "ファミレス"
            cuisine.contains("izakaya") -> return "居酒屋"
        }
        return when (candidate.amenity) {
            "cafe" -> "カフェ"
            "ice_cream" -> "アイス"
            "pub", "bar" -> "居酒屋"
            "fast_food" -> "ファミレス"
            else -> "その他"
        }
    }
}
