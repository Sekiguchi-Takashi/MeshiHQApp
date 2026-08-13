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
        MenuItem::class
    ],
    version = 2,
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

        @Volatile
        private var instance: MeshiDatabase? = null

        fun get(context: Context): MeshiDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MeshiDatabase::class.java,
                    "meshihq.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
