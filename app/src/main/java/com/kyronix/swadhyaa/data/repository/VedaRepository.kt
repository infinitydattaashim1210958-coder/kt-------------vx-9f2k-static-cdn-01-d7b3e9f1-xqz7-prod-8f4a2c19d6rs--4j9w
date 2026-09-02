package com.kyronix.swadhyaa.data.repository

import com.kyronix.swadhyaa.data.local.CoreDatabase
import com.kyronix.swadhyaa.domain.model.VedaSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Single source of truth for Veda data.
 * All Room access on Dispatchers.IO.
 */
class VedaRepository(
    private val db: CoreDatabase
) {
    private val vedaDao get() = db.vedaDao()

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
