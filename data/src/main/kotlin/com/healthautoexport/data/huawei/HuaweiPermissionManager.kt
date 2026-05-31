package com.healthautoexport.data.huawei

import com.healthautoexport.domain.logic.PermissionScopes
import com.healthautoexport.domain.model.DataSourceId
import com.healthautoexport.domain.model.HealthMetricType
import com.healthautoexport.domain.model.HealthPermission
import com.healthautoexport.domain.model.MetricSelection
import com.healthautoexport.domain.model.PermissionState
import com.healthautoexport.domain.port.PermissionManager
import com.healthautoexport.domain.port.PermissionRequestResult
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Quản lý ủy quyền đọc cho **Huawei_Health_Kit** (Requirement 2), hiện thực [PermissionManager].
 *
 * Toàn bộ tương tác với SDK Huawei đi qua [HuaweiHealthClient] để build/chạy được khi không có HMS
 * (Requirement 2.1); tập scope đã ủy quyền được lưu bền vững qua [HuaweiScopeStore] trên DataStore
 * (Requirements 2.3, 2.6). Quan hệ "lựa chọn → scope cần yêu cầu" và "scope đã cấp → trạng thái
 * theo metric" dựa trên logic thuần [PermissionScopes] để bảo đảm đúng-một-một với lựa chọn
 * (Requirement 2.2) và trạng thái toàn phần theo metric (Requirement 2.7).
 *
 * Hành vi chính:
 * - [requestReadPermissions]: yêu cầu read scope **chỉ cho** [MetricSelection] (Requirement 2.2);
 *   bọc luồng ủy quyền trong `withTimeoutOrNull(60s)` — quá hạn thì **hủy luồng, không lưu scope**,
 *   trả [PermissionRequestResult.Failed] với `timedOut = true` để App báo lỗi và cho thử lại
 *   (Requirements 2.4, 2.8). Thành công thì lưu scope đã cấp (Requirement 2.3).
 * - [grantedStatus]: "Đã/Chưa ủy quyền" theo từng metric, dựa trên scope **đã lưu trên thiết bị**
 *   (Requirement 2.7).
 * - [refreshGrants]: trả tập [HealthPermission] suy từ scope đã lưu (Requirement 2.6).
 *
 * Dưới [NoOpHuaweiHealthClient]: [requestReadPermissions] trả `Failed(unavailable)`, không lưu
 * scope; [grantedStatus] báo mọi metric là [PermissionState.NOT_GRANTED]; [refreshGrants] trả rỗng
 * (Requirement 2.1).
 *
 * @property client ranh giới trừu tượng hóa SDK Huawei.
 * @property scopeStore lưu trữ bền vững scope đã ủy quyền.
 * @property authorizationTimeoutMillis hạn chót luồng ủy quyền (mặc định 60s — Requirement 2.8).
 */
internal class HuaweiPermissionManager(
    private val client: HuaweiHealthClient,
    private val scopeStore: HuaweiScopeStore,
    private val authorizationTimeoutMillis: Long = DEFAULT_AUTH_TIMEOUT_MILLIS,
) : PermissionManager {

    override suspend fun requestReadPermissions(
        source: DataSourceId,
        selection: MetricSelection,
    ): PermissionRequestResult {
        requireHuawei(source)

        // HMS không khả dụng → không thể ủy quyền; giữ nguyên (không lưu) scope (Requirement 2.1).
        if (!client.isHmsAvailable()) {
            return PermissionRequestResult.Failed(reason = HuaweiMessages.UNAVAILABLE_REASON)
        }

        // Tập scope cần yêu cầu = đúng lựa chọn của người dùng (Requirement 2.2) qua logic thuần.
        val readScopes = PermissionScopes.permissionsForSelection(selection, source)
        val requested = HuaweiScopeMapper.toHuaweiScopes(readScopes)

        // Bọc luồng ủy quyền trong giới hạn 60s; quá hạn -> hủy hợp tác, không lưu (Requirement 2.8).
        val result: HuaweiAuthResult? = try {
            withTimeoutOrNull(authorizationTimeoutMillis) {
                client.requestAuthorization(requested)
            }
        } catch (_: TimeoutCancellationException) {
            null
        }

        return when (result) {
            null -> {
                // Timeout: hủy luồng và bảo đảm không giữ lại trạng thái dở dang (Requirement 2.8).
                client.cancelAuthorization()
                PermissionRequestResult.Failed(
                    reason = HuaweiMessages.TIMEOUT_REASON,
                    timedOut = true,
                )
            }

            is HuaweiAuthResult.Authorized -> {
                // Lưu tập scope thực được cấp (Requirement 2.3).
                scopeStore.saveScopes(result.grantedScopes)
                val granted = grantedPermissions(result.grantedScopes, source)
                val deniedMetrics = selection.metrics - granted.map { it.metric }.toSet()
                if (deniedMetrics.isEmpty()) {
                    PermissionRequestResult.Granted(granted)
                } else {
                    // Người dùng ủy quyền một phần — kèm metric chưa được cấp (Requirement 2.5).
                    PermissionRequestResult.Denied(granted = granted, deniedMetrics = deniedMetrics)
                }
            }

            is HuaweiAuthResult.Failed ->
                // Báo lý do do Huawei cung cấp; App cho phép thử lại (Requirement 2.4).
                PermissionRequestResult.Failed(reason = result.reason)

            HuaweiAuthResult.Unavailable ->
                PermissionRequestResult.Failed(reason = HuaweiMessages.UNAVAILABLE_REASON)
        }
    }

    /**
     * Huawei_Health_Kit không có khái niệm "quyền đọc nền" tách biệt như Health_Connect; quyền nền
     * được bao trùm bởi cùng tập scope đọc. Hàm này phản ánh trạng thái ủy quyền hiện tại: trả
     * [PermissionRequestResult.Granted] với tập scope đã lưu khi HMS khả dụng, ngược lại
     * [PermissionRequestResult.Failed] (Requirement 2.1).
     */
    override suspend fun requestBackgroundReadPermission(
        source: DataSourceId,
    ): PermissionRequestResult {
        requireHuawei(source)
        if (!client.isHmsAvailable()) {
            return PermissionRequestResult.Failed(reason = HuaweiMessages.UNAVAILABLE_REASON)
        }
        return PermissionRequestResult.Granted(refreshGrants(source))
    }

    override suspend fun grantedStatus(
        source: DataSourceId,
        selection: MetricSelection,
    ): Map<HealthMetricType, PermissionState> {
        requireHuawei(source)
        // Trạng thái dựa trên scope ĐÃ LƯU trên thiết bị (Requirement 2.7).
        val granted = grantedPermissions(scopeStore.authorizedScopes(), source)
        return PermissionScopes.grantedStatus(selection, granted, source)
    }

    override suspend fun refreshGrants(source: DataSourceId): Set<HealthPermission> {
        requireHuawei(source)
        // Khi HMS không khả dụng, coi như không có quyền nào (Requirement 2.1).
        if (!client.isHmsAvailable()) return emptySet()
        return grantedPermissions(scopeStore.authorizedScopes(), source)
    }

    /** Suy tập [HealthPermission] (theo metric) từ tập [HuaweiScope] đã ủy quyền. */
    private fun grantedPermissions(
        authorized: Set<HuaweiScope>,
        source: DataSourceId,
    ): Set<HealthPermission> =
        HuaweiScopeMapper.metricsFromScopes(authorized)
            .mapTo(mutableSetOf()) { PermissionScopes.permissionFor(it, source) }

    private fun requireHuawei(source: DataSourceId) {
        require(source == DataSourceId.HUAWEI_HEALTH_KIT) {
            "HuaweiPermissionManager chỉ xử lý ${DataSourceId.HUAWEI_HEALTH_KIT}, nhận được $source"
        }
    }

    private companion object {
        /** 60 giây — hạn chót luồng ủy quyền Huawei (Requirement 2.8). */
        const val DEFAULT_AUTH_TIMEOUT_MILLIS: Long = 60_000L
    }
}
