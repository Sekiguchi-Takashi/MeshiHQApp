package jp.appathy.meshihq.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MeshiDao {

    @Query("SELECT * FROM shop ORDER BY updated_at DESC")
    fun observeShops(): Flow<List<Shop>>

    @Query(
        "SELECT * FROM shop WHERE name LIKE '%' || :keyword || '%' " +
            "OR name_kana LIKE '%' || :keyword || '%' " +
            "OR category LIKE '%' || :keyword || '%' ORDER BY updated_at DESC"
    )
    fun searchShops(keyword: String): Flow<List<Shop>>

    @Query("SELECT * FROM shop WHERE id = :id")
    fun observeShop(id: Long): Flow<Shop?>

    @Query("SELECT * FROM shop WHERE id = :id")
    suspend fun getShop(id: Long): Shop?

    @Query("SELECT * FROM shop")
    suspend fun getAllShops(): List<Shop>

    @Upsert
    suspend fun upsertShop(shop: Shop): Long

    @Query("DELETE FROM shop WHERE id = :id")
    suspend fun deleteShopById(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFacts(facts: List<FactSource>)

    @Query("SELECT * FROM fact_source WHERE shop_id = :shopId ORDER BY observed_at DESC")
    fun observeFacts(shopId: Long): Flow<List<FactSource>>

    @Query("SELECT * FROM pending_change WHERE state = 'pending' ORDER BY created_at DESC")
    fun observePendingChanges(): Flow<List<PendingChange>>

    @Query("SELECT * FROM shop WHERE osm_id = :osmId LIMIT 1")
    suspend fun getShopByOsmId(osmId: String): Shop?

    @Query(
        "SELECT * FROM shop WHERE lat BETWEEN :minLat AND :maxLat " +
            "AND lon BETWEEN :minLon AND :maxLon"
    )
    suspend fun getShopsInBounds(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double
    ): List<Shop>

    @Query(
        "SELECT * FROM fact_source WHERE shop_id = :shopId AND field_name = :field " +
            "ORDER BY confidence DESC, observed_at DESC LIMIT 1"
    )
    suspend fun bestFact(shopId: Long, field: String): FactSource?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPending(changes: List<PendingChange>)

    @Query("SELECT * FROM pending_change WHERE id = :id")
    suspend fun getPending(id: Long): PendingChange?

    @Query("UPDATE pending_change SET state = :state WHERE id = :id")
    suspend fun setPendingState(id: Long, state: String)

    @Query("SELECT COUNT(*) FROM pending_change WHERE state = 'pending'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM photo WHERE shop_id = :shopId ORDER BY created_at DESC")
    fun observePhotos(shopId: Long): Flow<List<Photo>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: Photo): Long

    @Query("SELECT * FROM photo WHERE id = :id")
    suspend fun getPhoto(id: Long): Photo?

    @Query("DELETE FROM photo WHERE id = :id")
    suspend fun deletePhoto(id: Long)

    @Query("SELECT * FROM menu_item WHERE shop_id = :shopId ORDER BY section, price, name")
    fun observeMenu(shopId: Long): Flow<List<MenuItem>>

    @Query("SELECT * FROM menu_item WHERE shop_id = :shopId ORDER BY section, price, name")
    suspend fun getMenu(shopId: Long): List<MenuItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMenuItems(items: List<MenuItem>)

    @Query("DELETE FROM menu_item WHERE id = :id")
    suspend fun deleteMenuItem(id: Long)
}
