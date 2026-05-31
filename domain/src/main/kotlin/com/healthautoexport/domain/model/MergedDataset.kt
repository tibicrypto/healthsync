package com.healthautoexport.domain.model

/**
 * Tập dữ liệu **đã hợp nhất và loại trùng** — đầu ra của `DataMerger` và là đầu vào của
 * `Aggregator` (Requirements 7.1, 7.6).
 *
 * `MergedDataset` gom mọi bản ghi từ nhiều Data_Source thành **một chuỗi có thứ tự duy nhất cho
 * mỗi Health_Metric** (Requirement 7.1): khóa của [recordsByMetric] là loại metric, giá trị là
 * danh sách [UnifiedRecord] đã loại trùng và **đã sắp xếp tăng dần theo khóa tổng
 * `(timestamp, dataSourceId, value)`** (Requirement 7.6). Mỗi bản ghi vẫn giữ nguyên
 * [UnifiedRecord.dataSourceId] để bảo toàn nguồn gốc sau merge (Requirements 4.5, 7.7).
 *
 * Các [Workout] được **chuyển tiếp nguyên trạng** (pass-through): việc hợp nhất/loại trùng ở
 * task này chỉ áp dụng cho các bản ghi metric (Requirement 7); workout được gộp theo thứ tự
 * nguồn cung cấp và không bị loại bỏ tại đây.
 *
 * Đây là mô hình **thuần Kotlin/JVM** (không phụ thuộc Android) nên pipeline Merge → Aggregate
 * chạy và kiểm thử nhanh trên JVM.
 *
 * @property recordsByMetric ánh xạ mỗi [HealthMetricType] tới chuỗi [UnifiedRecord] đã loại trùng
 *   và đã sắp xếp (Requirements 7.1, 7.6). Thứ tự duyệt khóa là xác định (theo thứ tự khai báo
 *   của enum [HealthMetricType]).
 * @property workouts danh sách [Workout] được chuyển tiếp nguyên trạng từ mọi nguồn.
 */
data class MergedDataset(
    val recordsByMetric: Map<HealthMetricType, List<UnifiedRecord>>,
    val workouts: List<Workout> = emptyList(),
) {
    /**
     * Toàn bộ bản ghi của mọi metric trải phẳng thành một danh sách, giữ nguyên thứ tự duyệt
     * khóa của [recordsByMetric] và thứ tự bản ghi bên trong mỗi metric. Tiện cho các bước
     * tuần tự hóa/kiểm thử cần một danh sách phẳng.
     */
    val records: List<UnifiedRecord>
        get() = recordsByMetric.values.flatten()

    companion object {
        /** Một [MergedDataset] rỗng (không metric, không workout). */
        fun empty(): MergedDataset = MergedDataset(emptyMap(), emptyList())
    }
}
