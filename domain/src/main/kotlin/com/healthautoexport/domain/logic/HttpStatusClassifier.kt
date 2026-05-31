package com.healthautoexport.domain.logic

/**
 * Phân loại mã trạng thái HTTP cho Export_Job tới REST API và Home Assistant
 * (Requirements 16.5, 16.6).
 *
 * Hàm thuần: một Export_Job được coi là thành công **khi và chỉ khi** mã nằm trong dải 2xx
 * `[200, 299]`; ngoài dải đó là thất bại (ghi mã trạng thái vào Sync_Log).
 *
 * Property 43 — *Validates: Requirements 16.5, 16.6*.
 */
object HttpStatusClassifier {

    /** Cận dưới (bao gồm) của dải thành công 2xx. */
    const val SUCCESS_MIN: Int = 200

    /** Cận trên (bao gồm) của dải thành công 2xx. */
    const val SUCCESS_MAX: Int = 299

    /**
     * Trả về `true` nếu [code] biểu thị thành công (nằm trong `[200, 299]`).
     *
     * @param code mã trạng thái HTTP nhận từ phản hồi.
     */
    fun isSuccess(code: Int): Boolean = code in SUCCESS_MIN..SUCCESS_MAX
}
