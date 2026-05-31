package com.healthautoexport.domain.model

/**
 * Tập các loại Health_Metric mà App hỗ trợ, phủ đủ 12 nhóm chỉ số của Requirement 4.1.
 *
 * Mỗi hằng số mang một [group] thuộc một trong 12 [MetricGroup] và được tổ chức khớp với
 * bảng "Canonical Units và Metric Catalog Mapping" trong design.md.
 *
 * Tên hằng số (vd [STEP_COUNT]) là định danh nội bộ của App; tên canonical dạng snake_case
 * (vd `step_count`) dùng trong JSON/CSV được khai báo ở `MetricCatalog` (task 2.5), không
 * lặp lại tại đây để giữ một nguồn sự thật duy nhất.
 *
 * @property group nhóm phân loại của chỉ số (Requirement 4.1).
 */
enum class HealthMetricType(val group: MetricGroup) {
    // --- Activity (vận động) — chủ yếu CUMULATIVE ---
    STEP_COUNT(MetricGroup.ACTIVITY),
    DISTANCE(MetricGroup.ACTIVITY),
    ACTIVE_ENERGY(MetricGroup.ACTIVITY),
    BASAL_ENERGY_BURNED(MetricGroup.ACTIVITY),
    FLIGHTS_CLIMBED(MetricGroup.ACTIVITY),
    STEP_CADENCE(MetricGroup.ACTIVITY),
    WALKING_RUNNING_SPEED(MetricGroup.ACTIVITY),
    WHEELCHAIR_PUSHES(MetricGroup.ACTIVITY),

    // --- Body Measurement (chỉ số cơ thể) — INSTANTANEOUS ---
    WEIGHT_BODY_MASS(MetricGroup.BODY_MEASUREMENT),
    HEIGHT(MetricGroup.BODY_MEASUREMENT),
    BODY_FAT_PERCENTAGE(MetricGroup.BODY_MEASUREMENT),
    LEAN_BODY_MASS(MetricGroup.BODY_MEASUREMENT),
    BODY_MASS_INDEX(MetricGroup.BODY_MEASUREMENT),

    // --- Heart (tim mạch) — INSTANTANEOUS, có biến thể structured (blood_pressure) ---
    HEART_RATE(MetricGroup.HEART),
    RESTING_HEART_RATE(MetricGroup.HEART),
    HEART_RATE_VARIABILITY(MetricGroup.HEART),
    BLOOD_PRESSURE(MetricGroup.HEART),
    VO2_MAX(MetricGroup.HEART),

    // --- Respiratory (hô hấp) ---
    RESPIRATORY_RATE(MetricGroup.RESPIRATORY),
    BLOOD_OXYGEN_SATURATION(MetricGroup.RESPIRATORY),

    // --- Vitals (dấu hiệu sinh tồn) ---
    BODY_TEMPERATURE(MetricGroup.VITALS),
    BASAL_BODY_TEMPERATURE(MetricGroup.VITALS),
    BLOOD_GLUCOSE(MetricGroup.VITALS), // kèm metadata mealTime (Requirement 6.4)

    // --- Nutrition (dinh dưỡng) — CUMULATIVE ---
    DIETARY_WATER(MetricGroup.NUTRITION),
    DIETARY_ENERGY(MetricGroup.NUTRITION),
    CARBOHYDRATES(MetricGroup.NUTRITION),
    PROTEIN(MetricGroup.NUTRITION),
    TOTAL_FAT(MetricGroup.NUTRITION),

    // --- Sleep (giấc ngủ) — structured theo giai đoạn (Requirement 6.1) ---
    SLEEP_ANALYSIS(MetricGroup.SLEEP),

    // --- Mindfulness (chánh niệm) ---
    MINDFUL_MINUTES(MetricGroup.MINDFULNESS),

    // --- Mobility (di chuyển) ---
    WALKING_SPEED(MetricGroup.MOBILITY),

    // --- Reproductive (sinh sản) — dạng category ---
    MENSTRUATION_FLOW(MetricGroup.REPRODUCTIVE_HEALTH),
    OVULATION_TEST(MetricGroup.REPRODUCTIVE_HEALTH),
    SEXUAL_ACTIVITY(MetricGroup.REPRODUCTIVE_HEALTH),

    // --- Hearing (thính giác) — mặc định "không hỗ trợ" tới khi SDK phơi bày ---
    HEADPHONE_AUDIO_EXPOSURE(MetricGroup.HEARING),
    ENVIRONMENTAL_AUDIO_EXPOSURE(MetricGroup.HEARING),

    // --- Other / specialized (chuyên biệt) — structured (Requirements 6.2, 6.3) ---
    ECG(MetricGroup.OTHER),
    HEART_RATE_NOTIFICATIONS(MetricGroup.OTHER),
}
