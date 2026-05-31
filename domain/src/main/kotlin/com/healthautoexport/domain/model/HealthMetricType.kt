package com.healthautoexport.domain.model

/**
 * Tập các loại Health_Metric mà App hỗ trợ, phủ đủ 12 nhóm chỉ số của Requirement 4.1.
 *
 * Các hằng số được nhóm theo danh mục (Activity, Body Measurement, Heart, Respiratory,
 * Vitals, Nutrition, Sleep, Mindfulness, Mobility, Reproductive, Hearing, Other/specialized)
 * khớp với bảng "Canonical Units và Metric Catalog Mapping" trong design.md.
 *
 * Tên hằng số (vd [STEP_COUNT]) là định danh nội bộ của App; tên canonical dạng snake_case
 * (vd `step_count`) dùng trong JSON/CSV được khai báo ở `MetricCatalog` (task 2.5), không
 * lặp lại tại đây để giữ một nguồn sự thật duy nhất.
 */
enum class HealthMetricType {
    // --- Activity (vận động) — chủ yếu CUMULATIVE ---
    STEP_COUNT,
    DISTANCE,
    ACTIVE_ENERGY,
    BASAL_ENERGY_BURNED,
    FLIGHTS_CLIMBED,
    STEP_CADENCE,
    WALKING_RUNNING_SPEED,
    WHEELCHAIR_PUSHES,

    // --- Body Measurement (chỉ số cơ thể) — INSTANTANEOUS ---
    WEIGHT_BODY_MASS,
    HEIGHT,
    BODY_FAT_PERCENTAGE,
    LEAN_BODY_MASS,
    BODY_MASS_INDEX,

    // --- Heart (tim mạch) — INSTANTANEOUS, có biến thể structured (blood_pressure) ---
    HEART_RATE,
    RESTING_HEART_RATE,
    HEART_RATE_VARIABILITY,
    BLOOD_PRESSURE,
    VO2_MAX,

    // --- Respiratory (hô hấp) ---
    RESPIRATORY_RATE,
    BLOOD_OXYGEN_SATURATION,

    // --- Vitals (dấu hiệu sinh tồn) ---
    BODY_TEMPERATURE,
    BASAL_BODY_TEMPERATURE,
    BLOOD_GLUCOSE, // kèm metadata mealTime (Requirement 6.4)

    // --- Nutrition (dinh dưỡng) — CUMULATIVE ---
    DIETARY_WATER,
    DIETARY_ENERGY,
    CARBOHYDRATES,
    PROTEIN,
    TOTAL_FAT,

    // --- Sleep (giấc ngủ) — structured theo giai đoạn (Requirement 6.1) ---
    SLEEP_ANALYSIS,

    // --- Mindfulness (chánh niệm) ---
    MINDFUL_MINUTES,

    // --- Mobility (di chuyển) ---
    WALKING_SPEED,

    // --- Reproductive (sinh sản) — dạng category ---
    MENSTRUATION_FLOW,
    OVULATION_TEST,
    SEXUAL_ACTIVITY,

    // --- Hearing (thính giác) — mặc định "không hỗ trợ" tới khi SDK phơi bày ---
    HEADPHONE_AUDIO_EXPOSURE,
    ENVIRONMENTAL_AUDIO_EXPOSURE,

    // --- Other / specialized (chuyên biệt) — structured (Requirements 6.2, 6.3) ---
    ECG,
    HEART_RATE_NOTIFICATIONS,
}
