package com.healthautoexport.domain.model

import java.math.BigDecimal

/**
 * Tập các trường siêu dữ liệu **tùy chọn** đi kèm một [Workout] (Requirement 5.5).
 *
 * Chỉ chứa những trường khả dụng từ Data_Source: một trường `null` nghĩa là dữ liệu tương ứng
 * **vắng mặt** và sẽ bị bỏ qua khi tuần tự hóa (Requirement 5.5). Nhờ vậy, một Workout xuất ra
 * chứa đúng các trường khả dụng và bỏ qua các trường không khả dụng.
 *
 * Các đại lượng số dùng [BigDecimal] để giữ độ chính xác của giá trị (nhất quán với cách App
 * giữ `qty` trong mô hình hợp nhất), tránh sai số dấu phẩy động khi chuyển đổi đơn vị canonical.
 *
 * @property activeEnergyKcal năng lượng hoạt động ([CanonicalUnit.KCAL]), hoặc `null` khi vắng.
 * @property totalEnergyKcal tổng năng lượng ([CanonicalUnit.KCAL]), hoặc `null` khi vắng.
 * @property distanceMeters quãng đường tính bằng mét ([CanonicalUnit.METER]), hoặc `null` khi vắng.
 * @property avgSpeedMps tốc độ trung bình ([CanonicalUnit.METER_PER_SECOND]), hoặc `null` khi vắng.
 * @property elevationGainMeters độ cao chênh lệch tính bằng mét, hoặc `null` khi vắng.
 * @property stepCount số bước trong phiên tập, hoặc `null` khi vắng.
 * @property heartRateRecovery chuỗi mẫu hồi phục nhịp tim (tăng dần theo dấu thời gian), hoặc
 *   `null` khi vắng (Requirement 5.4).
 */
data class WorkoutMetrics(
    val activeEnergyKcal: BigDecimal? = null,
    val totalEnergyKcal: BigDecimal? = null,
    val distanceMeters: BigDecimal? = null,
    val avgSpeedMps: BigDecimal? = null,
    val elevationGainMeters: BigDecimal? = null,
    val stepCount: Long? = null,
    val heartRateRecovery: List<HeartRateSample>? = null,
)
