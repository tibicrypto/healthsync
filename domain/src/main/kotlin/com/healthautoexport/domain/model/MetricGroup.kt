package com.healthautoexport.domain.model

/**
 * 12 nhóm phân loại Health_Metric mà App hỗ trợ (Requirement 4.1).
 *
 * Mỗi [HealthMetricType] thuộc về đúng một `MetricGroup` qua thuộc tính
 * [HealthMetricType.group]. Các nhóm này dùng để tổ chức danh sách chỉ số trên UI
 * và để bảo đảm danh mục bao phủ đủ 12 nhóm theo Requirement 4.1.
 */
enum class MetricGroup {
    /** Vận động — bước chân, quãng đường, năng lượng hoạt động... */
    ACTIVITY,

    /** Đo lường cơ thể — cân nặng, chiều cao, tỷ lệ mỡ, BMI... */
    BODY_MEASUREMENT,

    /** Tim mạch — nhịp tim, HRV, huyết áp, VO2 max... */
    HEART,

    /** Thính giác — phơi nhiễm âm thanh tai nghe và môi trường. */
    HEARING,

    /** Dinh dưỡng — nước, năng lượng nạp vào, carbohydrate, protein, chất béo... */
    NUTRITION,

    /** Chánh niệm — phút thiền/chánh niệm. */
    MINDFULNESS,

    /** Vận động chức năng — tốc độ đi bộ và các chỉ số mobility khác. */
    MOBILITY,

    /** Sức khỏe sinh sản — chu kỳ kinh nguyệt, rụng trứng, hoạt động tình dục. */
    REPRODUCTIVE_HEALTH,

    /** Hô hấp — nhịp thở, độ bão hòa oxy trong máu. */
    RESPIRATORY,

    /** Giấc ngủ — phân tích giai đoạn giấc ngủ. */
    SLEEP,

    /** Sinh hiệu — nhiệt độ cơ thể, đường huyết... */
    VITALS,

    /** Khác / chuyên biệt — ECG, cảnh báo nhịp tim và các loại đặc thù khác. */
    OTHER,
}
