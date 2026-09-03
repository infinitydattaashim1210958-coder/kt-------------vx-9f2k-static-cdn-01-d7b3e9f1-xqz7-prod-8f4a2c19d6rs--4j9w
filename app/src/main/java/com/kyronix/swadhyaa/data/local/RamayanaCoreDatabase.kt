package com.kyronix.swadhyaa.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kyronix.swadhyaa.data.local.dao.RamayanaDao
import com.kyronix.swadhyaa.data.local.entity.KandaEntity
import com.kyronix.swadhyaa.data.local.entity.SargaEntity
import com.kyronix.swadhyaa.data.local.entity.ShlokaEntity

/**
 * Read-only Room database for ramayana_core.db asset.
 * Same rules as CoreDatabase: no destructive migrations, asset-backed.
 */
@Database(
    entities = [
        KandaEntity::class,
        SargaEntity::class,
        ShlokaEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class RamayanaCoreDatabase : RoomDatabase() {

    abstract fun ramayanaDao(): RamayanaDao

    companion object {
        private const val DB_NAME = "ramayana_core"
        
        @Volatile
        private var INSTANCE: RamayanaCoreDatabase? = null

        fun getInstance(context: Context): RamayanaCoreDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context).also { INSTANCE = it }
            }
        }

        private fun build(context: Context): RamayanaCoreDatabase {
    val dbFile: File = DatabaseAssetManager.ramayanaDbFile(context)
    require(dbFile.exists() && dbFile.length() > 500_000) {
        "ramayana_core.db is not ready. Call DatabaseAssetManager.ensureReady() first."
    }

    return Room.databaseBuilder(
        context.applicationContext,
        RamayanaCoreDatabase::class.java,
        DB_NAME
    )
        .createFromFile(dbFile)  // ← was createFromAsset(), which has no file to read
        .build()
     }
    }
}
