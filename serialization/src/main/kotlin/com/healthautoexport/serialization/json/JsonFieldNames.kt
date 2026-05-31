package com.healthautoexport.serialization.json

/**
 * Tên khóa JSON dùng chung cho [JsonSerializer] và [JsonParser].
 *
 * Tập trung tại một nơi để serializer (ghi) và parser (đọc) **không bao giờ lệch nhau** — điều
 * kiện cần cho thuộc tính round-trip (Property 1, Requirement 10.8).
 */
internal object JsonFieldNames {

    // --- Envelope cấp cao nhất (Requirement 10.1) -------------------------------------------
    const val DATA = "data"
    const val METRICS = "metrics"
    const val WORKOUTS = "workouts"
    const val STATE_OF_MIND = "stateOfMind"
    const val MEDICATIONS = "medications"
    const val SYMPTOMS = "symptoms"
    const val CYCLE_TRACKING = "cycleTracking"
    const val ECG = "ecg"
    const val HEART_RATE_NOTIFICATIONS = "heartRateNotifications"

    /** Tám khóa mảng cấp cao nhất theo đúng thứ tự tài liệu hóa (Requirements 10.1, 10.3). */
    val ENVELOPE_KEYS: List<String> = listOf(
        METRICS, WORKOUTS, STATE_OF_MIND, MEDICATIONS,
        SYMPTOMS, CYCLE_TRACKING, ECG, HEART_RATE_NOTIFICATIONS,
    )

    // --- MetricSeries + UnifiedRecord -------------------------------------------------------
    const val NAME = "name"
    const val UNITS = "units"
    const val SERIES_DATA = "data"

    const val QTY = "qty"
    const val DATE = "date"
    const val EXTRAS = "extras"

    // Lược đồ riêng cho giá trị metric (Requirement 10.6)
    const val SYSTOLIC = "systolic"
    const val DIASTOLIC = "diastolic"
    const val MIN = "min"
    const val AVG = "avg"
    const val MAX = "max"
    const val COUNT = "count"
    const val STATE = "state"
    const val CLASSIFICATION = "classification"
    const val AVERAGE_BPM = "averageBpm"
    const val SAMPLING_HZ = "samplingHz"
    const val VOLTAGES = "voltages"

    // ExtraValue (tagged union)
    const val EXTRA_KIND = "kind"
    const val EXTRA_VALUE = "value"
    const val EXTRA_KIND_STRING = "string"
    const val EXTRA_KIND_NUMBER = "number"
    const val EXTRA_KIND_ENUM = "enum"

    // --- Workout ----------------------------------------------------------------------------
    const val ID = "id"
    const val TYPE = "type"
    const val START = "start"
    const val END = "end"
    const val DURATION = "duration"
    const val DATA_SOURCE = "dataSource"
    const val ROUTE = "route"
    const val HEART_RATE_DATA = "heartRateData"

    // RoutePoint
    const val LAT = "lat"
    const val LON = "lon"
    const val ALTITUDE = "altitude"

    // WorkoutMetrics (optional fields, Requirement 5.5)
    const val ACTIVE_ENERGY = "activeEnergy"
    const val TOTAL_ENERGY = "totalEnergy"
    const val DISTANCE = "distance"
    const val AVG_SPEED = "avgSpeed"
    const val ELEVATION_GAIN = "elevationGain"
    const val STEP_COUNT = "stepCount"
    const val HEART_RATE_RECOVERY = "heartRateRecovery"

    // --- StateOfMind ------------------------------------------------------------------------
    const val KIND = "kind"
    const val VALENCE = "valence"
    const val LABELS = "labels"
    const val ASSOCIATIONS = "associations"

    // --- Medication / Symptom ---------------------------------------------------------------
    const val DOSE = "dose"
    const val SEVERITY = "severity"

    // --- CycleTracking ----------------------------------------------------------------------
    const val FLOW = "flow"
    const val OVULATION_TEST = "ovulationTest"
    const val SEXUAL_ACTIVITY = "sexualActivity"

    // --- HeartRateNotification --------------------------------------------------------------
    const val THRESHOLD_BPM = "thresholdBpm"
    const val SAMPLES = "samples"
}
