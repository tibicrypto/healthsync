package com.healthautoexport.domain.model

import java.time.Instant

/**
 * Một mục Sync_Log ghi kết quả của một Export_Job (Requirement 23).
 *
 * Đây là mô hình **thuần domain** tương ứng với `SyncLogEntity` của Room (task 16.1). Mỗi mục
 * **chỉ chứa metadata** — không chứa dữ liệu sức khỏe thô (Requirement 23.4). Mục hiển thị giảm
 * dần theo [completionUtc], tie-break giảm dần theo [startUtc] (Requirement 23.3); khi vượt giới
 * hạn, xóa mục cũ nhất theo cùng khóa (Requirement 23.5).
 *
 * @property id định danh ổn định của mục log.
 * @property startUtc thời điểm bắt đầu Export_Job (Requirements 23.1, 23.3 tie-break).
 * @property completionUtc thời điểm hoàn tất, hoặc `null` nếu chưa hoàn tất (Requirement 23.3).
 * @property automationId định danh Automation liên quan, hoặc `null` cho Quick_Export.
 * @property exportFormat định dạng xuất của job, hoặc `null` nếu không xác định.
 * @property destinationType loại Destination đích, hoặc `null` nếu không xác định.
 * @property status trạng thái kết quả ([ExportStatus]).
 * @property message mô tả người dùng đọc được; SHALL KHÔNG chứa dữ liệu thô (Requirement 23.4).
 */
data class SyncLogEntry(
    val id: String,
    val startUtc: Instant,
    val completionUtc: Instant?,
    val automationId: String?,
    val exportFormat: ExportFormat?,
    val destinationType: DestinationType?,
    val status: ExportStatus,
    val message: String?,
)
