package com.kyronix.swadhyaa.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "swadhyay_prefs")

/**
 * Continue-reading position + bookmarks.
 * Stored as JSON strings in DataStore (no Room schema change).
 */
class UserPrefs(private val context: Context) {

    private val keyContinue = stringPreferencesKey("continue_json")
    private val keyBookmarks = stringPreferencesKey("bookmarks_json")
    private val keyPacks = stringPreferencesKey("downloaded_packs_json")

    data class ContinuePos(
        val kind: String, // "veda" | "ramayana"
        val corpusId: Int,
        val itemId: Int,
        val label: String
    )

    data class Bookmark(
        val kind: String,
        val corpusId: Int,
        val itemId: Int,
        val label: String,
        val snippet: String,
        val savedAt: Long = System.currentTimeMillis()
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

    private fun ContinuePos.toJson(): String =
        JSONObject()
            .put("kind", kind)
            .put("corpusId", corpusId)
            .put("itemId", itemId)
            .put("label", label)
            .toString()

    private fun parseContinue(s: String): ContinuePos? = try {
        val o = JSONObject(s)
        ContinuePos(
            kind = o.getString("kind"),
            corpusId = o.getInt("corpusId"),
            itemId = o.getInt("itemId"),
            label = o.getString("label")
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
                savedAt = o.optLong("savedAt")
            )
        }
    } catch (_: Exception) { emptyList() }
}
