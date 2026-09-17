package com.kyronix.swadhyaa.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Writable master database entities.
 * Schema must stay additive-only. Matches master-db.js CREATE TABLE statements.
 * v3 addition: LibraryBookSelectionEntity for per-paragraph highlights/bookmarks.
 */

@Entity(tableName = "installed_packages")
data class InstalledPackageEntity(
    @PrimaryKey @ColumnInfo(name = "package_id") val packageId: String,
    val category: String,
    @ColumnInfo(name = "source_id") val sourceId: Int,
    val title: String?,
    val version: Int = 1,
    @ColumnInfo(name = "installed_at") val installedAt: String? = null,
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

// ── Digital Library ───────────────────────────────────────────────────────────

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

/**
 * A user-created text selection (highlight or bookmark) on a paragraph.
 * Added in DB v3. Never comes from a pack — purely local user data.
 *
 * kind: "highlight" or "bookmark". Bookmarks additionally appear in
 * a dedicated list; highlights are only shown inline in the reader.
 */
@Entity(
    tableName = "library_book_selections",
    indices = [Index(value = ["book_id", "chapter_id", "para_seq"], name = "idx_lib_sel_para")]
)
data class LibraryBookSelectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "chapter_id") val chapterId: String,
    @ColumnInfo(name = "para_seq") val paraSeq: Int,
    @ColumnInfo(name = "sel_start") val selStart: Int,
    @ColumnInfo(name = "sel_end") val selEnd: Int,
    @ColumnInfo(name = "selected_text") val selectedText: String,
    val kind: String = "highlight",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
