package com.healthautoexport.data.huawei

import com.healthautoexport.domain.model.HealthMetricType
import com.healthautoexport.domain.model.WorkoutType
import java.time.Instant
import java.time.ZoneOffset

/**
 * Một **bản ghi sức khỏe thô trung lập** mà [HuaweiHealthClient] trả về cho mỗi điểm dữ liệu đọc
 * được từ Huawei_Health_Kit (Requirements 2.x, 4.2).
 *
 * Vì lời gọi SDK Huawei nằm sau interface [HuaweiHealthClient] (xem chú thích lớp đó), kiểu dữ liệu
 * thô không thể là kiểu của SDK Huawei (`SamplePoint`/`SampleSet`) — nếu không `:data` sẽ phải phụ
 * thuộc `com.huawei.hms:health`. Thay vào đó, client trả về danh sách `HuaweiRawSample` **trung
 * lập**, và [HuaweiMetricMapper] dịch chúng sang [com.healthautoexport.domain.model.UnifiedRecord]
 * với đơn vị canonical (Requirement 4.2). Một bản hiện thực thật cho Huawei flavor sẽ chuyển
 * `SamplePoint` của SDK thành `HuaweiRawSample` ngay tại ranh giới adapter.
 *
 * Quy ước về đơn vị: [value] được mang theo **đơn vị gốc của Huawei** kèm mô tả ở [rawUnit]
 * (tùy chọn). [HuaweiMetricMapper] chịu trách nhiệm chuyển sang đơn vị canonical của metric.
 *
 * @property metric loại Health_Metric mà client đã nhận diện cho điểm dữ liệu (đã ánh xạ từ kiểu
 *   dữ liệu Huawei sang [HealthMetricType] tại ranh giới adapter).
 * @property value giá trị số đo ở đơn vị gốc của Huawei; `null` nếu điểm dữ liệu thuộc loại có
 *   cấu trúc (vd huyết áp) và dùng [structured] thay thế.
 * @property structured giá trị có cấu trúc cho các metric không vô hướng (huyết áp, giấc ngủ...);
 *   `null` cho metric vô hướng thông thường.
 * @property rawUnit mô tả đơn vị gốc do Huawei cung cấp (tùy chọn), để mapper kiểm tra/chuyển đổi.
 * @property startTime thời điểm bắt đầu của mẫu (UTC).
 * @property endTime thời điểm kết thúc của mẫu (UTC); với mẫu tức thời có thể bằng [startTime].
 * @property zoneOffset độ lệch múi giờ gốc của mẫu, dùng để định dạng dấu thời gian `... Z`.
 * @property extras siêu dữ liệu phụ trung lập (vd `mealTime` của đường huyết — Requirement 6.4),
 *   ánh xạ tên-khóa → chuỗi; mapper dịch sang `extras` của UnifiedRecord.
 */
internal data class HuaweiRawSample(
    val metric: HealthMetricType,
    val value: Double?,
    val structured: HuaweiStructuredValue? = null,
    val rawUnit: String? = null,
    val startTime: Instant,
    val endTime: Instant,
    val zoneOffset: ZoneOffset = ZoneOffset.UTC,
    val extras: Map<String, String> = emptyMap(),
)

/**
 * Giá trị có cấu trúc trung lập cho các metric không vô hướng đọc từ Huawei_Health_Kit.
 *
 * Tách riêng khỏi [com.healthautoexport.domain.model.MetricValue] để giữ DTO của adapter độc lập
 * với mô hình domain; [HuaweiMetricMapper] dịch sang biến thể `MetricValue` phù hợp.
 */
internal sealed interface HuaweiStructuredValue {

    /**
     * Huyết áp (Requirement: schema BLOOD_PRESSURE), đơn vị gốc `mmHg`.
     *
     * @property systolic huyết áp tâm thu.
     * @property diastolic huyết áp tâm trương.
     */
    data class BloodPressure(
        val systolic: Double,
        val diastolic: Double,
    ) : HuaweiStructuredValue

    /**
     * Một phân đoạn giấc ngủ (Requirement 6.1), thời lượng tính bằng giây.
     *
     * @property stateName tên trạng thái giấc ngủ trung lập (vd `DEEP`, `REM`); mapper dịch sang
     *   [com.healthautoexport.domain.model.SleepState].
     * @property durationSeconds thời lượng phân đoạn (giây); SHALL ≥ 0.
     */
    data class SleepSegment(
        val stateName: String,
        val durationSeconds: Long,
    ) : HuaweiStructuredValue
}

/**
 * Một phiên tập luyện thô trung lập mà [HuaweiHealthClient] trả về (Requirement 5.x).
 *
 * Tương tự [HuaweiRawSample], kiểu này giữ adapter độc lập với SDK Huawei; [HuaweiMetricMapper]
 * dịch sang [com.healthautoexport.domain.model.Workout].
 *
 * @property id định danh ổn định của phiên tập do Huawei cung cấp.
 * @property type loại Workout đã ánh xạ ([WorkoutType]).
 * @property startTime thời điểm bắt đầu (UTC).
 * @property endTime thời điểm kết thúc (UTC).
 * @property route tuyến đường GPS tùy chọn (lat/lon/timestamp/độ cao), hoặc `null` khi không có.
 * @property heartRate chuỗi nhịp tim tùy chọn, hoặc `null` khi không có.
 */
internal data class HuaweiRawWorkout(
    val id: String,
    val type: WorkoutType,
    val startTime: Instant,
    val endTime: Instant,
    val route: List<HuaweiRawRoutePoint>? = null,
    val heartRate: List<HuaweiRawHeartRate>? = null,
)

/**
 * Một điểm tuyến đường GPS thô trung lập (Requirements 5.2, 5.3).
 *
 * @property latitude vĩ độ (độ thập phân).
 * @property longitude kinh độ (độ thập phân).
 * @property timestamp thời điểm ghi nhận (UTC).
 * @property altitudeMeters độ cao (mét), hoặc `null` khi không khả dụng (Requirement 5.3).
 */
internal data class HuaweiRawRoutePoint(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Instant,
    val altitudeMeters: Double? = null,
)

/**
 * Một mẫu nhịp tim thô trung lập của phiên tập (Requirement 5.4).
 *
 * @property timestamp thời điểm ghi nhận (UTC).
 * @property bpm số nhịp mỗi phút.
 */
internal data class HuaweiRawHeartRate(
    val timestamp: Instant,
    val bpm: Int,
)
