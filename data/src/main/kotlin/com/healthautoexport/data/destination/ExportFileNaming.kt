package com.healthautoexport.data.destination

import com.healthautoexport.domain.port.ExportPayload
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Sinh **tên tệp cơ sở** cho một payload xuất, dùng chung cho các Destination ghi tệp
 * (Google Drive 17.x, Dropbox 18.x, Local Storage 21.x).
 *
 * Tên cơ sở theo định dạng dấu thời gian UTC `YYYYMMDD-HHMMSS` của thời điểm bắt đầu Export_Job
 * (Requirement 21.3), kèm phần mở rộng tương ứng với [com.healthautoexport.domain.model.ExportFormat]
 * (vd `20240131-080000.json`). Hậu tố phân biệt cho trường hợp trùng tên được thêm về sau bởi
 * [com.healthautoexport.domain.logic.FileNameGenerator].
 *
 * Hàm thuần: không thực hiện I/O.
 */
internal object ExportFileNaming {

    /** Bộ định dạng dấu thời gian UTC `YYYYMMDD-HHMMSS` (Requirement 21.3). */
    private val TIMESTAMP: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)

    /**
     * Trả về tên tệp cơ sở `YYYYMMDD-HHMMSS.<ext>` cho [payload].
     *
     * @param payload payload đã tuần tự hóa mang [ExportPayload.jobStartUtc] và
     *   [ExportPayload.format].
     */
    fun baseName(payload: ExportPayload): String {
        val stamp = TIMESTAMP.format(payload.jobStartUtc)
        return "$stamp.${payload.format.fileExtension}"
    }
}
