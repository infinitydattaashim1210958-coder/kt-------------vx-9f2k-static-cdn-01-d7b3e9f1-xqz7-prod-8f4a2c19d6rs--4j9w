package com.kyronix.swadhyaa.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.kyronix.swadhyaa.data.local.entity.KandaEntity
import com.kyronix.swadhyaa.data.local.entity.SargaEntity
import com.kyronix.swadhyaa.data.local.entity.ShlokaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RamayanaDao {

    @Query("SELECT * FROM kandas ORDER BY id")
    fun observeKandas(): Flow<List<KandaEntity>>

    @Query("SELECT * FROM kandas WHERE id = :id LIMIT 1")
    suspend fun getKanda(id: Int): KandaEntity?

    @Query("SELECT * FROM sargas WHERE kanda_id = :kandaId ORDER BY chapter, id")
    suspend fun getSargas(kandaId: Int): List<SargaEntity>

    @Query("SELECT * FROM sargas WHERE id = :id LIMIT 1")
    suspend fun getSarga(id: Int): SargaEntity?

    @Query(
        """
        SELECT * FROM shlokas
        WHERE sarga_id = :sargaId
        ORDER BY id
        """
    )
    suspend fun getShlokasForSarga(sargaId: Int): List<ShlokaEntity>

    @Query("SELECT * FROM shlokas WHERE id = :id LIMIT 1")
    suspend fun getShloka(id: Int): ShlokaEntity?

    @Query(
        """
        SELECT * FROM shlokas
        WHERE kanda_id = :kandaId AND sarga_id = :sargaId
        ORDER BY id
        LIMIT 1
        """
    )
    suspend fun getFirstShloka(kandaId: Int, sargaId: Int): ShlokaEntity?

    @Query(
        """
        SELECT * FROM shlokas
        WHERE id > :currentId AND kanda_id = :kandaId
        ORDER BY id LIMIT 1
        """
    )
    suspend fun getNextShloka(kandaId: Int, currentId: Int): ShlokaEntity?

    @Query(
        """
        SELECT * FROM shlokas
        WHERE id < :currentId AND kanda_id = :kandaId
        ORDER BY id DESC LIMIT 1
        """
    )
    suspend fun getPrevShloka(kandaId: Int, currentId: Int): ShlokaEntity?

    @Query("SELECT COUNT(*) FROM shlokas WHERE kanda_id = :kandaId")
    suspend fun getShlokaCount(kandaId: Int): Int

}
