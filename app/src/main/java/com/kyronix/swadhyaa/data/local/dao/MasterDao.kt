package com.kyronix.swadhyaa.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.SkipQueryVerification
import androidx.room.Transaction
import com.kyronix.swadhyaa.data.local.entity.InstalledPackageEntity
import com.kyronix.swadhyaa.data.local.entity.LibraryBookChapterEntity
import com.kyronix.swadhyaa.data.local.entity.LibraryBookParagraphEntity
import com.kyronix.swadhyaa.data.local.entity.LibraryBookRefEntity
import com.kyronix.swadhyaa.data.local.entity.LibraryBookSelectionEntity
import com.kyronix.swadhyaa.data.local.entity.MahabharataAdhyayaEntity
import com.kyronix.swadhyaa.data.local.entity.MahabharataUpakhyanaEntity
import com.kyronix.swadhyaa.data.local.entity.RamayanaBhashyaContentEntity
import com.kyronix.swadhyaa.data.local.entity.VedaBhashyaContentEntity

@Dao
interface MasterDao {

    // ── Installed packages ──────────────────────────────────────────────────

    @Query("SELECT * FROM installed_packages WHERE package_id = :packageId LIMIT 1")
    suspend fun getInstalledPackage(packageId: String): InstalledPackageEntity?

    @Query("SELECT * FROM installed_packages WHERE category = :category")
    suspend fun getInstalledByCategory(category: String): List<InstalledPackageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInstalledPackage(pkg: InstalledPackageEntity)

    @Query("DELETE FROM installed_packages WHERE package_id = :packageId")
    suspend fun deleteInstalledPackage(packageId: String)

    // ── Veda bhāṣya ─────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVedaBhashya(items: List<VedaBhashyaContentEntity>)

    @Query("SELECT * FROM veda_bhashya_contents WHERE scholar_id = :scholarId AND mantra_id = :mantraId")
    suspend fun getVedaBhashya(scholarId: Int, mantraId: Int): List<VedaBhashyaContentEntity>

    @Query("DELETE FROM veda_bhashya_contents WHERE scholar_id = :scholarId")
    suspend fun deleteVedaBhashyaForScholar(scholarId: Int)

    // ── Ramayana bhāṣya ─────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRamayanaBhashya(items: List<RamayanaBhashyaContentEntity>)

    @Query("SELECT * FROM ramayana_kanda_bhashya_contents WHERE scholar_id = :scholarId AND shloka_id = :shlokaId")
    suspend fun getRamayanaBhashya(scholarId: Int, shlokaId: Int): List<RamayanaBhashyaContentEntity>

    @Query("DELETE FROM ramayana_kanda_bhashya_contents WHERE scholar_id = :scholarId")
    suspend fun deleteRamayanaBhashyaForScholar(scholarId: Int)

    // ── Mahabharata ─────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMahabharataAdhyayas(items: List<MahabharataAdhyayaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMahabharataUpakhyanas(items: List<MahabharataUpakhyanaEntity>)

    @Query("SELECT * FROM mahabharata_adhyayas WHERE parba_id = :parbaId ORDER BY adhyaya_no ASC")
    suspend fun getMahabharataAdhyayas(parbaId: Int): List<MahabharataAdhyayaEntity>

    @Query("SELECT * FROM mahabharata_upakhyanas WHERE adhyaya_id = :adhyayaId")
    suspend fun getMahabharataUpakhyanas(adhyayaId: Int): List<MahabharataUpakhyanaEntity>

    @Query("DELETE FROM mahabharata_adhyayas WHERE parba_id = :parbaId")
    suspend fun deleteMahabharataAdhyayasForParba(parbaId: Int)

    @Query("DELETE FROM mahabharata_upakhyanas WHERE adhyaya_id IN (SELECT id FROM mahabharata_adhyayas WHERE parba_id = :parbaId)")
    suspend fun deleteMahabharataUpakhyanasForParba(parbaId: Int)

    @Transaction
    suspend fun installVedaPack(pkg: InstalledPackageEntity, contents: List<VedaBhashyaContentEntity>) {
        insertVedaBhashya(contents); upsertInstalledPackage(pkg)
    }

    @Transaction
    suspend fun installRamayanaPack(pkg: InstalledPackageEntity, contents: List<RamayanaBhashyaContentEntity>) {
        insertRamayanaBhashya(contents); upsertInstalledPackage(pkg)
    }

    @Transaction
    suspend fun installMahabharataPack(
        pkg: InstalledPackageEntity,
        adhyayas: List<MahabharataAdhyayaEntity>,
        upakhyanas: List<MahabharataUpakhyanaEntity>
    ) {
        insertMahabharataAdhyayas(adhyayas); insertMahabharataUpakhyanas(upakhyanas); upsertInstalledPackage(pkg)
    }

    // ── Digital Library (db-type books) ────────────────────────────────────

    @Query("DELETE FROM library_book_refs WHERE book_id = :bookId")
    suspend fun deleteLibraryRefs(bookId: String)

    @Query("DELETE FROM library_book_paragraphs WHERE book_id = :bookId")
    suspend fun deleteLibraryParagraphs(bookId: String)

    @Query("DELETE FROM library_book_chapters WHERE book_id = :bookId")
    suspend fun deleteLibraryChapters(bookId: String)

    @SkipQueryVerification
    @Query("DELETE FROM library_book_paragraphs_fts WHERE book_id = :bookId")
    suspend fun deleteLibraryParagraphsFts(bookId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLibraryChapters(items: List<LibraryBookChapterEntity>)

    @Insert
    suspend fun insertLibraryParagraphs(items: List<LibraryBookParagraphEntity>)

    @Insert
    suspend fun insertLibraryRefs(items: List<LibraryBookRefEntity>)

    @SkipQueryVerification
    @Query("""
        INSERT INTO library_book_paragraphs_fts (rowid, content, book_id, chapter_id, para_id)
        SELECT id, content, book_id, chapter_id, id
        FROM library_book_paragraphs WHERE book_id = :bookId
    """)
    suspend fun rebuildLibraryParagraphsFts(bookId: String)

    @Query("SELECT * FROM library_book_chapters WHERE book_id = :bookId ORDER BY seq")
    suspend fun getLibraryChapters(bookId: String): List<LibraryBookChapterEntity>

    @Query("SELECT * FROM library_book_paragraphs WHERE book_id = :bookId AND chapter_id = :chapterId ORDER BY seq")
    suspend fun getLibraryParagraphs(bookId: String, chapterId: String): List<LibraryBookParagraphEntity>

    @Query("""
        SELECT * FROM library_book_refs
        WHERE book_id = :bookId AND chapter_id = :chapterId
        ORDER BY para_seq, ref_seq
    """)
    suspend fun getLibraryRefs(bookId: String, chapterId: String): List<LibraryBookRefEntity>

    @SkipQueryVerification
    @Query("""
        SELECT chapter_id, content FROM library_book_paragraphs_fts
        WHERE book_id = :bookId AND library_book_paragraphs_fts MATCH :escapedTerm
        LIMIT :limit
    """)
    suspend fun searchLibraryBookFts(bookId: String, escapedTerm: String, limit: Int): List<LibrarySearchRow>

    @Query("""
        SELECT chapter_id, content FROM library_book_paragraphs
        WHERE book_id = :bookId AND content LIKE :escapedLikePattern ESCAPE '\'
        LIMIT :limit
    """)
    suspend fun searchLibraryBookLike(bookId: String, escapedLikePattern: String, limit: Int): List<LibrarySearchRow>

    @Transaction
    suspend fun installLibraryDbBook(
        pkg: InstalledPackageEntity,
        chapters: List<LibraryBookChapterEntity>,
        paragraphs: List<LibraryBookParagraphEntity>,
        refs: List<LibraryBookRefEntity>
    ) {
        val bookId = pkg.sourceIdText
            ?: throw IllegalArgumentException("installLibraryDbBook requires pkg.sourceIdText")
        deleteLibraryRefs(bookId); deleteLibraryParagraphs(bookId); deleteLibraryChapters(bookId)
        try { deleteLibraryParagraphsFts(bookId) } catch (_: Exception) {}
        insertLibraryChapters(chapters); insertLibraryParagraphs(paragraphs); insertLibraryRefs(refs)
        try { rebuildLibraryParagraphsFts(bookId) } catch (_: Exception) {}
        upsertInstalledPackage(pkg)
    }

    @Transaction
    suspend fun removeLibraryDbBook(bookId: String, packageId: String) {
        deleteLibraryRefs(bookId); deleteLibraryParagraphs(bookId); deleteLibraryChapters(bookId)
        try { deleteLibraryParagraphsFts(bookId) } catch (_: Exception) {}
        deleteLibrarySelections(bookId)
        deleteInstalledPackage(packageId)
    }

    // ── User selections: highlights & bookmarks (v3) ────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSelection(sel: LibraryBookSelectionEntity): Long

    @Query("""
        SELECT * FROM library_book_selections
        WHERE book_id = :bookId AND chapter_id = :chapterId AND para_seq = :paraSeq
        ORDER BY sel_start
    """)
    suspend fun getSelectionsForParagraph(
        bookId: String, chapterId: String, paraSeq: Int
    ): List<LibraryBookSelectionEntity>

    @Query("""
        SELECT * FROM library_book_selections
        WHERE book_id = :bookId AND chapter_id = :chapterId
        ORDER BY para_seq, sel_start
    """)
    suspend fun getSelectionsForChapter(bookId: String, chapterId: String): List<LibraryBookSelectionEntity>

    @Query("SELECT * FROM library_book_selections WHERE book_id = :bookId ORDER BY created_at DESC")
    suspend fun getAllSelectionsForBook(bookId: String): List<LibraryBookSelectionEntity>

    @Query("SELECT * FROM library_book_selections WHERE book_id = :bookId AND kind = 'bookmark' ORDER BY created_at DESC")
    suspend fun getBookmarksForBook(bookId: String): List<LibraryBookSelectionEntity>

    @Query("DELETE FROM library_book_selections WHERE id = :id")
    suspend fun deleteSelection(id: Int)

    @Query("DELETE FROM library_book_selections WHERE book_id = :bookId")
    suspend fun deleteLibrarySelections(bookId: String)

    @Query("""
        DELETE FROM library_book_selections
        WHERE book_id = :bookId AND chapter_id = :chapterId
        AND para_seq = :paraSeq AND sel_start = :selStart AND sel_end = :selEnd
    """)
    suspend fun deleteSelectionByRange(
        bookId: String, chapterId: String, paraSeq: Int, selStart: Int, selEnd: Int
    )
}

data class LibrarySearchRow(
    @ColumnInfo(name = "chapter_id") val chapterId: String,
    val content: String?
)
