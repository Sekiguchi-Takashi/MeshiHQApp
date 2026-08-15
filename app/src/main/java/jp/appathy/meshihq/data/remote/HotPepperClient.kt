package jp.appathy.meshihq.data.remote

import jp.appathy.meshihq.domain.SourceType
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * ホットペッパーグルメサーチAPI。
 * 利用にはリクルートWEBサービスのAPIキーが必要で、アプリ内にクレジット表記を出すこと。
 * 取得した店舗情報そのものの再販は規約で禁止されている。
 */
object HotPepperClient {

    private const val ENDPOINT = "https://webservice.recruit.co.jp/hotpepper/gourmet/v1/"
    private const val PAGE_SIZE = 100
    private const val MAX_PAGES = 5

    /**
     * keyword で検索し、店名と住所の両方の条件に合うものだけ返す。
     * nameContains が空なら店名は問わない。
     */
    fun search(
        apiKey: String,
        keyword: String,
        nameContains: String,
        addressContains: String
    ): List<ImportCandidate> {
        val result = mutableListOf<ImportCandidate>()
        var start = 1
        var page = 0
        while (page < MAX_PAGES) {
            val url = buildString {
                append(ENDPOINT)
                append("?key=").append(URLEncoder.encode(apiKey, "UTF-8"))
                append("&keyword=").append(URLEncoder.encode(keyword, "UTF-8"))
                append("&count=").append(PAGE_SIZE)
                append("&start=").append(start)
                append("&datum=world&format=json")
            }
            val body = get(url)
            val results = JSONObject(body).optJSONObject("results")
                ?: throw IllegalStateException("ホットペッパーAPIの応答を解釈できませんでした")
            results.optJSONObject("error")?.let {
                throw IllegalStateException("APIキーが受け付けられませんでした")
            }
            val shops = results.optJSONArray("shop") ?: break
            for (i in 0 until shops.length()) {
                val shop = shops.getJSONObject(i)
                val name = shop.optString("name").takeIf { it.isNotBlank() } ?: continue
                val address = shop.optString("address").takeIf { it.isNotBlank() }
                if (nameContains.isNotBlank() && !name.contains(nameContains)) continue
                if (addressContains.isNotBlank() &&
                    (address == null || !address.contains(addressContains))
                ) continue
                result.add(
                    ImportCandidate(
                        externalId = "hp/" + shop.optString("id"),
                        name = name,
                        lat = shop.optDouble("lat", 0.0),
                        lon = shop.optDouble("lng", 0.0),
                        category = HotPepperCategory.of(
                            shop.optJSONObject("genre")?.optString("name").orEmpty(),
                            name
                        ),
                        address = address,
                        phone = null,
                        openingHoursRaw = shop.optString("open").takeIf { it.isNotBlank() },
                        website = shop.optJSONObject("urls")?.optString("pc")
                            ?.takeIf { it.isNotBlank() },
                        budgetPerPerson = parseBudget(
                            shop.optJSONObject("budget")?.optString("average").orEmpty()
                        ),
                        sourceType = SourceType.HOTPEPPER
                    )
                )
            }
            val available = results.optInt("results_available", 0)
            start += PAGE_SIZE
            page++
            if (start > available) break
        }
        return result.filter { it.lat != 0.0 && it.lon != 0.0 }
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.setRequestProperty("User-Agent", "MeshiHQApp/1.3")
        val body = if (connection.responseCode in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            throw IllegalStateException("ホットペッパーAPI ${connection.responseCode}")
        }
        connection.disconnect()
        return body
    }

    /** 「2001～3000円」のような表記から下限側を拾う。 */
    private fun parseBudget(text: String): Int? {
        val match = Regex("([0-9,]{3,7})").find(text.replace("，", ",")) ?: return null
        return match.groupValues[1].replace(",", "").toIntOrNull()?.takeIf { it in 100..100000 }
    }
}

object HotPepperCategory {

    fun of(genre: String, name: String): String {
        val text = genre + name
        return when {
            text.contains("ラーメン") -> "ラーメン"
            text.contains("寿司") || text.contains("すし") || text.contains("鮨") -> "寿司"
            text.contains("焼肉") || text.contains("ホルモン") -> "焼肉"
            text.contains("しゃぶ") || text.contains("すき焼") -> "しゃぶしゃぶ"
            text.contains("イタリア") || text.contains("パスタ") -> "イタリアン"
            text.contains("ピザ") || text.contains("ピッツァ") -> "ピザ専門"
            text.contains("たこ焼") || text.contains("お好み焼") -> "たこ焼き"
            text.contains("うどん") || text.contains("そば") -> "うどん"
            text.contains("バーガー") -> "ハンバーガー"
            text.contains("アイス") || text.contains("ジェラート") -> "アイス"
            text.contains("カフェ") || text.contains("喫茶") -> "カフェ"
            text.contains("居酒屋") || text.contains("ダイニングバー") || text.contains("バル") -> "居酒屋"
            text.contains("中華") || text.contains("中国") -> "中華"
            text.contains("ファミリーレストラン") || text.contains("ファミレス") -> "ファミレス"
            text.contains("和食") || text.contains("日本料理") || text.contains("定食") -> "和食"
            else -> "その他"
        }
    }
}
