package com.healthautoexport.domain.logic

import com.healthautoexport.domain.model.ExportFormat

/**
 * Ánh xạ [ExportFormat] sang MIME media type dùng cho tiêu đề `Content-Type` của yêu cầu REST API /
 * Home Assistant (Requirement 16.3).
 *
 * Hàm thuần. Ánh xạ chuẩn:
 * - [ExportFormat.JSON] → `application/json`
 * - [ExportFormat.CSV] → `text/csv`
 * - [ExportFormat.GPX] → `application/gpx+xml`
 *
 * Quyết định thiết kế: media type của CSV là **`text/csv`** cho tài liệu CSV đơn. Khi xuất CSV được
 * đóng gói thành một archive (nhiều tệp CSV ⇒ ZIP), payload không còn là CSV thuần nên dùng
 * [ARCHIVE_MEDIA_TYPE] (`application/zip`); xem [forArchivedCsv]. Việc CSV có được đóng gói hay
 * không do tầng tuần tự hóa quyết định, nên mapper cung cấp cả hai lối.
 *
 * Property 46 — *Validates: Requirements 16.3*: `Content-Type` SHALL bằng media type chuẩn của
 * định dạng đó.
 */
object ContentTypeMapper {

    /** Media type cho payload CSV đã đóng gói thành ZIP archive. */
    const val ARCHIVE_MEDIA_TYPE: String = "application/zip"

    /**
     * Trả về media type chuẩn của [format] (tài liệu đơn, không đóng gói).
     *
     * Ủy quyền cho [ExportFormat.mediaType] để giữ một nguồn sự thật duy nhất.
     */
    fun mediaType(format: ExportFormat): String = format.mediaType

    /**
     * Trả về media type khi payload CSV được đóng gói thành ZIP ([ARCHIVE_MEDIA_TYPE]); với mọi định
     * dạng khác trả về media type chuẩn từ [mediaType].
     *
     * @param format định dạng xuất.
     * @param archived `true` nếu CSV được nén thành ZIP archive.
     */
    fun mediaType(format: ExportFormat, archived: Boolean): String =
        if (archived && format == ExportFormat.CSV) ARCHIVE_MEDIA_TYPE else mediaType(format)
}
