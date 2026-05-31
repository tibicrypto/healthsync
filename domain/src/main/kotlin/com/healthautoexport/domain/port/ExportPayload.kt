package com.healthautoexport.domain.port

import com.healthautoexport.domain.model.ExportFormat
import java.time.Instant

/**
 * Nội dung đã tuần tự hóa của một Export_Job, sẵn sàng gửi tới một [com.healthautoexport.domain.port.Destination]
 * (Requirements 16.3, 21.3).
 *
 * @property bytes nội dung đã tuần tự hóa (văn bản UTF-8 cho JSON/CSV/GPX, hoặc ZIP cho archive CSV).
 * @property contentType MIME type dùng cho tiêu đề Content-Type khi gửi HTTP (Requirement 16.3).
 * @property jobStartUtc thời điểm bắt đầu job, dùng để đặt tên tệp `YYYYMMDD-HHMMSS`
 *   (Requirement 21.3).
 * @property format định dạng xuất, dùng để chọn đuôi tệp (Requirement 21.3).
 */
data class ExportPayload(
    val bytes: ByteArray,
    val contentType: String,
    val jobStartUtc: Instant,
    val format: ExportFormat,
) {
    /** So sánh theo nội dung mảng byte để [ExportPayload] có ngữ nghĩa giá trị đúng đắn. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExportPayload) return false
        return bytes.contentEquals(other.bytes) &&
            contentType == other.contentType &&
            jobStartUtc == other.jobStartUtc &&
            format == other.format
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + jobStartUtc.hashCode()
        result = 31 * result + format.hashCode()
        return result
    }
}
