package com.kyronix.swadhyaa.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kyronix.swadhyaa.data.local.dao.MasterDao
import com.kyronix.swadhyaa.data.local.entity.InstalledPackageEntity
import com.kyronix.swadhyaa.data.local.entity.MahabharataAdhyayaEntity
import com.kyronix.swadhyaa.data.local.entity.MahabharataUpakhyanaEntity
import com.kyronix.swadhyaa.data.local.entity.RamayanaBhashyaContentEntity
import com.kyronix.swadhyaa.data.local.entity.VedaBhashyaContentEntity

/**
 * Writable master database. Created empty on first launch.
 * All content arrives via pack merges (WorkManager).
 *
 * Migration policy: additive only. Never drop tables or columns.
 */
@Database(
    entities = [
        InstalledPackageEntity::class,
        VedaBhashyaContentEntity::class,
        RamayanaBhashyaContentEntity::class,
        MahabharataAdhyayaEntity::class,
        MahabharataUpakhyanaEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class MasterDatabase : RoomDatabase() {

    abstract fun masterDao(): MasterDao

    companion object {
        private const val DB_NAME = "swadhyay_master"

        @Volatile
        private var INSTANCE: MasterDatabase? = null

        fun getInstance(context: Context): MasterDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context).also { INSTANCE = it }
            }
        }

        private fun build(context: Context): MasterDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                MasterDatabase::class.java,
                DB_NAME
            )
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Match master-db.js PRAGMAs for crash safety + concurrent reads.
                        db.execSQL("PRAGMA journal_mode=WAL;")
                        db.execSQL("PRAGMA synchronous=NORMAL;")
                        db.execSQL("PRAGMA foreign_keys=ON;")
                    }
                })
                // Future migrations go here. Additive only.
                // .addMigrations(MIGRATION_1_2)
                .build()
        }

        // Example future migration template (do not enable until needed)
        /*
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE installed_packages ADD COLUMN extra TEXT")
            }
        }
        */
    }
}
