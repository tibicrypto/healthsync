package com.healthautoexport.data.persistence

import androidx.room.TypeConverter
import com.healthautoexport.domain.model.AggregationPeriod
import com.healthautoexport.domain.model.DestinationType
import com.healthautoexport.domain.model.ExportFormat
import com.healthautoexport.domain.model.ExportStatus

/**
 * Room [TypeConverter] cho các enum domain dùng trong [AutomationEntity] và [SyncLogEntity].
 *
 * Mỗi enum được lưu dưới dạng `String` (tên hằng số qua [Enum.name]) và khôi phục bằng
 * `valueOf`. Lưu tên hằng số (thay vì `ordinal`) giữ cho schema **ổn định trước thay đổi thứ tự**
 * khai báo enum trong tương lai. Các giá trị `null` được truyền nguyên trạng để hỗ trợ các cột
 * nullable (vd `SyncLogEntity.exportFormat`).
 *
 * Instant được lưu trực tiếp dạng epoch-millis `Long` trong entity nên không cần converter ở đây.
 */
object RoomConverters {

    // --- ExportFormat ---

    @TypeConverter
    fun fromExportFormat(value: ExportFormat?): String? = value?.name

    @TypeConverter
    fun toExportFormat(value: String?): ExportFormat? = value?.let(ExportFormat::valueOf)

    // --- AggregationPeriod ---

    @TypeConverter
    fun fromAggregationPeriod(value: AggregationPeriod?): String? = value?.name

    @TypeConverter
    fun toAggregationPeriod(value: String?): AggregationPeriod? =
        value?.let(AggregationPeriod::valueOf)

    // --- DestinationType ---

    @TypeConverter
    fun fromDestinationType(value: DestinationType?): String? = value?.name

    @TypeConverter
    fun toDestinationType(value: String?): DestinationType? =
        value?.let(DestinationType::valueOf)

    // --- ExportStatus ---

    @TypeConverter
    fun fromExportStatus(value: ExportStatus?): String? = value?.name

    @TypeConverter
    fun toExportStatus(value: String?): ExportStatus? = value?.let(ExportStatus::valueOf)
}
