package com.healthautoexport.domain.model

import java.time.Instant
import java.time.ZoneOffset

/**
 * Unified_Record — một điểm dữ liệu sức khỏe **đã chuẩn hóa**, là đơn vị dữ liệu trung tâm mà
 * toàn bộ pipeline Merge → Aggregate → Serialize thao tác.
 *
 * Mỗi bản ghi nguồn (từ Health_Connect hoặc Huawei_Health_Kit) được ánh xạ về một `UnifiedRecord`
 * với giá trị đã chuyển sang **đơn vị canonical** của metric (Requirement 4.2) và mang theo định
 * danh nguồn gốc [dataSourceId] để bảo toàn xuất xứ (Requirement 4.5) cũng như để `Data_Merger`
 * giải quyết trùng lặp một cách xác định.
 *
 * Đây là mô hình **thuần Kotlin/JVM** (không phụ thuộc Android); dấu thời gian dùng
 * [java.time.Instant] (UTC) kèm [ZoneOffset] để tuần tự hóa đúng định dạng `... Z`.
 *
 * @property metric loại Health_Metric của bản ghi (Requirement 4.1).
 * @property value giá trị đã chuẩn hóa — đơn ([MetricValue.Scalar]) hoặc có cấu trúc
 *   (huyết áp, giấc ngủ, ECG...); dùng [java.math.BigDecimal] để bảo toàn độ chính xác
 *   (Requirement 10.5).
 * @property unit đơn vị canonical tương ứng của [metric] (Requirement 4.2).
 * @property timestamp mốc thời gian của bản ghi ở UTC (Requirement 9.4).
 * @property zoneOffset độ lệch múi giờ gốc, dùng để định dạng dấu thời gian `yyyy-MM-dd HH:mm:ss Z`.
 * @property dataSourceId định danh Data_Source gốc; SHALL được bảo toàn qua merge/aggregate
 *   (Requirement 4.5).
 * @property extras siêu dữ liệu phụ (mealTime, reason, trạng thái...) ánh xạ theo khóa tới
 *   [ExtraValue] (Requirements 6.4, 6.3); mặc định rỗng.
 */
data class UnifiedRecord(
    val metric: HealthMetricType,
    val value: MetricValue,
    val unit: CanonicalUnit,
    val timestamp: Instant,
    val zoneOffset: ZoneOffset,
    val dataSourceId: DataSourceId,
    val extras: Map<String, ExtraValue> = emptyMap(),
)
