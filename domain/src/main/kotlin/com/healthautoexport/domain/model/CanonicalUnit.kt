package com.healthautoexport.domain.model

/**
 * Đơn vị canonical (chuẩn hóa) cho các loại Health_Metric.
 *
 * Mỗi [HealthMetricType] được gán **đúng một** `CanonicalUnit` qua `MetricCatalog` (task 2.5);
 * `DataReader` chuyển mọi giá trị bản ghi nguồn về đơn vị này (Requirement 4.2). Tên hằng số
 * là định danh nội bộ an toàn cho Kotlin, còn [symbol] là chuỗi đơn vị xuất ra trường `units`
 * trong JSON/CSV (vd `"count"`, `"bpm"`, `"%"`, `"mmHg"`) — bám theo bảng "Canonical Units và
 * Metric Catalog Mapping" trong design.md.
 *
 * @property symbol chuỗi `units` dùng trong tuần tự hóa (Requirements 10.4, 11.1).
 */
enum class CanonicalUnit(val symbol: String) {
    /** Số đếm thuần (bước, lần, tầng...). */
    COUNT("count"),

    /** Mét — quãng đường, chiều cao. */
    METER("m"),

    /** Kilocalorie — năng lượng. */
    KCAL("kcal"),

    /** Nhịp mỗi phút — nhịp tim. */
    BPM("bpm"),

    /** Kilogram — cân nặng, khối lượng nạc. */
    KILOGRAM("kg"),

    /** Phần trăm — tỷ lệ mỡ cơ thể, SpO2. */
    PERCENT("%"),

    /** Milimét thủy ngân — huyết áp. */
    MMHG("mmHg"),

    /** Mili giây — biến thiên nhịp tim (HRV). */
    MILLISECOND("ms"),

    /** Mililít trên kilogram mỗi phút — VO2 max. */
    ML_PER_KG_MIN("mL/(kg·min)"),

    /** Số đếm mỗi phút — nhịp bước, nhịp thở. */
    COUNT_PER_MIN("count/min"),

    /** Mét trên giây — tốc độ. */
    METER_PER_SECOND("m/s"),

    /** Độ C — nhiệt độ cơ thể. */
    DEGREE_CELSIUS("degC"),

    /** Miligram trên decilít — đường huyết. */
    MG_PER_DL("mg/dL"),

    /** Lít — lượng nước nạp vào. */
    LITER("L"),

    /** Gram — dinh dưỡng (carbohydrate, protein, chất béo). */
    GRAM("g"),

    /** Giây — thời lượng giai đoạn giấc ngủ. */
    SECOND("s"),

    /** Phút — thời lượng chánh niệm. */
    MINUTE("min"),

    /** Decibel A-weighted SPL — phơi nhiễm âm thanh. */
    DBASPL("dBASPL"),

    /** Microvolt — chuỗi mẫu điện áp ECG. */
    MICROVOLT("µV"),

    /**
     * Giá trị phân loại (không có đơn vị đo lường) — chu kỳ kinh nguyệt, rụng trứng,
     * hoạt động tình dục. Bổ sung ngoài danh sách đơn vị số học để mọi `HealthMetricType`
     * trong catalog đều có đúng một `CanonicalUnit`.
     */
    CATEGORY("category"),
}
