package jp.appathy.meshihq.data.repo

import jp.appathy.meshihq.data.db.FactSource
import jp.appathy.meshihq.data.db.MeshiDao
import jp.appathy.meshihq.data.db.Shop
import jp.appathy.meshihq.domain.SourceType
import kotlinx.coroutines.flow.Flow

class ShopRepository(private val dao: MeshiDao) {

    fun observeShops(): Flow<List<Shop>> = dao.observeShops()

    fun searchShops(keyword: String): Flow<List<Shop>> =
        if (keyword.isBlank()) dao.observeShops() else dao.searchShops(keyword)

    fun observeShop(id: Long): Flow<Shop?> = dao.observeShop(id)

    fun observeFacts(shopId: Long): Flow<List<FactSource>> = dao.observeFacts(shopId)

    suspend fun getShop(id: Long): Shop? = dao.getShop(id)

    suspend fun getAllShops(): List<Shop> = dao.getAllShops()

    suspend fun delete(id: Long) = dao.deleteShopById(id)

    /**
     * 手入力による保存。値そのものは shop に、出所と観測日時は fact_source に残す。
     */
    suspend fun saveManual(shop: Shop): Long {
        val now = System.currentTimeMillis()
        val target = shop.copy(
            createdAt = if (shop.createdAt == 0L) now else shop.createdAt,
            updatedAt = now
        )
        val newId = dao.upsertShop(target)
        val shopId = if (target.id == 0L) newId else target.id
        val facts = fieldsOf(target).mapNotNull { (field, value) ->
            if (value.isNullOrBlank()) null else FactSource(
                shopId = shopId,
                fieldName = field,
                value = value,
                sourceType = SourceType.SELF_VISIT,
                confidence = SourceType.confidenceOf(SourceType.SELF_VISIT),
                observedAt = now,
                createdAt = now
            )
        }
        dao.insertFacts(facts)
        return shopId
    }

    suspend fun toggleFavorite(shop: Shop) {
        dao.upsertShop(shop.copy(isFavorite = !shop.isFavorite, updatedAt = System.currentTimeMillis()))
    }

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
