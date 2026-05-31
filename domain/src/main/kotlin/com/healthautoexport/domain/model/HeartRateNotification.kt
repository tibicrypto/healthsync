package com.healthautoexport.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset

/**
 * Sự kiện cảnh báo nhịp tim (high/low/irregular) ở cấp envelope — một trong tám danh mục của
 * [ExportDataset] (Requirement 10.1), phản chiếu lược đồ `heartRateNotifications` của
 * Health Auto Export gốc.
 *
 * Mỗi sự kiện mang thời điểm bắt đầu/kết thúc (mỗi mốc kèm thông tin múi giờ), ngưỡng (bpm) và
 * các mẫu nhịp tim liên quan theo đúng thứ tự ghi nhận (Requirement 6.3).
 *
 * @property kind loại cảnh báo (cao/thấp/bất thường).
 * @property start thời điểm bắt đầu sự kiện (UTC) (Requirement 6.3).
 * @property startZoneOffset độ lệch múi giờ của [start] (Requirement 6.3).
 * @property end thời điểm kết thúc sự kiện (UTC) (Requirement 6.3).
 * @property endZoneOffset độ lệch múi giờ của [end] (Requirement 6.3).
 * @property thresholdBpm ngưỡng kích hoạt tính bằng bpm ([CanonicalUnit.BPM]) (Requirement 6.3).
 * @property samples các mẫu nhịp tim liên quan theo thứ tự tăng dần theo dấu thời gian
 *   (Requirement 6.3).
 * @property dataSourceId định danh Data_Source gốc của sự kiện (Requirement 4.5).
 */
data class HeartRateNotification(
    val kind: HeartRateNotificationKind,
    val start: Instant,
    val startZoneOffset: ZoneOffset,
    val end: Instant,
    val endZoneOffset: ZoneOffset,
    val thresholdBpm: BigDecimal,
    val samples: List<HeartRateSample>,
    val dataSourceId: DataSourceId,
)

/**
 * Phân loại sự kiện cảnh báo nhịp tim (Requirement 6.3).
 */
enum class HeartRateNotificationKind {
    /** Nhịp tim cao bất thường. */
    HIGH,

    /** Nhịp tim thấp bất thường. */
    LOW,

    /** Nhịp tim không đều (irregular rhythm). */
    IRREGULAR,
}
