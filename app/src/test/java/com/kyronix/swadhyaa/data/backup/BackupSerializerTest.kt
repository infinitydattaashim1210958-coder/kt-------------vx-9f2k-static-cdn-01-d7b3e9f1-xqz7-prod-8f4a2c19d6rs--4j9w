package com.kyronix.swadhyaa.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSerializerTest {

    private fun samplePayload(accountId: String = "sub-123") = BackupPayload(
        schemaVersion = BackupPayload.CURRENT_SCHEMA_VERSION,
        createdAt = 1_700_000_000_000L,
        accountSubjectId = accountId,
        appVersionName = "1.0.test",
        continuePos = ContinuePosBackup(
            kind = "veda",
            corpusId = 1,
            itemId = 42,
            label = "RV 1.1.1",
            scrollPercent = 30
        ),
        bookmarks = listOf(
            BookmarkBackup(
                kind = "veda",
                corpusId = 1,
                itemId = 10,
                label = "Bookmark A",
                snippet = "snippet",
                savedAt = 1_700_000_000_100L
            )
        ),
        librarySelections = listOf(
            LibrarySelectionBackup(
                bookId = "book1",
                chapterId = "ch1",
                paraSeq = 3,
                selStart = 0,
                selEnd = 5,
                selectedText = "hello",
                kind = "highlight",
                createdAt = 1_700_000_000_200L
            )
        ),
        readerSettings = ReaderSettingsBackup(
            fontSize = 20,
            fontFamily = "default",
            banglaFont = "hind_siliguri",
            devanagariFont = "noto_serif_devanagari",
            theme = "dark",
            accentTheme = "gold",
            lineHeight = "normal",
            pageMargin = "normal",
            justifyText = true,
            keepAwake = false,
            language = "বাংলা",
            script = "বাংলা",
            transliteration = false
        )
    )

    @Test
    fun roundTrip_preservesFieldsAndIntegrity() {
        val original = samplePayload()
        val json = BackupSerializer.toJson(original)
        val result = BackupSerializer.fromJson(json)
        assertTrue(result is BackupValidationResult.Valid)
        val parsed = (result as BackupValidationResult.Valid).payload
        assertEquals(original.schemaVersion, parsed.schemaVersion)
        assertEquals(original.accountSubjectId, parsed.accountSubjectId)
        assertEquals(original.continuePos?.itemId, parsed.continuePos?.itemId)
        assertEquals(1, parsed.bookmarks.size)
        assertEquals(1, parsed.librarySelections.size)
        assertEquals(20, parsed.readerSettings?.fontSize)
        assertTrue(parsed.contentSha256.isNotBlank())
    }

    @Test
    fun tamperedContent_failsIntegrity() {
        val json = BackupSerializer.toJson(samplePayload())
        val tampered = json.replace("Bookmark A", "Bookmark HACKED")
        val result = BackupSerializer.fromJson(tampered)
        assertTrue(result is BackupValidationResult.Invalid)
        assertTrue((result as BackupValidationResult.Invalid).reason.contains("Integrity"))
    }

    @Test
    fun unsupportedSchema_rejected() {
        val json = """{"schemaVersion":99,"createdAt":1,"accountSubjectId":"x","bookmarks":[],"librarySelections":[]}"""
        val result = BackupSerializer.fromJson(json)
        assertTrue(result is BackupValidationResult.Invalid)
        assertTrue((result as BackupValidationResult.Invalid).reason.contains("Unsupported schema"))
    }

    @Test
    fun missingAccountId_rejected() {
        val json = """{"schemaVersion":1,"createdAt":1,"accountSubjectId":"","bookmarks":[],"librarySelections":[]}"""
        val result = BackupSerializer.fromJson(json)
        assertTrue(result is BackupValidationResult.Invalid)
    }

    @Test
    fun emptyBackup_valid() {
        val payload = BackupPayload(
            createdAt = 1L,
            accountSubjectId = "acc",
            appVersionName = null,
            continuePos = null,
            bookmarks = emptyList(),
            librarySelections = emptyList(),
            readerSettings = null
        )
        val json = BackupSerializer.toJson(payload)
        val result = BackupSerializer
... 
