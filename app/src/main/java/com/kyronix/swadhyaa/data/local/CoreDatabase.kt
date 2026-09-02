package com.kyronix.swadhyaa.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kyronix.swadhyaa.data.local.dao.VedaDao
import com.kyronix.swadhyaa.data.local.entity.BhashyaPresenceEntity
import com.kyronix.swadhyaa.data.local.entity.MantraEntity
import com.kyronix.swadhyaa.data.local.entity.ScholarEntity
import com.kyronix.swadhyaa.data.local.entity.ScholarFieldEntity
import com.kyronix.swadhyaa.data.local.entity.VedaEntity

/**
 * Read-only Room database backed by the pre-populated core.db asset.
 *
 * CRITICAL RULES:
 * 1. Never call fallbackToDestructiveMigration().
 * 2. Never change entity schemas that would require a migration of the asset.
 * 3. createFromAsset copies the DB on first open; subsequent opens reuse it.
 * 4. FTS5 virtual table (search_index) is present in the asset; Room does not
 *    need an entity for it — we query it via raw @Query in VedaDao.
 */
@Database(
    entities = [
        VedaEntity::class,
        MantraEntity::class,
        ScholarEntity::class,
        ScholarFieldEntity::class,
        BhashyaPresenceEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class CoreDatabase : RoomDatabase() {

    abstract fun vedaDao(): VedaDao

    companion object {
        private const val DB_NAME = "core"
        private const val ASSET_PATH = "databases/core.db"

        @Volatile
        private var INSTANCE: CoreDatabase? = null

        fun getInstance(context: Context): CoreDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context).also { INSTANCE = it }
            }
        }

        private fun build(context: Context): CoreDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                CoreDatabase::class.java,
                DB_NAME
            )
                .createFromAsset(ASSET_PATH)
                // Read-only after copy; no migrations allowed on asset DBs.
                .build()
        }
    }
}
