package com.kyronix.swadhyaa.data.migration

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.kyronix.swadhyaa.data.local.CoreDatabase
import com.kyronix.swadhyaa.data.local.RamayanaCoreDatabase
import com.kyronix.swadhyaa.data.prefs.ReaderSettings
import com.kyronix.swadhyaa.data.prefs.SettingsRepository
import com.kyronix.swadhyaa.data.prefs.UserPrefs
import com.kyronix.swadhyaa.data.repository.MahabharataManifest
import com.kyronix.swadhyaa.data.repository.MahabharataRepository
import org.json.JSONArray
import org.json.JSONObject

/**
 * One-time migration of legacy (Capacitor/JS) user data into the Kotlin
 * app's own storage. See RISK_REGISTER.md R3 and DATA_MIGRATION_PLAN.md.
 *
 * ── What this resolves, and the evidence behind each resolver ──────────
 *
 * Veda mantra bookmarks/positions (`#/mantra/<vedaCode>/<ref>`): fully
 * resolved to real (vedaId, mantraId) via CoreDatabase's bundled
 * VedaDao.getVedaByCode()/getMantraByRef() — these query core.db, an APK
 * asset always present, so resolution never depends on network or an
 * installed pack.
 *
 * Ramayana (`#/ramayana/shloka/<ref>`): fully resolved via
 * RamayanaDao.getShlokaByRef(). ref is "K<kandaId>.S<sargaId>.<shlokaId>"
 * — verified byte-for-byte against legacy's ramGetShlokaByRef (regex
 * ^K(\d+)\.S(\d+)\.(\d+)$ over kanda_id/sarga_id/id, all raw row ids, not
 * chapter/sarga numbers as an earlier draft of this file assumed before
 * checking). Reads ramayana_core.db, a bundled asset — same
 * no-network-dependency guarantee as Veda.
 *
 * Mahabharata (`#/mahabharata/parba/<legacyId>/adhyay/<adhyayId>`):
 * corpusId resolved via MahabharataManifest.parbaNoFromLegacyId() —
 * legacy's hash carries a 301..318 id (verified against mahabharata.js's
 * MAHABHARATA_PARBAS literal: id = 300 + parba_no), which Kotlin's own
 * ParbaInfo doesn't have at all (Kotlin uses parbaNo, 1..18, everywhere).
 * itemId (the adhyaya row) is resolved only if that parba's pack is
 * ALREADY downloaded on-device (PackDownloadManager.isDownloaded is a
 * local file check, no network) — migration must never trigger a pack
 * fetch just to translate a bookmark. If the pack isn't present, corpusId
 * still resolves (needs no pack access) and itemId stays -1.
 *
 * Note on legacy's own Mahabharata architecture, discovered while
 * verifying the above: mahabharata.js's *live* code path
 * (mbGetAdhyayasForParba etc.) reads from window.SwadhyayMasterDB — a
 * merged master database — not the per-pack attach/evict machinery
 * described for it in LEGACY_ARCHITECTURE.md. That machinery is present
 * in the file but explicitly commented as dead ("inert... safe to
 * delete") by legacy's own source. LEGACY_ARCHITECTURE.md and
 * DATABASE_CONTRACT.md have been corrected to reflect this — it also
 * means Kotlin's parked MasterDatabase/MasterDao (RISK_REGISTER.md R5) is
 * a closer architectural match to legacy's real current behavior than
 * this migration engine's own on-device-pack-check approach above, which
 * is a pragmatic migration-time compromise, not a claim that it's the
 * right long-term query path for the app's Mahabharata screens.
 *
 * Library (`#/library/read/<bookId>`): not resolved at all — Digital
 * Library has no typed row ids in Kotlin yet (RISK_REGISTER.md R4).
 * bookId is recoverable from legacyHash whenever that lands.
 *
 * ── Source file location ──────────────────────────────────────────────
 *
 * "CapacitorStorage" is the well-documented SharedPreferences file name
 * used by the official @capacitor/preferences plugin on Android, but this
 * was NOT independently verified against a real installed legacy APK in
 * this session (see DATA_MIGRATION_PLAN.md §1 — inspecting
 * /data/data/<pkg>/shared_prefs/ on a real device is the way to confirm
 * it). If it's wrong, [runIfNeeded] safely no-ops (nothing to migrate
 * found) rather than crashing — correcting CANDIDATE_PREFS_FILES is a
 * one-line fix once confirmed.
 *
 * Only meaningful when running as the `prod` build flavor
 * (applicationId com.kyronix.swadhyaam) installed as an update over the
 * legacy app — that's the only scenario where Android's private app data
 * directory is actually shared with the legacy install. The `dev` flavor
 * has a different applicationId by design (see RISK_REGISTER.md R1) and
 * will never find legacy data to migrate, which is expected, not a bug.
 */
object LegacyMigrationEngine {

    private const val TAG = "LegacyMigrationEngine"

    /** Bump this to force a re-run (e.g. after fixing a resolver above). */
    const val SCHEMA_VERSION = 1

    private val CANDIDATE_PREFS_FILES = listOf("CapacitorStorage")

    private const val KEY_BOOKMARKS = "chaturveda_bookmarks"
    private const val KEY_READING_POSITIONS = "chaturveda_reading_positions"

    // Bare keys — see DATA_MIGRATION_PLAN.md §1's corrected note on why
    // these are NOT "chaturveda_"-prefixed on the native Android path.
    private val SETTINGS_KEYS = listOf(
        "fontSize", "fontFamily", "theme", "accentTheme", "lineHeight",
        "pageMargin", "justifyText", "keepAwake", "language", "script",
        "transliteration"
    )

    data class MigrationResult(
        val ran: Boolean,
        val sourceFile: String? = null,
        val bookmarksMigrated: Int = 0,
        val bookmarksResolved: Int = 0,
        val readingPositionMigrated: Boolean = false,
        val settingsMigrated: Boolean = false,
        val errors: List<String> = emptyList()
    )

    suspend fun runIfNeeded(context: Context): MigrationResult {
        val userPrefs = UserPrefs(context)
        if (userPrefs.isLegacyMigrationDone(SCHEMA_VERSION)) {
            return MigrationResult(ran = false)
        }

        val found = findLegacySharedPreferences(context)
        if (found == null) {
            Log.i(
                TAG,
                "No legacy SharedPreferences file found among candidates " +
                    "$CANDIDATE_PREFS_FILES. Either a fresh install (nothing to " +
                    "migrate — expected), or the candidate file name needs " +
                    "correcting for this device (see this class's doc comment)."
            )
            userPrefs.markLegacyMigrationDone(SCHEMA_VERSION)
            return MigrationResult(ran = true, sourceFile = null)
        }
        val (fileName, sp) = found

        val errors = mutableListOf<String>()
        var bookmarksMigrated = 0
        var bookmarksResolved = 0
        var readingPositionMigrated = false
        var settingsMigrated = false

        // Each section below is independently try/caught — one bad key
        // must never abort the others. See DATA_MIGRATION_PLAN.md §3.

        try {
            val settings = migrateSettings(sp)
            if (settings != null) {
                SettingsRepository(context).applyMigrated(settings)
                settingsMigrated = true
            }
        } catch (e: Exception) {
            val msg = "settings: ${e.message}"
            errors += msg
            Log.e(TAG, "Settings migration failed", e)
        }

        try {
            val rawPositions = sp.getString(KEY_READING_POSITIONS, null)
            if (rawPositions != null) {
                // Full map preserved verbatim first, before any parsing
                // that could throw — see UserPrefs.saveRawLegacyReadingPositions doc.
                userPrefs.saveRawLegacyReadingPositions(rawPositions)
                val latest = pickLatestReadingPosition(rawPositions)
                if (latest != null) {
                    userPrefs.saveContinue(resolveContinuePos(context, latest))
                    readingPositionMigrated = true
                }
            }
        } catch (e: Exception) {
            val msg = "reading positions: ${e.message}"
            errors += msg
            Log.e(TAG, "Reading-position migration failed", e)
        }

        try {
            val rawBookmarks = sp.getString(KEY_BOOKMARKS, null)
            if (rawBookmarks != null) {
                val resolved = parseLegacyBookmarks(rawBookmarks).map { resolveBookmark(context, it) }
                userPrefs.addMigratedBookmarks(resolved)
                bookmarksMigrated = resolved.size
                bookmarksResolved = resolved.count { it.itemId != -1 }
            }
        } catch (e: Exception) {
            val msg = "bookmarks: ${e.message}"
            errors += msg
            Log.e(TAG, "Bookmark migration failed", e)
        }

        // Marked done regardless of per-section errors: every section
        // above is safe to have skipped (nothing destructive happens on
        // skip — legacy data is never deleted by this engine, on-device or
        // otherwise), and re-attempting the whole engine on every app
        // launch after a partial failure would just repeat the same
        // failure forever with no new information. A real retry, if one
        // section needs it, is: bump SCHEMA_VERSION.
        userPrefs.markLegacyMigrationDone(SCHEMA_VERSION)

        val result = MigrationResult(
            ran = true,
            sourceFile = fileName,
            bookmarksMigrated = bookmarksMigrated,
            bookmarksResolved = bookmarksResolved,
            readingPositionMigrated = readingPositionMigrated,
            settingsMigrated = settingsMigrated,
            errors = errors
        )
        Log.i(TAG, "Legacy migration finished: $result")
        return result
    }

    private fun findLegacySharedPreferences(context: Context): Pair<String, SharedPreferences>? {
        for (name in CANDIDATE_PREFS_FILES) {
            val sp = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val looksReal = sp.contains(KEY_BOOKMARKS) ||
                sp.contains(KEY_READING_POSITIONS) ||
                SETTINGS_KEYS.any { sp.contains(it) }
            if (looksReal) return name to sp
        }
        return null
    }

    // ── Settings ─────────────────────────────────────────────────────

    /**
     * Pure function of a SharedPreferences snapshot — the actual
     * read-from-disk part is not independently unit-testable without
     * Robolectric (not in this project's dependencies), but this
     * translation logic is exercised in
     * LegacyMigrationEngineTest via a fake SharedPreferences.
     */
    internal fun migrateSettings(sp: SharedPreferences): ReaderSettings? {
        if (SETTINGS_KEYS.none { sp.contains(it) }) return null
        val d = ReaderSettings()
        return ReaderSettings(
            fontSize = sp.getString("fontSize", null)?.toIntOrNull() ?: d.fontSize,
            fontFamily = sp.getString("fontFamily", null) ?: d.fontFamily,
            theme = sp.getString("theme", null) ?: d.theme,
            accentTheme = sp.getString("accentTheme", null) ?: d.accentTheme,
            lineHeight = sp.getString("lineHeight", null) ?: d.lineHeight,
            pageMargin = sp.getString("pageMargin", null) ?: d.pageMargin,
            justifyText = sp.getString("justifyText", null)?.toBooleanStrictOrNull() ?: d.justifyText,
            keepAwake = sp.getString("keepAwake", null)?.toBooleanStrictOrNull() ?: d.keepAwake,
            language = sp.getString("language", null) ?: d.language,
            script = sp.getString("script", null) ?: d.script,
            transliteration = sp.getString("transliteration", null)?.toBooleanStrictOrNull() ?: d.transliteration
        )
    }

    // ── Reading position ────────────────────────────────────────────

    internal data class LegacyPosition(
        val scriptureKey: String,
        val hash: String,
        val title: String?,
        val scrollPercent: Int?,
        val updatedAt: Long
    )

    /** Pure JSON parsing — testable without Context/SharedPreferences. */
    internal fun parseLegacyReadingPositions(json: String): List<LegacyPosition> {
        val obj = JSONObject(json)
        val out = mutableListOf<LegacyPosition>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val o = obj.optJSONObject(k) ?: continue
            out += LegacyPosition(
                scriptureKey = k,
                hash = o.optString("hash"),
                title = if (o.has("title")) o.optString("title") else null,
                scrollPercent = if (o.has("scrollPercent")) o.optInt("scrollPercent") else null,
                updatedAt = o.optLong("updatedAt")
            )
        }
        return out
    }

    /** Legacy keeps one slot per scripture; Kotlin's ContinuePos is single-slot today — pick the most recent. */
    internal fun pickLatestReadingPosition(json: String): LegacyPosition? =
        parseLegacyReadingPositions(json).maxByOrNull { it.updatedAt }

    private suspend fun resolveContinuePos(context: Context, pos: LegacyPosition): UserPrefs.ContinuePos {
        val id = resolveHash(context, pos.hash)
        return UserPrefs.ContinuePos(
            kind = id.kind,
            corpusId = id.corpusId,
            itemId = id.itemId,
            label = pos.title ?: pos.hash,
            scrollPercent = pos.scrollPercent,
            legacyHash = pos.hash
        )
    }

    // ── Bookmarks ────────────────────────────────────────────────────

    internal data class LegacyBookmark(
        val id: String?,
        val hash: String,
        val title: String?,
        val note: String?,
        val scrollPercent: Int?,
        val createdAt: Long
    )

    /** Pure JSON parsing — testable without Context/SharedPreferences. */
    internal fun parseLegacyBookmarks(json: String): List<LegacyBookmark> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            LegacyBookmark(
                id = if (o.has("id")) o.optString("id") else null,
                hash = o.optString("hash"),
                title = if (o.has("title")) o.optString("title") else null,
                note = if (o.has("note")) o.optString("note") else null,
                scrollPercent = if (o.has("scrollPercent")) o.optInt("scrollPercent") else null,
                createdAt = o.optLong("createdAt")
            )
        }
    }

    private suspend fun resolveBookmark(context: Context, b: LegacyBookmark): UserPrefs.Bookmark {
        val id = resolveHash(context, b.hash)
        return UserPrefs.Bookmark(
            kind = id.kind,
            corpusId = id.corpusId,
            itemId = id.itemId,
            label = b.title ?: b.hash,
            snippet = "",
            savedAt = if (b.createdAt > 0) b.createdAt else System.currentTimeMillis(),
            note = b.note,
            scrollPercent = b.scrollPercent,
            legacyId = b.id,
            legacyHash = b.hash
        )
    }

    // ── Hash resolution ──────────────────────────────────────────────

    internal data class KtIdentity(val kind: String, val corpusId: Int, val itemId: Int)

    /**
     * Resolves a legacy router hash to (kind, corpusId, itemId), with -1
     * sentinels for anything not confidently resolvable today. See this
     * class's doc comment for exactly what's resolved and why. Never
     * throws on a malformed/unrecognized hash — falls through to
     * kind="unknown" so one bad record can't fail the whole batch (the
     * caller in [runIfNeeded] also isolates per-section, this is
     * per-record on top of that).
     */
    private suspend fun resolveHash(context: Context, rawHash: String): KtIdentity {
        val h = rawHash.removePrefix("#").removePrefix("/")
        val parts = h.split("/")

        return try {
            when {
                parts.getOrNull(0) == "mantra" && parts.size >= 3 ->
                    resolveVedaMantra(context, vedaCode = parts[1], ref = parts.drop(2).joinToString("/"))

                parts.getOrNull(0) == "ramayana" && parts.getOrNull(1) == "shloka" && parts.size >= 3 ->
                    resolveRamayanaShloka(context, ref = parts[2])

                parts.getOrNull(0) == "mahabharata" && parts.getOrNull(1) == "parba" ->
                    resolveMahabharataAdhyaya(context, parts)

                parts.getOrNull(0) == "library" -> KtIdentity("library", -1, -1)

                else -> KtIdentity("unknown", -1, -1)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Hash resolution failed for '$rawHash', preserving unresolved", e)
            KtIdentity(parts.getOrNull(0) ?: "unknown", -1, -1)
        }
    }

    private suspend fun resolveVedaMantra(context: Context, vedaCode: String, ref: String): KtIdentity {
        val dao = CoreDatabase.getInstance(context).vedaDao()
        val veda = dao.getVedaByCode(vedaCode) ?: return KtIdentity("veda", -1, -1)
        val vedaId = veda.id ?: return KtIdentity("veda", -1, -1)
        val mantra = dao.getMantraByRef(vedaId, ref) ?: return KtIdentity("veda", vedaId, -1)
        return KtIdentity("veda", vedaId, mantra.id ?: -1)
    }

    /**
     * Legacy ref format "K<kandaId>.S<sargaId>.<shlokaId>" — all raw row
     * ids, verified byte-exact against ramayana.js's ramGetShlokaByRef
     * regex (^K(\d+)\.S(\d+)\.(\d+)$) and its query. Reads ramayana_core.db
     * — a bundled APK asset, so this never depends on network or an
     * installed pack (unlike Mahabharata below).
     */
    internal val ramayanaRefPattern = Regex("^K(\\d+)\\.S(\\d+)\\.(\\d+)$")

    private suspend fun resolveRamayanaShloka(context: Context, ref: String): KtIdentity {
        val m = ramayanaRefPattern.matchEntire(ref) ?: return KtIdentity("ramayana", -1, -1)
        val (kandaId, sargaId, shlokaId) = m.destructured
        val dao = RamayanaCoreDatabase.getInstance(context).ramayanaDao()
        val shloka = dao.getShlokaByRef(kandaId.toInt(), sargaId.toInt(), shlokaId.toInt())
            ?: return KtIdentity("ramayana", kandaId.toIntOrNull() ?: -1, -1)
        return KtIdentity("ramayana", shloka.kandaId, shloka.id ?: -1)
    }

    /**
     * Legacy hash is "#/mahabharata/parba/<legacyId>/adhyay/<adhyayId>" —
     * <legacyId> is 301..318 (MahabharataManifest.parbaNoFromLegacyId
     * converts to Kotlin's own 1..18 parbaNo scheme, verified against
     * legacy source — see that function's doc). <adhyayId> is a raw row
     * id from the per-parba pack's own adhyayas table.
     *
     * itemId is resolved only if that parba's pack is ALREADY downloaded
     * on this device (PackDownloadManager.isDownloaded is a local
     * file-existence check, no network call) — migration must never
     * trigger a pack download. If not downloaded, corpusId (parbaNo) is
     * still resolved — that needs no DB or pack access at all — and
     * itemId stays -1 with legacyHash preserved.
     */
    private suspend fun resolveMahabharataAdhyaya(context: Context, parts: List<String>): KtIdentity {
        val legacyParbaId = parts.getOrNull(2)?.toIntOrNull() ?: return KtIdentity("mahabharata", -1, -1)
        val parbaNo = MahabharataManifest.parbaNoFromLegacyId(legacyParbaId)
            ?: return KtIdentity("mahabharata", -1, -1)
        val parbaInfo = MahabharataManifest.byParbaNo(parbaNo)
            ?: return KtIdentity("mahabharata", parbaNo, -1)

        val legacyAdhyayId = parts.getOrNull(4)?.toIntOrNull()
        if (legacyAdhyayId == null || !MahabharataRepository.isDownloaded(context, parbaInfo)) {
            // Pack not on this device (or hash didn't carry an adhyay
            // segment, e.g. a parba-level position) — resolve corpusId
            // only, never fetch the pack just to migrate a bookmark.
            return KtIdentity("mahabharata", parbaNo, -1)
        }

        val adhyayas = MahabharataRepository
            .getAdhyayas(context, parbaInfo)
            .getOrNull()
            ?: return KtIdentity("mahabharata", parbaNo, -1)
        val found = adhyayas.any { it.id == legacyAdhyayId }
        return KtIdentity("mahabharata", parbaNo, if (found) legacyAdhyayId else -1)
    }
}
