package com.kyronix.swadhyaa.domain.model

/**
 * UI-facing mantra payload from core.db.
 */
data class MantraContent(
    val id: Int,
    val vedaId: Int,
    val vedaCode: String,
    val vedaName: String,
    val level1: Int?,
    val level2: Int?,
    val level3: Int?,
    val mantraNo: Int?,
    val sanskrit: String,
    val sanskritSwara: String?,
    val rishi: String?,
    val devata: String?,
    val chhanda: String?,
    val refLabel: String
)
