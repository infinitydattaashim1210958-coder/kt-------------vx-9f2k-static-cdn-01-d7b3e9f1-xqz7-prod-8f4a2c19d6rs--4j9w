package com.kyronix.swadhyaa.data.repository

import com.kyronix.swadhyaa.data.local.CoreDatabase
import com.kyronix.swadhyaa.data.local.entity.MantraEntity
import com.kyronix.swadhyaa.domain.model.MantraContent
import com.kyronix.swadhyaa.domain.model.VedaSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Single source of truth for Veda data from production core.db.
 */
class VedaRepository(
    private val db: CoreDatabase
) {
    private val dao get() = db.vedaDao()

    suspend fun getVedaSummaries(): List<VedaSummary> = withContext(Dispatchers.IO) {
        val vedas = db.openHelper.readableDatabase
            .query("SELECT id, code, name FROM vedas ORDER BY id")
            .use { c ->
                val result = mutableListOf<Triple<Int, String, String>>()
                while (c.moveToNext()) {
                    result += Triple(c.getInt(0), c.getString(1), c.getString(2))
                }
                result
            }
        vedas.map { (id, code, name) ->
            VedaSummary(
                id = id,
                code = code,
                name = name,
                mantraCount = dao.getMantraCount(id)
            )
        }
    }

    suspend fun getTotalMantraCount(): Int = withContext(Dispatchers.IO) {
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM mantras").use { c ->
            c.moveToFirst()
            c.getInt(0)
        }
    }

    suspend fun getFirstMantra(vedaId: Int): MantraContent? = withContext(Dispatchers.IO) {
        val m = dao.getFirstMantra(vedaId) ?: return@withContext null
        toContent(m)
    }

    suspend fun getMantraAt(
        vedaId: Int,
        level1: Int?,
        level2: Int?,
        level3: Int?,
        mantraNo: Int
    ): MantraContent? = withContext(Dispatchers.IO) {
        val m = dao.getMantraAt(vedaId, level1, level2, level3, mantraNo) ?: return@withContext null
        toContent(m)
    }

    suspend fun getNext(vedaId: Int, currentId: Int): MantraContent? = withContext(Dispatchers.IO) {
        dao.getNextMantra(vedaId, currentId)?.let { toContent(it) }
    }

    suspend fun getPrev(vedaId: Int, currentId: Int): MantraContent? = withContext(Dispatchers.IO) {
        dao.getPrevMantra(vedaId, currentId)?.let { toContent(it) }
    }

    suspend fun getLevel1List(vedaId: Int): List<Int> = withContext(Dispatchers.IO) {
        dao.getLevel1List(vedaId)
    }

    suspend fun getLevel2List(vedaId: Int, level1: Int): List<Int> = withContext(Dispatchers.IO) {
        dao.getLevel2List(vedaId, level1)
    }

    suspend fun getLevel3List(vedaId: Int, level1: Int, level2: Int): List<Int> =
        withContext(Dispatchers.IO) {
            dao.getLevel3List(vedaId, level1, level2)
        }

    suspend fun getMantraNoList(
        vedaId: Int,
        level1: Int?,
        level2: Int?,
        level3: Int?
    ): List<Int> = withContext(Dispatchers.IO) {
        dao.getMantraNoList(vedaId, level1, level2, level3)
    }

    private suspend fun toContent(m: MantraEntity): MantraContent {
        val id = m.id ?: 0
        val veda = dao.getVedaById(m.vedaId)
        val l1 = m.level1
        val l2 = m.level2
        val l3 = m.level3
        val no = m.mantraNo
        val ref = buildString {
            append(veda?.name ?: "Veda")
            if (l1 != null) append(" $l1")
            if (l2 != null) append("/$l2")
            if (l3 != null) append("/$l3")
            if (no != null) append("/$no")
        }
        return MantraContent(
            id = id,
            vedaId = m.vedaId,
            vedaCode = veda?.code ?: "",
            vedaName = veda?.name ?: "",
            level1 = l1,
            level2 = l2,
            level3 = l3,
            mantraNo = no,
            sanskrit = m.sanskritText?.trim().orEmpty(),
            rishi = m.rishi?.trim(),
            devata = m.devata?.trim(),
            chhanda = m.chhanda?.trim(),
            refLabel = ref
        )
    }
}
