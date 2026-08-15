package jp.appathy.meshihq.data.remote

/**
 * 取込元によらない共通の候補。
 * externalId は重複取込の判定キーで、shop.osm_id 列にそのまま格納する
 * （OSMは "node/123"、ホットペッパーは "hp/J001234567" の形）。
 */
data class ImportCandidate(
    val externalId: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val category: String,
    val address: String? = null,
    val phone: String? = null,
    val openingHoursRaw: String? = null,
    val website: String? = null,
    val budgetPerPerson: Int? = null,
    val sourceType: String
)
