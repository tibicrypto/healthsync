package com.healthautoexport.domain.port

/**
 * Kết quả gửi payload tới một Destination, dùng để ghi Sync_Log (Requirements 16–21).
 */
sealed interface DestinationResult {

    /**
     * Gửi thành công.
     *
     * @property detail chi tiết người dùng đọc được (vd tên tệp đã lưu).
     */
    data class Success(val detail: String) : DestinationResult

    /**
     * Gửi thất bại.
     *
     * @property reason lý do thất bại (vd mã HTTP ngoài 2xx kèm body, lỗi xác thực...).
     * @property retryEligible `true` nếu lần gửi có thể thử lại (Requirements 15.7, 20.6).
     */
    data class Failure(
        val reason: String,
        val retryEligible: Boolean,
    ) : DestinationResult
}
