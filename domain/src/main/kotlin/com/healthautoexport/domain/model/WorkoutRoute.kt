package com.healthautoexport.domain.model

/**
 * Một chuỗi tuyến đường GPS độc lập của một Workout (Requirements 5.2, 12.6).
 *
 * Trong một [Workout] đầy đủ, tuyến đường được mang trực tiếp bởi [Workout.route] dưới dạng
 * `List<RoutePoint>`. Kiểu `WorkoutRoute` này tách riêng chuỗi tuyến đường khỏi phần còn lại
 * của Workout để phục vụ vòng khứ hồi (round-trip) GPX: `GpxParser` đọc một tài liệu GPX 1.1
 * trở lại thành `List<WorkoutRoute>` (Requirement 12.6), nơi siêu dữ liệu Workout (loại, thời
 * lượng, nhịp tim, ...) không hiện diện trong tài liệu GPX nên không thể khôi phục.
 *
 * [workoutId] là tùy chọn vì định danh Workout gốc thường không được mã hóa trong GPX; khi
 * khả dụng (vd lấy từ tên track) có thể dùng để liên kết ngược về [Workout].
 *
 * @property workoutId định danh Workout gốc nếu khôi phục được, ngược lại `null`.
 * @property points các điểm tuyến đường theo thứ tự tăng dần theo dấu thời gian (Requirement 5.2).
 */
data class WorkoutRoute(
    val workoutId: String?,
    val points: List<RoutePoint>,
)
