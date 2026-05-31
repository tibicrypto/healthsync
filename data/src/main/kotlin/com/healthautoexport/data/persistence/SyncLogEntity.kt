package com.healthautoexport.data.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.healthautoexport.domain.model.DestinationType
import com.healthautoexport.domain.model.ExportFormat
import com.healthautoexport.domain.model.ExportStatus

/**
 * Bản ghi Room cho một mục Sync_Log (Requirement 23).
 *
 * Biểu diễn lưu trữ của [com.healthautoexport.domain.model.SyncLogEntry]. Mỗi mục **chỉ chứa
 * metadata** về một Export_Job — KHÔNG chứa bất kỳ giá trị sức khỏe thô nào (Requirement 23.4):
 * không có cột nào mang dữ liệu bản ghi, [message] chỉ là mô tả người dùng đọc được.
 *
 * Quy ước lưu trữ:
 * - **Instant lưu dạng epoch-millis `Long`** ([startUtc], [completionUtc]).
 * - **Enum lưu dạng `String`** qua [RoomConverters] ([exportFormat], [destinationType], [status]).
 *
 * Thứ tự hiển thị và chính sách thu hồi được áp ở tầng repository bằng các chính sách thuần của
 * domain ([com.healthautoexport.domain.logic.SyncLogOrdering],
 * [com.healthautoexport.domain.logic.SyncLogEvictionPolicy]).
 *
 * @property id khóa chính, định danh ổn định của mục log.
 * @property startUtc epoch-millis thời điểm bắt đầu Export_Job (Requirements 23.1, 23.3 tie-break).
 * @property completionUtc epoch-millis thời điểm hoàn tất, hoặc `null` nếu chưa hoàn tất (Req 23.3).
 * @property automationId định danh Automation liên quan, hoặc `null` cho Quick_Export.
 * @property exportFormat định dạng xuất của job, hoặc `null` nếu không xác định.
 * @property destinationType loại Destination đích, hoặc `null` nếu không xác định.
 * @property status trạng thái kết quả (lưu dạng String).
 * @property message mô tả người dùng đọc được; KHÔNG chứa dữ liệu thô (Requirement 23.4).
 */
@Entity(tableName = "sync_log")
data class SyncLogEntity(
    @PrimaryKey val id: String,
    val startUtc: Long,
    val completionUtc: Long?,
    val automationId: String?,
    val exportFormat: ExportFormat?,
    val destinationType: DestinationType?,
    val status: ExportStatus,
    val message: String?,
)
