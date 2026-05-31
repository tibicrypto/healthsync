package com.healthautoexport.domain.model

/**
 * Mức độ chi tiết thời gian dùng để tổng hợp bản ghi (Requirement 8.1).
 *
 * `Aggregator` căn khung theo ranh giới lịch theo múi giờ thiết bị (Requirement 8.3);
 * riêng [SECOND] trả về bản ghi thô không tổng hợp (Requirement 8.7).
 */
enum class AggregationPeriod {
    SECOND,
    MINUTE,
    HOUR,
    DAY,
    WEEK,
    MONTH,
    YEAR,
}
