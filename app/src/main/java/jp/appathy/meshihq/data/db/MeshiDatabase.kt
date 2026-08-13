package jp.appathy.meshihq.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Shop::class, FactSource::class, PendingChange::class],
    version = 1,
    exportSchema = false
)
abstract class MeshiDatabase : RoomDatabase() {

    abstract fun dao(): MeshiDao

    companion object {
        @Volatile
        private var instance: MeshiDatabase? = null

        fun get(context: Context): MeshiDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MeshiDatabase::class.java,
                    "meshihq.db"
                ).build().also { instance = it }
            }
    }
}
