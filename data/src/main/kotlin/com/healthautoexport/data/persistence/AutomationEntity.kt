package com.healthautoexport.data.persistence

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.healthautoexport.domain.model.AggregationPeriod
import com.healthautoexport.domain.model.DestinationType
import com.healthautoexport.domain.model.ExportFormat

/**
 * Bản ghi Room cho một Automation (Requirement 14).
 *
 * Đây là biểu diễn lưu trữ của mô hình domain [com.healthautoexport.domain.model.Automation];
 * [PersistenceMappers] ánh xạ qua lại giữa hai mô hình.
 *
 * Quy ước lưu trữ:
 * - **Instant lưu dạng epoch-millis `Long`** ([firstActivatedAtUtc], [lastSuccessfulEndUtc]).
 * - **Enum lưu dạng `String`** qua [RoomConverters] (cột TEXT chứa `enum.name`).
 * - **Tập metric/workout lưu dạng JSON** ([selectedMetricsJson], [selectedWorkoutsJson]) được
 *   mã hóa bằng kotlinx.serialization (xem [PersistenceMappers]).
 *
 * Chỉ mục **unique** trên [nameLower] thực thi ràng buộc tên Automation không trùng nhau khi bỏ
 * qua hoa/thường (Requirement 14.7); credential của Destination **không** lưu tại đây mà nằm ở
 * [com.healthautoexport.domain.port.CredentialStore] (Requirement 22.9), chỉ tham chiếu qua
 * [destinationConfigRef].
 *
 * @property id khóa chính, định danh ổn định (cũng là unique work name của Scheduler, Req 15.1).
 * @property name tên hiển thị (1–100 ký tự, Requirement 14.1).
 * @property nameLower bản thường-hóa của [name] dùng cho chỉ mục unique không phân biệt hoa/thường
 *   (Requirement 14.7).
 * @property selectedMetricsJson JSON mã hóa tập `HealthMetricType` đã chọn.
 * @property selectedWorkoutsJson JSON mã hóa tập `WorkoutType` đã chọn.
 * @property exportFormat định dạng xuất (lưu dạng String).
 * @property aggregationPeriod mức tổng hợp (lưu dạng String).
 * @property scheduleIntervalMinutes khoảng lặp lịch tính bằng phút (15..43200, Requirement 15.3).
 * @property enabled `true` nếu Automation đang bật.
 * @property destinationType loại Destination đích (lưu dạng String).
 * @property destinationConfigRef tham chiếu cấu hình Destination (credential ở CredentialStore).
 * @property firstActivatedAtUtc epoch-millis thời điểm kích hoạt lần đầu, hoặc `null` (Req 9.9).
 * @property lastSuccessfulEndUtc epoch-millis thời điểm kết thúc job thành công gần nhất, hoặc
 *   `null` (Requirement 9.8).
 */
@Entity(
    tableName = "automations",
    indices = [Index(value = ["nameLower"], unique = true)], // Requirement 14.7
)
data class AutomationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val nameLower: String,
    val selectedMetricsJson: String,
    val selectedWorkoutsJson: String,
    val exportFormat: ExportFormat,
    val aggregationPeriod: AggregationPeriod,
    val scheduleIntervalMinutes: Long,
    val enabled: Boolean,
    val destinationType: DestinationType,
    val destinationConfigRef: String,
    val firstActivatedAtUtc: Long?,
    val lastSuccessfulEndUtc: Long?,
)
