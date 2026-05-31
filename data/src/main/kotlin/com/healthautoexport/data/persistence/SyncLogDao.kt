package com.healthautoexport.data.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO Room cho bảng `sync_log` (Requirement 23).
 *
 * Cung cấp các thao tác thô; thứ tự hiển thị (Requirement 23.3) và thu hồi theo giới hạn
 * (Requirement 23.5) được áp ở [com.healthautoexport.data.persistence.SyncLogRepositoryImpl]
 * bằng các chính sách thuần của domain để giữ một nguồn sự thật duy nhất cho logic này.
 *
 * [observeAll] sắp xếp ở mức SQL (giảm dần `completionUtc`, tie-break `startUtc`) để khớp với
 * [com.healthautoexport.domain.logic.SyncLogOrdering]; tầng repository vẫn áp comparator domain
 * lần cuối nhằm bảo đảm thứ tự nhất quán kể cả với các mục `completionUtc` null.
 */
@Dao
interface SyncLogDao {

    /**
     * Luồng các mục Sync_Log; sắp xếp sơ bộ ở SQL theo thứ tự hiển thị. Mục `completionUtc` null
     * (job đang chạy) được coi là mới nhất nên đứng đầu (Requirement 23.3).
     */
    @Query(
        """
        SELECT * FROM sync_log
        ORDER BY (completionUtc IS NULL) DESC, completionUtc DESC, startUtc DESC
        """,
    )
    fun observeAll(): Flow<List<SyncLogEntity>>

    /** Ảnh chụp toàn bộ mục log (dùng để tính thu hồi sau khi chèn). */
    @Query("SELECT * FROM sync_log")
    suspend fun getAll(): List<SyncLogEntity>

    /** Số lượng mục log hiện có. */
    @Query("SELECT COUNT(*) FROM sync_log")
    suspend fun count(): Int

    /** Chèn một mục log mới (thay thế nếu trùng khóa chính) (Requirement 23.1). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SyncLogEntity)

    /** Xóa các mục log theo danh sách [ids] (dùng cho thu hồi, Requirement 23.5). */
    @Query("DELETE FROM sync_log WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    /** Xóa toàn bộ Sync_Log (Requirements 22.6, 23.6). */
    @Query("DELETE FROM sync_log")
    suspend fun deleteAll()
}
