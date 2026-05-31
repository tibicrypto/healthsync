package com.healthautoexport.domain.model

import java.math.BigDecimal

/**
 * Dung sai loại trùng cho một Health_Metric (Requirement 7.2).
 *
 * Hai bản ghi cùng metric được coi là trùng nếu chênh lệch dấu thời gian ≤ [timeSeconds] **và**
 * chênh lệch giá trị ≤ [valueMagnitude] (Requirement 7.3). Cả hai dung sai SHALL không âm
 * (Requirement 7.2).
 *
 * @property timeSeconds dung sai dấu thời gian tính bằng giây; SHALL ≥ 0 (Requirement 7.2).
 * @property valueMagnitude dung sai độ lớn giá trị theo đơn vị canonical của metric; SHALL ≥ 0
 *   (Requirement 7.2).
 */
data class DuplicateTolerance(
    val timeSeconds: Long,
    val valueMagnitude: BigDecimal,
) {
    init {
        require(timeSeconds >= 0) {
            "DuplicateTolerance.timeSeconds phải ≥ 0 (Requirement 7.2), nhận được $timeSeconds"
        }
        require(valueMagnitude.signum() >= 0) {
            "DuplicateTolerance.valueMagnitude phải ≥ 0 (Requirement 7.2), nhận được $valueMagnitude"
        }
    }
}

/**
 * Bảng tra cứu dung sai loại trùng theo từng [HealthMetricType] (Requirement 7.2).
 *
 * `Data_Merger` dùng bảng này để quyết định hai bản ghi có trùng hay không. Giá trị mặc định
 * cho mỗi metric đến từ [MetricCatalog.spec] (`defaultTolerance`); người dùng/cấu hình có thể
 * ghi đè qua bảng này.
 */
typealias DuplicateToleranceTable = Map<HealthMetricType, DuplicateTolerance>
