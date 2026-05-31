package com.healthautoexport.domain.port

import com.healthautoexport.domain.model.DataSourceId
import com.healthautoexport.domain.model.HealthMetricType
import com.healthautoexport.domain.model.HealthPermission
import com.healthautoexport.domain.model.MetricSelection
import com.healthautoexport.domain.model.PermissionState

/**
 * Port quản lý quyền/ủy quyền đọc cho mỗi Data_Source (Requirements 1, 2).
 *
 * Yêu cầu quyền **chỉ cho** lựa chọn của người dùng (Requirements 1.2, 2.2); tập quyền đã cấp
 * được lưu qua DataStore ở tầng dữ liệu (Requirements 1.3, 2.3). Khi yêu cầu lỗi/timeout, giữ
 * nguyên tập cũ và ghi Sync_Log (Requirements 1.9, 2.8).
 */
interface PermissionManager {

    /** Yêu cầu quyền đọc chỉ cho [selection] trên [source] (Requirements 1.2, 2.2). */
    suspend fun requestReadPermissions(
        source: DataSourceId,
        selection: MetricSelection,
    ): PermissionRequestResult

    /** Yêu cầu quyền đọc **nền** cho xuất theo lịch (Requirements 1.5, 1.10). */
    suspend fun requestBackgroundReadPermission(source: DataSourceId): PermissionRequestResult

    /** Trạng thái hiển thị (đã/chưa cấp) cho từng metric đã chọn (Requirements 1.7, 2.7). */
    suspend fun grantedStatus(
        source: DataSourceId,
        selection: MetricSelection,
    ): Map<HealthMetricType, PermissionState>

    /**
     * Làm mới và trả về tập quyền hiện được cấp; dùng để phát hiện thu hồi đầu mỗi Export_Job
     * (Requirements 1.6, 2.6).
     */
    suspend fun refreshGrants(source: DataSourceId): Set<HealthPermission>
}
