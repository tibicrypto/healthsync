package com.healthautoexport.domain.model

/**
 * Đầu vào duy nhất cho mọi Serializer — envelope phản chiếu đối tượng `data` cấp cao nhất của
 * Health Auto Export gốc, luôn chứa **đủ tám danh mục** (Requirement 10.1).
 *
 * Tám danh sách luôn hiện diện; khi không có bản ghi, danh sách tương ứng là rỗng (`[]`) chứ
 * không bị bỏ qua hay đặt `null` — nhờ đó JSON_Serializer luôn phát ra đủ tám khóa mảng và bảo
 * toàn round-trip (Requirements 10.1, 10.3). Mỗi danh mục mặc định là `emptyList()`, vì vậy
 * `ExportDataset()` (hoặc [empty]) tạo ra một envelope rỗng hợp lệ với đủ tám khóa.
 *
 * @property metrics chuỗi chỉ số tiêu chuẩn và lược đồ riêng, mỗi phần tử là một [MetricSeries].
 * @property workouts danh sách buổi tập (Requirement 5.x).
 * @property stateOfMind các bản ghi trạng thái tinh thần.
 * @property medications các bản ghi dùng thuốc.
 * @property symptoms các bản ghi triệu chứng.
 * @property cycleTracking các bản ghi theo dõi chu kỳ.
 * @property ecg các bản ghi điện tâm đồ (Requirement 6.2).
 * @property heartRateNotifications các sự kiện cảnh báo nhịp tim (Requirement 6.3).
 */
data class ExportDataset(
    val metrics: List<MetricSeries> = emptyList(),
    val workouts: List<Workout> = emptyList(),
    val stateOfMind: List<StateOfMind> = emptyList(),
    val medications: List<Medication> = emptyList(),
    val symptoms: List<Symptom> = emptyList(),
    val cycleTracking: List<CycleTrackingEntry> = emptyList(),
    val ecg: List<EcgRecord> = emptyList(),
    val heartRateNotifications: List<HeartRateNotification> = emptyList(),
) {
    companion object {
        /**
         * Envelope rỗng với cả tám danh mục là danh sách rỗng — biểu diễn "không có bản ghi"
         * mà vẫn giữ đủ tám khóa khi tuần tự hóa (Requirements 10.1, 10.3). Tương đương
         * `ExportDataset()` nhờ các tham số mặc định `emptyList()`.
         */
        fun empty(): ExportDataset = ExportDataset()
    }
}

/**
 * Một chuỗi chỉ số trong [ExportDataset.metrics], tương ứng một mảng trong `metrics` của JSON.
 *
 * @property name tên canonical dạng snake_case của chỉ số (vd `"step_count"`).
 * @property units chuỗi đơn vị canonical (vd `"count"`, `"bpm"`) — khớp [CanonicalUnit.symbol].
 * @property data các [UnifiedRecord] thuộc chỉ số này, đã chuẩn hóa về đơn vị [units].
 */
data class MetricSeries(
    val name: String,
    val units: String,
    val data: List<UnifiedRecord>,
)
