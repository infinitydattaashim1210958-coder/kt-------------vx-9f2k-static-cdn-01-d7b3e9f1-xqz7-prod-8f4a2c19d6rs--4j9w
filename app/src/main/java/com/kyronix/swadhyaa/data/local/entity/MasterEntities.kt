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
    val category: String,               // 'veda' | 'ramayana_kanda' | 'mahabharata' | 'library_book'
    @ColumnInfo(name = "source_id") val sourceId: Int,
    val title: String?,
    val version: Int = 1,
    @ColumnInfo(name = "installed_at") val installedAt: String? = null,
    /**
     * Library books use string ids (e.g. "gurugiri"), unlike the other
     * three categories' numeric ids — [sourceId] is 0 (unused placeholder)
     * for category="library_book" rows; this field carries the real id
     * instead. Added additively — every existing category keeps using
     * [sourceId] as before and leaves this null.
     */
    @ColumnInfo(name = "source_id_text") val sourceIdText: String? = null
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

// ── Digital Library ("db"-type books only — "html"-type books have no
// structured storage at all, see LibraryHtmlBookRepository) ──────────
// Schema verified directly against master-db.js's CREATE TABLE statements
// and mergeLibraryBookPack/getLibraryBookChapters/getLibraryBookParagraphs
// — book_id is the manifest.json string id (e.g. "gurugiri"), not a
// numeric pack id like the other three categories.

@Entity(
    tableName = "library_book_chapters",
    primaryKeys = ["book_id", "chapter_id"]
)
data class LibraryBookChapterEntity(
    @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "chapter_id") val chapterId: String,
    val seq: Int?,
    val heading: String?,
    @ColumnInfo(name = "is_cover") val isCover: Boolean = false,
    @ColumnInfo(name = "heading_bold") val headingBold: Boolean = false,
    @ColumnInfo(name = "heading_center") val headingCenter: Boolean = false,
    @ColumnInfo(name = "heading_underline") val headingUnderline: Boolean = false,
    @ColumnInfo(name = "heading_size") val headingSize: Double = 12.0
)

@Entity(
    tableName = "library_book_paragraphs",
    indices = [Index(value = ["book_id", "chapter_id"], name = "idx_lib_para_chapter")]
)
data class LibraryBookParagraphEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "chapter_id") val chapterId: String,
    val seq: Int?,
    val content: String?,
    @ColumnInfo(name = "is_bold") val isBold: Boolean = false,
    @ColumnInfo(name = "is_center") val isCenter: Boolean = false,
    @ColumnInfo(name = "is_right") val isRight: Boolean = false,
    @ColumnInfo(name = "is_underline") val isUnderline: Boolean = false,
    @ColumnInfo(name = "font_size") val fontSize: Double = 12.0
)

/**
 * A footnote/reference marker. Joins to its owning paragraph by
 * (book_id, chapter_id, para_seq == LibraryBookParagraphEntity.seq) — NOT
 * by paragraph id. Verified directly against getLibraryBookParagraphs,
 * which groups refs by `r.para_seq` matched against each paragraph's own
 * `seq` field; a paragraph can carry more than one footnote marker.
 */
@Entity(
    tableName = "library_book_refs",
    indices = [Index(value = ["book_id", "chapter_id", "para_seq"], name = "idx_lib_refs_para")]
)
data class LibraryBookRefEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "chapter_id") val chapterId: String,
    @ColumnInfo(name = "para_seq") val paraSeq: Int,
    @ColumnInfo(name = "ref_seq") val refSeq: Int,
    @ColumnInfo(name = "ref_number") val refNumber: String?,
    @ColumnInfo(name = "ref_note") val refNote: String?
)
