package com.healthautoexport.domain.port

import com.healthautoexport.domain.model.ExportStatus

/**
 * Tóm tắt cuối cùng của một Export_Job, thu thập kết quả và toàn bộ cảnh báo để ghi đúng một mục
 * Sync_Log (Requirements 13.1, 23.1, 23.2).
 *
 * @property status trạng thái kết quả của job ([ExportStatus]).
 * @property detail mô tả người dùng đọc được; SHALL KHÔNG chứa dữ liệu thô (Requirement 23.4).
 * @property warnings các cảnh báo gom được trong suốt job (metric/nguồn bị loại trừ, trường
 *   thiếu, workout không route bị loại khỏi GPX...) (Requirements 4.7, 5.6, 6.6).
 * @property recordCount số bản ghi đã xuất (sau merge/aggregate), `0` cho kết quả rỗng
 *   (Requirement 5.8).
 */
data class JobReport(
    val status: ExportStatus,
    val detail: String,
    val warnings: List<ReadWarning> = emptyList(),
    val recordCount: Int = 0,
)
