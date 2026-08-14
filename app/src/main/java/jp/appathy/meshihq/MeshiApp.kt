package jp.appathy.meshihq

import android.app.Application
import android.content.Context
import jp.appathy.meshihq.data.db.MeshiDatabase
import jp.appathy.meshihq.data.repo.ShopRepository
import org.osmdroid.config.Configuration

class MeshiApp : Application() {

    lateinit var repository: ShopRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = ShopRepository(MeshiDatabase.get(this).dao())
        val prefs = getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        Configuration.getInstance().load(this, prefs)
        Configuration.getInstance().userAgentValue = packageName
        Configuration.getInstance().osmdroidBasePath = java.io.File(cacheDir, "osmdroid")
        Configuration.getInstance().osmdroidTileCache = java.io.File(cacheDir, "osmdroid/tiles")
    }
}

object Prefs {
    private const val NAME = "meshihq"

    fun radiusMeters(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt("radius", 1500)

    fun setRadiusMeters(context: Context, value: Int) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putInt("radius", value).apply()
    }

    fun defaultLat(context: Context): Double =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getFloat("def_lat", 35.6812f).toDouble()

    fun defaultLon(context: Context): Double =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getFloat("def_lon", 139.7671f).toDouble()

    fun lastImportAt(context: Context): Long =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getLong("last_import", 0L)

    fun setLastImportAt(context: Context, value: Long) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putLong("last_import", value).apply()
    }

    fun setDefaultCenter(context: Context, lat: Double, lon: Double) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putFloat("def_lat", lat.toFloat())
            .putFloat("def_lon", lon.toFloat())
            .apply()
    }
}
