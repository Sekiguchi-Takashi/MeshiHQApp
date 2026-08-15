package jp.appathy.meshihq.data.repo

import jp.appathy.meshihq.data.db.FactSource
import jp.appathy.meshihq.data.db.Collection
import jp.appathy.meshihq.data.db.CollectionCount
import jp.appathy.meshihq.data.db.CollectionShop
import jp.appathy.meshihq.data.db.MenuItem
import jp.appathy.meshihq.data.db.MeshiDao
import jp.appathy.meshihq.data.db.Photo
import jp.appathy.meshihq.data.db.PendingChange
import jp.appathy.meshihq.data.db.Shop
import jp.appathy.meshihq.data.db.Visit
import jp.appathy.meshihq.data.remote.ImportCandidate
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

    fun observePhotos(shopId: Long): Flow<List<Photo>> = dao.observePhotos(shopId)

    fun observeMenu(shopId: Long): Flow<List<MenuItem>> = dao.observeMenu(shopId)

    suspend fun getMenu(shopId: Long): List<MenuItem> = dao.getMenu(shopId)

    suspend fun addPhoto(
        shopId: Long,
        path: String,
        kind: String,
        takenAt: Long? = null,
        caption: String? = null
    ) {
        dao.insertPhoto(
            Photo(
                shopId = shopId,
                path = path,
                kind = kind,
                caption = caption,
                takenAt = takenAt,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    /** 実ファイルの削除はUI側で行うため、消したパスを返す。 */
    suspend fun removePhoto(id: Long): String? {
        val photo = dao.getPhoto(id)
        dao.deletePhoto(id)
        return photo?.path
    }

    suspend fun addMenuItems(
        shopId: Long,
        entries: List<Pair<String, Int?>>,
        sourceType: String,
        section: String? = null
    ) {
        if (entries.isEmpty()) return
        val now = System.currentTimeMillis()
        val existing = dao.getMenu(shopId)
        val fresh = entries.filter { (name, price) ->
            existing.none { it.name.trim() == name.trim() && (price == null || it.price == price) }
        }
        if (fresh.isEmpty()) return
        dao.insertMenuItems(
            fresh.map { (name, price) ->
                MenuItem(
                    shopId = shopId,
                    name = name,
                    price = price,
                    section = section,
                    sourceType = sourceType,
                    confidence = SourceType.confidenceOf(sourceType),
                    createdAt = now,
                    updatedAt = now
                )
            }
        )
    }

    suspend fun deleteMenuItem(id: Long) = dao.deleteMenuItem(id)

    /**
     * 同じ品名が複数あるとき、価格の入っているものを1件だけ残す。
     * 同じメニュー表を撮り直したときに増えるのを畳む。
     */
    suspend fun mergeDuplicateMenu(shopId: Long): Int {
        val items = dao.getMenu(shopId)
        var removed = 0
        items.groupBy { it.name.trim() }.forEach { (_, group) ->
            if (group.size <= 1) return@forEach
            val keep = group.firstOrNull { it.price != null } ?: group.first()
            group.filter { it.id != keep.id }.forEach { duplicate ->
                dao.deleteMenuItem(duplicate.id)
                removed++
            }
        }
        return removed
    }

    fun observeVisits(shopId: Long): Flow<List<Visit>> = dao.observeVisits(shopId)

    fun observeAllVisits(): Flow<List<Visit>> = dao.observeAllVisits()

    suspend fun addVisit(
        shopId: Long,
        visitedAt: Long,
        people: Int,
        amount: Int?,
        rating: Int?,
        memo: String?
    ) {
        val now = System.currentTimeMillis()
        dao.insertVisit(
            Visit(
                shopId = shopId,
                visitedAt = visitedAt,
                people = people,
                amount = amount,
                rating = rating,
                memo = memo,
                createdAt = now
            )
        )
        val shop = dao.getShop(shopId)
        if (shop != null && shop.status != "active") {
            dao.upsertShop(shop.copy(status = "active", updatedAt = now))
        }
    }

    suspend fun deleteVisit(id: Long) = dao.deleteVisit(id)

    fun observeCollections(): Flow<List<Collection>> = dao.observeCollections()

    fun observeCollectionCounts(): Flow<List<CollectionCount>> = dao.observeCollectionCounts()

    fun observeCollectionIds(shopId: Long): Flow<List<Long>> = dao.observeCollectionIds(shopId)

    fun observeCollectionLinks(): Flow<List<CollectionShop>> = dao.observeCollectionLinks()

    suspend fun createCollection(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        dao.insertCollection(Collection(name = trimmed, createdAt = System.currentTimeMillis()))
    }

    suspend fun deleteCollection(id: Long) = dao.deleteCollection(id)

    suspend fun setCollectionMembership(collectionId: Long, shopId: Long, member: Boolean) {
        if (member) dao.linkCollection(CollectionShop(collectionId, shopId))
        else dao.unlinkCollection(collectionId, shopId)
    }

    suspend fun shopsInCollection(collectionId: Long): List<Shop> = dao.shopsInCollection(collectionId)

    suspend fun toggleFavorite(shop: Shop) {
        dao.upsertShop(shop.copy(isFavorite = !shop.isFavorite, updatedAt = System.currentTimeMillis()))
    }

    /**
     * 取込。新規は追加し、既存店は値ごとに信頼度を比較して自動更新か承認待ちに振り分ける。
     * 取込元（OSM／ホットペッパー）によらず同じ判定を通す。
     */
    suspend fun importCandidates(candidates: List<ImportCandidate>): ImportResult {
        var added = 0
        var updated = 0
        var pending = 0
        var untouched = 0
        val now = System.currentTimeMillis()

        for (candidate in candidates) {
            val confidence = SourceType.confidenceOf(candidate.sourceType)
            val existing = dao.getShopByOsmId(candidate.externalId) ?: findSameShop(candidate)
            val incoming = incomingFields(candidate)

            if (existing == null) {
                val shop = Shop(
                    name = candidate.name,
                    category = candidate.category,
                    lat = candidate.lat,
                    lon = candidate.lon,
                    address = candidate.address,
                    phone = candidate.phone,
                    osmId = candidate.externalId,
                    website = candidate.website,
                    openingHours = OpeningHours.parse(candidate.openingHoursRaw),
                    openingHoursRaw = candidate.openingHoursRaw,
                    budgetDinnerMin = candidate.budgetPerPerson,
                    status = "unknown",
                    createdAt = now,
                    updatedAt = now
                )
                val shopId = dao.upsertShop(shop)
                dao.insertFacts(
                    incoming.mapNotNull { (field, value) ->
                        if (value.isNullOrBlank()) null
                        else fact(shopId, field, value, candidate.sourceType, now)
                    }
                )
                added++
                continue
            }

            var working: Shop = existing
            var changed = false
            if (working.osmId == null) {
                working = working.copy(osmId = candidate.externalId)
                changed = true
            }
            if (working.website == null && candidate.website != null) {
                working = working.copy(website = candidate.website)
                changed = true
            }
            if (working.openingHoursRaw == null && candidate.openingHoursRaw != null) {
                working = working.copy(openingHoursRaw = candidate.openingHoursRaw)
                changed = true
            }

            val outcome = applyValues(working, incoming, candidate.sourceType, confidence, now)
            working = outcome.first
            if (outcome.second.isNotEmpty() || changed) {
                dao.upsertShop(working.copy(updatedAt = now))
                dao.insertFacts(outcome.second)
                updated++
            }
            if (outcome.third.isNotEmpty()) {
                dao.insertPending(outcome.third)
                pending += outcome.third.size
            }
            if (outcome.second.isEmpty() && outcome.third.isEmpty() && !changed) untouched++
        }
        return ImportResult(added, updated, pending, untouched)
    }

    /** 公式サイトなど、1店舗ぶんの値を取込と同じ判定で反映する。 */
    suspend fun applyExternalValues(
        shopId: Long,
        values: List<Pair<String, String?>>,
        sourceType: String
    ): ImportResult {
        val shop = dao.getShop(shopId) ?: return ImportResult()
        val now = System.currentTimeMillis()
        val confidence = SourceType.confidenceOf(sourceType)
        val outcome = applyValues(shop, values, sourceType, confidence, now)
        if (outcome.second.isNotEmpty()) {
            dao.upsertShop(outcome.first.copy(updatedAt = now))
            dao.insertFacts(outcome.second)
        }
        if (outcome.third.isNotEmpty()) dao.insertPending(outcome.third)
        return ImportResult(
            updated = if (outcome.second.isEmpty()) 0 else 1,
            pending = outcome.third.size
        )
    }

    suspend fun moveShop(shopId: Long, lat: Double, lon: Double) {
        val shop = dao.getShop(shopId) ?: return
        dao.upsertShop(shop.copy(lat = lat, lon = lon, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setWebsite(shopId: Long, url: String?) {
        val shop = dao.getShop(shopId) ?: return
        dao.upsertShop(
            shop.copy(
                website = url?.trim()?.takeIf { it.isNotBlank() },
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun applyValues(
        shop: Shop,
        values: List<Pair<String, String?>>,
        sourceType: String,
        confidence: Double,
        now: Long
    ): Triple<Shop, List<FactSource>, List<PendingChange>> {
        var working = shop
        val facts = mutableListOf<FactSource>()
        val queued = mutableListOf<PendingChange>()
        for ((field, value) in values) {
            if (value.isNullOrBlank()) continue
            val current = valueOf(working, field)
            if (current == value) continue
            val currentConfidence = dao.bestFact(working.id, field)?.confidence
                ?: if (current.isNullOrBlank()) 0.0 else 0.5
            if (current.isNullOrBlank() || confidence >= currentConfidence) {
                working = applyField(working, field, value)
                facts.add(fact(working.id, field, value, sourceType, now))
            } else {
                queued.add(
                    PendingChange(
                        shopId = working.id,
                        fieldName = field,
                        currentValue = current,
                        proposedValue = value,
                        sourceType = sourceType,
                        confidence = confidence,
                        reason = "既存値の信頼度 $currentConfidence を下回るため保留",
                        createdAt = now
                    )
                )
            }
        }
        return Triple(working, facts, queued)
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

    private suspend fun findSameShop(candidate: ImportCandidate): Shop? {
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

    private fun incomingFields(candidate: ImportCandidate): List<Pair<String, String?>> = listOf(
        "name" to candidate.name,
        "category" to candidate.category,
        "address" to candidate.address,
        "phone" to candidate.phone,
        "opening_hours" to OpeningHours.parse(candidate.openingHoursRaw)
    )

    private fun valueOf(shop: Shop, field: String): String? = when (field) {
        "website" -> shop.website
        "opening_hours_raw" -> shop.openingHoursRaw
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
        "website" -> shop.copy(website = value)
        "opening_hours_raw" -> shop.copy(
            openingHoursRaw = value,
            openingHours = OpeningHours.parse(value) ?: shop.openingHours
        )
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
