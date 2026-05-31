package com.healthautoexport.domain.model

/**
 * Lựa chọn của người dùng cho một Export_Job: tập [HealthMetricType] và tập [WorkoutType] cần
 * xuất (Requirements 4.4, 5.7).
 *
 * `MetricSelection` là đầu vào cho nhiều thành phần: `PermissionManager` yêu cầu quyền **chỉ
 * cho** các loại đã chọn (Requirements 1.2, 2.2); `Data_Reader` đọc đúng các loại này; và
 * `MetricSelectionResolver` (task 9.1) giao với tập quyền/khả dụng/hỗ trợ để ra tập hiệu lực.
 *
 * Một Export_Job hợp lệ cần chọn ít nhất một metric hoặc workout; việc xác thực "0 metric" bị
 * từ chối (Requirement 4.8) do tầng use case/UI thực hiện, không ép buộc tại kiểu dữ liệu này.
 *
 * @property metrics tập loại Health_Metric được chọn.
 * @property workouts tập loại Workout được chọn.
 */
data class MetricSelection(
    val metrics: Set<HealthMetricType> = emptySet(),
    val workouts: Set<WorkoutType> = emptySet(),
) {
    /** `true` khi không có metric lẫn workout nào được chọn (lựa chọn rỗng). */
    val isEmpty: Boolean get() = metrics.isEmpty() && workouts.isEmpty()
}
