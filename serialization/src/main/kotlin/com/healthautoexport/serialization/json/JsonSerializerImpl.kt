package com.healthautoexport.serialization.json

import com.healthautoexport.domain.model.CycleTrackingEntry
import com.healthautoexport.domain.model.EcgRecord
import com.healthautoexport.domain.model.ExportDataset
import com.healthautoexport.domain.model.ExtraValue
import com.healthautoexport.domain.model.HeartRateNotification
import com.healthautoexport.domain.model.HeartRateSample
import com.healthautoexport.domain.model.Medication
import com.healthautoexport.domain.model.MetricSeries
import com.healthautoexport.domain.model.MetricValue
import com.healthautoexport.domain.model.RoutePoint
import com.healthautoexport.domain.model.StateOfMind
import com.healthautoexport.domain.model.Symptom
import com.healthautoexport.domain.model.UnifiedRecord
import com.healthautoexport.domain.model.Workout
import com.healthautoexport.domain.model.WorkoutMetrics
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.math.BigDecimal

/**
 * Hiện thực [JsonSerializer] cho Export_Format JSON của App (Requirement 10).
 *
 * Chiến lược: dựng một cây [JsonElement] **thủ công** bằng DSL của kotlinx.serialization, nhờ đó
 * kiểm soát hoàn toàn việc định dạng số `qty` qua [JsonFormat.decimalElement]
 * (`BigDecimal.toPlainString`, không bao giờ là [Double]) — bảo đảm ký pháp thập phân và ≥ 6 chữ
 * số thập phân (Requirement 10.5). Cây được mã hóa thành chuỗi compact; khi ghi byte ở Destination,
 * chuỗi mã hóa UTF-8 **không BOM** (Requirement 10.2).
 *
 * Envelope `data` luôn chứa đủ tám mảng theo đúng thứ tự tài liệu hóa; danh mục rỗng là `[]`
 * (Requirements 10.1, 10.3).
 *
 * Lược đồ của từng phần tử khớp với [JsonParserImpl] để bảo toàn round-trip (Property 1):
 * - **Chỉ số tiêu chuẩn**: `{ qty, date, dataSource, extras? }`.
 * - **Lược đồ riêng** (Requirement 10.6): huyết áp `{ systolic, diastolic, ... }`, thống kê nhịp
 *   tim `{ min, avg, max, ... }`, tổng hợp tức thời `{ min, avg, max, count, ... }`, giấc ngủ
 *   `{ state, qty, ... }`, ECG `{ classification, averageBpm, samplingHz, voltages, ... }`.
 *
 * Mọi đại lượng [BigDecimal] đều ghi qua [JsonFormat.decimalElement]; số nguyên (count, duration,
 * bpm, stepCount) ghi dưới dạng số nguyên JSON; dấu thời gian theo `yyyy-MM-dd HH:mm:ss Z`
 * (Requirements 10.4, 10.7).
 */
class JsonSerializerImpl : JsonSerializer {

    private val json = Json { prettyPrint = false }

    override fun serialize(dataset: ExportDataset): String {
        val root = buildJsonObject {
            put(
                JsonFieldNames.DATA,
                buildJsonObject {
                    put(JsonFieldNames.METRICS, array(dataset.metrics, ::encodeSeries))
                    put(JsonFieldNames.WORKOUTS, array(dataset.workouts, ::encodeWorkout))
                    put(JsonFieldNames.STATE_OF_MIND, array(dataset.stateOfMind, ::encodeStateOfMind))
                    put(JsonFieldNames.MEDICATIONS, array(dataset.medications, ::encodeMedication))
                    put(JsonFieldNames.SYMPTOMS, array(dataset.symptoms, ::encodeSymptom))
                    put(JsonFieldNames.CYCLE_TRACKING, array(dataset.cycleTracking, ::encodeCycleTracking))
                    put(JsonFieldNames.ECG, array(dataset.ecg, ::encodeEcgRecord))
                    put(
                        JsonFieldNames.HEART_RATE_NOTIFICATIONS,
                        array(dataset.heartRateNotifications, ::encodeHeartRateNotification),
                    )
                },
            )
        }
        return json.encodeToString(JsonElement.serializer(), root)
    }

    // ---------------------------------------------------------------------------------------
    // metrics
    // ---------------------------------------------------------------------------------------

    private fun encodeSeries(series: MetricSeries): JsonObject = buildJsonObject {
        put(JsonFieldNames.NAME, series.name)
        put(JsonFieldNames.UNITS, series.units)
        put(JsonFieldNames.SERIES_DATA, array(series.data, ::encodeRecord))
    }

    private fun encodeRecord(record: UnifiedRecord): JsonObject = buildJsonObject {
        // Khóa đặc thù theo biến thể giá trị (Requirement 10.6).
        when (val value = record.value) {
            is MetricValue.Scalar ->
                put(JsonFieldNames.QTY, JsonFormat.decimalElement(value.qty))

            is MetricValue.BloodPressure -> {
                put(JsonFieldNames.SYSTOLIC, JsonFormat.decimalElement(value.systolic))
                put(JsonFieldNames.DIASTOLIC, JsonFormat.decimalElement(value.diastolic))
            }

            is MetricValue.HeartRateStat -> {
                put(JsonFieldNames.MIN, JsonFormat.decimalElement(value.min))
                put(JsonFieldNames.AVG, JsonFormat.decimalElement(value.avg))
                put(JsonFieldNames.MAX, JsonFormat.decimalElement(value.max))
            }

            is MetricValue.StatSummary -> {
                put(JsonFieldNames.MIN, JsonFormat.decimalElement(value.min))
                put(JsonFieldNames.AVG, JsonFormat.decimalElement(value.avg))
                put(JsonFieldNames.MAX, JsonFormat.decimalElement(value.max))
                put(JsonFieldNames.COUNT, value.count)
            }

            is MetricValue.SleepSegment -> {
                put(JsonFieldNames.STATE, value.state.name)
                // Thời lượng giai đoạn ghi là số nguyên giây (Requirement 6.1).
                put(JsonFieldNames.QTY, value.durationSeconds)
            }

            is MetricValue.Ecg -> {
                put(JsonFieldNames.CLASSIFICATION, value.classification)
                put(JsonFieldNames.AVERAGE_BPM, value.averageBpm)
                put(JsonFieldNames.SAMPLING_HZ, JsonFormat.decimalElement(value.samplingHz))
                put(JsonFieldNames.VOLTAGES, decimalArray(value.voltages))
            }
        }
        put(JsonFieldNames.DATE, JsonFormat.formatTimestamp(record.timestamp, record.zoneOffset))
        put(JsonFieldNames.DATA_SOURCE, record.dataSourceId.name)
        if (record.extras.isNotEmpty()) {
            put(JsonFieldNames.EXTRAS, encodeExtras(record.extras))
        }
    }

    private fun encodeExtras(extras: Map<String, ExtraValue>): JsonObject = buildJsonObject {
        extras.forEach { (key, value) -> put(key, encodeExtra(value)) }
    }

    private fun encodeExtra(value: ExtraValue): JsonObject = buildJsonObject {
        when (value) {
            is ExtraValue.StringValue -> {
                put(JsonFieldNames.EXTRA_KIND, JsonFieldNames.EXTRA_KIND_STRING)
                put(JsonFieldNames.EXTRA_VALUE, value.value)
            }

            is ExtraValue.NumberValue -> {
                put(JsonFieldNames.EXTRA_KIND, JsonFieldNames.EXTRA_KIND_NUMBER)
                put(JsonFieldNames.EXTRA_VALUE, JsonFormat.decimalElement(value.value))
            }

            is ExtraValue.EnumValue -> {
                put(JsonFieldNames.EXTRA_KIND, JsonFieldNames.EXTRA_KIND_ENUM)
                put(JsonFieldNames.EXTRA_VALUE, value.name)
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // workouts
    // ---------------------------------------------------------------------------------------

    private fun encodeWorkout(workout: Workout): JsonObject = buildJsonObject {
        put(JsonFieldNames.ID, workout.id)
        put(JsonFieldNames.TYPE, workout.type.name)
        put(JsonFieldNames.START, JsonFormat.formatTimestampUtc(workout.start))
        put(JsonFieldNames.END, JsonFormat.formatTimestampUtc(workout.end))
        put(JsonFieldNames.DURATION, workout.durationSeconds)
        put(JsonFieldNames.DATA_SOURCE, workout.dataSourceId.name)

        // route/heartRateData: chỉ phát khi khả dụng để round-trip null ↔ vắng khóa (Req 5.5).
        workout.route?.let { put(JsonFieldNames.ROUTE, array(it, ::encodeRoutePoint)) }
        workout.heartRateSeries?.let {
            put(JsonFieldNames.HEART_RATE_DATA, array(it, ::encodeHeartRateSample))
        }
        encodeWorkoutMetrics(this, workout.optionalFields)
    }

    /** Phát chỉ các trường tùy chọn khả dụng của Workout (Requirement 5.5). */
    private fun encodeWorkoutMetrics(builder: JsonObjectBuilder, metrics: WorkoutMetrics) {
        metrics.activeEnergyKcal?.let { builder.put(JsonFieldNames.ACTIVE_ENERGY, JsonFormat.decimalElement(it)) }
        metrics.totalEnergyKcal?.let { builder.put(JsonFieldNames.TOTAL_ENERGY, JsonFormat.decimalElement(it)) }
        metrics.distanceMeters?.let { builder.put(JsonFieldNames.DISTANCE, JsonFormat.decimalElement(it)) }
        metrics.avgSpeedMps?.let { builder.put(JsonFieldNames.AVG_SPEED, JsonFormat.decimalElement(it)) }
        metrics.elevationGainMeters?.let { builder.put(JsonFieldNames.ELEVATION_GAIN, JsonFormat.decimalElement(it)) }
        metrics.stepCount?.let { builder.put(JsonFieldNames.STEP_COUNT, it) }
        metrics.heartRateRecovery?.let {
            builder.put(JsonFieldNames.HEART_RATE_RECOVERY, array(it, ::encodeHeartRateSample))
        }
    }

    private fun encodeRoutePoint(point: RoutePoint): JsonObject = buildJsonObject {
        put(JsonFieldNames.LAT, JsonPrimitive(point.latitude))
        put(JsonFieldNames.LON, JsonPrimitive(point.longitude))
        put(JsonFieldNames.DATE, JsonFormat.formatTimestampUtc(point.timestamp))
        point.altitudeMeters?.let { put(JsonFieldNames.ALTITUDE, JsonPrimitive(it)) }
    }

    private fun encodeHeartRateSample(sample: HeartRateSample): JsonObject = buildJsonObject {
        put(JsonFieldNames.DATE, JsonFormat.formatTimestampUtc(sample.timestamp))
        put(JsonFieldNames.QTY, sample.bpm)
    }

    // ---------------------------------------------------------------------------------------
    // stateOfMind / medications / symptoms / cycleTracking
    // ---------------------------------------------------------------------------------------

    private fun encodeStateOfMind(entry: StateOfMind): JsonObject = buildJsonObject {
        put(JsonFieldNames.DATE, JsonFormat.formatTimestamp(entry.timestamp, entry.zoneOffset))
        put(JsonFieldNames.KIND, entry.kind)
        entry.valence?.let { put(JsonFieldNames.VALENCE, JsonFormat.decimalElement(it)) }
        put(JsonFieldNames.LABELS, stringArray(entry.labels))
        put(JsonFieldNames.ASSOCIATIONS, stringArray(entry.associations))
    }

    private fun encodeMedication(entry: Medication): JsonObject = buildJsonObject {
        put(JsonFieldNames.DATE, JsonFormat.formatTimestamp(entry.timestamp, entry.zoneOffset))
        put(JsonFieldNames.NAME, entry.name)
        entry.dose?.let { put(JsonFieldNames.DOSE, it) }
        entry.unit?.let { put(JsonFieldNames.UNITS, it) }
    }

    private fun encodeSymptom(entry: Symptom): JsonObject = buildJsonObject {
        put(JsonFieldNames.DATE, JsonFormat.formatTimestamp(entry.timestamp, entry.zoneOffset))
        put(JsonFieldNames.NAME, entry.name)
        entry.severity?.let { put(JsonFieldNames.SEVERITY, it) }
    }

    private fun encodeCycleTracking(entry: CycleTrackingEntry): JsonObject = buildJsonObject {
        put(JsonFieldNames.DATE, JsonFormat.formatTimestamp(entry.timestamp, entry.zoneOffset))
        entry.flow?.let { put(JsonFieldNames.FLOW, it) }
        entry.ovulationTestResult?.let { put(JsonFieldNames.OVULATION_TEST, it) }
        entry.sexualActivity?.let { put(JsonFieldNames.SEXUAL_ACTIVITY, it) }
    }

    // ---------------------------------------------------------------------------------------
    // ecg / heartRateNotifications
    // ---------------------------------------------------------------------------------------

    private fun encodeEcgRecord(record: EcgRecord): JsonObject = buildJsonObject {
        put(JsonFieldNames.DATE, JsonFormat.formatTimestamp(record.timestamp, record.zoneOffset))
        put(JsonFieldNames.CLASSIFICATION, record.classification)
        put(JsonFieldNames.AVERAGE_BPM, record.averageBpm)
        put(JsonFieldNames.SAMPLING_HZ, JsonFormat.decimalElement(record.samplingHz))
        put(JsonFieldNames.VOLTAGES, decimalArray(record.voltages))
        put(JsonFieldNames.DATA_SOURCE, record.dataSourceId.name)
    }

    private fun encodeHeartRateNotification(event: HeartRateNotification): JsonObject = buildJsonObject {
        put(JsonFieldNames.KIND, event.kind.name)
        put(JsonFieldNames.START, JsonFormat.formatTimestamp(event.start, event.startZoneOffset))
        put(JsonFieldNames.END, JsonFormat.formatTimestamp(event.end, event.endZoneOffset))
        put(JsonFieldNames.THRESHOLD_BPM, JsonFormat.decimalElement(event.thresholdBpm))
        put(JsonFieldNames.SAMPLES, array(event.samples, ::encodeHeartRateSample))
        put(JsonFieldNames.DATA_SOURCE, event.dataSourceId.name)
    }

    // ---------------------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------------------

    private fun <T> array(items: List<T>, encode: (T) -> JsonElement): JsonElement = buildJsonArray {
        items.forEach { add(encode(it)) }
    }

    private fun stringArray(items: List<String>): JsonElement = buildJsonArray {
        items.forEach { add(it) }
    }

    private fun decimalArray(items: List<BigDecimal>): JsonElement = buildJsonArray {
        items.forEach { add(JsonFormat.decimalElement(it)) }
    }
}
