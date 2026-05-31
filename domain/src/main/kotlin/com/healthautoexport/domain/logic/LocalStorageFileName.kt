package com.healthautoexport.domain.logic

import com.healthautoexport.domain.model.ExportFormat
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.Instant

/**
 * Đặt tên tệp cho Destination Local Storage theo dấu thời gian UTC `YYYYMMDD-HHMMSS` của thời điểm
 * bắt đầu Export_Job, theo sau là phần mở rộng tương ứng [ExportFormat] (Requirement 21.3).
 *
 * Hàm thuần: chỉ định dạng [Instant] theo UTC, không phụ thuộc múi giờ hệ thống hay I/O.
 *
 * Property 41 — *Validates: Requirements 21.3*: tên sinh ra SHALL khớp mẫu `YYYYMMDD-HHMMSS`
 * (4 chữ số năm; tháng/ngày/giờ/phút/giây mỗi thành phần 2 chữ số) theo UTC, kèm đúng đuôi định dạng.
 */
object LocalStorageFileName {

    /** Bộ định dạng `YYYYMMDD-HHMMSS` cố định UTC, không phụ thuộc Locale. */
    private val FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)

    /**
     * Tạo tên tệp `YYYYMMDD-HHMMSS.<ext>` cho [jobStartUtc] và [format].
     *
     * @param jobStartUtc thời điểm bắt đầu Export_Job (diễn giải theo UTC).
     * @param format định dạng xuất, quyết định phần mở rộng (`json`/`csv`/`gpx`).
     * @return tên tệp ví dụ `"20240115-093005.json"`.
     */
    fun forJob(jobStartUtc: Instant, format: ExportFormat): String =
        "${FORMATTER.format(jobStartUtc)}.${format.fileExtension}"
}
