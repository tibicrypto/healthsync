package com.healthautoexport.domain.port

import com.healthautoexport.domain.model.DataSourceId
import com.healthautoexport.domain.model.SourcePriority
import kotlinx.coroutines.flow.Flow

/**
 * Port lưu trữ lựa chọn bật/tắt mỗi Data_Source và thứ hạng ưu tiên nguồn (Requirements 3.1, 3.2,
 * 7.8).
 *
 * Hiện thực bằng DataStore ở tầng dữ liệu (task 15.2); lựa chọn được khôi phục đúng qua các phiên
 * làm việc (Requirement 3.2).
 */
interface SourceToggleStore {

    /** Luồng tập các Data_Source hiện đang **bật** (Requirements 3.1, 3.2). */
    fun observeEnabledSources(): Flow<Set<DataSourceId>>

    /** Đọc tập Data_Source đang bật tại thời điểm gọi (dùng trong Export_Job). */
    suspend fun enabledSources(): Set<DataSourceId>

    /** Bật/tắt một [source] một cách độc lập (Requirement 3.1). */
    suspend fun setEnabled(source: DataSourceId, enabled: Boolean)

    /** Đọc thứ hạng ưu tiên nguồn để giải quyết trùng lặp (Requirements 7.4, 7.8). */
    suspend fun sourcePriority(): SourcePriority

    /** Lưu thứ hạng ưu tiên nguồn (Requirement 7.8). */
    suspend fun setSourcePriority(priority: SourcePriority)
}
