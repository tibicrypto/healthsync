package com.healthautoexport.domain.logic

/**
 * Xác thực cấu hình Destination REST API (Requirement 16.1): URL dùng scheme HTTP/HTTPS, độ dài URL
 * `<= 2048` ký tự, và số header tùy chỉnh `<= 50`.
 *
 * Hàm thuần: chỉ phân tích chuỗi/đếm, không mở kết nối. Trả về [RestConfigValidation] mô tả mọi lý do
 * vi phạm để tầng UI hiển thị (chấp nhận **khi và chỉ khi** không có lý do nào).
 *
 * Property 45 — *Validates: Requirements 16.1*.
 */
object RestConfigValidator {

    /** Độ dài URL tối đa cho phép (Requirement 16.1). */
    const val MAX_URL_LENGTH: Int = 2048

    /** Số header tùy chỉnh tối đa cho phép (Requirement 16.1). */
    const val MAX_HEADER_COUNT: Int = 50

    /** Các scheme được chấp nhận, so khớp không phân biệt hoa thường. */
    private val ALLOWED_SCHEMES = setOf("http", "https")

    /**
     * Xác thực [url] và [headerCount].
     *
     * @param url URL đích đã cấu hình.
     * @param headerCount số header tùy chỉnh; SHALL `>= 0`.
     * @return [RestConfigValidation.Valid] nếu hợp lệ, ngược lại [RestConfigValidation.Invalid] kèm
     *   danh sách [RestConfigViolation].
     */
    fun validate(url: String, headerCount: Int): RestConfigValidation {
        require(headerCount >= 0) { "headerCount ($headerCount) phải >= 0" }

        val violations = buildList {
            if (!hasAllowedScheme(url)) add(RestConfigViolation.INVALID_SCHEME)
            if (url.length > MAX_URL_LENGTH) add(RestConfigViolation.URL_TOO_LONG)
            if (headerCount > MAX_HEADER_COUNT) add(RestConfigViolation.TOO_MANY_HEADERS)
        }

        return if (violations.isEmpty()) {
            RestConfigValidation.Valid
        } else {
            RestConfigValidation.Invalid(violations)
        }
    }

    /** Kiểm tra scheme đứng đầu [url] có thuộc tập HTTP/HTTPS hay không (không phân biệt hoa thường). */
    private fun hasAllowedScheme(url: String): Boolean {
        val separator = url.indexOf("://")
        if (separator <= 0) return false
        val scheme = url.substring(0, separator).lowercase()
        return scheme in ALLOWED_SCHEMES
    }
}

/**
 * Lý do một cấu hình REST bị từ chối (Requirement 16.1).
 */
enum class RestConfigViolation {
    /** Scheme không phải HTTP hoặc HTTPS. */
    INVALID_SCHEME,

    /** Độ dài URL vượt [RestConfigValidator.MAX_URL_LENGTH]. */
    URL_TOO_LONG,

    /** Số header vượt [RestConfigValidator.MAX_HEADER_COUNT]. */
    TOO_MANY_HEADERS,
}

/**
 * Kết quả xác thực cấu hình REST.
 */
sealed interface RestConfigValidation {

    /** `true` nếu cấu hình hợp lệ. */
    val isValid: Boolean

    /** Cấu hình hợp lệ. */
    data object Valid : RestConfigValidation {
        override val isValid: Boolean get() = true
    }

    /**
     * Cấu hình không hợp lệ.
     *
     * @property violations các lý do vi phạm; SHALL không rỗng.
     */
    data class Invalid(val violations: List<RestConfigViolation>) : RestConfigValidation {
        override val isValid: Boolean get() = false
    }
}
