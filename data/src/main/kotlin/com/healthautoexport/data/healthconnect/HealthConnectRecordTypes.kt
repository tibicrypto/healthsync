package com.healthautoexport.data.healthconnect

import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.DistanceRecord
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
import com.healthautoexport.domain.model.DataSourceId
import com.healthautoexport.domain.model.HealthMetricType
import com.healthautoexport.domain.model.MetricCatalog
import kotlin.reflect.KClass

/**
 * Bảng tra cứu **duy nhất** ánh xạ mỗi [HealthMetricType] mà bộ điều hợp Health_Connect hiểu được
 * sang loại bản ghi tương ứng của `androidx.health.connect.client.records`
 * (Requirements 4.1, 4.2, 4.6).
 *
 * Đây là "nguồn sự thật" được tái sử dụng bởi cả ba thành phần Health_Connect:
 * - [HealthConnectMetricMapper] dùng để chuyển bản ghi nguồn về [com.healthautoexport.domain.model.UnifiedRecord];
 * - [HealthConnectDataSource] dùng để biết cần đọc loại bản ghi nào cho mỗi metric đã chọn;
 * - [HealthConnectPermissionManager] dùng để suy ra chuỗi quyền đọc Health_Connect tương ứng.
 *
 * Giữ ánh xạ ở **một nơi** giúp dễ điều chỉnh khi API SDK thay đổi (theo gợi ý trong design.md)
 * và bảo đảm ba thành phần luôn nhất quán về tập metric được hỗ trợ. Loại bản ghi của Workout
 * ([workoutRecordType]) tách riêng vì Workout không phải một [HealthMetricType].
 */
object HealthConnectRecordTypes {

    /**
     * Ánh xạ metric → loại bản ghi Health_Connect mà [HealthConnectMetricMapper] biết cách chuyển
     * về đơn vị canonical. Chỉ liệt kê các metric thực sự được bộ điều hợp hỗ trợ trên Health_Connect.
     */
    private val metricToRecordType: Map<HealthMetricType, KClass<out Record>> = mapOf(
        // --- Activity ---
        HealthMetricType.STEP_COUNT to StepsRecord::class,
        HealthMetricType.DISTANCE to DistanceRecord::class,
        HealthMetricType.ACTIVE_ENERGY to ActiveCaloriesBurnedRecord::class,
        HealthMetricType.BASAL_ENERGY_BURNED to TotalCaloriesBurnedRecord::class,

        // --- Heart ---
        HealthMetricType.HEART_RATE to HeartRateRecord::class,
        HealthMetricType.RESTING_HEART_RATE to RestingHeartRateRecord::class,
        HealthMetricType.BLOOD_PRESSURE to BloodPressureRecord::class,

        // --- Body Measurement ---
        HealthMetricType.WEIGHT_BODY_MASS to WeightRecord::class,
        HealthMetricType.HEIGHT to HeightRecord::class,
        HealthMetricType.BODY_FAT_PERCENTAGE to BodyFatRecord::class,

        // --- Respiratory ---
        HealthMetricType.BLOOD_OXYGEN_SATURATION to OxygenSaturationRecord::class,
        HealthMetricType.RESPIRATORY_RATE to RespiratoryRateRecord::class,

        // --- Vitals ---
        HealthMetricType.BODY_TEMPERATURE to BodyTemperatureRecord::class,
        HealthMetricType.BLOOD_GLUCOSE to BloodGlucoseRecord::class,

        // --- Sleep (structured, theo giai đoạn) ---
        HealthMetricType.SLEEP_ANALYSIS to SleepSessionRecord::class,
    )

    /** Loại bản ghi Health_Connect cho Workout (Requirement 5.1). */
    val workoutRecordType: KClass<out Record> = ExerciseSessionRecord::class

    /**
     * Tập [HealthMetricType] mà bộ điều hợp Health_Connect **biết cách** ánh xạ (không xét khả
     * dụng trên thiết bị). Giao tập này với `MetricCatalog.isSupportedBy(.., HEALTH_CONNECT)` cho
     * ra [supportedMetrics] thực sự (Requirement 4.6).
     */
    val mapperKnownMetrics: Set<HealthMetricType>
        get() = metricToRecordType.keys

    /**
     * Tập metric Health_Connect được hỗ trợ: vừa được mapper hiểu, vừa được khai báo cung cấp bởi
     * Health_Connect trong `MetricCatalog` (Requirements 4.3, 4.6).
     */
    val supportedMetrics: Set<HealthMetricType>
        get() = mapperKnownMetrics
            .filterTo(mutableSetOf()) {
                MetricCatalog.isSupportedBy(it, DataSourceId.HEALTH_CONNECT)
            }

    /** Loại bản ghi Health_Connect tương ứng [metric], hoặc `null` nếu mapper không hỗ trợ. */
    fun recordTypeFor(metric: HealthMetricType): KClass<out Record>? =
        metricToRecordType[metric]
}
