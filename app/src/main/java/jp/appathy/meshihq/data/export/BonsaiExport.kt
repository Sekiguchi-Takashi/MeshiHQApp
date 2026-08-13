package jp.appathy.meshihq.data.export

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import jp.appathy.meshihq.data.db.Shop
import jp.appathy.meshihq.domain.Budget
import jp.appathy.meshihq.domain.ShopStatus
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * BonsaiApp の RAG 資料形式（frontmatter 付き Markdown）で書き出す。
 * BONSAI_API.md は読むだけで、サーバ側の仕様には触れない。
 */
object BonsaiExport {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.JAPAN)

    fun exportAll(context: Context, treeUri: Uri, shops: List<Shop>): Int {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return 0
        var count = 0
        for (shop in shops) {
            val fileName = "shop_%04d_%s.md".format(shop.id, sanitize(shop.name))
            tree.findFile(fileName)?.delete()
            val file = tree.createFile("text/markdown", fileName) ?: continue
            context.contentResolver.openOutputStream(file.uri)?.use { out ->
                out.write(toMarkdown(shop).toByteArray(Charsets.UTF_8))
            }
            count++
        }
        writeBackup(context, tree, shops)
        return count
    }

    fun toMarkdown(shop: Shop): String {
        val sb = StringBuilder()
        sb.append("---\n")
        sb.append("type: shop\n")
        sb.append("shop_id: ").append(shop.id).append('\n')
        sb.append("name: ").append(shop.name).append('\n')
        sb.append("category: ").append(shop.category).append('\n')
        sb.append("status: ").append(ShopStatus.labelOf(shop.status)).append('\n')
        sb.append("lat: ").append(shop.lat).append('\n')
        sb.append("lon: ").append(shop.lon).append('\n')
        shop.address?.let { sb.append("address: ").append(it).append('\n') }
        shop.phone?.let { sb.append("phone: ").append(it).append('\n') }
        sb.append("updated_at: ").append(dateFormat.format(Date(shop.updatedAt))).append('\n')
        sb.append("---\n\n")
        sb.append("# ").append(shop.name).append("（").append(shop.category).append("）\n\n")
        line(sb, "住所", shop.address)
        line(sb, "電話", shop.phone)
        line(sb, "営業時間", shop.openingHours ?: shop.openingHoursRaw)
        line(sb, "定休日", shop.closedDays)
        line(sb, "予算（昼）", Budget.label(shop.budgetLunchMin, shop.budgetLunchMax))
        line(sb, "予算（夜）", Budget.label(shop.budgetDinnerMin, shop.budgetDinnerMax))
        line(sb, "お気に入り", if (shop.isFavorite) "はい" else "いいえ")
        if (!shop.memo.isNullOrBlank()) {
            sb.append("\n## メモ\n\n").append(shop.memo).append('\n')
        }
        return sb.toString()
    }

    private fun line(sb: StringBuilder, label: String, value: String?) {
        if (!value.isNullOrBlank()) sb.append("- ").append(label).append(": ").append(value).append('\n')
    }

    private fun writeBackup(context: Context, tree: DocumentFile, shops: List<Shop>) {
        tree.findFile("shops.json")?.delete()
        val file = tree.createFile("application/json", "shops.json") ?: return
        val array = JSONArray()
        for (shop in shops) array.put(toJson(shop))
        context.contentResolver.openOutputStream(file.uri)?.use { out ->
            out.write(array.toString(2).toByteArray(Charsets.UTF_8))
        }
    }

    fun toJson(shop: Shop): JSONObject = JSONObject().apply {
        put("id", shop.id)
        put("name", shop.name)
        put("name_kana", shop.nameKana ?: JSONObject.NULL)
        put("category", shop.category)
        put("lat", shop.lat)
        put("lon", shop.lon)
        put("address", shop.address ?: JSONObject.NULL)
        put("phone", shop.phone ?: JSONObject.NULL)
        put("osm_id", shop.osmId ?: JSONObject.NULL)
        put("opening_hours", shop.openingHours ?: JSONObject.NULL)
        put("opening_hours_raw", shop.openingHoursRaw ?: JSONObject.NULL)
        put("closed_days", shop.closedDays ?: JSONObject.NULL)
        put("budget_lunch_min", shop.budgetLunchMin ?: JSONObject.NULL)
        put("budget_lunch_max", shop.budgetLunchMax ?: JSONObject.NULL)
        put("budget_dinner_min", shop.budgetDinnerMin ?: JSONObject.NULL)
        put("budget_dinner_max", shop.budgetDinnerMax ?: JSONObject.NULL)
        put("memo", shop.memo ?: JSONObject.NULL)
        put("is_favorite", shop.isFavorite)
        put("status", shop.status)
        put("created_at", shop.createdAt)
        put("updated_at", shop.updatedAt)
    }

    fun fromJson(text: String): List<Shop> {
        val array = JSONArray(text)
        val list = mutableListOf<Shop>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            list.add(
                Shop(
                    id = 0,
                    name = o.optString("name", "名称未設定"),
                    nameKana = o.optStringOrNull("name_kana"),
                    category = o.optString("category", "その他"),
                    lat = o.optDouble("lat", 0.0),
                    lon = o.optDouble("lon", 0.0),
                    address = o.optStringOrNull("address"),
                    phone = o.optStringOrNull("phone"),
                    osmId = o.optStringOrNull("osm_id"),
                    openingHours = o.optStringOrNull("opening_hours"),
                    openingHoursRaw = o.optStringOrNull("opening_hours_raw"),
                    closedDays = o.optStringOrNull("closed_days"),
                    budgetLunchMin = o.optIntOrNull("budget_lunch_min"),
                    budgetLunchMax = o.optIntOrNull("budget_lunch_max"),
                    budgetDinnerMin = o.optIntOrNull("budget_dinner_min"),
                    budgetDinnerMax = o.optIntOrNull("budget_dinner_max"),
                    memo = o.optStringOrNull("memo"),
                    isFavorite = o.optBoolean("is_favorite", false),
                    status = o.optString("status", "active"),
                    createdAt = o.optLong("created_at", 0L),
                    updatedAt = o.optLong("updated_at", 0L)
                )
            )
        }
        return list
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (isNull(key)) null else optInt(key).takeIf { it > 0 }

    private fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\s]"), "_").take(24)
}
