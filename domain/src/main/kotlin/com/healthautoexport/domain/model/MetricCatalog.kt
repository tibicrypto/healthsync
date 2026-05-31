package com.healthautoexport.domain.model

import java.math.BigDecimal

/**
 * Bảng tra cứu tĩnh (thuần JVM) ánh xạ mỗi [HealthMetricType] tới đặc tả chuẩn hóa của nó —
 * **nguồn sự thật duy nhất** cho Aggregator, Serializer và UI (Requirements 4.1, 4.2, 4.3, 4.6).
 *
 * `MetricCatalog` quyết định: (a) tên canonical (snake_case) và đơn vị canonical dùng khi tuần
 * tự hóa; (b) [MetricKind] để Aggregator chọn `sum` vs `{min, avg, max, count}` (Requirements
 * 8.4, 8.5); (c) [MetricSchema] để Serializer chọn lược đồ chuẩn vs lược đồ riêng
 * (Requirement 10.6); (d) [DuplicateTolerance] mặc định cho `Data_Merger` (Requirement 7.2);
 * và (e) khả năng cung cấp của từng Data_Source qua [isSupportedBy] (Requirements 4.3, 4.6).
 *
 * Bảng bám theo "Canonical Units và Metric Catalog Mapping" trong design.md. Khối khởi tạo xác
 * minh **mọi** [HealthMetricType] đều có một entry để bảo đảm danh mục phủ đủ các nhóm
 * (Requirement 4.1).
 */
object MetricCatalog {

    /**
     * Đặc tả chuẩn hóa của một [HealthMetricType].
     *
     * @property canonicalName tên canonical dạng snake_case dùng trong JSON/CSV (vd `step_count`).
     * @property unit đơn vị canonical duy nhất của metric (Requirement 4.2).
     * @property kind phân loại tích lũy/tức thời để Aggregator tóm tắt đúng (Requirements 8.4, 8.5).
     * @property schema lược đồ tuần tự hóa (chuẩn vs riêng) (Requirement 10.6).
     * @property defaultTolerance dung sai loại trùng mặc định, không âm (Requirement 7.2).
     */
    data class Spec(
        val canonicalName: String,
        val unit: CanonicalUnit,
        val kind: MetricKind,
        val schema: MetricSchema,
        val defaultTolerance: DuplicateTolerance,
    )

    /** Entry nội bộ: [Spec] + tập Data_Source có thể cung cấp metric trên thiết bị. */
    private data class Entry(
        val spec: Spec,
        val supportedBy: Set<DataSourceId>,
    )

    private val HEALTH_CONNECT = setOf(DataSourceId.HEALTH_CONNECT)
    private val HUAWEI = setOf(DataSourceId.HUAWEI_HEALTH_KIT)
    private val BOTH = setOf(DataSourceId.HEALTH_CONNECT, DataSourceId.HUAWEI_HEALTH_KIT)
    private val NONE = emptySet<DataSourceId>()

    private fun tol(timeSeconds: Long, valueMagnitude: String): DuplicateTolerance =
        DuplicateTolerance(timeSeconds, BigDecimal(valueMagnitude))

    private val entries: Map<HealthMetricType, Entry> = mapOf(
        // --- Activity ---
        HealthMetricType.STEP_COUNT to Entry(
            Spec("step_count", CanonicalUnit.COUNT, MetricKind.CUMULATIVE, MetricSchema.STANDARD, tol(60, "0")),
            BOTH,
        ),
        HealthMetricType.DISTANCE to Entry(
            Spec("distance", CanonicalUnit.METER, MetricKind.CUMULATIVE, MetricSchema.STANDARD, tol(60, "1.0")),
            BOTH,
        ),
        HealthMetricType.ACTIVE_ENERGY to Entry(
            Spec("active_energy", CanonicalUnit.KCAL, MetricKind.CUMULATIVE, MetricSchema.STANDARD, tol(60, "1.0")),
            BOTH,
        ),
        HealthMetricType.BASAL_ENERGY_BURNED to Entry(
            Spec("basal_energy_burned", CanonicalUnit.KCAL, MetricKind.CUMULATIVE, MetricSchema.STANDARD, tol(60, "1.0")),
            BOTH,
        ),
        HealthMetricType.FLIGHTS_CLIMBED to Entry(
            Spec("flights_climbed", CanonicalUnit.COUNT, MetricKind.CUMULATIVE, MetricSchema.STANDARD, tol(60, "0")),
            HEALTH_CONNECT,
        ),
        HealthMetricType.STEP_CADENCE to Entry(
            Spec("step_cadence", CanonicalUnit.COUNT_PER_MIN, MetricKind.INSTANTANEOUS, MetricSchema.STANDARD, tol(60, "1")),
            HEALTH_CONNECT,
        ),
        HealthMetricType.WALKING_RUNNING_SPEED to Entry(
            Spec("walking_running_speed", CanonicalUnit.METER_PER_SECOND, MetricKind.INSTANTANEOUS, MetricSchema.STANDARD, tol(60, "0.1")),
            BOTH,
        ),
        HealthMetricType.WHEELCHAIR_PUSHES to Entry(
            Spec("wheelchair_pushes", CanonicalUnit.COUNT, MetricKind.CUMULATIVE, MetricSchema.STANDARD, tol(60, "0")),
            HEALTH_CONNECT,
        ),

        // --- Body Measurement ---
        HealthMetricType.WEIGHT_BODY_MASS to Entry(
            Spec("weight_body_mass", CanonicalUnit.KILOGRAM, MetricKind.INSTANTANEOUS, MetricSchema.STANDARD, tol(300, "0.1")),
            BOTH,
        ),
        HealthMetricType.HEIGHT to Entry(
            Spec("height", CanonicalUnit.METER, MetricKind.INSTANTANEOUS, MetricSchema.STANDARD, tol(300, "0.01")),
            BOTH,
        ),
        HealthMetricType.BODY_FAT_PERCENTAGE to Entry(
            Spec("body_fat_percentage", CanonicalUnit.PERCENT, MetricKind.INSTANTANEOUS, MetricSchema.STANDARD, tol(300, "0.5")),
            BOTH,
        ),
        HealthMetricType.LEAN_BODY_MASS to Entry(
            Spec("lean_body_mass", CanonicalUnit.KILOGRAM, MetricKind.INSTANTANEOUS, MetricSchema.STANDARD, tol(300, "0.1")),
            HEALTH_CONNECT,
        ),
        HealthMetricType.BODY_MASS_INDEX to Entry(
            Spec("body_mass_index", CanonicalUnit.COUNT, MetricKind.INSTANTANEOUS, MetricSchema.STANDARD, tol(300, "0.1")),
            HUAWEI,
        ),

        // --- Heart ---
        HealthMetricType.HEART_RATE to Entry(
            Spec("heart_rate", CanonicalUnit.BPM, MetricKind.INSTANTANEOUS, MetricSchema.STANDARD, tol(5, "1")),
            BOTH,
        ),
        HealthMetricType.RESTING_HEART_RATE to Entry(
            Spec("resting_heart_rate", CanonicalUnit.BPM, MetricKind.INSTANTANEOUS, MetricSchema.STANDARD, tol(300, "1")),
            BOTH,
        ),
        HealthMetricType.HEART_RATE_VARIABILITY to Entry(
            Spec("heart_rate_variability", CanonicalUnit.MILLISECOND, MetricKind.INSTANTANEOUS, MetricSchema.STANDARD, tol(60, "1")),
            HEALTH_CONNECT,
        ),
        HealthMetricType.BLOOD_PRESSURE to Entry(
            Spec("blood_pressure", CanonicalUnit.MMHG, MetricKind.INSTANTANEOUS, MetricSchema.BLOOD_PRESSURE, tol(60, "1")),
            BOTH,
        ),
        HealthMetricType.VO2_MAX to Entry(
            Spec("vo2_max", CanonicalUnit.ML_PER_KG_MIN, MetricKind.INSTANTANEOUS, MetricSchema.STANDARD, tol(300, "0.1")),
            HEALTH_CONNECT,
        ),

        // --- Respiratory ---
        HealthMetricType.RESPIRATORY_RATE to Entry(
            Spec("respiratory_rate", CanonicalUnit.COUNT_PER_MIN, MetricKind.INSTANTANEOUS, MetricSchema.STANDARD, tol(60, "0.5")),
            HEALTH_CONNECT,
        ),
        HealthMetricType.BLOOD_OXYGEN_SATURATION to Entry(
            Spec("blood_oxygen_saturation", CanonicalUnit.PERCENT, MetricKind.INSTANTANEOUS, MetricSchema.STANDARD, tol(60, "0.5")),
            BOTH,
        ),

        // --- Vitals ---
        HealthMetricType.BODY_TEMPERATURE to Entry(
            Spec("body_temperature", CanonicalUnit.DEGREE_CELSIUS, MetricKind.INSTANTANEOUS, MetricSchema.STANDARD, tol(300, "0.1")),
            BOTH,
        ),
        HealthMetricType.BASAL_BODY_TEMPERATURE to Entry(
            Spec("basal_body_temperature", CanonicalUnit.DEGREE_CELSIUS, MetricKind.INSTANTANEOUS, MetricSchema.STANDARD, tol(300, "0.1")),
            HEALTH_CONNECT,
        ),
        HealthMetricType.BLOOD_GLUCOSE to Entry(
            Spec("blood_glucose", CanonicalUnit.MG_PER_DL, MetricKind.INSTANTANEOUS, MetricSchema.STANDARD, tol(60, "1")),
            BOTH,
        ),

        // --- Nutrition ---
        HealthMetricType.DIETARY_WATER to Entry(
            Spec("dietary_water", CanonicalUnit.LITER, MetricKind.CUMULATIVE, MetricSchema.STANDARD, tol(60, "0.01")),
            HEALTH_CONNECT,
        ),
        HealthMetricType.DIETARY_ENERGY to Entry(
            Spec("dietary_energy", CanonicalUnit.KCAL, MetricKind.CUMULATIVE, MetricSchema.STANDARD, tol(60, "1")),
            HEALTH_CONNECT,
        ),
        HealthMetricType.CARBOHYDRATES to Entry(
            Spec("carbohydrates", CanonicalUnit.GRAM, MetricKind.CUMULATIVE, MetricSchema.STANDARD, tol(60, "0.1")),
            HEALTH_CONNECT,
        ),
        HealthMetricType.PROTEIN to Entry(
            Spec("protein", CanonicalUnit.GRAM, MetricKind.CUMULATIVE, MetricSchema.STANDARD, tol(60, "0.1")),
            HEALTH_CONNECT,
        ),
        HealthMetricType.TOTAL_FAT to Entry(
            Spec("total_fat", CanonicalUnit.GRAM, MetricKind.CUMULATIVE, MetricSchema.STANDARD, tol(60, "0.1")),
            HEALTH_CONNECT,
        ),

        // --- Sleep ---
        HealthMetricType.SLEEP_ANALYSIS to Entry(
            Spec("sleep_analysis", CanonicalUnit.SECOND, MetricKind.INSTANTANEOUS, MetricSchema.SLEEP, tol(60, "60")),
            BOTH,
        ),

        // --- Mindfulness ---
        HealthMetricType.MINDFUL_MINUTES to Entry(
            Spec("mindful_minutes", CanonicalUnit.MINUTE, MetricKind.CUMULATIVE, MetricSchema.STANDARD, tol(60, "1")),
            HEALTH_CONNECT,
        ),

        // --- Mobility ---
        HealthMetricType.WALKING_SPEED to Entry(
            Spec("walking_speed", CanonicalUnit.METER_PER_SECOND, MetricKind.INSTANTANEOUS, MetricSchema.STANDARD, tol(60, "0.1")),
            HEALTH_CONNECT,
        ),

        // --- Reproductive Health ---
        HealthMetricType.MENSTRUATION_FLOW to Entry(
            Spec("menstruation_flow", CanonicalUnit.CATEGORY, MetricKind.INSTANTANEOUS, MetricSchema.STANDARD, tol(0, "0")),
            HEALTH_CONNECT,
        ),
        HealthMetricType.OVULATION_TEST to Entry(
            Spec("ovulation_test", CanonicalUnit.CATEGORY, MetricKind.INSTANTANEOUS, MetricSchema.STANDARD, tol(0, "0")),
            HEALTH_CONNECT,
        ),
        HealthMetricType.SEXUAL_ACTIVITY to Entry(
            Spec("sexual_activity", CanonicalUnit.CATEGORY, MetricKind.INSTANTANEOUS, MetricSchema.STANDARD, tol(0, "0")),
            HEALTH_CONNECT,
        ),

        // --- Hearing (mặc định "không hỗ trợ" tới khi SDK phơi bày, Requirement 4.3) ---
        HealthMetricType.HEADPHONE_AUDIO_EXPOSURE to Entry(
            Spec("headphone_audio_exposure", CanonicalUnit.DBASPL, MetricKind.INSTANTANEOUS, MetricSchema.STANDARD, tol(60, "1")),
            NONE,
        ),
        HealthMetricType.ENVIRONMENTAL_AUDIO_EXPOSURE to Entry(
            Spec("environmental_audio_exposure", CanonicalUnit.DBASPL, MetricKind.INSTANTANEOUS, MetricSchema.STANDARD, tol(60, "1")),
            NONE,
        ),

        // --- Other / specialized (structured) ---
        HealthMetricType.ECG to Entry(
            Spec("ecg", CanonicalUnit.MICROVOLT, MetricKind.INSTANTANEOUS, MetricSchema.ECG, tol(1, "0")),
            NONE,
        ),
        HealthMetricType.HEART_RATE_NOTIFICATIONS to Entry(
            Spec("heart_rate_notifications", CanonicalUnit.BPM, MetricKind.INSTANTANEOUS, MetricSchema.HR_NOTIFICATION, tol(1, "1")),
            HEALTH_CONNECT,
        ),
    )

    init {
        // Bảo đảm catalog phủ đủ mọi HealthMetricType (do đó đủ 12 nhóm — Requirement 4.1) và
        // mọi dung sai mặc định không âm (Requirement 7.2, được DuplicateTolerance.init kiểm tra).
        val missing = HealthMetricType.entries.filterNot { entries.containsKey(it) }
        require(missing.isEmpty()) {
            "MetricCatalog thiếu Spec cho các HealthMetricType: $missing"
        }
    }

    /** Đặc tả chuẩn hóa của [type] (Requirements 4.1, 4.2, 8.4, 8.5, 10.6). */
    fun spec(type: HealthMetricType): Spec =
        entries.getValue(type).spec

    /**
     * `true` nếu [source] có thể cung cấp [type] trên thiết bị; dùng để loại metric không được
     * nguồn nào đang bật cung cấp khỏi danh sách chọn (Requirements 4.3, 4.6).
     */
    fun isSupportedBy(type: HealthMetricType, source: DataSourceId): Boolean =
        source in entries.getValue(type).supportedBy

    /** Bảng dung sai mặc định cho toàn bộ metric — đầu vào khởi tạo cho `Data_Merger`. */
    fun defaultToleranceTable(): DuplicateToleranceTable =
        entries.mapValues { (_, entry) -> entry.spec.defaultTolerance }
}
