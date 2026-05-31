package com.healthautoexport.domain.model

import java.time.Instant

/**
 * Khoảng thời gian [startUtc, endUtc] dùng để giới hạn các bản ghi đưa vào một Export_Job
 * (Requirement 9.1).
 *
 * Mọi mốc thời gian được diễn giải theo **UTC** (Requirement 9.4). Việc lọc bản ghi bao gồm
 * **cả hai đầu mút** (so sánh theo UTC, Requirement 9.5) do `DateRangeResolver` thực hiện
 * (task 8.1); kiểu dữ liệu này chỉ giữ cặp mốc và bất biến thứ tự.
 *
 * Bất biến: cho phép `endUtc == startUtc` (khoảng bằng không, Requirement 9.3) nhưng từ chối
 * `endUtc` đứng trước `startUtc` (Requirement 9.2) qua `require` trong khối khởi tạo.
 *
 * @property startUtc thời điểm bắt đầu (UTC).
 * @property endUtc thời điểm kết thúc (UTC); SHALL không đứng trước [startUtc].
 */
data class DateRange(
    val startUtc: Instant,
    val endUtc: Instant,
) {
    init {
        require(!endUtc.isBefore(startUtc)) {
            "DateRange.endUtc ($endUtc) không được đứng trước startUtc ($startUtc) (Requirement 9.2)"
        }
    }
}
