package com.healthautoexport.domain.model

/**
 * Lược đồ tuần tự hóa của một Health_Metric, cho biết [MetricValue] mang cấu trúc nào và
 * do đó JSON_Serializer dùng lược đồ chuẩn hay lược đồ riêng (Requirement 10.6).
 *
 * Mỗi giá trị tương ứng với một biến thể của `MetricValue` (task 2.2):
 * - [STANDARD] ↔ `MetricValue.Scalar` (name/units/data[] với `qty` đơn).
 * - [BLOOD_PRESSURE] ↔ `MetricValue.BloodPressure` (systolic/diastolic).
 * - [SLEEP] ↔ `MetricValue.SleepSegment` (state + durationSeconds).
 * - [ECG] ↔ `MetricValue.Ecg` (classification, averageBpm, samplingHz, voltages).
 * - [HR_NOTIFICATION] ↔ sự kiện cảnh báo nhịp tim (Requirement 6.3).
 * - [HEART_RATE_STAT] ↔ `MetricValue.HeartRateStat` (min/avg/max sau tổng hợp instantaneous).
 */
enum class MetricSchema {
    STANDARD,
    BLOOD_PRESSURE,
    SLEEP,
    ECG,
    HR_NOTIFICATION,
    HEART_RATE_STAT,
}
