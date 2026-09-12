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
import com.kyronix.swadhyaa.data.local.entity.MahabharataAdhyayaEntity
import com.kyronix.swadhyaa.data.local.entity.MahabharataUpakhyanaEntity
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

    // ── Mahabharata ──────────────────────────────────────────────────
    // Previously missing entirely despite MahabharataAdhyayaEntity/
    // MahabharataUpakhyanaEntity existing in MasterEntities.kt — the
    // schema could not have been populated even if this DB were wired in.
    // Added for internal consistency; see the architecture-status note at
    // the top of MasterDatabase.kt before wiring any of this in for real.

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMahabharataAdhyayas(items: List<MahabharataAdhyayaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMahabharataUpakhyanas(items: List<MahabharataUpakhyanaEntity>)

    @Query("SELECT * FROM mahabharata_adhyayas WHERE parba_id = :parbaId ORDER BY adhyaya_no ASC")
    suspend fun getMahabharataAdhyayas(parbaId: Int): List<MahabharataAdhyayaEntity>

    @Query("SELECT * FROM mahabharata_upakhyanas WHERE adhyaya_id = :adhyayaId")
    suspend fun getMahabharataUpakhyanas(adhyayaId: Int): List<MahabharataUpakhyanaEntity>

    @Query(
        """
        DELETE FROM mahabharata_adhyayas
        WHERE parba_id = :parbaId
        """
    )
    suspend fun deleteMahabharataAdhyayasForParba(parbaId: Int)

    @Query(
        """
        DELETE FROM mahabharata_upakhyanas
        WHERE adhyaya_id IN (SELECT id FROM mahabharata_adhyayas WHERE parba_id = :parbaId)
        """
    )
    suspend fun deleteMahabharataUpakhyanasForParba(parbaId: Int)

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

    @Transaction
    suspend fun installMahabharataPack(
        pkg: InstalledPackageEntity,
        adhyayas: List<MahabharataAdhyayaEntity>,
        upakhyanas: List<MahabharataUpakhyanaEntity>
    ) {
        insertMahabharataAdhyayas(adhyayas)
        insertMahabharataUpakhyanas(upakhyanas)
        upsertInstalledPackage(pkg)
    }

    // ── Digital Library ("db"-type books) ────────────────────────────
    // Matches master-db.js's mergeLibraryBookPack/getLibraryBookChapters/
    // getLibraryBookParagraphs/searchLibraryBook exactly — see
    // MasterEntities.kt's Library section doc for the schema evidence.

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

    /**
     * Rebuilds the FTS index from the just-inserted paragraphs, entirely
     * in SQL — matches legacy's INSERT INTO ...fts (rowid, ...) SELECT id,
     * ... exactly, including rowid = paragraph.id. Doing this in SQL
     * (rather than round-tripping generated ids through Kotlin) avoids any
     * risk of the FTS rows getting out of order relative to what Room
     * actually assigned.
     */
    @SkipQueryVerification
    @Query(
        """
        INSERT INTO library_book_paragraphs_fts (rowid, content, book_id, chapter_id, para_id)
        SELECT id, content, book_id, chapter_id, id
        FROM library_book_paragraphs WHERE book_id = :bookId
        """
    )
    suspend fun rebuildLibraryParagraphsFts(bookId: String)

    @Query("SELECT * FROM library_book_chapters WHERE book_id = :bookId ORDER BY seq")
    suspend fun getLibraryChapters(bookId: String): List<LibraryBookChapterEntity>

    @Query("SELECT * FROM library_book_paragraphs WHERE book_id = :bookId AND chapter_id = :chapterId ORDER BY seq")
    suspend fun getLibraryParagraphs(bookId: String, chapterId: String): List<LibraryBookParagraphEntity>

    @Query(
        """
        SELECT * FROM library_book_refs
        WHERE book_id = :bookId AND chapter_id = :chapterId
        ORDER BY para_seq, ref_seq
        """
    )
    suspend fun getLibraryRefs(bookId: String, chapterId: String): List<LibraryBookRefEntity>

    // BUGFIX: removed getLibraryChapterCount() — it was
    // `@Query("SELECT COUNT(*) FROM library_book_chapters WHERE book_id = :bookId") suspend fun ...(): Int`.
    // A bare Int/Long return type from @Query is the one shape Room's
    // codegen can read as EITHER "scalar SELECT result" OR "rows affected
    // by an update/delete" — and this was the only query in this whole
    // DAO with that shape (everything else returns List<Entity>/an
    // entity/Unit, which Room can only ever treat as read-only). It's
    // also the only Library DAO call reachable from the catalog screen —
    // matching exactly where "Queries can be performed using
    // SQLiteDatabase query or rawQuery methods only." showed up. Callers
    // now use getLibraryChapters(bookId).isNotEmpty() instead (see
    // LibraryDbBookRepository.isDownloaded), which has no such ambiguity.

    /**
     * FTS query text must already be escaped by the caller (see
     * SearchRepository.escapeFtsQuery / R10) — this DAO does not escape it
     * itself, matching how VedaDao's raw FTS queries work.
     */
    @SkipQueryVerification
    @Query(
        """
        SELECT chapter_id, content FROM library_book_paragraphs_fts
        WHERE book_id = :bookId AND library_book_paragraphs_fts MATCH :escapedTerm
        LIMIT :limit
        """
    )
    suspend fun searchLibraryBookFts(bookId: String, escapedTerm: String, limit: Int): List<LibrarySearchRow>

    @Query(
        """
        SELECT chapter_id, content FROM library_book_paragraphs
        WHERE book_id = :bookId AND content LIKE :escapedLikePattern ESCAPE '\'
        LIMIT :limit
        """
    )
    suspend fun searchLibraryBookLike(bookId: String, escapedLikePattern: String, limit: Int): List<LibrarySearchRow>

    /**
     * Idempotent atomic install/re-merge of a downloaded "db"-type library
     * book: deletes any prior rows for this book_id first (matches legacy
     * exactly — handles a re-download after an interrupted install without
     * duplicating content), inserts the fresh chapters/paragraphs/refs,
     * rebuilds the FTS index, then marks the package installed.
     */
    @Transaction
    suspend fun installLibraryDbBook(
        pkg: InstalledPackageEntity,
        chapters: List<LibraryBookChapterEntity>,
        paragraphs: List<LibraryBookParagraphEntity>,
        refs: List<LibraryBookRefEntity>
    ) {
        val bookId = pkg.sourceIdText
            ?: throw IllegalArgumentException("installLibraryDbBook requires pkg.sourceIdText (the book id)")
        deleteLibraryRefs(bookId)
        deleteLibraryParagraphs(bookId)
        deleteLibraryChapters(bookId)
        deleteLibraryParagraphsFts(bookId)

        insertLibraryChapters(chapters)
        insertLibraryParagraphs(paragraphs)
        insertLibraryRefs(refs)
        rebuildLibraryParagraphsFts(bookId)

        upsertInstalledPackage(pkg)
    }

    @Transaction
    suspend fun removeLibraryDbBook(bookId: String, packageId: String) {
        deleteLibraryRefs(bookId)
        deleteLibraryParagraphs(bookId)
        deleteLibraryChapters(bookId)
        deleteLibraryParagraphsFts(bookId)
        deleteInstalledPackage(packageId)
    }
}

/** Row shape shared by both the FTS and LIKE-fallback library search queries. */
data class LibrarySearchRow(
    @ColumnInfo(name = "chapter_id") val chapterId: String,
    val content: String?
)
