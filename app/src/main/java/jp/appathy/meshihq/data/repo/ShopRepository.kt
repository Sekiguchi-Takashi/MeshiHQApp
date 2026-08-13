package jp.appathy.meshihq.data.repo

import jp.appathy.meshihq.data.db.FactSource
import jp.appathy.meshihq.data.db.MeshiDao
import jp.appathy.meshihq.data.db.PendingChange
import jp.appathy.meshihq.data.db.Shop
import jp.appathy.meshihq.data.remote.OsmCandidate
import jp.appathy.meshihq.data.remote.OsmCategory
import jp.appathy.meshihq.domain.Geo
import jp.appathy.meshihq.domain.OpeningHours
import jp.appathy.meshihq.domain.SourceType
import kotlinx.coroutines.flow.Flow

data class ImportResult(
    val added: Int = 0,
    val updated: Int = 0,
    val pending: Int = 0,
    val untouched: Int = 0
)

class ShopRepository(private val dao: MeshiDao) {

    fun observeShops(): Flow<List<Shop>> = dao.observeShops()

    fun searchShops(keyword: String): Flow<List<Shop>> =
        if (keyword.isBlank()) dao.observeShops() else dao.searchShops(keyword)

    fun observeShop(id: Long): Flow<Shop?> = dao.observeShop(id)

    fun observeFacts(shopId: Long): Flow<List<FactSource>> = dao.observeFacts(shopId)

    fun observePendingChanges(): Flow<List<PendingChange>> = dao.observePendingChanges()

    fun observePendingCount(): Flow<Int> = dao.observePendingCount()

    suspend fun getShop(id: Long): Shop? = dao.getShop(id)

    suspend fun getAllShops(): List<Shop> = dao.getAllShops()

    suspend fun delete(id: Long) = dao.deleteShopById(id)

    suspend fun saveManual(shop: Shop): Long {
        val now = System.currentTimeMillis()
        val target = shop.copy(
            createdAt = if (shop.createdAt == 0L) now else shop.createdAt,
            updatedAt = now
        )
        val newId = dao.upsertShop(target)
        val shopId = if (target.id == 0L) newId else target.id
        val facts = fieldsOf(target).mapNotNull { (field, value) ->
            if (value.isNullOrBlank()) null else fact(shopId, field, value, SourceType.SELF_VISIT, now)
        }
        dao.insertFacts(facts)
        return shopId
    }

    suspend fun toggleFavorite(shop: Shop) {
        dao.upsertShop(shop.copy(isFavorite = !shop.isFavorite, updatedAt = System.currentTimeMillis()))
    }

    /**
     * OSM取込。新規は追加し、既存店は値ごとに信頼度を比較して自動更新か承認待ちに振り分ける。
     */
    suspend fun importFromOsm(candidates: List<OsmCandidate>): ImportResult {
        var added = 0
        var updated = 0
        var pending = 0
        var untouched = 0
        val now = System.currentTimeMillis()
        val confidence = SourceType.confidenceOf(SourceType.OSM)

        for (candidate in candidates) {
            val existing = dao.getShopByOsmId(candidate.osmId) ?: findSameShop(candidate)
            val incoming = incomingFields(candidate)

            if (existing == null) {
                val parsed = OpeningHours.parse(candidate.openingHoursRaw)
                val shop = Shop(
                    name = candidate.name,
                    category = OsmCategory.of(candidate),
                    lat = candidate.lat,
                    lon = candidate.lon,
                    address = candidate.address,
                    phone = candidate.phone,
                    osmId = candidate.osmId,
                    openingHours = parsed,
                    openingHoursRaw = candidate.openingHoursRaw,
                    status = "unknown",
                    createdAt = now,
                    updatedAt = now
                )
                val shopId = dao.upsertShop(shop)
                dao.insertFacts(
                    incoming.mapNotNull { (field, value) ->
                        if (value.isNullOrBlank()) null
                        else fact(shopId, field, value, SourceType.OSM, now)
                    }
                )
                added++
                continue
            }

            var working = existing
            var changed = false
            val queued = mutableListOf<PendingChange>()
            val newFacts = mutableListOf<FactSource>()

            if (working.osmId == null) {
                working = working.copy(osmId = candidate.osmId)
                changed = true
            }

            for ((field, value) in incoming) {
                if (value.isNullOrBlank()) continue
                val current = valueOf(working, field)
                if (current == value) continue
                val currentConfidence = dao.bestFact(working.id, field)?.confidence
                    ?: if (current.isNullOrBlank()) 0.0 else 0.5
                if (current.isNullOrBlank() || confidence >= currentConfidence) {
                    working = applyField(working, field, value)
                    newFacts.add(fact(working.id, field, value, SourceType.OSM, now))
                    changed = true
                } else {
                    queued.add(
                        PendingChange(
                            shopId = working.id,
                            fieldName = field,
                            currentValue = current,
                            proposedValue = value,
                            sourceType = SourceType.OSM,
                            confidence = confidence,
                            reason = "OSMの値が既存値と異なります（既存の信頼度 $currentConfidence）",
                            createdAt = now
                        )
                    )
                }
            }

            if (changed) {
                if (candidate.openingHoursRaw != null && working.openingHours == null) {
                    working = working.copy(openingHoursRaw = candidate.openingHoursRaw)
                }
                dao.upsertShop(working.copy(updatedAt = now))
                dao.insertFacts(newFacts)
                updated++
            }
            if (queued.isNotEmpty()) {
                dao.insertPending(queued)
                pending += queued.size
            }
            if (!changed && queued.isEmpty()) untouched++
        }
        return ImportResult(added, updated, pending, untouched)
    }

    suspend fun approve(pendingId: Long) {
        val change = dao.getPending(pendingId) ?: return
        val shop = dao.getShop(change.shopId) ?: return
        val now = System.currentTimeMillis()
        val applied = applyField(shop, change.fieldName, change.proposedValue)
        dao.upsertShop(applied.copy(updatedAt = now))
        dao.insertFacts(
            listOf(
                FactSource(
                    shopId = shop.id,
                    fieldName = change.fieldName,
                    value = change.proposedValue,
                    sourceType = change.sourceType,
                    confidence = change.confidence,
                    observedAt = now,
                    createdAt = now
                )
            )
        )
        dao.setPendingState(pendingId, "approved")
    }

    suspend fun reject(pendingId: Long) = dao.setPendingState(pendingId, "rejected")

    private suspend fun findSameShop(candidate: OsmCandidate): Shop? {
        val delta = 0.0009
        val nearby = dao.getShopsInBounds(
            candidate.lat - delta,
            candidate.lat + delta,
            candidate.lon - delta,
            candidate.lon + delta
        )
        return nearby.firstOrNull { shop ->
            shop.name == candidate.name &&
                Geo.distanceMeters(shop.lat, shop.lon, candidate.lat, candidate.lon) <= 80
        }
    }

    private fun incomingFields(candidate: OsmCandidate): List<Pair<String, String?>> = listOf(
        "name" to candidate.name,
        "category" to OsmCategory.of(candidate),
        "address" to candidate.address,
        "phone" to candidate.phone,
        "opening_hours" to OpeningHours.parse(candidate.openingHoursRaw)
    )

    private fun valueOf(shop: Shop, field: String): String? = when (field) {
        "name" -> shop.name
        "category" -> shop.category
        "address" -> shop.address
        "phone" -> shop.phone
        "opening_hours" -> shop.openingHours
        "closed_days" -> shop.closedDays
        "status" -> shop.status
        "memo" -> shop.memo
        else -> null
    }

    private fun applyField(shop: Shop, field: String, value: String?): Shop = when (field) {
        "name" -> shop.copy(name = value ?: shop.name)
        "category" -> shop.copy(category = value ?: shop.category)
        "address" -> shop.copy(address = value)
        "phone" -> shop.copy(phone = value)
        "opening_hours" -> shop.copy(openingHours = value)
        "closed_days" -> shop.copy(closedDays = value)
        "status" -> shop.copy(status = value ?: shop.status)
        "memo" -> shop.copy(memo = value)
        else -> shop
    }

    private fun fact(
        shopId: Long,
        field: String,
        value: String,
        sourceType: String,
        now: Long
    ) = FactSource(
        shopId = shopId,
        fieldName = field,
        value = value,
        sourceType = sourceType,
        confidence = SourceType.confidenceOf(sourceType),
        observedAt = now,
        createdAt = now
    )

    private fun fieldsOf(shop: Shop): List<Pair<String, String?>> = listOf(
        "name" to shop.name,
        "category" to shop.category,
        "address" to shop.address,
        "phone" to shop.phone,
        "opening_hours" to shop.openingHours,
        "closed_days" to shop.closedDays,
        "budget_lunch" to budgetText(shop.budgetLunchMin, shop.budgetLunchMax),
        "budget_dinner" to budgetText(shop.budgetDinnerMin, shop.budgetDinnerMax),
        "status" to shop.status,
        "memo" to shop.memo
    )

    private fun budgetText(min: Int?, max: Int?): String? =
        if (min == null && max == null) null else "${min ?: max}-${max ?: min}"
}
