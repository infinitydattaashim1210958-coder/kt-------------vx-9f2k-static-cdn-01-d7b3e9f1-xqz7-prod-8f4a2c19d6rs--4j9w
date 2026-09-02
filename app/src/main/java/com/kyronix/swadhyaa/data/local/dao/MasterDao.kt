package com.kyronix.swadhyaa.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.kyronix.swadhyaa.data.local.entity.InstalledPackageEntity
import com.kyronix.swadhyaa.data.local.entity.RamayanaBhashyaContentEntity
import com.kyronix.swadhyaa.data.local.entity.VedaBhashyaContentEntity

@Dao
interface MasterDao {

    // ── Installed packages ──────────────────────────────────────────

    @Query("SELECT * FROM installed_packages WHERE package_id = :packageId LIMIT 1")
    suspend fun getInstalledPackage(packageId: String): InstalledPackageEntity?

    @Query("SELECT * FROM installed_packages WHERE category = :category")
    suspend fun getInstalledByCategory(category: String): List<InstalledPackageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInstalledPackage(pkg: InstalledPackageEntity)

    @Query("DELETE FROM installed_packages WHERE package_id = :packageId")
    suspend fun deleteInstalledPackage(packageId: String)

    // ── Veda bhāṣya ─────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVedaBhashya(items: List<VedaBhashyaContentEntity>)

    @Query(
        """
        SELECT * FROM veda_bhashya_contents
        WHERE scholar_id = :scholarId AND mantra_id = :mantraId
        """
    )
    suspend fun getVedaBhashya(scholarId: Int, mantraId: Int): List<VedaBhashyaContentEntity>

    @Query("DELETE FROM veda_bhashya_contents WHERE scholar_id = :scholarId")
    suspend fun deleteVedaBhashyaForScholar(scholarId: Int)

    // ── Ramayana bhāṣya ─────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRamayanaBhashya(items: List<RamayanaBhashyaContentEntity>)

    @Query(
        """
        SELECT * FROM ramayana_kanda_bhashya_contents
        WHERE scholar_id = :scholarId AND shloka_id = :shlokaId
        """
    )
    suspend fun getRamayanaBhashya(scholarId: Int, shlokaId: Int): List<RamayanaBhashyaContentEntity>

    @Query("DELETE FROM ramayana_kanda_bhashya_contents WHERE scholar_id = :scholarId")
    suspend fun deleteRamayanaBhashyaForScholar(scholarId: Int)

    /**
     * Atomic install of a pack: insert content + mark installed.
     * Caller must run inside withTransaction.
     */
    @Transaction
    suspend fun installVedaPack(
        pkg: InstalledPackageEntity,
        contents: List<VedaBhashyaContentEntity>
    ) {
        insertVedaBhashya(contents)
        upsertInstalledPackage(pkg)
    }

    @Transaction
    suspend fun installRamayanaPack(
        pkg: InstalledPackageEntity,
        contents: List<RamayanaBhashyaContentEntity>
    ) {
        insertRamayanaBhashya(contents)
        upsertInstalledPackage(pkg)
    }
}
