package com.healthautoexport.data.huawei

import com.healthautoexport.domain.logic.MapOutcome
import com.healthautoexport.domain.logic.MapWithWarnings
import com.healthautoexport.domain.model.CanonicalUnit
import com.healthautoexport.domain.model.DataSourceId
import com.healthautoexport.domain.model.ExtraValue
import com.healthautoexport.domain.model.HealthMetricType
import com.healthautoexport.domain.model.MetricCatalog
import com.healthautoexport.domain.model.MetricSchema
import com.healthautoexport.domain.model.MetricValue
import com.healthautoexport.domain.model.RoutePoint
import com.healthautoexport.domain.model.SleepState
import com.healthautoexport.domain.model.UnifiedRecord
import com.healthautoexport.domain.model.Workout
import com.healthautoexport.domain.model.WorkoutMetrics
import com.healthautoexport.domain.model.HeartRateSample
import com.healthautoexport.domain.port.ReadWarning
import java.math.BigDecimal

/**
 * Dịch các DTO **trung lập** mà [HuaweiHealthClient] trả về ([HuaweiRawSample]/[HuaweiRawWorkout])
 * sang mô hình hợp nhất của domain ([UnifiedRecord]/[Workout]), với:
 *
 * - **đơn vị canonical** lấy từ [MetricCatalog] (Requirement 4.2), và
 * - **`dataSourceId = [DataSourceId.HUAWEI_HEALTH_KIT]`** trên mọi bản ghi để bảo toàn nguồn gốc
 *   (Requirement 4.5).
 *
 * Vì kiểu dữ liệu thô đã được trừu tượng hóa sau [HuaweiHealthClient] (xem chú thích interface),
 * mapper này thao tác hoàn toàn trên DTO trung lập và **không** phụ thuộc SDK Huawei. Mapper áp
 * dụng quy tắc **bỏ-qua-và-tiếp-tục** (Requirements 4.7, 6.6) qua [MapWithWarnings]: bản ghi không
 * ánh xạ/không chuyển đơn vị được bị bỏ qua kèm một [ReadWarning], không hủy cả Export_Job.
 *
 * Lưu ý phạm vi: bản hiện thực [NoOpHuaweiHealthClient] luôn trả về danh sách rỗng, nên dưới NoOp
 * mapper cũng trả về kết quả rỗng. Việc chuyển đổi đơn vị gốc→canonical ở đây giữ ở mức tối thiểu
 * (Huawei thường đã dùng đơn vị tương thích); một bản hiện thực thật cho Huawei flavor có thể mở
 * rộng [convertToCanonical] cho các đơn vị gốc cần quy đổi.
 */
internal class HuaweiMetricMapper {

    private val source = DataSourceId.HUAWEI_HEALTH_KIT

    /**
     * Ánh xạ danh sách [raw] sang [UnifiedRecord] đã chuẩn hóa, gom cảnh báo cho bản ghi bị bỏ
     * (Requirements 4.2, 4.5, 4.7).
     *
     * @return cặp `(records, warnings)`: bản ghi giữ lại (đã đóng dấu nguồn) và cảnh báo phát sinh.
     */
    fun mapRecords(raw: List<HuaweiRawSample>): MappedRecords {
        val result = MapWithWarnings.mapRecords(
            raw = raw,
            source = source,
            map = ::mapSingle,
        )
        return MappedRecords(records = result.kept, warnings = result.warnings)
    }

    /**
     * Ánh xạ danh sách phiên tập thô sang [Workout] (Requirement 5.x), bỏ-qua-và-tiếp-tục với
     * phiên không hợp lệ.
     *
     * @return cặp `(workouts, warnings)`.
     */
    fun mapWorkouts(raw: List<HuaweiRawWorkout>): MappedWorkouts {
        val result = MapWithWarnings.collect(
            raw = raw,
            onError = { _, error ->
                ReadWarning(
                    source = source,
                    metric = null,
                    message = "Bỏ qua một phiên tập Huawei không ánh xạ được: " +
                        (error.message ?: error.javaClass.simpleName),
                )
            },
            map = ::mapWorkout,
        )
        return MappedWorkouts(workouts = result.kept, warnings = result.warnings)
    }

    /** Ánh xạ một mẫu thô → [MapOutcome] của [UnifiedRecord]. */
    private fun mapSingle(raw: HuaweiRawSample): MapOutcome<UnifiedRecord> {
        val spec = MetricCatalog.spec(raw.metric)
        val value = mapValue(raw, spec) ?: return skip(raw.metric, "giá trị không hợp lệ hoặc thiếu")
        val record = UnifiedRecord(
            metric = raw.metric,
            value = value,
            unit = spec.unit,
            timestamp = raw.startTime,
            zoneOffset = raw.zoneOffset,
            dataSourceId = source,
            extras = mapExtras(raw.extras),
        )
        return MapOutcome.Kept(record)
    }

    /** Chuyển giá trị thô (vô hướng hoặc có cấu trúc) sang [MetricValue] theo [spec]. */
    private fun mapValue(raw: HuaweiRawSample, spec: MetricCatalog.Spec): MetricValue? =
        when (spec.schema) {
            MetricSchema.BLOOD_PRESSURE -> {
                val bp = raw.structured as? HuaweiStructuredValue.BloodPressure ?: return null
                MetricValue.BloodPressure(
                    systolic = bp.systolic.toBigDecimal(),
                    diastolic = bp.diastolic.toBigDecimal(),
                )
            }
            MetricSchema.SLEEP -> {
                val seg = raw.structured as? HuaweiStructuredValue.SleepSegment ?: return null
                if (seg.durationSeconds < 0) return null
                MetricValue.SleepSegment(
                    state = mapSleepState(seg.stateName),
                    durationSeconds = seg.durationSeconds,
                )
            }
            // Các metric vô hướng (STANDARD) cũng như mọi schema còn lại mà Huawei cung cấp dưới
            // dạng giá trị đơn được biểu diễn bằng Scalar ở đơn vị canonical.
            else -> {
                val qty = raw.value ?: return null
                val canonical = convertToCanonical(qty, raw.rawUnit, spec.unit) ?: return null
                MetricValue.Scalar(canonical)
            }
        }

    /** Ánh xạ một phiên tập thô → [MapOutcome] của [Workout] (Requirements 5.1–5.5). */
    private fun mapWorkout(raw: HuaweiRawWorkout): MapOutcome<Workout> {
        if (raw.endTime.isBefore(raw.startTime)) {
            return MapOutcome.Skipped(
                listOf(
                    ReadWarning(
                        source = source,
                        metric = null,
                        message = "Bỏ qua phiên tập Huawei có thời điểm kết thúc trước thời điểm bắt đầu",
                    ),
                ),
            )
        }
        val durationSeconds = raw.endTime.epochSecond - raw.startTime.epochSecond
        // Tuyến đường/nhịp tim được sắp xếp tăng dần theo timestamp (Requirements 5.2, 5.4).
        val route = raw.route
            ?.map { RoutePoint(it.latitude, it.longitude, it.timestamp, it.altitudeMeters) }
            ?.sortedBy { it.timestamp }
        val heartRate = raw.heartRate
            ?.map { HeartRateSample(it.timestamp, it.bpm) }
            ?.sortedBy { it.timestamp }
        val workout = Workout(
            id = raw.id,
            type = raw.type,
            start = raw.startTime,
            end = raw.endTime,
            durationSeconds = durationSeconds,
            route = route,
            heartRateSeries = heartRate,
            optionalFields = WorkoutMetrics(),
            dataSourceId = source,
        )
        return MapOutcome.Kept(workout)
    }

    /**
     * Quy đổi [qty] từ đơn vị gốc của Huawei ([rawUnit]) sang đơn vị canonical [target].
     *
     * Mặc định Huawei cung cấp giá trị ở đơn vị tương thích canonical, nên phép quy đổi là đồng
     * nhất (giữ nguyên). Trả về `null` để báo "không chuyển được" khi cần (Requirement 4.7) — bản
     * hiện thực thật có thể bổ sung các quy tắc quy đổi (vd mmol/L → mg/dL) tại đây.
     */
    private fun convertToCanonical(
        qty: Double,
        @Suppress("UNUSED_PARAMETER") rawUnit: String?,
        @Suppress("UNUSED_PARAMETER") target: CanonicalUnit,
    ): BigDecimal? {
        if (qty.isNaN() || qty.isInfinite()) return null
        return qty.toBigDecimal()
    }

    /** Ánh xạ tên trạng thái giấc ngủ trung lập của Huawei sang [SleepState]. */
    private fun mapSleepState(name: String): SleepState =
        when (name.trim().uppercase()) {
            "AWAKE" -> SleepState.AWAKE
            "REM" -> SleepState.REM
            "CORE", "LIGHT" -> SleepState.CORE
            "DEEP" -> SleepState.DEEP
            "ASLEEP" -> SleepState.ASLEEP
            "IN_BED", "INBED" -> SleepState.IN_BED
            else -> SleepState.UNSPECIFIED
        }

    /** Dịch siêu dữ liệu phụ trung lập (chuỗi) sang [ExtraValue.EnumValue] theo khóa. */
    private fun mapExtras(extras: Map<String, String>): Map<String, ExtraValue> =
        extras.mapValues { (_, v) -> ExtraValue.EnumValue(v) }

    private fun skip(metric: HealthMetricType, reason: String): MapOutcome<UnifiedRecord> =
        MapOutcome.Skipped(
            listOf(
                ReadWarning(
                    source = source,
                    metric = metric,
                    message = "Bỏ qua bản ghi Huawei (${metric.name}): $reason",
                ),
            ),
        )

    /**
     * Kết quả ánh xạ bản ghi: các [UnifiedRecord] giữ lại và cảnh báo phát sinh.
     *
     * @property records bản ghi đã chuẩn hóa, mang `dataSourceId = HUAWEI_HEALTH_KIT`.
     * @property warnings cảnh báo về bản ghi bị bỏ (Requirements 4.7, 6.6).
     */
    data class MappedRecords(
        val records: List<UnifiedRecord>,
        val warnings: List<ReadWarning>,
    )

    /**
     * Kết quả ánh xạ phiên tập: các [Workout] giữ lại và cảnh báo phát sinh.
     *
     * @property workouts phiên tập đã ánh xạ, mang `dataSourceId = HUAWEI_HEALTH_KIT`.
     * @property warnings cảnh báo về phiên tập bị bỏ.
     */
    data class MappedWorkouts(
        val workouts: List<Workout>,
        val warnings: List<ReadWarning>,
    )
}
