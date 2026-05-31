package com.healthautoexport.domain.model

import java.time.Instant
import java.time.ZoneOffset

/**
 * Bản ghi theo dõi chu kỳ (Cycle Tracking) — một trong tám danh mục của [ExportDataset]
 * (Requirement 10.1), phản chiếu lược đồ `cycleTracking` của Health Auto Export gốc.
 *
 * @property timestamp thời điểm ghi nhận, lưu theo UTC (Requirement 9.4).
 * @property zoneOffset độ lệch múi giờ để định dạng dấu thời gian có hậu tố vùng.
 * @property flow lượng kinh nguyệt dưới dạng chuỗi phân loại (có thể vắng), vd `"light"`, `"heavy"`.
 * @property ovulationTestResult kết quả que thử rụng trứng (có thể vắng), vd `"positive"`.
 * @property sexualActivity có hoạt động tình dục hay không (có thể vắng nếu không ghi nhận).
 */
data class CycleTrackingEntry(
    val timestamp: Instant,
    val zoneOffset: ZoneOffset,
    val flow: String?,
    val ovulationTestResult: String?,
    val sexualActivity: Boolean?,
)
