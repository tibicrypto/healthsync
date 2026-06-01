package com.healthautoexport.domain.usecase

import com.healthautoexport.domain.model.AggregationPeriod
import com.healthautoexport.domain.model.DateRange
import com.healthautoexport.domain.model.DestinationType
import com.healthautoexport.domain.model.ExportFormat
import com.healthautoexport.domain.model.MetricSelection
import com.healthautoexport.domain.port.DestinationConfig

/**
 * Cấu hình đầy đủ cho một lần chạy Export_Job, dùng chung cho cả Quick_Export và xuất theo lịch
 * (Requirements 13.1, 15.x). `RunExportJobUseCase` nhận đối tượng này, phân giải pipeline và ghi
 * đúng một mục Sync_Log.
 *
 * @property selection các metric/workout người dùng chọn (Requirements 4.4, 5.7). Một lựa chọn
 *   rỗng (0 metric và 0 workout) bị từ chối trước khi đọc (Requirement 4.8).
 * @property format định dạng xuất JSON / CSV / GPX (Requirement 13.1).
 * @property period mức tổng hợp dùng cho `Aggregator` (Requirement 8.1).
 * @property dateRange khoảng thời gian đã phân giải để đọc/lọc (Requirement 9.x).
 * @property destinationType loại Destination đích (Requirements 14.2, 16–21).
 * @property destinationConfig cấu hình Destination (không chứa bí mật); `null` nghĩa là **chưa
 *   cấu hình Destination** — khi đó `NetworkEgressGuard` chặn mọi egress (Requirement 22.4) và
 *   job kết thúc thất bại mà không gửi gì.
 * @property automationId định danh Automation nếu job thuộc một Automation theo lịch; `null` cho
 *   Quick_Export (Requirement 23.1).
 * @property maxSyncLogEntries giới hạn số mục Sync_Log giữ lại sau khi ghi (50..5000, mặc định
 *   500) (Requirement 23.5).
 */
data class ExportJobConfig(
    val selection: MetricSelection,
    val format: ExportFormat,
    val period: AggregationPeriod,
    val dateRange: DateRange,
    val destinationType: DestinationType,
    val destinationConfig: DestinationConfig?,
    val automationId: String? = null,
    val maxSyncLogEntries: Int = DEFAULT_MAX_SYNC_LOG_ENTRIES,
) {
    init {
        require(maxSyncLogEntries in MIN_SYNC_LOG_ENTRIES..MAX_SYNC_LOG_ENTRIES) {
            "maxSyncLogEntries phải trong $MIN_SYNC_LOG_ENTRIES..$MAX_SYNC_LOG_ENTRIES " +
                "(Requirement 23.5), nhận được $maxSyncLogEntries"
        }
    }

    companion object {
        /** Giới hạn Sync_Log mặc định (Requirement 23.5). */
        const val DEFAULT_MAX_SYNC_LOG_ENTRIES: Int = 500

        /** Giới hạn Sync_Log tối thiểu cấu hình được (Requirement 23.5). */
        const val MIN_SYNC_LOG_ENTRIES: Int = 50

        /** Giới hạn Sync_Log tối đa cấu hình được (Requirement 23.5). */
        const val MAX_SYNC_LOG_ENTRIES: Int = 5000
    }
}
