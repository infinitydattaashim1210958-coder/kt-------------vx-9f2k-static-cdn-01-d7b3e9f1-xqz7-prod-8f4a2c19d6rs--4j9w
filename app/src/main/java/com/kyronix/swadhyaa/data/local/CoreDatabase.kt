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
 * Read-only Room database backed by the APK-bundled core.db asset.
 *
 * Matches [RamayanaCoreDatabase]'s pattern (and legacy's build.yml, which
 * bundles core.db/ramayana_core.db inside the APK) so that Veda content
 * works fully offline immediately after install — no network required on
 * first launch. This previously loaded via DatabaseAssetManager's runtime
 * GitHub-Release download instead, which silently dropped that offline
 * guarantee and crashed with no connectivity on first launch. Fixed to
 * restore parity — see RISK_REGISTER.md R9.
 *
 * The asset itself (app/src/main/assets/databases/core.db) is produced by
 * CI (android.yml: gh release download v1 → gunzip) exactly as it always
 * was for the JVM DatabaseVerificationTest — that path is now the one the
 * real app uses too, not a disconnected copy only the test ever read.
 *
 * CRITICAL RULES (unchanged):
 * 1. Never call fallbackToDestructiveMigration().
 * 2. Never change entity schemas that would require a migration of the asset.
 * 3. FTS5 virtual table (search_index) is present in the DB; Room does not
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
                // Read-only after install; no migrations allowed on release DBs.
                .build()
        }

        /** Call after clearing app data so the next getInstance() rebuilds. */
        fun clearInstance() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
