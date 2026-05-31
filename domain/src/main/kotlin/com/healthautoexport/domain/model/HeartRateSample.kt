package com.healthautoexport.domain.model

import java.time.Instant

/**
 * Một mẫu nhịp tim đơn lẻ trong chuỗi nhịp tim của một [Workout] (Requirement 5.4).
 *
 * Các mẫu trong một chuỗi ([Workout.heartRateSeries]) cũng như trong dữ liệu hồi phục nhịp
 * tim ([WorkoutMetrics.heartRateRecovery]) được giữ theo thứ tự **tăng dần** theo [timestamp]
 * (Requirement 5.4). Bất biến này do `Data_Reader` bảo đảm khi đọc dữ liệu; kiểu dữ liệu ở đây
 * chỉ mang giá trị và không tự sắp xếp.
 *
 * @property timestamp thời điểm ghi nhận mẫu nhịp tim (UTC, không kèm múi giờ).
 * @property bpm số nhịp mỗi phút ([CanonicalUnit.BPM]) tại thời điểm [timestamp].
 */
data class HeartRateSample(
    val timestamp: Instant,
    val bpm: Int,
)
