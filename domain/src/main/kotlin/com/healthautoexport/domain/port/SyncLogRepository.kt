package com.healthautoexport.domain.port

import com.healthautoexport.domain.model.SyncLogEntry
import kotlinx.coroutines.flow.Flow

/**
 * Port lưu trữ Sync_Log (Requirement 23).
 *
 * Hiện thực bằng Room ở tầng dữ liệu (task 16.1). Danh sách hiển thị giảm dần theo
 * `completionUtc`, tie-break giảm dần theo `startUtc` (Requirement 23.3); khi vượt giới hạn cấu
 * hình (50–5000, mặc định 500) xóa mục cũ nhất cho tới khi đạt giới hạn (Requirement 23.5). Mục
 * log chỉ chứa metadata, không chứa dữ liệu thô (Requirement 23.4).
 */
interface SyncLogRepository {

    /** Luồng các mục Sync_Log theo thứ tự hiển thị (Requirement 23.3). */
    fun observeAll(): Flow<List<SyncLogEntry>>

    /**
     * Ghi một mục log mới rồi áp chính sách eviction theo [maxEntries] (Requirement 23.5).
     *
     * @param entry mục log cần ghi.
     * @param maxEntries giới hạn số mục giữ lại (50..5000).
     */
    suspend fun append(entry: SyncLogEntry, maxEntries: Int)

    /** Xóa toàn bộ Sync_Log (Data_Wipe, Requirement 22.6). */
    suspend fun deleteAll()
}
