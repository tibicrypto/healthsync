package com.healthautoexport.domain.model

/**
 * Phân loại bản chất của một Health_Metric để `Aggregator` chọn đúng phép tóm tắt
 * (Requirements 8.4, 8.5). Gắn tĩnh cho mỗi [HealthMetricType] qua `MetricCatalog`.
 */
enum class MetricKind {
    /** Đại lượng cộng dồn theo thời gian (bước, quãng đường, năng lượng...). Tổng hợp = sum. */
    CUMULATIVE,

    /** Phép đo tại một thời điểm (nhịp tim, SpO2, cân nặng...). Tổng hợp = {min, avg, max, count}. */
    INSTANTANEOUS,
}
