package com.healthautoexport.domain.model

import java.time.Instant

/**
 * Một điểm vị trí trong tuyến đường GPS của một [Workout] (Requirements 5.2, 5.3).
 *
 * Mỗi điểm luôn chứa [latitude], [longitude] và [timestamp]; các điểm trong một tuyến đường
 * ([Workout.route]) được giữ theo thứ tự **tăng dần** theo [timestamp] (Requirement 5.2).
 *
 * Trường [altitudeMeters] là **tùy chọn**: khi giá trị độ cao của riêng điểm không khả dụng,
 * điểm vẫn được giữ lại trong tuyến đường và chỉ trường độ cao của điểm đó bị bỏ qua
 * (`altitudeMeters = null`), không loại bỏ điểm khỏi tuyến đường (Requirement 5.3).
 *
 * @property latitude vĩ độ (độ thập phân).
 * @property longitude kinh độ (độ thập phân).
 * @property timestamp thời điểm ghi nhận điểm (UTC, không kèm múi giờ).
 * @property altitudeMeters độ cao tính bằng mét, hoặc `null` khi không khả dụng (Requirement 5.3).
 */
data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Instant,
    val altitudeMeters: Double? = null,
)
