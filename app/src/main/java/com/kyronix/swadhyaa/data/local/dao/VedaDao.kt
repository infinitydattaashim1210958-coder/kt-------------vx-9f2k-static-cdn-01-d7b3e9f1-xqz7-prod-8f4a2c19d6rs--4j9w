package com.kyronix.swadhyaa.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.kyronix.swadhyaa.data.local.entity.MantraEntity
import com.kyronix.swadhyaa.data.local.entity.ScholarEntity
import com.kyronix.swadhyaa.data.local.entity.ScholarFieldEntity
import com.kyronix.swadhyaa.data.local.entity.VedaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VedaDao {

    @Query("SELECT * FROM vedas ORDER BY id")
    fun observeVedas(): Flow<List<VedaEntity>>

    @Query("SELECT * FROM vedas WHERE code = :code LIMIT 1")
    suspend fun getVedaByCode(code: String): VedaEntity?

    @Query("SELECT * FROM vedas WHERE id = :id LIMIT 1")
    suspend fun getVedaById(id: Int): VedaEntity?

    @Query(
        """
        SELECT * FROM mantras
        WHERE veda_id = :vedaId
        ORDER BY level1, level2, level3, mantra_no
        """
    )
    suspend fun getMantras(vedaId: Int): List<MantraEntity>

    @Query(
        """
        SELECT * FROM mantras
        WHERE veda_id = :vedaId AND mantra_ref_id = :ref
        LIMIT 1
        """
    )
    suspend fun getMantraByRef(vedaId: Int, ref: String): MantraEntity?

    @Query(
        """
        SELECT * FROM mantras
        WHERE veda_id = :vedaId
          AND (:level1 IS NULL OR level1 = :level1)
          AND (:level2 IS NULL OR level2 = :level2)
          AND (:level3 IS NULL OR level3 = :level3)
        ORDER BY level1, level2, level3, mantra_no
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getMantraRange(
        vedaId: Int,
        level1: Int?,
        level2: Int?,
        level3: Int?,
        limit: Int,
        offset: Int
    ): List<MantraEntity>

    @Query("SELECT COUNT(*) FROM mantras WHERE veda_id = :vedaId")
    suspend fun getMantraCount(vedaId: Int): Int

    @Query(
        """
        SELECT DISTINCT level1 FROM mantras
        WHERE veda_id = :vedaId AND level1 IS NOT NULL
        ORDER BY level1
        """
    )
    suspend fun getLevel1List(vedaId: Int): List<Int>

    @Query(
        """
        SELECT DISTINCT level2 FROM mantras
        WHERE veda_id = :vedaId AND level1 = :level1 AND level2 IS NOT NULL
        ORDER BY level2
        """
    )
    suspend fun getLevel2List(vedaId: Int, level1: Int): List<Int>

    @Query(
        """
        SELECT DISTINCT level3 FROM mantras
        WHERE veda_id = :vedaId AND level1 = :level1 AND level2 = :level2
          AND level3 IS NOT NULL
        ORDER BY level3
        """
    )
    suspend fun getLevel3List(vedaId: Int, level1: Int, level2: Int): List<Int>

    @Query(
        """
        SELECT DISTINCT mantra_no FROM mantras
        WHERE veda_id = :vedaId
          AND (:level1 IS NULL OR level1 = :level1)
          AND (:level2 IS NULL OR level2 = :level2)
          AND (:level3 IS NULL OR level3 = :level3)
          AND mantra_no IS NOT NULL
        ORDER BY mantra_no
        """
    )
    suspend fun getMantraNoList(
        vedaId: Int,
        level1: Int?,
        level2: Int?,
        level3: Int?
    ): List<Int>

    @Query(
        """
        SELECT * FROM mantras
        WHERE veda_id = :vedaId
          AND (:level1 IS NULL OR level1 = :level1)
          AND (:level2 IS NULL OR level2 = :level2)
          AND (:level3 IS NULL OR level3 = :level3)
          AND mantra_no = :mantraNo
        LIMIT 1
        """
    )
    suspend fun getMantraAt(
        vedaId: Int,
        level1: Int?,
        level2: Int?,
        level3: Int?,
        mantraNo: Int
    ): MantraEntity?

    @Query(
        """
        SELECT * FROM mantras
        WHERE veda_id = :vedaId
        ORDER BY level1, level2, level3, mantra_no, id
        LIMIT 1
        """
    )
    suspend fun getFirstMantra(vedaId: Int): MantraEntity?

    @Query(
        """
        SELECT * FROM mantras
        WHERE veda_id = :vedaId AND id > :currentId
        ORDER BY id LIMIT 1
        """
    )
    suspend fun getNextMantra(vedaId: Int, currentId: Int): MantraEntity?

    @Query(
        """
        SELECT * FROM mantras
        WHERE veda_id = :vedaId AND id < :currentId
        ORDER BY id DESC LIMIT 1
        """
    )
    suspend fun getPrevMantra(vedaId: Int, currentId: Int): MantraEntity?

    @Query("SELECT * FROM scholars WHERE veda_id = :vedaId ORDER BY display_order, name")
    suspend fun getScholarsForVeda(vedaId: Int): List<ScholarEntity>

    @Query("SELECT * FROM scholar_fields WHERE scholar_id = :scholarId ORDER BY display_order")
    suspend fun getFieldsForScholar(scholarId: Int): List<ScholarFieldEntity>

    @Query(
        """
        SELECT s.* FROM scholars s
        INNER JOIN bhashya_presence bp ON bp.scholar_id = s.id
        WHERE bp.mantra_id = :mantraId
        ORDER BY s.display_order, s.name
        """
    )
    suspend fun getScholarsForMantra(mantraId: Int): List<ScholarEntity>
}
