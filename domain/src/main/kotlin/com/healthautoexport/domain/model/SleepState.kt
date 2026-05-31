package com.healthautoexport.domain.model

/**
 * Giai đoạn của một phân đoạn giấc ngủ (Requirement 6.1).
 *
 * Mỗi `MetricValue.SleepSegment` mang một `SleepState` cùng thời lượng (giây, ≥ 0).
 * [UNSPECIFIED] dùng khi Data_Source không phân loại được giai đoạn cụ thể.
 */
enum class SleepState {
    AWAKE,
    REM,
    CORE,
    DEEP,
    ASLEEP,
    IN_BED,
    UNSPECIFIED,
}
