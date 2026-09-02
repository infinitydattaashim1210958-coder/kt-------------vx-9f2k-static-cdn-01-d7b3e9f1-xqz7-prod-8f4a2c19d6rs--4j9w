package com.kyronix.swadhyaa.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Exact schema mirror of core.db → vedas.
 * Never alter column order or types — asset is read-only.
 */
@Entity(tableName = "vedas")
data class VedaEntity(
    @PrimaryKey val id: Int,
    val code: String,
    val name: String,
    @ColumnInfo(name = "level1_label") val level1Label: String?,
    @ColumnInfo(name = "level2_label") val level2Label: String?,
    @ColumnInfo(name = "level3_label") val level3Label: String?,
    @ColumnInfo(name = "mantra_no_label") val mantraNoLabel: String?
)

/**
 * Exact schema mirror of core.db → mantras.
 * 20,380 rows. Indexes preserved for navigation + ref lookup.
 */
@Entity(
    tableName = "mantras",
    indices = [
        Index(value = ["veda_id", "mantra_ref_id"], name = "idx_mantras_ref"),
        Index(value = ["veda_id", "level1", "level2", "level3", "mantra_no"], name = "idx_mantras_nav")
    ]
)
data class MantraEntity(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "veda_id") val vedaId: Int,
    @ColumnInfo(name = "mantra_ref_id") val mantraRefId: String,
    val level1: Int?,
    val level2: Int?,
    val level3: Int?,
    @ColumnInfo(name = "mantra_no") val mantraNo: Int?,
    @ColumnInfo(name = "sanskrit_text") val sanskritText: String?,
    @ColumnInfo(name = "sanskrit_swara") val sanskritSwara: String?,
    val devata: String?,
    val rishi: String?,
    val chhanda: String?,
    val swara: String?
)

@Entity(tableName = "scholars")
data class ScholarEntity(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "veda_id") val vedaId: Int,
    val name: String,
    val language: String?,
    @ColumnInfo(name = "display_order") val displayOrder: Int = 100,
    @ColumnInfo(name = "pack_file") val packFile: String?,
    @ColumnInfo(name = "pack_size_bytes") val packSizeBytes: Long?,
    @ColumnInfo(name = "entry_count") val entryCount: Int?
)

@Entity(tableName = "scholar_fields")
data class ScholarFieldEntity(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "scholar_id") val scholarId: Int,
    @ColumnInfo(name = "field_key") val fieldKey: String,
    @ColumnInfo(name = "display_order") val displayOrder: Int?
)

@Entity(
    tableName = "bhashya_presence",
    primaryKeys = ["mantra_id", "scholar_id"],
    indices = [Index(value = ["mantra_id"], name = "idx_presence_mantra")]
)
data class BhashyaPresenceEntity(
    @ColumnInfo(name = "mantra_id") val mantraId: Int,
    @ColumnInfo(name = "scholar_id") val scholarId: Int
)
