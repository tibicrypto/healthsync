package com.healthautoexport.domain.model

/**
 * Ba định dạng xuất được hỗ trợ.
 *
 * @property fileExtension đuôi tệp (không kèm dấu chấm) dùng để đặt tên tệp Local Storage
 *   (Requirement 21.3) và đặt tên tài liệu trong archive CSV.
 * @property mediaType MIME media type dùng cho tiêu đề Content-Type khi gửi tới REST API /
 *   Home Assistant (Requirement 16.3).
 */
enum class ExportFormat(val fileExtension: String, val mediaType: String) {
    JSON("json", "application/json"),
    CSV("csv", "text/csv"),
    GPX("gpx", "application/gpx+xml"),
}
