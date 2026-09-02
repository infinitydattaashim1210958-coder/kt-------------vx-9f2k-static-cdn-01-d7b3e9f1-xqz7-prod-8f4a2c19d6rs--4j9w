package com.kyronix.swadhyaa.domain.model

/**
 * UI-facing summary of a Veda.
 * Derived from the production core.db — never invents data.
 */
data class VedaSummary(
    val id: Int,
    val code: String,
    val name: String,
    val mantraCount: Int
)
