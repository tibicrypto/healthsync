package com.healthautoexport.domain.logic

import java.util.Locale

/**
 * Kết quả xác thực tên Automation (Requirements 14.1, 14.7).
 *
 * Là kiểu **sealed** để gọi-bên (use case / ViewModel) buộc phải xử lý đủ mọi nhánh: hợp lệ
 * ([Valid]) hoặc không hợp lệ ([Invalid]) kèm [Invalid.reason] mô tả nguyên nhân.
 */
sealed interface AutomationNameValidation {

    /** `true` nếu kết quả là hợp lệ. Tiện cho điều kiện ngắn gọn ở gọi-bên. */
    val isValid: Boolean get() = this is Valid

    /** Tên hợp lệ: độ dài nằm trong 1..100 và không trùng (không phân biệt hoa/thường). */
    data object Valid : AutomationNameValidation

    /**
     * Tên không hợp lệ.
     *
     * @property reason nguyên nhân từ chối ([Reason]).
     */
    data class Invalid(val reason: Reason) : AutomationNameValidation

    /** Các nguyên nhân khiến một tên Automation bị từ chối. */
    enum class Reason {
        /** Tên rỗng (độ dài 0) — vi phạm độ dài tối thiểu 1 ký tự (Requirement 14.1). */
        EMPTY,

        /** Tên dài quá 100 ký tự (Requirement 14.1). */
        TOO_LONG,

        /** Trùng (không phân biệt hoa/thường) với một Automation đã tồn tại (Requirement 14.7). */
        DUPLICATE,
    }
}

/**
 * Validator **thuần** (không I/O, không phụ thuộc Android) cho tên Automation.
 *
 * Hiện thực hai ràng buộc:
 * - **Độ dài 1..100 ký tự** (Requirement 14.1).
 * - **Tính duy nhất không phân biệt hoa/thường** so với tập tên đã tồn tại trên thiết bị
 *   (Requirement 14.7) — nền tảng cho Property 50.
 *
 * Thiết kế thuần giúp kiểm thử dựa-trên-thuộc-tính (PBT) dễ dàng. Việc *giữ nguyên dữ liệu người
 * dùng đã nhập* khi từ chối (Requirement 14.7) là trách nhiệm của tầng UI/use case, không thuộc
 * validator này.
 *
 * Ghi chú thiết kế:
 * - Độ dài đo theo `String.length` (số đơn vị mã UTF-16), **không** cắt khoảng trắng (no-trim) để
 *   trung thành với phát biểu "1..100 ký tự".
 * - So trùng dùng [String.lowercase] với [Locale.ROOT] để cho kết quả **xác định**, không phụ
 *   thuộc locale thiết bị.
 * - Thứ tự ưu tiên kiểm tra: rỗng → quá dài → trùng. Nhờ đó kết quả là đơn trị và ổn định.
 */
object AutomationNameValidator {

    /** Độ dài tối thiểu cho tên Automation (Requirement 14.1). */
    const val MIN_LENGTH: Int = 1

    /** Độ dài tối đa cho tên Automation (Requirement 14.1). */
    const val MAX_LENGTH: Int = 100

    /**
     * Xác thực [candidate] dựa trên độ dài và tính duy nhất so với [existingNames].
     *
     * @param candidate tên người dùng đề xuất (chưa cắt khoảng trắng).
     * @param existingNames tập tên Automation đã tồn tại trên thiết bị. Có thể chứa tên ở dạng
     *   hoa/thường bất kỳ; phép so trùng tự chuẩn hóa về chữ thường.
     * @return [AutomationNameValidation.Valid] khi hợp lệ; ngược lại [AutomationNameValidation.Invalid]
     *   kèm nguyên nhân.
     */
    fun validate(
        candidate: String,
        existingNames: Set<String>,
    ): AutomationNameValidation {
        if (candidate.length < MIN_LENGTH) {
            return AutomationNameValidation.Invalid(AutomationNameValidation.Reason.EMPTY)
        }
        if (candidate.length > MAX_LENGTH) {
            return AutomationNameValidation.Invalid(AutomationNameValidation.Reason.TOO_LONG)
        }
        val candidateKey = candidate.lowercase(Locale.ROOT)
        val isDuplicate = existingNames.any { it.lowercase(Locale.ROOT) == candidateKey }
        if (isDuplicate) {
            return AutomationNameValidation.Invalid(AutomationNameValidation.Reason.DUPLICATE)
        }
        return AutomationNameValidation.Valid
    }
}
