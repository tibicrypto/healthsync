package com.healthautoexport.domain.logic

/**
 * Xác thực khoảng lặp lịch của một Automation (Requirements 15.3, 15.4).
 *
 * Hàm thuần: App SHALL chấp nhận khoảng lặp **khi và chỉ khi** `15 phút <= interval <= 30 ngày`;
 * ngoài phạm vi thì từ chối và giữ giá trị hợp lệ trước đó (việc giữ giá trị do tầng gọi xử lý).
 *
 * Property 48 — *Validates: Requirements 15.3, 15.4*.
 */
object ScheduleIntervalValidator {

    /** Khoảng lặp tối thiểu: 15 phút. */
    const val MIN_MINUTES: Long = 15

    /** Khoảng lặp tối đa: 30 ngày = 43200 phút. */
    const val MAX_MINUTES: Long = 30L * 24 * 60

    /**
     * Trả về `true` nếu [minutes] nằm trong `[15, 43200]` (15 phút .. 30 ngày).
     *
     * @param minutes khoảng lặp tính bằng phút.
     */
    fun isValid(minutes: Long): Boolean = minutes in MIN_MINUTES..MAX_MINUTES
}
