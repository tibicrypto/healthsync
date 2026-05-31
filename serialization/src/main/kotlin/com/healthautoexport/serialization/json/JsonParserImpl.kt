package com.healthautoexport.serialization.json

import com.healthautoexport.domain.model.CanonicalUnit
import com.healthautoexport.domain.model.CycleTrackingEntry
import com.healthautoexport.domain.model.DataSourceId
import com.healthautoexport.domain.model.EcgRecord
import com.healthautoexport.domain.model.ExportDataset
import com.healthautoexport.domain.model.ExtraValue
import com.healthautoexport.domain.model.HealthMetricType
import com.healthautoexport.domain.model.HeartRateNotification
import com.healthautoexport.domain.model.HeartRateNotificationKind
import com.healthautoexport.domain.model.HeartRateSample
import com.healthautoexport.domain.model.Medication
import com.healthautoexport.domain.model.MetricCatalog
import com.healthautoexport.domain.model.MetricSeries
import com.healthautoexport.domain.model.MetricValue
import com.healthautoexport.domain.model.RoutePoint
import com.healthautoexport.domain.model.SleepState
import com.healthautoexport.domain.model.StateOfMind
import com.healthautoexport.domain.model.Symptom
import com.healthautoexport.domain.model.UnifiedRecord
import com.healthautoexport.domain.model.Workout
import com.healthautoexport.domain.model.WorkoutMetrics
import com.healthautoexport.domain.model.WorkoutType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Hiện thực [JsonParser] cho Export_Format JSON của App (Requirements 10.8, 10.9).
 *
 * Bộ phân tích đọc văn bản JSON do [JsonSerializerImpl] sinh ra và khôi phục lại [ExportDataset]
 * tương đương, bảo toàn round-trip (Property 1). Lược đồ đọc khớp **chính xác** với lược đồ ghi
 * (xem [JsonFieldNames]).
 *
 * Chiến lược **không tạo dataset một phần** (Requirement 10.9): toàn bộ quá trình dựng dataset
 * chạy bên trong một khối `try`; mọi vi phạm lược đồ ném [JsonParseException] mang con trỏ vị trí
 * (pointer) và làm hủy toàn bộ quá trình. Dataset chỉ được trả về qua [Result.success] **sau khi**
 * dựng xong trọn vẹn; nếu có lỗi, trả về [Result.failure] và không có dataset bộ phận nào lọt ra.
 *
 * Phân biệt biến thể [MetricValue] của một bản ghi chỉ số dựa trên tập khóa hiện diện (theo thứ
 * tự ưu tiên để tránh nhập nhằng):
 * 1. có `systolic` → [MetricValue.BloodPressure];
 * 2. có `classification` → [MetricValue.Ecg];
 * 3. có `state` → [MetricValue.SleepSegment];
 * 4. có `min` → [MetricValue.StatSummary] nếu kèm `count`, ngược lại [MetricValue.HeartRateStat];
 * 5. có `qty` → [MetricValue.Scalar];
 * 6. còn lại → lỗi (Requirement 10.9).
 */
class JsonParserImpl : JsonParser {

    private val json = Json { isLenient = false }

    override fun parse(text: String): Result<ExportDataset> = runCatching {
        val root = parseRoot(text)
        val data = root.obj(JsonFieldNames.DATA, "data")

        // Yêu cầu đủ tám khóa mảng (Requirements 10.1, 10.3); thiếu/sai kiểu → lỗi (Req 10.9).
        ExportDataset(
            metrics = data.array(JsonFieldNames.METRICS, "data/metrics")
                .mapIndexed { i, e -> parseSeries(e, "data/metrics[$i]") },
            workouts = data.array(JsonFieldNames.WORKOUTS, "data/workouts")
                .mapIndexed { i, e -> parseWorkout(e, "data/workouts[$i]") },
            stateOfMind = data.array(JsonFieldNames.STATE_OF_MIND, "data/stateOfMind")
                .mapIndexed { i, e -> parseStateOfMind(e, "data/stateOfMind[$i]") },
            medications = data.array(JsonFieldNames.MEDICATIONS, "data/medications")
                .mapIndexed { i, e -> parseMedication(e, "data/medications[$i]") },
            symptoms = data.array(JsonFieldNames.SYMPTOMS, "data/symptoms")
                .mapIndexed { i, e -> parseSymptom(e, "data/symptoms[$i]") },
            cycleTracking = data.array(JsonFieldNames.CYCLE_TRACKING, "data/cycleTracking")
                .mapIndexed { i, e -> parseCycleTracking(e, "data/cycleTracking[$i]") },
            ecg = data.array(JsonFieldNames.ECG, "data/ecg")
                .mapIndexed { i, e -> parseEcgRecord(e, "data/ecg[$i]") },
            heartRateNotifications = data.array(JsonFieldNames.HEART_RATE_NOTIFICATIONS, "data/heartRateNotifications")
                .mapIndexed { i, e -> parseHeartRateNotification(e, "data/heartRateNotifications[$i]") },
        )
    }

    private fun parseRoot(text: String): JsonObject {
        val element = try {
            json.parseToJsonElement(text)
        } catch (e: Exception) {
            throw JsonParseException("", "JSON không phân tích được: ${e.message}")
        }
        return element as? JsonObject
            ?: throw JsonParseException("", "phần tử cấp cao nhất phải là một đối tượng JSON")
    }

    // ---------------------------------------------------------------------------------------
    // metrics
    // ---------------------------------------------------------------------------------------

    private fun parseSeries(element: JsonElement, ptr: String): MetricSeries {
        val obj = element.asObject(ptr)
        val name = obj.string(JsonFieldNames.NAME, "$ptr/name")
        val units = obj.string(JsonFieldNames.UNITS, "$ptr/units")
        // `metric` và `unit` của mỗi UnifiedRecord không được mã hóa từng bản ghi: trong một
        // dataset hợp lệ (do pipeline/serializer sinh ra) mọi bản ghi của một series chia sẻ cùng
        // metric/unit, được suy ra từ `name`/`units` của series qua MetricCatalog/CanonicalUnit.
        // Nhờ vậy round-trip (Property 1) được bảo toàn ở cấp UnifiedRecord.
        val metric = metricForName(name, "$ptr/name")
        val unit = unitForSymbol(units, "$ptr/units")
        val data = obj.array(JsonFieldNames.SERIES_DATA, "$ptr/data")
            .mapIndexed { i, e -> parseRecord(e, metric, unit, "$ptr/data[$i]") }
        return MetricSeries(name = name, units = units, data = data)
    }

    private fun parseRecord(
        element: JsonElement,
        metric: HealthMetricType,
        unit: CanonicalUnit,
        ptr: String,
    ): UnifiedRecord {
        val obj = element.asObject(ptr)
        val value = parseMetricValue(obj, ptr)
        val date = obj.string(JsonFieldNames.DATE, "$ptr/date")
        val timestamp = parseTimestamp(date, "$ptr/date")
        val dataSource = parseDataSource(obj, ptr)
        val extras = parseExtras(obj, ptr)
        return UnifiedRecord(
            metric = metric,
            value = value,
            unit = unit,
            timestamp = timestamp.toInstant(),
            zoneOffset = timestamp.offset,
            dataSourceId = dataSource,
            extras = extras,
        )
    }

    private fun parseMetricValue(obj: JsonObject, ptr: String): MetricValue = when {
        obj.containsKey(JsonFieldNames.SYSTOLIC) -> MetricValue.BloodPressure(
            systolic = obj.decimal(JsonFieldNames.SYSTOLIC, "$ptr/systolic"),
            diastolic = obj.decimal(JsonFieldNames.DIASTOLIC, "$ptr/diastolic"),
        )

        obj.containsKey(JsonFieldNames.CLASSIFICATION) -> MetricValue.Ecg(
            classification = obj.string(JsonFieldNames.CLASSIFICATION, "$ptr/classification"),
            averageBpm = obj.int(JsonFieldNames.AVERAGE_BPM, "$ptr/averageBpm"),
            samplingHz = obj.decimal(JsonFieldNames.SAMPLING_HZ, "$ptr/samplingHz"),
            voltages = obj.decimalList(JsonFieldNames.VOLTAGES, "$ptr/voltages"),
        )

        obj.containsKey(JsonFieldNames.STATE) -> MetricValue.SleepSegment(
            state = obj.enumValue(JsonFieldNames.STATE, "$ptr/state") { SleepState.valueOf(it) },
            durationSeconds = obj.long(JsonFieldNames.QTY, "$ptr/qty"),
        )

        obj.containsKey(JsonFieldNames.MIN) -> {
            val min = obj.decimal(JsonFieldNames.MIN, "$ptr/min")
            val avg = obj.decimal(JsonFieldNames.AVG, "$ptr/avg")
            val max = obj.decimal(JsonFieldNames.MAX, "$ptr/max")
            if (obj.containsKey(JsonFieldNames.COUNT)) {
                MetricValue.StatSummary(min, avg, max, obj.long(JsonFieldNames.COUNT, "$ptr/count"))
            } else {
                MetricValue.HeartRateStat(min, avg, max)
            }
        }

        obj.containsKey(JsonFieldNames.QTY) ->
            MetricValue.Scalar(obj.decimal(JsonFieldNames.QTY, "$ptr/qty"))

        else -> throw JsonParseException(
            ptr,
            "bản ghi chỉ số thiếu trường giá trị (qty/systolic/min/state/classification)",
        )
    }

    private fun parseExtras(obj: JsonObject, ptr: String): Map<String, ExtraValue> {
        val extras = obj[JsonFieldNames.EXTRAS] ?: return emptyMap()
        val extrasObj = extras.asObject("$ptr/extras")
        return extrasObj.mapValues { (key, value) ->
            parseExtra(value, "$ptr/extras/$key")
        }
    }

    private fun parseExtra(element: JsonElement, ptr: String): ExtraValue {
        val obj = element.asObject(ptr)
        return when (val kind = obj.string(JsonFieldNames.EXTRA_KIND, "$ptr/kind")) {
            JsonFieldNames.EXTRA_KIND_STRING ->
                ExtraValue.StringValue(obj.string(JsonFieldNames.EXTRA_VALUE, "$ptr/value"))

            JsonFieldNames.EXTRA_KIND_NUMBER ->
                ExtraValue.NumberValue(obj.decimal(JsonFieldNames.EXTRA_VALUE, "$ptr/value"))

            JsonFieldNames.EXTRA_KIND_ENUM ->
                ExtraValue.EnumValue(obj.string(JsonFieldNames.EXTRA_VALUE, "$ptr/value"))

            else -> throw JsonParseException("$ptr/kind", "loại extra không hợp lệ: '$kind'")
        }
    }

    // ---------------------------------------------------------------------------------------
    // workouts
    // ---------------------------------------------------------------------------------------

    private fun parseWorkout(element: JsonElement, ptr: String): Workout {
        val obj = element.asObject(ptr)
        return Workout(
            id = obj.string(JsonFieldNames.ID, "$ptr/id"),
            type = obj.enumValue(JsonFieldNames.TYPE, "$ptr/type") { WorkoutType.valueOf(it) },
            start = parseTimestamp(obj.string(JsonFieldNames.START, "$ptr/start"), "$ptr/start").toInstant(),
            end = parseTimestamp(obj.string(JsonFieldNames.END, "$ptr/end"), "$ptr/end").toInstant(),
            durationSeconds = obj.long(JsonFieldNames.DURATION, "$ptr/duration"),
            route = obj[JsonFieldNames.ROUTE]?.let { routeEl ->
                routeEl.asArray("$ptr/route").mapIndexed { i, e -> parseRoutePoint(e, "$ptr/route[$i]") }
            },
            heartRateSeries = obj[JsonFieldNames.HEART_RATE_DATA]?.let { hrEl ->
                hrEl.asArray("$ptr/heartRateData").mapIndexed { i, e -> parseHeartRateSample(e, "$ptr/heartRateData[$i]") }
            },
            optionalFields = parseWorkoutMetrics(obj, ptr),
            dataSourceId = parseDataSource(obj, ptr),
        )
    }

    private fun parseWorkoutMetrics(obj: JsonObject, ptr: String): WorkoutMetrics = WorkoutMetrics(
        activeEnergyKcal = obj.optionalDecimal(JsonFieldNames.ACTIVE_ENERGY, "$ptr/activeEnergy"),
        totalEnergyKcal = obj.optionalDecimal(JsonFieldNames.TOTAL_ENERGY, "$ptr/totalEnergy"),
        distanceMeters = obj.optionalDecimal(JsonFieldNames.DISTANCE, "$ptr/distance"),
        avgSpeedMps = obj.optionalDecimal(JsonFieldNames.AVG_SPEED, "$ptr/avgSpeed"),
        elevationGainMeters = obj.optionalDecimal(JsonFieldNames.ELEVATION_GAIN, "$ptr/elevationGain"),
        stepCount = obj.optionalLong(JsonFieldNames.STEP_COUNT, "$ptr/stepCount"),
        heartRateRecovery = obj[JsonFieldNames.HEART_RATE_RECOVERY]?.let { hrEl ->
            hrEl.asArray("$ptr/heartRateRecovery")
                .mapIndexed { i, e -> parseHeartRateSample(e, "$ptr/heartRateRecovery[$i]") }
        },
    )

    private fun parseRoutePoint(element: JsonElement, ptr: String): RoutePoint {
        val obj = element.asObject(ptr)
        return RoutePoint(
            latitude = obj.double(JsonFieldNames.LAT, "$ptr/lat"),
            longitude = obj.double(JsonFieldNames.LON, "$ptr/lon"),
            timestamp = parseTimestamp(obj.string(JsonFieldNames.DATE, "$ptr/date"), "$ptr/date").toInstant(),
            altitudeMeters = obj.optionalDouble(JsonFieldNames.ALTITUDE, "$ptr/altitude"),
        )
    }

    private fun parseHeartRateSample(element: JsonElement, ptr: String): HeartRateSample {
        val obj = element.asObject(ptr)
        return HeartRateSample(
            timestamp = parseTimestamp(obj.string(JsonFieldNames.DATE, "$ptr/date"), "$ptr/date").toInstant(),
            bpm = obj.int(JsonFieldNames.QTY, "$ptr/qty"),
        )
    }

    // ---------------------------------------------------------------------------------------
    // stateOfMind / medications / symptoms / cycleTracking
    // ---------------------------------------------------------------------------------------

    private fun parseStateOfMind(element: JsonElement, ptr: String): StateOfMind {
        val obj = element.asObject(ptr)
        val ts = parseTimestamp(obj.string(JsonFieldNames.DATE, "$ptr/date"), "$ptr/date")
        return StateOfMind(
            timestamp = ts.toInstant(),
            zoneOffset = ts.offset,
            kind = obj.string(JsonFieldNames.KIND, "$ptr/kind"),
            valence = obj.optionalDecimal(JsonFieldNames.VALENCE, "$ptr/valence"),
            labels = obj.stringList(JsonFieldNames.LABELS, "$ptr/labels"),
            associations = obj.stringList(JsonFieldNames.ASSOCIATIONS, "$ptr/associations"),
        )
    }

    private fun parseMedication(element: JsonElement, ptr: String): Medication {
        val obj = element.asObject(ptr)
        val ts = parseTimestamp(obj.string(JsonFieldNames.DATE, "$ptr/date"), "$ptr/date")
        return Medication(
            timestamp = ts.toInstant(),
            zoneOffset = ts.offset,
            name = obj.string(JsonFieldNames.NAME, "$ptr/name"),
            dose = obj.optionalString(JsonFieldNames.DOSE, "$ptr/dose"),
            unit = obj.optionalString(JsonFieldNames.UNITS, "$ptr/units"),
        )
    }

    private fun parseSymptom(element: JsonElement, ptr: String): Symptom {
        val obj = element.asObject(ptr)
        val ts = parseTimestamp(obj.string(JsonFieldNames.DATE, "$ptr/date"), "$ptr/date")
        return Symptom(
            timestamp = ts.toInstant(),
            zoneOffset = ts.offset,
            name = obj.string(JsonFieldNames.NAME, "$ptr/name"),
            severity = obj.optionalString(JsonFieldNames.SEVERITY, "$ptr/severity"),
        )
    }

    private fun parseCycleTracking(element: JsonElement, ptr: String): CycleTrackingEntry {
        val obj = element.asObject(ptr)
        val ts = parseTimestamp(obj.string(JsonFieldNames.DATE, "$ptr/date"), "$ptr/date")
        return CycleTrackingEntry(
            timestamp = ts.toInstant(),
            zoneOffset = ts.offset,
            flow = obj.optionalString(JsonFieldNames.FLOW, "$ptr/flow"),
            ovulationTestResult = obj.optionalString(JsonFieldNames.OVULATION_TEST, "$ptr/ovulationTest"),
            sexualActivity = obj.optionalBoolean(JsonFieldNames.SEXUAL_ACTIVITY, "$ptr/sexualActivity"),
        )
    }

    // ---------------------------------------------------------------------------------------
    // ecg / heartRateNotifications
    // ---------------------------------------------------------------------------------------

    private fun parseEcgRecord(element: JsonElement, ptr: String): EcgRecord {
        val obj = element.asObject(ptr)
        val ts = parseTimestamp(obj.string(JsonFieldNames.DATE, "$ptr/date"), "$ptr/date")
        return EcgRecord(
            timestamp = ts.toInstant(),
            zoneOffset = ts.offset,
            classification = obj.string(JsonFieldNames.CLASSIFICATION, "$ptr/classification"),
            averageBpm = obj.int(JsonFieldNames.AVERAGE_BPM, "$ptr/averageBpm"),
            samplingHz = obj.decimal(JsonFieldNames.SAMPLING_HZ, "$ptr/samplingHz"),
            voltages = obj.decimalList(JsonFieldNames.VOLTAGES, "$ptr/voltages"),
            dataSourceId = parseDataSource(obj, ptr),
        )
    }

    private fun parseHeartRateNotification(element: JsonElement, ptr: String): HeartRateNotification {
        val obj = element.asObject(ptr)
        val start = parseTimestamp(obj.string(JsonFieldNames.START, "$ptr/start"), "$ptr/start")
        val end = parseTimestamp(obj.string(JsonFieldNames.END, "$ptr/end"), "$ptr/end")
        return HeartRateNotification(
            kind = obj.enumValue(JsonFieldNames.KIND, "$ptr/kind") { HeartRateNotificationKind.valueOf(it) },
            start = start.toInstant(),
            startZoneOffset = start.offset,
            end = end.toInstant(),
            endZoneOffset = end.offset,
            thresholdBpm = obj.decimal(JsonFieldNames.THRESHOLD_BPM, "$ptr/thresholdBpm"),
            samples = obj.array(JsonFieldNames.SAMPLES, "$ptr/samples")
                .mapIndexed { i, e -> parseHeartRateSample(e, "$ptr/samples[$i]") },
            dataSourceId = parseDataSource(obj, ptr),
        )
    }

    // ---------------------------------------------------------------------------------------
    // shared field readers — mỗi lỗi ném JsonParseException kèm pointer (Requirement 10.9)
    // ---------------------------------------------------------------------------------------

    private fun parseDataSource(obj: JsonObject, ptr: String): DataSourceId {
        val raw = obj.string(JsonFieldNames.DATA_SOURCE, "$ptr/dataSource")
        return try {
            DataSourceId.valueOf(raw)
        } catch (e: IllegalArgumentException) {
            throw JsonParseException("$ptr/dataSource", "DataSourceId không hợp lệ: '$raw'")
        }
    }

    private fun parseTimestamp(text: String, ptr: String): OffsetDateTime =
        try {
            JsonFormat.parseTimestamp(text)
        } catch (e: Exception) {
            throw JsonParseException(ptr, "dấu thời gian không khớp 'yyyy-MM-dd HH:mm:ss Z': '$text'")
        }

    private fun JsonObject.obj(key: String, ptr: String): JsonObject =
        (this[key] ?: throw JsonParseException(ptr, "thiếu khóa bắt buộc")).asObject(ptr)

    private fun JsonObject.array(key: String, ptr: String): JsonArray =
        (this[key] ?: throw JsonParseException(ptr, "thiếu khóa mảng bắt buộc")).asArray(ptr)

    private fun JsonObject.string(key: String, ptr: String): String =
        (this[key] ?: throw JsonParseException(ptr, "thiếu khóa bắt buộc")).asString(ptr)

    private fun JsonObject.optionalString(key: String, ptr: String): String? =
        this[key]?.asString(ptr)

    private fun JsonObject.decimal(key: String, ptr: String): BigDecimal =
        (this[key] ?: throw JsonParseException(ptr, "thiếu khóa số bắt buộc")).asDecimal(ptr)

    private fun JsonObject.optionalDecimal(key: String, ptr: String): BigDecimal? =
        this[key]?.asDecimal(ptr)

    private fun JsonObject.long(key: String, ptr: String): Long =
        (this[key] ?: throw JsonParseException(ptr, "thiếu khóa số nguyên bắt buộc")).asLong(ptr)

    private fun JsonObject.optionalLong(key: String, ptr: String): Long? =
        this[key]?.asLong(ptr)

    private fun JsonObject.int(key: String, ptr: String): Int =
        (this[key] ?: throw JsonParseException(ptr, "thiếu khóa số nguyên bắt buộc")).asInt(ptr)

    private fun JsonObject.double(key: String, ptr: String): Double =
        (this[key] ?: throw JsonParseException(ptr, "thiếu khóa số bắt buộc")).asDouble(ptr)

    private fun JsonObject.optionalDouble(key: String, ptr: String): Double? =
        this[key]?.asDouble(ptr)

    private fun JsonObject.optionalBoolean(key: String, ptr: String): Boolean? =
        this[key]?.asBoolean(ptr)

    private fun JsonObject.stringList(key: String, ptr: String): List<String> =
        array(key, ptr).mapIndexed { i, e -> e.asString("$ptr[$i]") }

    private fun JsonObject.decimalList(key: String, ptr: String): List<BigDecimal> =
        array(key, ptr).mapIndexed { i, e -> e.asDecimal("$ptr[$i]") }

    private fun <E> JsonObject.enumValue(key: String, ptr: String, decode: (String) -> E): E {
        val raw = string(key, ptr)
        return try {
            decode(raw)
        } catch (e: IllegalArgumentException) {
            throw JsonParseException(ptr, "giá trị enum không hợp lệ: '$raw'")
        }
    }

    // --- ép kiểu phần tử JSON ---------------------------------------------------------------

    private fun JsonElement.asObject(ptr: String): JsonObject =
        this as? JsonObject ?: throw JsonParseException(ptr, "phải là một đối tượng JSON")

    private fun JsonElement.asArray(ptr: String): JsonArray =
        this as? JsonArray ?: throw JsonParseException(ptr, "phải là một mảng JSON")

    private fun JsonElement.asPrimitive(ptr: String): JsonPrimitive =
        this as? JsonPrimitive ?: throw JsonParseException(ptr, "phải là một giá trị nguyên thủy")

    private fun JsonElement.asString(ptr: String): String {
        val prim = asPrimitive(ptr)
        if (!prim.isString) throw JsonParseException(ptr, "phải là một chuỗi")
        return prim.content
    }

    private fun JsonElement.asDecimal(ptr: String): BigDecimal {
        val prim = asPrimitive(ptr)
        if (prim.isString) throw JsonParseException(ptr, "phải là một số (không bao nháy)")
        return try {
            BigDecimal(prim.content)
        } catch (e: NumberFormatException) {
            throw JsonParseException(ptr, "không phải số hợp lệ: '${prim.content}'")
        }
    }

    private fun JsonElement.asLong(ptr: String): Long {
        val prim = asPrimitive(ptr)
        if (prim.isString) throw JsonParseException(ptr, "phải là một số nguyên (không bao nháy)")
        return prim.content.toLongOrNull()
            ?: throw JsonParseException(ptr, "không phải số nguyên hợp lệ: '${prim.content}'")
    }

    private fun JsonElement.asInt(ptr: String): Int {
        val prim = asPrimitive(ptr)
        if (prim.isString) throw JsonParseException(ptr, "phải là một số nguyên (không bao nháy)")
        return prim.content.toIntOrNull()
            ?: throw JsonParseException(ptr, "không phải số nguyên 32-bit hợp lệ: '${prim.content}'")
    }

    private fun JsonElement.asDouble(ptr: String): Double {
        val prim = asPrimitive(ptr)
        if (prim.isString) throw JsonParseException(ptr, "phải là một số (không bao nháy)")
        return prim.content.toDoubleOrNull()
            ?: throw JsonParseException(ptr, "không phải số thực hợp lệ: '${prim.content}'")
    }

    private fun JsonElement.asBoolean(ptr: String): Boolean {
        val prim = asPrimitive(ptr)
        if (prim.isString) throw JsonParseException(ptr, "phải là boolean (không bao nháy)")
        return when (prim.content) {
            "true" -> true
            "false" -> false
            else -> throw JsonParseException(ptr, "không phải boolean hợp lệ: '${prim.content}'")
        }
    }

    private companion object {
        /**
         * Ánh xạ ngược từ tên canonical (snake_case) → [HealthMetricType], dựng từ
         * `MetricCatalog`. Dùng để khôi phục `metric` của [UnifiedRecord] từ `MetricSeries.name`
         * (vốn không mã hóa từng bản ghi) nhằm bảo toàn round-trip (Property 1).
         */
        private val metricByCanonicalName: Map<String, HealthMetricType> =
            HealthMetricType.entries.associateBy { MetricCatalog.spec(it).canonicalName }

        /** Ánh xạ ngược từ ký hiệu đơn vị → [CanonicalUnit]. */
        private val unitBySymbol: Map<String, CanonicalUnit> =
            CanonicalUnit.entries.associateBy { it.symbol }
    }

    /**
     * Suy ra [HealthMetricType] từ tên canonical của series. Tên không nằm trong catalog là một
     * series không hợp lệ theo Export_Format của App → lỗi (Requirement 10.9).
     */
    private fun metricForName(name: String, ptr: String): HealthMetricType =
        metricByCanonicalName[name]
            ?: throw JsonParseException(ptr, "tên chỉ số canonical không hợp lệ: '$name'")

    /**
     * Suy ra [CanonicalUnit] từ chuỗi `units` của series. Ký hiệu không khớp đơn vị canonical
     * nào là một series không hợp lệ → lỗi (Requirement 10.9).
     */
    private fun unitForSymbol(symbol: String, ptr: String): CanonicalUnit =
        unitBySymbol[symbol]
            ?: throw JsonParseException(ptr, "ký hiệu đơn vị không hợp lệ: '$symbol'")
}
