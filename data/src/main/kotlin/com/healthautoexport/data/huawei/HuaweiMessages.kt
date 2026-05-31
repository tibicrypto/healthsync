package com.healthautoexport.data.huawei

/**
 * Thông điệp người dùng đọc được dùng chung bởi bộ điều hợp Huawei_Health_Kit (Data_Source và
 * Permission_Manager), gom về một nơi để bảo đảm nhất quán văn bản (Requirements 2.1, 2.8).
 *
 * Đây là `internal` vì chỉ phục vụ tầng `:data`; văn bản hiển thị cuối cùng có thể được tầng
 * presentation bản địa hóa lại nếu cần.
 */
internal object HuaweiMessages {

    /**
     * Lý do hiển thị khi HMS Core / Huawei_Health_Kit không khả dụng trên thiết bị
     * (Requirement 2.1). Khi gặp trạng thái này App tiếp tục hoạt động bằng Health_Connect.
     */
    const val UNAVAILABLE_REASON: String = "HMS Core/Huawei Health Kit không khả dụng"

    /**
     * Lý do hiển thị khi luồng ủy quyền Huawei vượt quá 60 giây và bị hủy (Requirement 2.8).
     * Người dùng được phép thử lại sau thông báo này.
     */
    const val TIMEOUT_REASON: String =
        "Yêu cầu ủy quyền Huawei Health Kit đã hết thời gian chờ (60 giây)"
}
