package com.healthautoexport.domain.port

import com.healthautoexport.domain.model.HealthMetricType
import com.healthautoexport.domain.model.HealthPermission

/**
 * Kết quả của một yêu cầu cấp/ủy quyền đọc (Requirements 1.2, 1.9, 2.2, 2.4, 2.8).
 *
 * Khi yêu cầu lỗi/hết thời gian chờ, App giữ nguyên tập quyền đã lưu trước đó và ghi Sync_Log
 * (Requirements 1.9, 2.8); với Huawei, hiển thị lý do thất bại và cho phép thử lại
 * (Requirements 2.4, 2.8).
 */
sealed interface PermissionRequestResult {

    /**
     * Yêu cầu hoàn tất; trả về tập quyền hiện được cấp sau yêu cầu.
     *
     * @property granted tập quyền hiện được cấp/ủy quyền.
     */
    data class Granted(val granted: Set<HealthPermission>) : PermissionRequestResult

    /**
     * Người dùng từ chối một phần hoặc toàn bộ; kèm các metric chưa được cấp để App loại khỏi
     * Export_Job (Requirements 1.4, 2.5).
     *
     * @property granted tập quyền được cấp (có thể rỗng).
     * @property deniedMetrics các metric đã chọn nhưng chưa được cấp quyền.
     */
    data class Denied(
        val granted: Set<HealthPermission>,
        val deniedMetrics: Set<HealthMetricType>,
    ) : PermissionRequestResult

    /**
     * Yêu cầu lỗi hoặc hết thời gian chờ (30s Health_Connect, 60s Huawei); tập quyền cũ được
     * giữ nguyên (Requirements 1.9, 2.8).
     *
     * @property reason lý do người dùng đọc được.
     * @property timedOut `true` nếu thất bại do hết thời gian chờ.
     */
    data class Failed(
        val reason: String,
        val timedOut: Boolean = false,
    ) : PermissionRequestResult
}
