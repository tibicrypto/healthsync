package com.healthautoexport.data.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO Room cho bảng `automations` (Requirements 14.5, 14.7).
 *
 * CRUD cho [AutomationEntity]. [findByNameIgnoreCase] khớp theo cột [AutomationEntity.nameLower]
 * (đã được thường-hóa khi ánh xạ) để phát hiện trùng tên không phân biệt hoa/thường
 * (Requirement 14.7); chỉ mục unique trên cột đó là lớp bảo vệ cuối cùng.
 */
@Dao
interface AutomationDao {

    /** Luồng toàn bộ Automation theo tên (ổn định), phát lại khi dữ liệu đổi. */
    @Query("SELECT * FROM automations ORDER BY nameLower ASC")
    fun observeAll(): Flow<List<AutomationEntity>>

    /** Lấy Automation theo khóa chính, hoặc `null` nếu không có. */
    @Query("SELECT * FROM automations WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): AutomationEntity?

    /**
     * Tìm theo tên đã thường-hóa (không phân biệt hoa/thường) để phát hiện trùng (Req 14.7).
     *
     * @param nameLower tên đã thường-hóa của Automation cần tra.
     */
    @Query("SELECT * FROM automations WHERE nameLower = :nameLower LIMIT 1")
    suspend fun findByNameLower(nameLower: String): AutomationEntity?

    /** Thêm mới hoặc thay thế (upsert) theo khóa chính (Requirement 14.5). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AutomationEntity)

    /** Xóa Automation theo [id] (Requirements 14.9, 22.6). */
    @Query("DELETE FROM automations WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Xóa toàn bộ Automation (Data_Wipe, Requirement 22.6). */
    @Query("DELETE FROM automations")
    suspend fun deleteAll()
}
