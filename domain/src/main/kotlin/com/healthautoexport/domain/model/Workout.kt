package com.healthautoexport.domain.model

import java.time.Instant

/**
 * Một phiên tập luyện đã ghi (Workout) trong mô hình dữ liệu hợp nhất chuẩn hóa
 * (Requirements 5.1, 5.2, 5.4, 5.5).
 *
 * Mỗi Workout luôn mang các trường lõi do `Data_Reader` đọc từ Data_Source đang bật: [type],
 * [start], [end] và [durationSeconds] (Requirement 5.1). Các phần còn lại là **tùy chọn**:
 *
 * - [route]: tuyến đường GPS dưới dạng chuỗi [RoutePoint] sắp xếp tăng dần theo dấu thời gian
 *   (Requirement 5.2); `null` khi Workout không có tuyến đường đã ghi. Workout không có tuyến
 *   đường bị loại khỏi đầu ra GPX (Requirement 5.6, xử lý ở `GpxSerializer`).
 * - [heartRateSeries]: chuỗi [HeartRateSample] sắp xếp tăng dần theo dấu thời gian
 *   (Requirement 5.4); `null` khi không có mẫu nhịp tim đi kèm.
 * - [optionalFields]: các trường siêu dữ liệu tùy chọn khác, chỉ chứa trường khả dụng
 *   (Requirement 5.5); mặc định là [WorkoutMetrics] rỗng (mọi trường `null`).
 *
 * [dataSourceId] bảo toàn nguồn gốc của phiên tập (nhất quán với [UnifiedRecord]) để phục vụ
 * hợp nhất/loại trùng xác định.
 *
 * @property id định danh ổn định của Workout (dùng để tham chiếu/loại trùng).
 * @property type loại bài tập ([WorkoutType]).
 * @property start thời điểm bắt đầu (UTC).
 * @property end thời điểm kết thúc (UTC).
 * @property durationSeconds thời lượng phiên tập tính bằng giây.
 * @property route tuyến đường GPS tùy chọn, hoặc `null` khi không có (Requirement 5.2).
 * @property heartRateSeries chuỗi nhịp tim tùy chọn, hoặc `null` khi không có (Requirement 5.4).
 * @property optionalFields siêu dữ liệu tùy chọn chỉ gồm trường khả dụng (Requirement 5.5).
 * @property dataSourceId định danh Data_Source gốc của Workout (Requirement 4.5).
 */
data class Workout(
    val id: String,
    val type: WorkoutType,
    val start: Instant,
    val end: Instant,
    val durationSeconds: Long,
    val route: List<RoutePoint>? = null,
    val heartRateSeries: List<HeartRateSample>? = null,
    val optionalFields: WorkoutMetrics = WorkoutMetrics(),
    val dataSourceId: DataSourceId,
)
