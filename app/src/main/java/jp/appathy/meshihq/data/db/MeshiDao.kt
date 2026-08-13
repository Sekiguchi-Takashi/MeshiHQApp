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
}
