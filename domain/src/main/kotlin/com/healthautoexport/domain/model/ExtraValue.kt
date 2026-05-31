package com.healthautoexport.domain.model

import java.math.BigDecimal

/**
 * Một giá trị siêu dữ liệu (metadata) phụ đính kèm một [Unified_Record][UnifiedRecord] qua
 * `extras: Map<String, ExtraValue>`.
 *
 * Dùng để mang các trường ngoài giá trị đo chính, ví dụ:
 * - `mealTime` của bản ghi đường huyết (Requirement 6.4),
 * - `reason`/ngưỡng của cảnh báo nhịp tim (Requirement 6.3),
 * - trạng thái giai đoạn của dữ liệu giấc ngủ, hay các nhãn phân loại khác.
 *
 * `ExtraValue` là một kiểu **đóng kín (sealed)** để giữ an toàn kiểu nhưng vẫn linh hoạt: số
 * dùng [BigDecimal] nhằm bảo toàn độ chính xác đồng nhất với [MetricValue] (Requirement 10.5).
 * Đây là mô hình thuần Kotlin/JVM, không phụ thuộc Android.
 */
sealed interface ExtraValue {

    /**
     * Giá trị dạng chuỗi tự do (vd ghi chú, mô tả không thuộc tập cố định).
     *
     * @property value nội dung chuỗi.
     */
    data class StringValue(val value: String) : ExtraValue

    /**
     * Giá trị số giữ độ chính xác bằng [BigDecimal] (vd ngưỡng bpm của cảnh báo nhịp tim).
     *
     * @property value giá trị số.
     */
    data class NumberValue(val value: BigDecimal) : ExtraValue

    /**
     * Giá trị thuộc một tập phân loại cố định, biểu diễn bằng tên hằng số ổn định (vd `mealTime`
     * của đường huyết: `BEFORE_MEAL`/`AFTER_MEAL`, hoặc trạng thái giấc ngủ).
     *
     * Lưu tên dưới dạng chuỗi (thay vì một enum cụ thể) để `extras` dùng chung được cho nhiều
     * loại nhãn khác nhau mà không ràng buộc domain vào từng enum riêng lẻ.
     *
     * @property name tên hằng số phân loại (ổn định, dùng làm khóa khi tuần tự hóa).
     */
    data class EnumValue(val name: String) : ExtraValue
}
