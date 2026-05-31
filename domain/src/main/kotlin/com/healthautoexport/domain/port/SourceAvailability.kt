package com.healthautoexport.domain.port

/**
 * Khả dụng của một Data_Source tại thời điểm truy vấn (Requirements 1.1, 2.1, 3.6).
 *
 * Khi [Unavailable], App có thể tiếp tục với nguồn còn lại (Requirement 2.1) và hiển thị thông
 * báo kèm liên kết cài đặt nếu có (Requirements 1.1, 1.8).
 */
sealed interface SourceAvailability {

    /** Nguồn sẵn sàng để đọc dữ liệu. */
    data object Available : SourceAvailability

    /**
     * Nguồn không khả dụng (SDK chưa cài, dịch vụ không sẵn sàng, hết thời gian chờ...).
     *
     * @property reason mô tả người dùng đọc được lý do không khả dụng.
     * @property installLink liên kết cài đặt/cập nhật (vd Play Store cho Health_Connect,
     *   Requirement 1.8), hoặc `null` nếu không áp dụng.
     */
    data class Unavailable(
        val reason: String,
        val installLink: String? = null,
    ) : SourceAvailability
}
