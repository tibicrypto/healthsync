package com.healthautoexport.domain.model

import java.time.Instant
import java.time.ZoneOffset

/**
 * Bản ghi triệu chứng (Symptom) — một trong tám danh mục của [ExportDataset]
 * (Requirement 10.1), phản chiếu lược đồ `symptoms` của Health Auto Export gốc.
 *
 * @property timestamp thời điểm ghi nhận, lưu theo UTC (Requirement 9.4).
 * @property zoneOffset độ lệch múi giờ để định dạng dấu thời gian có hậu tố vùng.
 * @property name tên triệu chứng.
 * @property severity mức độ nghiêm trọng dưới dạng chuỗi phân loại (có thể vắng),
 *   vd `"mild"`, `"moderate"`, `"severe"`.
 */
data class Symptom(
    val timestamp: Instant,
    val zoneOffset: ZoneOffset,
    val name: String,
    val severity: String?,
)
