package com.kyronix.swadhyaa.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Writable master database entities.
 * Schema must stay additive-only. Matches master-db.js CREATE TABLE statements.
 */

@Entity(tableName = "installed_packages")
data class InstalledPackageEntity(
    @PrimaryKey @ColumnInfo(name = "package_id") val packageId: String,
    val category: String,               // 'veda' | 'ramayana_kanda' | 'mahabharata'
    @ColumnInfo(name = "source_id") val sourceId: Int,
    val title: String?,
    val version: Int = 1,
    @ColumnInfo(name = "installed_at") val installedAt: String? = null
)

@Entity(
    tableName = "veda_bhashya_contents",
    indices = [Index(value = ["scholar_id", "mantra_id"], name = "idx_veda_bhashya_lookup")],
    primaryKeys = ["scholar_id", "mantra_id", "field_key"]
)
data class VedaBhashyaContentEntity(
    @ColumnInfo(name = "scholar_id") val scholarId: Int,
    @ColumnInfo(name = "mantra_id") val mantraId: Int,
    @ColumnInfo(name = "field_key") val fieldKey: String,
    val value: String
)

@Entity(
    tableName = "ramayana_kanda_bhashya_contents",
    indices = [Index(value = ["scholar_id", "shloka_id"], name = "idx_ram_bhashya_lookup")],
    primaryKeys = ["scholar_id", "shloka_id", "field_key"]
)
data class RamayanaBhashyaContentEntity(
    @ColumnInfo(name = "scholar_id") val scholarId: Int,
    @ColumnInfo(name = "shloka_id") val shlokaId: Int,
    @ColumnInfo(name = "field_key") val fieldKey: String,
    val value: String
)

@Entity(tableName = "mahabharata_adhyayas")
data class MahabharataAdhyayaEntity(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "parba_id") val parbaId: Int,
    val title: String?,
    @ColumnInfo(name = "adhyaya_no") val adhyayaNo: Int?
)

@Entity(tableName = "mahabharata_upakhyanas")
data class MahabharataUpakhyanaEntity(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "adhyaya_id") val adhyayaId: Int,
    val title: String?,
    val content: String?
)
