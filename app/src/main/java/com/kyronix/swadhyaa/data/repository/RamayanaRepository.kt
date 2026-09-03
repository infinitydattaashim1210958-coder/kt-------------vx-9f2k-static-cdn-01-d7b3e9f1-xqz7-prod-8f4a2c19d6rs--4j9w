package com.kyronix.swadhyaa.data.repository

import com.kyronix.swadhyaa.data.local.RamayanaCoreDatabase
import com.kyronix.swadhyaa.data.local.entity.KandaEntity

data class ShlokaContent(
    val id: Int,
    val kandaId: Int,
    val kandaName: String,
    val sargaId: Int,
    val sargaChapter: Int,
    val sargaName: String?,
    val shlokaNo: Int,       // position within the sarga (1-indexed; no explicit column in DB)
    val sanskrit: String,
    val refLabel: String
)

class RamayanaRepository(private val db: RamayanaCoreDatabase) {
    private val dao get() = db.ramayanaDao()

    suspend fun getKandas(): List<KandaEntity> = dao.getKandasOnce()

    suspend fun openFirst(): ShlokaContent? {
        val kanda = dao.getKandasOnce().minByOrNull { it.id ?: Int.MAX_VALUE } ?: return null
        return openKanda(kanda.id ?: return null)
    }

    suspend fun openKanda(kandaId: Int): ShlokaContent? {
        val sargas = dao.getSargas(kandaId)
        val sarga = sargas.firstOrNull() ?: return null
        val shloka = dao.getFirstShloka(kandaId, sarga.id ?: return null) ?: return null
        return toContent(shloka)
    }

    suspend fun jumpSarga(kandaId: Int, sargaId: Int): ShlokaContent? {
        val shloka = dao.getFirstShloka(kandaId, sargaId) ?: return null
        return toContent(shloka)
    }

    /** Resolves a sarga's display chapter-number back to its row id (for jumpSarga). */
    suspend fun findSargaId(kandaId: Int, chapter: Int): Int? =
        dao.getSargas(kandaId).firstOrNull { it.chapter == chapter }?.id

    /** Jump to the nth shloka (1-indexed) within the given sarga. */
    suspend fun jumpShlokaNo(sargaId: Int, shlokaNo: Int): ShlokaContent? {
        val list = dao.getShlokasForSarga(sargaId)
        val shloka = list.getOrNull(shlokaNo - 1) ?: return null
        return toContent(shloka)
    }

    suspend fun next(kandaId: Int, currentId: Int): ShlokaContent? =
        dao.getNextShloka(kandaId, currentId)?.let { toContent(it) }

    suspend fun prev(kandaId: Int, currentId: Int): ShlokaContent? =
        dao.getPrevShloka(kandaId, currentId)?.let { toContent(it) }

    suspend fun getSargaOptions(kandaId: Int): List<Int> =
        dao.getSargas(kandaId).map { it.chapter }

    suspend fun getShlokaNoOptions(sargaId: Int): List<Int> {
        val count = dao.getShlokasForSarga(sargaId).size
        return (1..count).toList()
    }

    private suspend fun toContent(s: com.kyronix.swadhyaa.data.local.entity.ShlokaEntity): ShlokaContent {
        val kanda = dao.getKanda(s.kandaId)
        val sarga = dao.getSarga(s.sargaId)
        val siblings = dao.getShlokasForSarga(s.sargaId)
        val position = siblings.indexOfFirst { it.id == s.id }.let { if (it >= 0) it + 1 else 1 }
        val kandaName = kanda?.name ?: ""
        val chapter = sarga?.chapter ?: 0
        return ShlokaContent(
            id = s.id ?: 0,
            kandaId = s.kandaId,
            kandaName = kandaName,
            sargaId = s.sargaId,
            sargaChapter = chapter,
            sargaName = sarga?.name,
            shlokaNo = position,
            sanskrit = s.sanskrit,
            refLabel = "$kandaName $chapter/$position"
        )
    }
}
