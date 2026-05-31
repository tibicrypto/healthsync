package com.healthautoexport.data.healthconnect

import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import com.healthautoexport.domain.logic.MapOutcome
import com.healthautoexport.domain.logic.MapWithWarnings
import com.healthautoexport.domain.logic.MapWithWarningsResult
import com.healthautoexport.domain.model.CanonicalUnit
import com.healthautoexport.domain.model.DataSourceId
import com.healthautoexport.domain.model.ExtraValue
import com.healthautoexport.domain.model.HealthMetricType
import com.healthautoexport.domain.model.HeartRateSample
import com.healthautoexport.domain.model.MetricCatalog
import com.healthautoexport.domain.model.MetricValue
import com.healthautoexport.domain.model.RoutePoint
import com.healthautoexport.domain.model.SleepState
import com.healthautoexport.domain.model.UnifiedRecord
import com.healthautoexport.domain.model.Workout
import com.healthautoexport.domain.model.WorkoutMetrics
import com.healthautoexport.domain.model.WorkoutType
import com.healthautoexport.domain.port.ReadWarning
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * Ánh xạ các loại bản ghi `androidx.health.connect.client.records.*` sang mô hình hợp nhất chuẩn
 * hóa của domain ([UnifiedRecord], [Workout]), chuyển mọi giá trị về **đơn vị canonical** theo
 * [MetricCatalog] (Requirements 4.1, 4.2).
 *
 * Bộ ánh xạ là **lõi thuần** của bộ điều hợp Health_Connect (task 13.1): nó không gọi mạng/SDK
 * mà chỉ nhận các đối tượng bản ghi đã đọc và biến đổi. Mọi phép ánh xạ đi qua
 * [MapWithWarnings.mapRecords] để bảo đảm:
 * - bản ghi không ánh xạ/chuyển đổi được bị **bỏ qua kèm [ReadWarning]**, không ném ngoại lệ
 *   làm hủy Export_Job (Requirements 4.7, 6.6);
 * - mọi [UnifiedRecord] giữ lại được đóng dấu `dataSourceId = HEALTH_CONNECT` (Requirement 4.5).
 *
 * Các loại dữ liệu được phủ (Requirements 4.2, 5.x, 6.x):
 * - Activity: [StepsRecord], [DistanceRecord], [ActiveCaloriesBurnedRecord], [TotalCaloriesBurnedRecord];
 * - Heart: [HeartRateRecord] (theo từng mẫu), [RestingHeartRateRecord], [BloodPressureRecord] (→ [MetricValue.BloodPressure]);
 * - Body: [WeightRecord], [HeightRecord], [BodyFatRecord];
 * - Respiratory/Vitals: [OxygenSaturationRecord], [RespiratoryRateRecord], [BodyTemperatureRecord], [BloodGlucoseRecord] (mealType → extras);
 * - Sleep: [SleepSessionRecord] (mỗi giai đoạn → một [MetricValue.SleepSegment] với `durationSeconds`);
 * - Workout: [ExerciseSessionRecord] → [Workout] (loại/start/end/duration + route + chuỗi nhịp tim).
 */
object HealthConnectMetricMapper {

    private val SOURCE = DataSourceId.HEALTH_CONNECT

    /** Tập [HealthMetricType] mà bộ ánh xạ này hỗ trợ (Requirement 4.6). */
    val supportedMetrics: Set<HealthMetricType>
        get() = HealthConnectRecordTypes.supportedMetrics

    // -----------------------------------------------------------------------------------------
    // Ánh xạ metric thường (Health_Metric → UnifiedRecord)
    // -----------------------------------------------------------------------------------------

    /**
     * Ánh xạ danh sách bản ghi Health_Connect [records] (đã đọc cho [metric]) thành các
     * [UnifiedRecord] đã chuẩn hóa, bỏ qua bản ghi lỗi kèm cảnh báo (Requirements 4.2, 4.7, 6.6).
     *
     * Một số metric **bung 1→n**: [HeartRateRecord] tạo một bản ghi cho mỗi mẫu, và
     * [SleepSessionRecord] tạo một bản ghi cho mỗi giai đoạn giấc ngủ. Các bản ghi này được "làm
     * phẳng" thành các đơn vị nguyên tử trước khi đi qua [MapWithWarnings.mapRecords].
     *
     * @param metric loại metric mà [records] thuộc về (quyết định đơn vị canonical & cách đọc trường).
     * @param records danh sách bản ghi Health_Connect thô (kiểu con của [Record]).
     * @return [MapWithWarningsResult] gồm các [UnifiedRecord] giữ lại (đóng dấu nguồn) và cảnh báo.
     */
    fun mapRecords(
        metric: HealthMetricType,
        records: List<Record>,
    ): MapWithWarningsResult<UnifiedRecord> = when (metric) {
        HealthMetricType.STEP_COUNT -> mapScalar<StepsRecord>(metric, records) { rec ->
            scalar(metric, BigDecimal.valueOf(rec.count), rec.startTime, rec.startZoneOffset)
        }

        HealthMetricType.DISTANCE -> mapScalar<DistanceRecord>(metric, records) { rec ->
            scalar(metric, bd(rec.distance.inMeters), rec.startTime, rec.startZoneOffset)
        }

        HealthMetricType.ACTIVE_ENERGY -> mapScalar<ActiveCaloriesBurnedRecord>(metric, records) { rec ->
            scalar(metric, bd(rec.energy.inKilocalories), rec.startTime, rec.startZoneOffset)
        }

        HealthMetricType.BASAL_ENERGY_BURNED -> mapScalar<TotalCaloriesBurnedRecord>(metric, records) { rec ->
            scalar(metric, bd(rec.energy.inKilocalories), rec.startTime, rec.startZoneOffset)
        }

        HealthMetricType.RESTING_HEART_RATE -> mapScalar<RestingHeartRateRecord>(metric, records) { rec ->
            scalar(metric, BigDecimal.valueOf(rec.beatsPerMinute), rec.time, rec.zoneOffset)
        }

        HealthMetricType.WEIGHT_BODY_MASS -> mapScalar<WeightRecord>(metric, records) { rec ->
            scalar(metric, bd(rec.weight.inKilograms), rec.time, rec.zoneOffset)
        }

        HealthMetricType.HEIGHT -> mapScalar<HeightRecord>(metric, records) { rec ->
            scalar(metric, bd(rec.height.inMeters), rec.time, rec.zoneOffset)
        }

        HealthMetricType.BODY_FAT_PERCENTAGE -> mapScalar<BodyFatRecord>(metric, records) { rec ->
            scalar(metric, bd(rec.percentage.value), rec.time, rec.zoneOffset)
        }

        HealthMetricType.BLOOD_OXYGEN_SATURATION -> mapScalar<OxygenSaturationRecord>(metric, records) { rec ->
            scalar(metric, bd(rec.percentage.value), rec.time, rec.zoneOffset)
        }

        HealthMetricType.RESPIRATORY_RATE -> mapScalar<RespiratoryRateRecord>(metric, records) { rec ->
            scalar(metric, bd(rec.rate), rec.time, rec.zoneOffset)
        }

        HealthMetricType.BODY_TEMPERATURE -> mapScalar<BodyTemperatureRecord>(metric, records) { rec ->
            scalar(metric, bd(rec.temperature.inCelsius), rec.time, rec.zoneOffset)
        }

        HealthMetricType.BLOOD_PRESSURE -> mapScalar<BloodPressureRecord>(metric, records) { rec ->
            UnifiedRecord(
                metric = metric,
                value = MetricValue.BloodPressure(
                    systolic = bd(rec.systolic.inMillimetersOfMercury),
                    diastolic = bd(rec.diastolic.inMillimetersOfMercury),
                ),
                unit = CanonicalUnit.MMHG,
                timestamp = rec.time,
                zoneOffset = rec.zoneOffset ?: ZoneOffset.UTC,
                dataSourceId = SOURCE,
            )
        }

        HealthMetricType.BLOOD_GLUCOSE -> mapScalar<BloodGlucoseRecord>(metric, records) { rec ->
            scalar(
                metric = metric,
                qty = bd(rec.level.inMilligramsPerDeciliter),
                timestamp = rec.time,
                zoneOffset = rec.zoneOffset,
                extras = mealTimeExtras(rec.relationToMeal),
            )
        }

        // --- Bung 1→n: mỗi mẫu nhịp tim là một bản ghi (Requirement 4.2). ---
        HealthMetricType.HEART_RATE -> {
            val samples: List<HeartRateSampleWithOffset> = records
                .filterIsInstance<HeartRateRecord>()
                .flatMap { rec -> rec.samples.map { HeartRateSampleWithOffset(it, rec.startZoneOffset) } }
            MapWithWarnings.mapRecords(samples, SOURCE, onError = errorFor(metric)) { item ->
                MapOutcome.Kept(
                    scalar(
                        metric = metric,
                        qty = BigDecimal.valueOf(item.sample.beatsPerMinute),
                        timestamp = item.sample.time,
                        zoneOffset = item.zoneOffset,
                    ),
                )
            }
        }

        // --- Bung 1→n: mỗi giai đoạn giấc ngủ là một SleepSegment (Requirement 6.1). ---
        HealthMetricType.SLEEP_ANALYSIS -> {
            val stages: List<SleepStageWithOffset> = records
                .filterIsInstance<SleepSessionRecord>()
                .flatMap { rec -> rec.stages.map { SleepStageWithOffset(it, rec.startZoneOffset) } }
            MapWithWarnings.mapRecords(stages, SOURCE, onError = errorFor(metric)) { item ->
                val durationSeconds = Duration.between(item.stage.startTime, item.stage.endTime).seconds
                if (durationSeconds < 0) {
                    // Thời lượng âm là dữ liệu không hợp lệ; bỏ qua kèm cảnh báo (Requirement 6.6).
                    MapOutcome.Skipped(
                        listOf(
                            ReadWarning(
                                source = SOURCE,
                                metric = metric,
                                message = "Bỏ qua một giai đoạn giấc ngủ có thời lượng âm.",
                            ),
                        ),
                    )
                } else {
                    MapOutcome.Kept(
                        UnifiedRecord(
                            metric = metric,
                            value = MetricValue.SleepSegment(
                                state = sleepStateOf(item.stage.stage),
                                durationSeconds = durationSeconds,
                            ),
                            unit = CanonicalUnit.SECOND,
                            timestamp = item.stage.startTime,
                            zoneOffset = item.zoneOffset ?: ZoneOffset.UTC,
                            dataSourceId = SOURCE,
                        ),
                    )
                }
            }
        }

        // Metric không thuộc tập bộ điều hợp Health_Connect hỗ trợ: không có bản ghi để ánh xạ.
        else -> MapWithWarningsResult(kept = emptyList(), warnings = emptyList())
    }

    // -----------------------------------------------------------------------------------------
    // Ánh xạ Workout (ExerciseSessionRecord → Workout)
    // -----------------------------------------------------------------------------------------

    /**
     * Ánh xạ một danh sách [ExerciseSessionRecord] thành các [Workout] đã chuẩn hóa, bỏ qua phiên
     * tập lỗi kèm cảnh báo (Requirements 5.1–5.5, 4.7).
     *
     * Mỗi Workout đọc loại ([workoutTypeOf]), thời điểm bắt đầu/kết thúc và thời lượng
     * (Requirement 5.1); tuyến đường (nếu có) là chuỗi [RoutePoint] **tăng dần theo thời gian**,
     * bỏ độ cao của riêng điểm thiếu độ cao mà vẫn giữ điểm (Requirements 5.2, 5.3); chuỗi nhịp
     * tim ([heartRates]) gắn vào Workout theo thứ tự **tăng dần** (Requirement 5.4).
     *
     * @param records danh sách phiên tập Health_Connect đã đọc.
     * @param heartRatesBySession ánh xạ id phiên → chuỗi nhịp tim đã đọc cho phiên đó (có thể rỗng).
     * @return [MapWithWarningsResult] gồm các [Workout] giữ lại và cảnh báo.
     */
    fun mapWorkouts(
        records: List<ExerciseSessionRecord>,
        heartRatesBySession: Map<String, List<HeartRateSample>> = emptyMap(),
    ): MapWithWarningsResult<Workout> =
        MapWithWarnings.collect(
            raw = records,
            onError = { _, error ->
                ReadWarning(
                    source = SOURCE,
                    metric = null,
                    message = "Bỏ qua một Workout không ánh xạ được: ${error.message ?: error.javaClass.simpleName}",
                )
            },
        ) { rec ->
            val series = heartRatesBySession[rec.metadata.id]
                ?.sortedBy { it.timestamp }
                ?.takeIf { it.isNotEmpty() }
            MapOutcome.Kept(
                Workout(
                    id = rec.metadata.id,
                    type = workoutTypeOf(rec.exerciseType),
                    start = rec.startTime,
                    end = rec.endTime,
                    durationSeconds = Duration.between(rec.startTime, rec.endTime).seconds.coerceAtLeast(0),
                    route = routeOf(rec),
                    heartRateSeries = series,
                    optionalFields = WorkoutMetrics(),
                    dataSourceId = SOURCE,
                ),
            )
        }

    /**
     * Trích tuyến đường của một phiên tập từ [ExerciseSessionRecord.exerciseRouteResult]
     * (Requirements 5.2, 5.3).
     *
     * Trả về:
     * - `null` khi phiên không có tuyến đường ([ExerciseRouteResult.NoData]) hoặc cần đồng ý
     *   chưa được cấp ([ExerciseRouteResult.ConsentRequired]) — Workout không route bị loại khỏi
     *   GPX ở `GpxSerializer` (Requirement 5.6);
     * - danh sách [RoutePoint] **sắp xếp tăng dần theo dấu thời gian**, mỗi điểm có
     *   vĩ độ/kinh độ/dấu thời gian, độ cao chỉ điền khi khả dụng (Requirement 5.3).
     */
    private fun routeOf(rec: ExerciseSessionRecord): List<RoutePoint>? {
        val result = rec.exerciseRouteResult
        if (result !is ExerciseRouteResult.Data) return null
        val points = result.exerciseRoute.route
        if (points.isEmpty()) return null
        return points
            .map { loc ->
                RoutePoint(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    timestamp = loc.time,
                    altitudeMeters = loc.altitude?.inMeters, // bỏ độ cao khi vắng (Requirement 5.3)
                )
            }
            .sortedBy { it.timestamp } // bảo đảm tăng dần theo thời gian (Requirement 5.2)
    }

    // -----------------------------------------------------------------------------------------
    // Bảng ánh xạ phụ
    // -----------------------------------------------------------------------------------------

    /**
     * Ánh xạ mã loại bài tập của Health_Connect ([ExerciseSessionRecord.exerciseType]) sang
     * [WorkoutType] của App; loại chưa có hằng số riêng rơi về [WorkoutType.OTHER] để không loại
     * bỏ phiên tập (Requirement 5.7).
     */
    fun workoutTypeOf(exerciseType: Int): WorkoutType = when (exerciseType) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
        -> WorkoutType.RUNNING

        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> WorkoutType.WALKING

        ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
        -> WorkoutType.CYCLING

        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
        -> WorkoutType.SWIMMING

        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> WorkoutType.HIKING
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
        ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
        -> WorkoutType.STRENGTH_TRAINING

        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> WorkoutType.YOGA
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> WorkoutType.HIIT

        ExerciseSessionRecord.EXERCISE_TYPE_ROWING,
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE,
        -> WorkoutType.ROWING

        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> WorkoutType.ELLIPTICAL
        ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> WorkoutType.PILATES
        ExerciseSessionRecord.EXERCISE_TYPE_DANCING -> WorkoutType.DANCE
        ExerciseSessionRecord.EXERCISE_TYPE_BOXING -> WorkoutType.BOXING
        ExerciseSessionRecord.EXERCISE_TYPE_SKIING -> WorkoutType.SKIING
        ExerciseSessionRecord.EXERCISE_TYPE_SNOWBOARDING -> WorkoutType.SNOWBOARDING
        ExerciseSessionRecord.EXERCISE_TYPE_TENNIS -> WorkoutType.TENNIS
        ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL -> WorkoutType.BASKETBALL
        ExerciseSessionRecord.EXERCISE_TYPE_SOCCER -> WorkoutType.SOCCER
        ExerciseSessionRecord.EXERCISE_TYPE_GOLF -> WorkoutType.GOLF

        ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING,
        ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE,
        -> WorkoutType.STAIR_CLIMBING

        else -> WorkoutType.OTHER
    }

    /** Ánh xạ mã giai đoạn giấc ngủ Health_Connect sang [SleepState] (Requirement 6.1). */
    private fun sleepStateOf(stage: Int): SleepState = when (stage) {
        SleepSessionRecord.STAGE_TYPE_AWAKE -> SleepState.AWAKE
        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> SleepState.IN_BED
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> SleepState.AWAKE
        SleepSessionRecord.STAGE_TYPE_SLEEPING -> SleepState.ASLEEP
        SleepSessionRecord.STAGE_TYPE_LIGHT -> SleepState.CORE
        SleepSessionRecord.STAGE_TYPE_DEEP -> SleepState.DEEP
        SleepSessionRecord.STAGE_TYPE_REM -> SleepState.REM
        else -> SleepState.UNSPECIFIED
    }

    /**
     * Chuyển mã quan hệ bữa ăn của [BloodGlucoseRecord] thành `extras["mealTime"]` (Requirement 6.4).
     * Trả về map rỗng khi quan hệ không xác định ([BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN]).
     */
    private fun mealTimeExtras(relationToMeal: Int): Map<String, ExtraValue> {
        val name = when (relationToMeal) {
            BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL -> "GENERAL"
            BloodGlucoseRecord.RELATION_TO_MEAL_FASTING -> "FASTING"
            BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL -> "BEFORE_MEAL"
            BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL -> "AFTER_MEAL"
            else -> return emptyMap()
        }
        return mapOf("mealTime" to ExtraValue.EnumValue(name))
    }

    // -----------------------------------------------------------------------------------------
    // Tiện ích nội bộ
    // -----------------------------------------------------------------------------------------

    /** Cặp mẫu nhịp tim + độ lệch múi giờ của bản ghi cha (dùng khi bung [HeartRateRecord]). */
    private data class HeartRateSampleWithOffset(
        val sample: HeartRateRecord.Sample,
        val zoneOffset: ZoneOffset?,
    )

    /** Cặp giai đoạn giấc ngủ + độ lệch múi giờ của phiên cha (dùng khi bung [SleepSessionRecord]). */
    private data class SleepStageWithOffset(
        val stage: SleepSessionRecord.Stage,
        val zoneOffset: ZoneOffset?,
    )

    /**
     * Ánh xạ một danh sách bản ghi đồng nhất kiểu [T] sang [UnifiedRecord] dùng
     * [MapWithWarnings.mapRecords]; mỗi bản ghi tạo đúng một [UnifiedRecord] qua [toRecord].
     *
     * Bản ghi không đúng kiểu [T] bị lọc trước (không nên xảy ra vì người gọi đọc theo loại), và
     * mọi ngoại lệ trong [toRecord] được [MapWithWarnings] bắt và chuyển thành cảnh báo.
     */
    private inline fun <reified T : Record> mapScalar(
        metric: HealthMetricType,
        records: List<Record>,
        crossinline toRecord: (T) -> UnifiedRecord,
    ): MapWithWarningsResult<UnifiedRecord> {
        val typed = records.filterIsInstance<T>()
        return MapWithWarnings.mapRecords(typed, SOURCE, onError = errorFor(metric)) { rec ->
            MapOutcome.Kept(toRecord(rec))
        }
    }

    /** Tạo hàm dựng [ReadWarning] cho [metric] khi một bản ghi ném ngoại lệ lúc ánh xạ. */
    private fun <R> errorFor(metric: HealthMetricType): (R, Throwable) -> ReadWarning =
        { _, error ->
            ReadWarning(
                source = SOURCE,
                metric = metric,
                message = "Bỏ qua một bản ghi ${MetricCatalog.spec(metric).canonicalName} không ánh xạ được: " +
                    (error.message ?: error.javaClass.simpleName),
            )
        }

    /** Dựng một [UnifiedRecord] vô hướng ([MetricValue.Scalar]) ở đơn vị canonical của [metric]. */
    private fun scalar(
        metric: HealthMetricType,
        qty: BigDecimal,
        timestamp: Instant,
        zoneOffset: ZoneOffset?,
        extras: Map<String, ExtraValue> = emptyMap(),
    ): UnifiedRecord = UnifiedRecord(
        metric = metric,
        value = MetricValue.Scalar(qty),
        unit = MetricCatalog.spec(metric).unit,
        timestamp = timestamp,
        zoneOffset = zoneOffset ?: ZoneOffset.UTC,
        dataSourceId = SOURCE,
        extras = extras,
    )

    /** Chuyển [Double] sang [BigDecimal] giữ biểu diễn thập phân ngắn gọn, ổn định cho round-trip. */
    private fun bd(value: Double): BigDecimal = BigDecimal.valueOf(value)
}
