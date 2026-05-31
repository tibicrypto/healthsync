package com.healthautoexport.domain.logic

/**
 * Xác thực cổng (port) broker MQTT (Requirement 19.1).
 *
 * Hàm thuần: cấu hình MQTT SHALL được chấp nhận **khi và chỉ khi** cổng là số nguyên trong
 * `[1, 65535]`.
 *
 * Property 47 — *Validates: Requirements 19.1*.
 */
object MqttPortValidator {

    /** Cổng hợp lệ nhỏ nhất (bao gồm). */
    const val MIN_PORT: Int = 1

    /** Cổng hợp lệ lớn nhất (bao gồm). */
    const val MAX_PORT: Int = 65535

    /**
     * Trả về `true` nếu [port] nằm trong `[1, 65535]`.
     *
     * @param port cổng broker do người dùng cấu hình.
     */
    fun isValid(port: Int): Boolean = port in MIN_PORT..MAX_PORT
}
