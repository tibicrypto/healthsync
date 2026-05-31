package com.healthautoexport.domain.port

import com.healthautoexport.domain.model.DataSourceId
import com.healthautoexport.domain.model.DateRange
import com.healthautoexport.domain.model.HealthMetricType
import com.healthautoexport.domain.model.WorkoutType

/**
 * Port trừu tượng hóa một Data_Source (Health_Connect hoặc Huawei_Health_Kit), che giấu khác
 * biệt nền tảng (Requirements 1, 2, 3, 4, 5, 6).
 *
 * Mỗi hiện thực tự lo kiểm tra khả dụng, ánh xạ loại bản ghi sang [com.healthautoexport.domain.model.UnifiedRecord]
 * đã chuẩn hóa đơn vị (Requirement 4.2) và gắn [com.healthautoexport.domain.model.DataSourceId]
 * gốc (Requirement 4.5). Đây là Port thuần domain; bộ điều hợp nằm ở tầng `:data`.
 */
interface HealthDataSource {

    /** Định danh ổn định của nguồn (dùng để sắp xếp/tie-break dedup theo bảng chữ cái). */
    val id: DataSourceId

    /** Kiểm tra khả dụng (SDK cài đặt, dịch vụ sẵn sàng) (Requirements 1.1, 2.1). */
    suspend fun availability(): SourceAvailability

    /** Các Health_Metric mà nguồn có thể cung cấp trên thiết bị hiện tại (Requirement 4.6). */
    suspend fun supportedMetrics(): Set<HealthMetricType>

    /**
     * Đọc các bản ghi đã chuẩn hóa trong [range] cho [metrics]/[workouts] đã chọn
     * (Requirements 1.5, 3.x, 5.x, 6.x).
     */
    suspend fun readRecords(
        metrics: Set<HealthMetricType>,
        workouts: Set<WorkoutType>,
        range: DateRange,
    ): SourceReadResult
}
