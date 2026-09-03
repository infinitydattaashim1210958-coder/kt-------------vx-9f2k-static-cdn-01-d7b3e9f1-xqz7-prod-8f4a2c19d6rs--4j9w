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
import java.io.File

/**
 * Read-only Room database backed by the downloaded core.db
 * (installed by [DatabaseAssetManager] from the GitHub Release).
 *
 * CRITICAL RULES:
 * 1. Never call fallbackToDestructiveMigration().
 * 2. Never change entity schemas that would require a migration of the asset.
 * 3. DatabaseAssetManager must have successfully installed the file before
 *    the first call to getInstance().
 * 4. FTS5 virtual table (search_index) is present in the DB; Room does not
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

        @Volatile
        private var INSTANCE: CoreDatabase? = null

        fun getInstance(context: Context): CoreDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context).also { INSTANCE = it }
            }
        }

        private fun build(context: Context): CoreDatabase {
            val dbFile: File = DatabaseAssetManager.coreDbFile(context)
            require(dbFile.exists() && dbFile.length() > 1_000_000) {
                "core.db is not ready. Call DatabaseAssetManager.ensureReady() first."
            }

            return Room.databaseBuilder(
                context.applicationContext,
                CoreDatabase::class.java,
                DB_NAME
            )
                .createFromFile(dbFile)
                // Read-only after install; no migrations allowed on release DBs.
                .build()
        }

        /** Call after clearCache() so the next getInstance() rebuilds. */
        fun clearInstance() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
