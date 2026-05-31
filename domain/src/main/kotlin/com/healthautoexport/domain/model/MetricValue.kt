package com.healthautoexport.domain.model

import java.math.BigDecimal

/**
 * Giá trị đã chuẩn hóa của một [Unified_Record][UnifiedRecord].
 *
 * `MetricValue` cho phép một bản ghi mang **giá trị đơn** (`qty`) hoặc **giá trị có cấu trúc**
 * (huyết áp, thống kê nhịp tim, giai đoạn giấc ngủ, ECG...). Mỗi biến thể tương ứng với một
 * [MetricSchema] để JSON_Serializer/CSV_Serializer chọn đúng lược đồ tuần tự hóa
 * (Requirement 10.6).
 *
 * Mọi đại lượng số dùng [BigDecimal] thay vì kiểu dấu phẩy động để **bảo toàn độ chính xác**
 * của `qty` qua round-trip JSON/CSV (Requirement 10.5) — không làm tròn hay mất chữ số.
 *
 * Đây là mô hình **thuần Kotlin/JVM** (không phụ thuộc Android) để pipeline Merge → Aggregate →
 * Serialize chạy và kiểm thử nhanh trên JVM.
 */
sealed interface MetricValue {

    /**
     * Giá trị vô hướng đơn — lược đồ [MetricSchema.STANDARD].
     *
     * Dùng cho phần lớn Health_Metric (số bước, quãng đường, năng lượng, cân nặng, SpO2...).
     * `qty` ở đơn vị canonical của metric và giữ nguyên độ chính xác bằng [BigDecimal]
     * (Requirement 10.5).
     *
     * @property qty giá trị đo ở đơn vị canonical.
     */
    data class Scalar(val qty: BigDecimal) : MetricValue

    /**
     * Huyết áp — lược đồ [MetricSchema.BLOOD_PRESSURE].
     *
     * Mang đồng thời tâm thu ([systolic]) và tâm trương ([diastolic]) ở đơn vị `mmHg`
     * ([CanonicalUnit.MMHG]); cả hai dùng [BigDecimal] để bảo toàn giá trị gốc.
     *
     * @property systolic huyết áp tâm thu (mmHg).
     * @property diastolic huyết áp tâm trương (mmHg).
     */
    data class BloodPressure(
        val systolic: BigDecimal,
        val diastolic: BigDecimal,
    ) : MetricValue

    /**
     * Thống kê nhịp tim min/avg/max — lược đồ [MetricSchema.HEART_RATE_STAT].
     *
     * Dùng cho dữ liệu nhịp tim đã được tóm tắt (vd chuỗi nhịp tim của một Workout) khi không
     * cần kèm số lượng mẫu. Với thống kê tổng hợp tổng quát theo khung thời gian (kèm `count`),
     * xem [StatSummary].
     *
     * @property min giá trị nhỏ nhất (bpm).
     * @property avg giá trị trung bình (bpm).
     * @property max giá trị lớn nhất (bpm).
     */
    data class HeartRateStat(
        val min: BigDecimal,
        val avg: BigDecimal,
        val max: BigDecimal,
    ) : MetricValue

    /**
     * Tóm tắt thống kê tổng quát của một metric tức thời (INSTANTANEOUS) sau khi `Aggregator`
     * gộp các bản ghi trong một khung thời gian: `{min, avg, max, count}` (Requirement 8.5).
     *
     * Khác với [HeartRateStat], biến thể này kèm [count] = số bản ghi gốc đóng góp vào khung,
     * dùng được cho mọi metric tức thời (cân nặng, SpO2, nhiệt độ, nhịp tim...).
     *
     * @property min giá trị nhỏ nhất trong khung (đơn vị canonical của metric).
     * @property avg giá trị trung bình trong khung.
     * @property max giá trị lớn nhất trong khung.
     * @property count số bản ghi gốc trong khung; SHALL ≥ 0 (0 chỉ dùng cho khung rỗng đặc biệt).
     */
    data class StatSummary(
        val min: BigDecimal,
        val avg: BigDecimal,
        val max: BigDecimal,
        val count: Long,
    ) : MetricValue {
        init {
            require(count >= 0) { "StatSummary.count phải ≥ 0, nhận được $count" }
        }
    }

    /**
     * Một phân đoạn giấc ngủ — lược đồ [MetricSchema.SLEEP] (Requirement 6.1).
     *
     * Mỗi phiên ngủ được phân rã thành các phân đoạn theo [SleepState] (awake/REM/core/deep...),
     * mỗi phân đoạn mang thời lượng tính bằng **giây, là số nguyên không âm** (Requirement 6.1).
     *
     * @property state giai đoạn của phân đoạn giấc ngủ.
     * @property durationSeconds thời lượng phân đoạn (giây); SHALL ≥ 0 (Requirement 6.1).
     */
    data class SleepSegment(
        val state: SleepState,
        val durationSeconds: Long,
    ) : MetricValue {
        init {
            require(durationSeconds >= 0) {
                "SleepSegment.durationSeconds phải ≥ 0 (Requirement 6.1), nhận được $durationSeconds"
            }
        }
    }

    /**
     * Một bản ghi điện tâm đồ (ECG) — lược đồ [MetricSchema.ECG] (Requirement 6.2).
     *
     * Giữ trọn chi tiết mà thiết bị ghi nhận: phân loại, nhịp tim trung bình, tần số lấy mẫu và
     * **toàn bộ chuỗi mẫu điện áp theo đúng thứ tự ghi nhận** (Requirement 6.2).
     *
     * @property classification chuỗi phân loại ECG do nguồn cung cấp (vd nhịp xoang, rung nhĩ...).
     * @property averageBpm nhịp tim trung bình; SHALL nằm trong khoảng `0..300` bpm (Requirement 6.2).
     * @property samplingHz tần số lấy mẫu (Hz); SHALL là số dương `> 0` (Requirement 6.2).
     * @property voltages chuỗi mẫu điện áp (`µV`) **theo thứ tự ghi nhận**, không sắp xếp lại
     *   (Requirement 6.2).
     */
    data class Ecg(
        val classification: String,
        val averageBpm: Int,
        val samplingHz: BigDecimal,
        val voltages: List<BigDecimal>,
    ) : MetricValue {
        init {
            require(averageBpm in 0..300) {
                "Ecg.averageBpm phải trong khoảng 0..300 bpm (Requirement 6.2), nhận được $averageBpm"
            }
            require(samplingHz.signum() > 0) {
                "Ecg.samplingHz phải > 0 (Requirement 6.2), nhận được $samplingHz"
            }
        }
    }
}
