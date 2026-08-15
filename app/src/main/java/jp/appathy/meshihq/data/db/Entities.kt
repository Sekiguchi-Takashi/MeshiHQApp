package jp.appathy.meshihq.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shop",
    indices = [
        Index(value = ["name"]),
        Index(value = ["osm_id"], unique = true),
        Index(value = ["lat", "lon"])
    ]
)
data class Shop(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "name_kana") val nameKana: String? = null,
    val category: String = "その他",
    val lat: Double,
    val lon: Double,
    val address: String? = null,
    val phone: String? = null,
    @ColumnInfo(name = "osm_id") val osmId: String? = null,
    val website: String? = null,
    @ColumnInfo(name = "opening_hours") val openingHours: String? = null,
    @ColumnInfo(name = "opening_hours_raw") val openingHoursRaw: String? = null,
    @ColumnInfo(name = "closed_days") val closedDays: String? = null,
    @ColumnInfo(name = "budget_lunch_min") val budgetLunchMin: Int? = null,
    @ColumnInfo(name = "budget_lunch_max") val budgetLunchMax: Int? = null,
    @ColumnInfo(name = "budget_dinner_min") val budgetDinnerMin: Int? = null,
    @ColumnInfo(name = "budget_dinner_max") val budgetDinnerMax: Int? = null,
    val memo: String? = null,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    val status: String = "active",
    @ColumnInfo(name = "created_at") val createdAt: Long = 0,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0
)

@Entity(
    tableName = "fact_source",
    foreignKeys = [
        ForeignKey(
            entity = Shop::class,
            parentColumns = ["id"],
            childColumns = ["shop_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["shop_id"])]
)
data class FactSource(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "shop_id") val shopId: Long,
    @ColumnInfo(name = "field_name") val fieldName: String,
    val value: String?,
    @ColumnInfo(name = "source_type") val sourceType: String,
    val confidence: Double,
    @ColumnInfo(name = "observed_at") val observedAt: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(
    tableName = "pending_change",
    foreignKeys = [
        ForeignKey(
            entity = Shop::class,
            parentColumns = ["id"],
            childColumns = ["shop_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["shop_id"])]
)
data class PendingChange(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "shop_id") val shopId: Long,
    @ColumnInfo(name = "field_name") val fieldName: String,
    @ColumnInfo(name = "current_value") val currentValue: String?,
    @ColumnInfo(name = "proposed_value") val proposedValue: String?,
    @ColumnInfo(name = "source_type") val sourceType: String,
    val confidence: Double,
    val reason: String? = null,
    val state: String = "pending",
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(
    tableName = "photo",
    foreignKeys = [
        ForeignKey(
            entity = Shop::class,
            parentColumns = ["id"],
            childColumns = ["shop_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["shop_id"])]
)
data class Photo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "shop_id") val shopId: Long,
    val path: String,
    val kind: String = "shop",
    val caption: String? = null,
    @ColumnInfo(name = "taken_at") val takenAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(
    tableName = "menu_item",
    foreignKeys = [
        ForeignKey(
            entity = Shop::class,
            parentColumns = ["id"],
            childColumns = ["shop_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["shop_id"])]
)
data class MenuItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "shop_id") val shopId: Long,
    val name: String,
    val price: Int? = null,
    val section: String? = null,
    @ColumnInfo(name = "source_type") val sourceType: String,
    val confidence: Double,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "visit",
    foreignKeys = [
        ForeignKey(
            entity = Shop::class,
            parentColumns = ["id"],
            childColumns = ["shop_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["shop_id"])]
)
data class Visit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "shop_id") val shopId: Long,
    @ColumnInfo(name = "visited_at") val visitedAt: Long,
    val people: Int = 1,
    val amount: Int? = null,
    val rating: Int? = null,
    val memo: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(
    tableName = "collection",
    indices = [Index(value = ["name"], unique = true)]
)
data class Collection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(
    tableName = "collection_shop",
    primaryKeys = ["collection_id", "shop_id"],
    foreignKeys = [
        ForeignKey(
            entity = Collection::class,
            parentColumns = ["id"],
            childColumns = ["collection_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Shop::class,
            parentColumns = ["id"],
            childColumns = ["shop_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["shop_id"])]
)
data class CollectionShop(
    @ColumnInfo(name = "collection_id") val collectionId: Long,
    @ColumnInfo(name = "shop_id") val shopId: Long
)

data class CollectionCount(
    val collectionId: Long,
    val count: Int
)
