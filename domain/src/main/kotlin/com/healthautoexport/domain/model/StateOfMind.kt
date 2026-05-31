package com.healthautoexport.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset

/**
 * Bản ghi trạng thái tinh thần (State of Mind) — một trong tám danh mục của [ExportDataset]
 * (Requirement 10.1), phản chiếu lược đồ `stateOfMind` của Health Auto Export gốc.
 *
 * @property timestamp thời điểm ghi nhận, lưu theo UTC (Requirement 9.4).
 * @property zoneOffset độ lệch múi giờ để định dạng dấu thời gian có hậu tố vùng.
 * @property kind loại ghi nhận (vd khoảnh khắc tức thời hay tâm trạng hằng ngày).
 * @property valence mức độ dễ chịu/khó chịu (có thể vắng); dùng [BigDecimal] giữ độ chính xác.
 * @property labels các nhãn cảm xúc đính kèm, theo đúng thứ tự ghi nhận.
 * @property associations các yếu tố liên quan (vd bối cảnh, hoạt động) gắn với trạng thái.
 */
data class StateOfMind(
    val timestamp: Instant,
    val zoneOffset: ZoneOffset,
    val kind: String,
    val valence: BigDecimal?,
    val labels: List<String>,
    val associations: List<String>,
)
