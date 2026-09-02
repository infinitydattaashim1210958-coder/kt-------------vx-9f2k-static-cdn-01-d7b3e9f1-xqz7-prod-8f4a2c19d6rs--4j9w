package com.kyronix.swadhyaa.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "kandas")
data class KandaEntity(
    @PrimaryKey
    val id: Int?,
    val name: String,
    @ColumnInfo(name = "english_name") val englishName: String?,
    @ColumnInfo(name = "sarga_count") val sargaCount: Int?
)

@Entity(
    tableName = "sargas",
    indices = [Index(value = ["kanda_id"], name = "idx_sargas_kanda")]
)
data class SargaEntity(
    @PrimaryKey
    val id: Int?,
    @ColumnInfo(name = "kanda_id") val kandaId: Int,
    val chapter: Int,
    val name: String?
)

@Entity(
    tableName = "shlokas",
    indices = [
        Index(value = ["kanda_id"], name = "idx_shlokas_kanda"),
        Index(value = ["sarga_id"], name = "idx_shlokas_sarga")
    ]
)
data class ShlokaEntity(
    @PrimaryKey
    val id: Int?,
    @ColumnInfo(name = "kanda_id") val kandaId: Int,
    @ColumnInfo(name = "sarga_id") val sargaId: Int,
    val sanskrit: String
)
