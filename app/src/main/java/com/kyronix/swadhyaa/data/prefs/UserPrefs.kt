package com.kyronix.swadhyaa.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

// internal (not private): SettingsRepository shares this exact DataStore
// instance. Creating a second preferencesDataStore(name = "swadhyay_prefs")
// delegate pointing at the same file would throw
// IllegalStateException("There are multiple DataStores active for this
// file") at runtime — a well-known DataStore pitfall, not obvious from a
// compile-time signature.
internal val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "swadhyay_prefs")

/**
 * Continue-reading position + bookmarks.
 * Stored as JSON strings in DataStore (no Room schema change).
 *
 * `kind` is intentionally a plain String, not a sealed class — it already
 * supported "mahabharata"/"library" values at the type level even before
 * the legacy migration engine started using them; only the doc comment and
 * the callers previously assumed veda/ramayana only. See
 * LegacyMigrationEngine.kt for where the other two kinds first get used.
 *
 * corpusId/itemId are -1 for a record whose legacy identity (hash) could
 * not yet be resolved to a real database row — see LegacyMigrationEngine's
 * KtIdentity resolver. Such a record is NOT dropped: legacyHash is always
 * preserved, so a future, more complete resolver (or a hash-based jump
 * fallback in the UI) can still recover it. -1 is a deliberate sentinel,
 * not a bug — callers that navigate by corpusId/itemId must check for it.
 */
class UserPrefs(private val context: Context) {

    private val keyContinue = stringPreferencesKey("continue_json")
    private val keyBookmarks = stringPreferencesKey("bookmarks_json")
    private val keyPacks = stringPreferencesKey("downloaded_packs_json")
    private val keyLegacyReadingPositionsRaw = stringPreferencesKey("legacy_reading_positions_raw_json")
    private val keyMigrationDoneVersion = intPreferencesKey("legacy_migration_done_version")

    data class ContinuePos(
        val kind: String, // "veda" | "ramayana" | "mahabharata" | "library"
        val corpusId: Int,
        val itemId: Int,
        val label: String,
        val scrollPercent: Int? = null,
        val legacyHash: String? = null
    )

    data class Bookmark(
        val kind: String,
        val corpusId: Int,
        val itemId: Int,
        val label: String,
        val snippet: String,
        val savedAt: Long = System.currentTimeMillis(),
        val note: String? = null,
        val scrollPercent: Int? = null,
        /** Legacy's own bookmark id (bm_<ts>_<rand>), preserved for traceability only. */
        val legacyId: String? = null,
        /** Legacy router hash this bookmark pointed at — see class doc. */
        val legacyHash: String? = null
    )

    val continueFlow: Flow<ContinuePos?> = context.dataStore.data.map { prefs ->
        prefs[keyContinue]?.let { parseContinue(it) }
    }

    val bookmarksFlow: Flow<List<Bookmark>> = context.dataStore.data.map { prefs ->
        prefs[keyBookmarks]?.let { parseBookmarks(it) }.orEmpty()
    }

    suspend fun saveContinue(pos: ContinuePos) {
        context.dataStore.edit { it[keyContinue] = pos.toJson() }
    }

    suspend fun addBookmark(b: Bookmark) {
        context.dataStore.edit { prefs ->
            val list = prefs[keyBookmarks]?.let { parseBookmarks(it) }.orEmpty().toMutableList()
            list.removeAll { it.kind == b.kind && it.itemId == b.itemId }
            list.add(0, b)
            prefs[keyBookmarks] = bookmarksToJson(list.take(200))
        }
    }

    /**
     * Bulk-add used only by [LegacyMigrationEngine]. Dedups by legacyHash
     * when present (migrated records) rather than by (kind, itemId), since
     * multiple unresolved records legitimately share itemId = -1 and must
     * not collapse into one. Existing non-legacy bookmarks are untouched
     * and always kept; migrated records are only added if no bookmark with
     * the same legacyHash already exists (safe to call twice / re-run).
     */
    suspend fun addMigratedBookmarks(migrated: List<Bookmark>) {
        if (migrated.isEmpty()) return
        context.dataStore.edit { prefs ->
            val existing = prefs[keyBookmarks]?.let { parseBookmarks(it) }.orEmpty()
            val existingHashes = existing.mapNotNull { it.legacyHash }.toSet()
            val toAdd = migrated.filter { it.legacyHash == null || it.legacyHash !in existingHashes }
            if (toAdd.isEmpty()) return@edit
            prefs[keyBookmarks] = bookmarksToJson((existing + toAdd).take(500))
        }
    }

    suspend fun removeBookmark(kind: String, itemId: Int) {
        context.dataStore.edit { prefs ->
            val list = prefs[keyBookmarks]?.let { parseBookmarks(it) }.orEmpty()
                .filterNot { it.kind == kind && it.itemId == itemId }
            prefs[keyBookmarks] = bookmarksToJson(list)
        }
    }

    suspend fun isBookmarked(kind: String, itemId: Int): Boolean {
        val list = context.dataStore.data.first()[keyBookmarks]?.let { parseBookmarks(it) }.orEmpty()
        return list.any { it.kind == kind && it.itemId == itemId }
    }

    suspend fun markPackDownloaded(packId: String) {
        context.dataStore.edit { prefs ->
            val set = prefs[keyPacks]?.split("|")?.filter { it.isNotBlank() }.orEmpty().toMutableSet()
            set += packId
            prefs[keyPacks] = set.joinToString("|")
        }
    }

    suspend fun isPackDownloaded(packId: String): Boolean {
        val set = context.dataStore.data.first()[keyPacks]?.split("|")?.filter { it.isNotBlank() }.orEmpty()
        return packId in set
    }

    // ── Legacy migration support ────────────────────────────────────
    // See LegacyMigrationEngine.kt and DATA_MIGRATION_PLAN.md.

    /**
     * Full legacy reading-positions map, preserved verbatim (untranslated
     * JSON, exactly as read from the legacy SharedPreferences file) so no
     * data is lost even though only the single most-recent entry is
     * currently promoted to the typed [continueFlow] slot — Kotlin's
     * reading-position model is single-slot today (see
     * FEATURE_PARITY_MATRIX.md "Continue Reading"); legacy kept one slot
     * per scripture. When multi-slot support is built, this raw JSON is
     * the source to re-derive it from, without needing the legacy app to
     * still be installed at that point.
     */
    suspend fun saveRawLegacyReadingPositions(json: String) {
        context.dataStore.edit { it[keyLegacyReadingPositionsRaw] = json }
    }

    suspend fun getRawLegacyReadingPositions(): String? =
        context.dataStore.data.first()[keyLegacyReadingPositionsRaw]

    /** True once LegacyMigrationEngine has successfully completed for this schema version. */
    suspend fun isLegacyMigrationDone(version: Int): Boolean =
        (context.dataStore.data.first()[keyMigrationDoneVersion] ?: 0) >= version

    suspend fun markLegacyMigrationDone(version: Int) {
        context.dataStore.edit { it[keyMigrationDoneVersion] = version }
    }

    private fun ContinuePos.toJson(): String =
        JSONObject()
            .put("kind", kind)
            .put("corpusId", corpusId)
            .put("itemId", itemId)
            .put("label", label)
            .apply {
                if (scrollPercent != null) put("scrollPercent", scrollPercent)
                if (legacyHash != null) put("legacyHash", legacyHash)
            }
            .toString()

    private fun parseContinue(s: String): ContinuePos? = try {
        val o = JSONObject(s)
        ContinuePos(
            kind = o.getString("kind"),
            corpusId = o.getInt("corpusId"),
            itemId = o.getInt("itemId"),
            label = o.getString("label"),
            scrollPercent = if (o.has("scrollPercent")) o.getInt("scrollPercent") else null,
            legacyHash = if (o.has("legacyHash")) o.getString("legacyHash") else null
        )
    } catch (_: Exception) { null }

    private fun bookmarksToJson(list: List<Bookmark>): String {
        val arr = JSONArray()
        list.forEach { b ->
            arr.put(
                JSONObject()
                    .put("kind", b.kind)
                    .put("corpusId", b.corpusId)
                    .put("itemId", b.itemId)
                    .put("label", b.label)
                    .put("snippet", b.snippet)
                    .put("savedAt", b.savedAt)
                    .apply {
                        if (b.note != null) put("note", b.note)
                        if (b.scrollPercent != null) put("scrollPercent", b.scrollPercent)
                        if (b.legacyId != null) put("legacyId", b.legacyId)
                        if (b.legacyHash != null) put("legacyHash", b.legacyHash)
                    }
            )
        }
        return arr.toString()
    }

    private fun parseBookmarks(s: String): List<Bookmark> = try {
        val arr = JSONArray(s)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Bookmark(
                kind = o.getString("kind"),
                corpusId = o.getInt("corpusId"),
                itemId = o.getInt("itemId"),
                label = o.getString("label"),
                snippet = o.optString("snippet"),
                savedAt = o.optLong("savedAt"),
                note = if (o.has("note")) o.getString("note") else null,
                scrollPercent = if (o.has("scrollPercent")) o.getInt("scrollPercent") else null,
                legacyId = if (o.has("legacyId")) o.getString("legacyId") else null,
                legacyHash = if (o.has("legacyHash")) o.getString("legacyHash") else null
            )
        }
    } catch (_: Exception) { emptyList() }
}
