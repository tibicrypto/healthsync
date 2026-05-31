package com.healthautoexport.domain.model

import java.time.Instant
import java.time.ZoneOffset

/**
 * Bản ghi dùng thuốc (Medication) — một trong tám danh mục của [ExportDataset]
 * (Requirement 10.1), phản chiếu lược đồ `medications` của Health Auto Export gốc.
 *
 * @property timestamp thời điểm ghi nhận, lưu theo UTC (Requirement 9.4).
 * @property zoneOffset độ lệch múi giờ để định dạng dấu thời gian có hậu tố vùng.
 * @property name tên thuốc.
 * @property dose liều lượng dưới dạng chuỗi (có thể vắng) — giữ nguyên định dạng nguồn cung cấp.
 * @property unit đơn vị liều (có thể vắng), vd `"mg"`.
 */
data class Medication(
    val timestamp: Instant,
    val zoneOffset: ZoneOffset,
    val name: String,
    val dose: String?,
    val unit: String?,
)
