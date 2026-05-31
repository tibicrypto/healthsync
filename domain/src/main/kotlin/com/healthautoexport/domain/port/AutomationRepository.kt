package com.healthautoexport.domain.port

import com.healthautoexport.domain.model.Automation
import kotlinx.coroutines.flow.Flow

/**
 * Port lưu trữ Automation (Requirements 14.5, 14.7).
 *
 * Hiện thực bằng Room ở tầng dữ liệu (task 16.1); ràng buộc unique tên không phân biệt hoa/thường
 * được bảo đảm bởi chỉ mục cùng [findByNameIgnoreCase] (Requirement 14.7).
 */
interface AutomationRepository {

    /** Luồng danh sách Automation, phát lại khi dữ liệu thay đổi. */
    fun observeAll(): Flow<List<Automation>>

    /** Lấy Automation theo [id], hoặc `null` nếu không tồn tại. */
    suspend fun findById(id: String): Automation?

    /**
     * Tìm Automation theo tên **không phân biệt hoa/thường**, để phát hiện trùng tên
     * (Requirement 14.7); trả về `null` nếu không có.
     */
    suspend fun findByNameIgnoreCase(name: String): Automation?

    /** Thêm mới hoặc cập nhật một Automation (Requirement 14.5). */
    suspend fun upsert(automation: Automation)

    /** Xóa Automation theo [id] (Requirements 14.9, 22.6). */
    suspend fun delete(id: String)

    /** Xóa toàn bộ Automation (Data_Wipe, Requirement 22.6). */
    suspend fun deleteAll()
}
