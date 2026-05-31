package com.healthautoexport.data.healthconnect

import androidx.health.connect.client.permission.HealthPermission as HcHealthPermission
import com.healthautoexport.domain.logic.PermissionScopes
import com.healthautoexport.domain.logic.ReadScope
import com.healthautoexport.domain.model.DataSourceId
import com.healthautoexport.domain.model.HealthMetricType
import com.healthautoexport.domain.model.HealthPermission
import com.healthautoexport.domain.model.MetricSelection

/**
 * Cầu nối **một nơi** giữa mô hình quyền thuần domain ([HealthPermission]/[ReadScope]) và chuỗi
 * quyền đọc của Health_Connect SDK (Requirements 1.2, 1.5, 1.6, 1.7).
 *
 * Mọi phép ánh xạ metric ↔ chuỗi quyền HC tập trung tại đây (tái dùng bảng loại bản ghi trong
 * [HealthConnectRecordTypes]) để dễ điều chỉnh khi API SDK đổi, và để [HealthConnectPermissionManager]
 * chỉ lo điều phối I/O. Chuỗi quyền được lấy qua `HealthPermission.getReadPermission(RecordType::class)`,
 * còn quyền đọc nền dùng hằng [HcHealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND]
 * (Requirements 1.5, 1.10).
 */
object HealthConnectPermissionMapping {

    private val SOURCE = DataSourceId.HEALTH_CONNECT

    /** Chuỗi quyền đọc nền của Health_Connect (Requirements 1.5, 1.10). */
    val backgroundReadPermission: String = HcHealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

    /**
     * Chuỗi quyền đọc Health_Connect cho một metric, hoặc `null` nếu bộ điều hợp không hỗ trợ
     * metric đó (không có loại bản ghi tương ứng).
     */
    fun readPermissionFor(metric: HealthMetricType): String? =
        HealthConnectRecordTypes.recordTypeFor(metric)?.let { HcHealthPermission.getReadPermission(it) }

    /** Chuỗi quyền đọc cho phiên tập (dùng khi người dùng chọn Workout — Requirement 5.1). */
    fun workoutReadPermission(): String =
        HcHealthPermission.getReadPermission(HealthConnectRecordTypes.workoutRecordType)

    /**
     * Tập chuỗi quyền Health_Connect **chính xác** cần yêu cầu cho [selection] (Requirements 1.2):
     * một quyền đọc cho mỗi metric đã chọn (được hỗ trợ) và quyền đọc phiên tập nếu có chọn Workout.
     *
     * Dựa trên [PermissionScopes.permissionsForSelection] (nguồn sự thật thuần) để bảo đảm "không
     * thừa, không thiếu" (Property 29), rồi dịch mỗi [ReadScope] thành chuỗi quyền HC.
     */
    fun requestPermissionStrings(selection: MetricSelection): Set<String> {
        val scopes = PermissionScopes.permissionsForSelection(selection, SOURCE)
        val result = LinkedHashSet<String>()
        for (scope in scopes) {
            when (scope) {
                is ReadScope.Metric -> readPermissionFor(scope.permission.metric)?.let { result += it }
                is ReadScope.Workout -> result += workoutReadPermission()
            }
        }
        return result
    }

    /**
     * Ánh xạ ngược tập chuỗi quyền HC [granted] về tập [HealthPermission] thuần domain
     * (chỉ phần metric), dùng để phát hiện thu hồi ở đầu mỗi Export_Job (Requirements 1.6, 1.7).
     *
     * Quyền phiên tập và quyền đọc nền không tương ứng một [HealthMetricType] nên bị bỏ qua trong
     * phép dịch ngược này (chúng được xử lý riêng).
     */
    fun toDomainPermissions(granted: Set<String>): Set<HealthPermission> =
        HealthConnectRecordTypes.mapperKnownMetrics
            .filter { metric -> readPermissionFor(metric)?.let { it in granted } == true }
            .mapTo(mutableSetOf()) { PermissionScopes.permissionFor(it, SOURCE) }

    /** `true` nếu tất cả quyền cần cho [selection] đều nằm trong [granted] (Requirement 1.2). */
    fun allGranted(selection: MetricSelection, granted: Set<String>): Boolean =
        requestPermissionStrings(selection).all { it in granted }

    /**
     * Các metric đã chọn nhưng **chưa** được cấp quyền đọc trong [granted] (Requirements 1.4, 1.7).
     */
    fun deniedMetrics(selection: MetricSelection, granted: Set<String>): Set<HealthMetricType> =
        selection.metrics.filterTo(mutableSetOf()) { metric ->
            readPermissionFor(metric)?.let { it !in granted } ?: true
        }
}
