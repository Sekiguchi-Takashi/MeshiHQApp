package jp.appathy.meshihq.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Shop::class,
        FactSource::class,
        PendingChange::class,
        Photo::class,
        MenuItem::class,
        Visit::class,
        Collection::class,
        CollectionShop::class
    ],
    version = 3,
    exportSchema = false
)
abstract class MeshiDatabase : RoomDatabase() {

    abstract fun dao(): MeshiDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `photo` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`shop_id` INTEGER NOT NULL, " +
                        "`path` TEXT NOT NULL, " +
                        "`kind` TEXT NOT NULL, " +
                        "`caption` TEXT, " +
                        "`created_at` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`shop_id`) REFERENCES `shop`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_photo_shop_id` ON `photo` (`shop_id`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `menu_item` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`shop_id` INTEGER NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`price` INTEGER, " +
                        "`section` TEXT, " +
                        "`source_type` TEXT NOT NULL, " +
                        "`confidence` REAL NOT NULL, " +
                        "`created_at` INTEGER NOT NULL, " +
                        "`updated_at` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`shop_id`) REFERENCES `shop`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_menu_item_shop_id` ON `menu_item` (`shop_id`)"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `visit` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`shop_id` INTEGER NOT NULL, " +
                        "`visited_at` INTEGER NOT NULL, " +
                        "`people` INTEGER NOT NULL, " +
                        "`amount` INTEGER, " +
                        "`rating` INTEGER, " +
                        "`memo` TEXT, " +
                        "`created_at` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`shop_id`) REFERENCES `shop`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_visit_shop_id` ON `visit` (`shop_id`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `collection` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`created_at` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_collection_name` ON `collection` (`name`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `collection_shop` (" +
                        "`collection_id` INTEGER NOT NULL, " +
                        "`shop_id` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`collection_id`, `shop_id`), " +
                        "FOREIGN KEY(`collection_id`) REFERENCES `collection`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(`shop_id`) REFERENCES `shop`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_collection_shop_shop_id` " +
                        "ON `collection_shop` (`shop_id`)"
                )
            }
        }

        @Volatile
        private var instance: MeshiDatabase? = null

        fun get(context: Context): MeshiDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MeshiDatabase::class.java,
                    "meshihq.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}
