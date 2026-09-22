package com.kyronix.swadhyaa.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * All information needed to stream and display a single mantra's audio.
 *
 * Built by the Reader screen (from MantraEntity + VedaEntity) and passed to
 * MantraPlayerService.  The service also builds these internally when
 * auto-advancing in Listening Mode.
 */
@Parcelize
data class MantraAudioRef(
    /** Primary key from the mantras table */
    val mantraId:      Int,
    /** Foreign key — needed so the service can query next/prev from VedaDao */
    val vedaId:        Int,
    /** "rigveda" | "samaveda" | "yajurveda" | "atharvaveda" */
    val vedaCode:      String,
    /** Hierarchical reference string, e.g. "1_2_2" for Rigveda 1·2·2 */
    val mantraRefId:   String,
    /** Direct GitHub release stream URL — no caching, play straight from CDN */
    val audioUrl:      String,
    /**
     * Sanskrit / Devanagari text of the mantra.
     * Used as album art text in the notification.
     * TODO: replace placeholder with the real MantraEntity text field
     *   (e.g. mantra.devanagari, mantra.text, mantra.samhitaText …)
     */
    val devanagariText: String = "",
    /** Short display label shown in the notification title, e.g. "ঋগ্বেদ ১।২।২" */
    val displayLabel:  String = mantraRefId
) : Parcelable
