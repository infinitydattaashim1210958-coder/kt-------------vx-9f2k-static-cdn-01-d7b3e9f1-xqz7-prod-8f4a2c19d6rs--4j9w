package com.kyronix.swadhyaa.data.repository

import com.kyronix.swadhyaa.data.local.CoreDatabase
import com.kyronix.swadhyaa.domain.model.VedaSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Single source of truth for Veda data.
 * All Room access stays on Dispatchers.IO.
 * Does not modify the underlying database.
 */
class VedaRepository(
    private val db: CoreDatabase
) {
    private val vedaDao get() = db.vedaDao()

    /**
     * Emits the list of Vedas with their mantra counts.
     * Reads only from the verified production core.db.
     */
    fun observeVedaSummaries(): Flow<List<VedaSummary>> = flow {
        val vedas = vedaDao.observeVedas()
        // Collect the Flow from DAO and enrich with counts
        vedas.collect { list ->
            val summaries = list.map { veda ->
                val count = vedaDao.getMantraCount(veda.id)
                VedaSummary(
                    id = veda.id,
                    code = veda.code,
                    name = veda.name,
                    mantraCount = count
                )
            }
            emit(summaries)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * One-shot load for simple screens.
     */
    suspend fun getVedaSummaries(): List<VedaSummary> = withContext(Dispatchers.IO) {
        // Use a direct query path to avoid nested Flow complexity on first load
        val vedas = db.openHelper.readableDatabase.query("SELECT id, code, name FROM vedas ORDER BY id").use { c ->
            val result = mutableListOf<Triple<Int, String, String>>()
            while (c.moveToNext()) {
                result += Triple(c.getInt(0), c.getString(1), c.getString(2))
            }
            result
        }
        vedas.map { (id, code, name) ->
            val count = vedaDao.getMantraCount(id)
            VedaSummary(id = id, code = code, name = name, mantraCount = count)
        }
    }

    suspend fun getTotalMantraCount(): Int = withContext(Dispatchers.IO) {
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM mantras").use { c ->
            c.moveToFirst()
            c.getInt(0)
        }
    }
}
