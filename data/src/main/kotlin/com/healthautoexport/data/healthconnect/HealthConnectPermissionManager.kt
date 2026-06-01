package com.healthautoexport.data.healthconnect

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.healthautoexport.domain.logic.PermissionScopes
import com.healthautoexport.domain.model.DataSourceId
import com.healthautoexport.domain.model.HealthMetricType
import com.healthautoexport.domain.model.HealthPermission
import com.healthautoexport.domain.model.MetricSelection
import com.healthautoexport.domain.model.PermissionState
import com.healthautoexport.domain.port.PermissionManager
import com.healthautoexport.domain.port.PermissionRequestResult
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Bộ điều hợp [PermissionManager] cho **Health Connect** (Requirement 1).
 *
 * Trách nhiệm:
 * - Xây tập quyền/scope đọc **chính xác** cho lựa chọn của người dùng (Requirement 1.2) qua
 *   [HealthConnectPermissionMapping] (dựa trên [PermissionScopes], không thừa/thiếu — Property 29).
 * - Yêu cầu quyền đọc **nền** cho xuất theo lịch (Requirements 1.5, 1.10).
 * - [grantedStatus]/[refreshGrants] đọc `client.permissionController.getGrantedPermissions()` để
 *   phát hiện thu hồi và trả trạng thái theo từng metric (Requirements 1.6, 1.7).
 * - Lưu tập quyền đã cấp qua [GrantedPermissionStore] (DataStore) (Requirement 1.3); khi yêu cầu
 *   lỗi/timeout (30s) thì **giữ nguyên** tập cũ và trả [PermissionRequestResult.Failed]
 *   (Requirement 1.9).
 *
 * **Seam launch tương tác (task 21/UI).** Yêu cầu quyền tương tác phải chạy qua một
 * `ActivityResultContract` (`PermissionController.createRequestPermissionResultContract()`), vốn
 * cần một `Activity`. Vì `:data` không sở hữu `Activity`, lớp này:
 * - phơi bày phần **truy vấn được**: tập chuỗi quyền cần yêu cầu ([buildRequestPermissions]) và
 *   hợp đồng kết quả ([createRequestPermissionContract]) để UI khởi chạy;
 * - nhận một seam [requester] (do UI cung cấp ở task 21) thực thi việc launch và trả tập quyền đã
 *   cấp. Khi seam **chưa** được nối, [requestReadPermissions] không tự prompt mà chỉ **đồng bộ lại**
 *   tập quyền hiện có rồi lưu (vẫn xác định và an toàn).
 *
 * @property context [Context] ứng dụng để truy vấn SDK/permission controller.
 * @property store kho lưu tập quyền đã cấp (Requirement 1.3).
 * @property providerPackageName gói cung cấp Health_Connect.
 * @property requester seam launch tương tác do UI nối (task 21); `null` nếu chưa nối.
 * @property requestTimeoutMillis hạn chờ yêu cầu quyền (Requirement 1.9), mặc định 30s.
 * @property grantedPermissionsReader seam đọc tập chuỗi quyền đã cấp; mặc định gọi permission
 *   controller của Health_Connect (tách ra để kiểm thử trên JVM).
 */
class HealthConnectPermissionManager(
    private val context: Context,
    private val store: GrantedPermissionStore,
    private val providerPackageName: String = HealthConnectDataSource.HEALTH_CONNECT_PROVIDER_PACKAGE,
    private val requester: HealthConnectPermissionRequester? = null,
    private val requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS,
    private val grantedPermissionsReader: suspend () -> Set<String> = {
        defaultGrantedPermissionsReader(context, providerPackageName)
    },
) : PermissionManager {

    /**
     * Yêu cầu quyền đọc **chỉ cho** [selection] trên Health_Connect (Requirements 1.2, 1.9).
     *
     * Khi seam [requester] được nối: chạy launch trong [requestTimeoutMillis] (30s); thành công →
     * lưu tập quyền mới (Requirement 1.3) và trả [PermissionRequestResult.Granted] hoặc
     * [PermissionRequestResult.Denied] (kèm metric chưa cấp — Requirement 1.4). Lỗi/timeout →
     * **giữ nguyên** tập đã lưu và trả [PermissionRequestResult.Failed] (Requirement 1.9).
     *
     * Khi seam chưa nối: đồng bộ lại tập quyền hiện có và lưu, trả kết quả phản ánh thực tế.
     */
    override suspend fun requestReadPermissions(
        source: DataSourceId,
        selection: MetricSelection,
    ): PermissionRequestResult {
        if (source != DataSourceId.HEALTH_CONNECT) {
            return PermissionRequestResult.Failed(
                reason = "HealthConnectPermissionManager chỉ xử lý nguồn Health Connect.",
            )
        }
        val requestStrings = HealthConnectPermissionMapping.requestPermissionStrings(selection)
        return runRequest(selection, requestStrings)
    }

    /**
     * Yêu cầu quyền đọc **nền** cho xuất theo lịch (Requirements 1.5, 1.10).
     *
     * Dùng cùng cơ chế timeout/giữ-tập-cũ như [requestReadPermissions]; tập yêu cầu chỉ gồm chuỗi
     * quyền nền [HealthConnectPermissionMapping.backgroundReadPermission].
     */
    override suspend fun requestBackgroundReadPermission(
        source: DataSourceId,
    ): PermissionRequestResult {
        if (source != DataSourceId.HEALTH_CONNECT) {
            return PermissionRequestResult.Failed(
                reason = "HealthConnectPermissionManager chỉ xử lý nguồn Health Connect.",
            )
        }
        val requestStrings = setOf(HealthConnectPermissionMapping.backgroundReadPermission)
        return runRequest(selection = MetricSelection(), requestStrings = requestStrings)
    }

    /**
     * Trạng thái quyền theo **từng** metric đã chọn (Requirement 1.7).
     *
     * Đọc tập quyền hiện cấp, dịch ngược về [HealthPermission] domain, rồi giao cho
     * [PermissionScopes.grantedStatus] để gán đúng một [PermissionState] cho mỗi metric (toàn phần
     * — Property 30). Nếu đọc lỗi, dùng tập đã lưu để vẫn hiển thị được trạng thái.
     */
    override suspend fun grantedStatus(
        source: DataSourceId,
        selection: MetricSelection,
    ): Map<HealthMetricType, PermissionState> {
        if (source != DataSourceId.HEALTH_CONNECT) {
            return selection.metrics.associateWith { PermissionState.NOT_GRANTED }
        }
        val granted: Set<HealthPermission> = try {
            val strings = grantedPermissionsReader()
            HealthConnectPermissionMapping.toDomainPermissions(strings)
        } catch (_: Throwable) {
            store.load(source) // dùng tập đã lưu khi không đọc được (Requirement 1.9)
        }
        return PermissionScopes.grantedStatus(selection, granted, source)
    }

    /**
     * Làm mới và trả tập quyền hiện được cấp; phát hiện thu hồi đầu mỗi Export_Job
     * (Requirements 1.6, 1.7).
     *
     * Đọc grants thực tế, lưu lại (Requirement 1.3) và trả về. Nếu đọc lỗi, **giữ nguyên** và trả
     * tập đã lưu (không tự ý coi như đã thu hồi — Requirement 1.9).
     */
    override suspend fun refreshGrants(source: DataSourceId): Set<HealthPermission> {
        if (source != DataSourceId.HEALTH_CONNECT) return emptySet()
        return try {
            val strings = grantedPermissionsReader()
            val domain = HealthConnectPermissionMapping.toDomainPermissions(strings)
            store.save(source, domain)
            domain
        } catch (_: Throwable) {
            store.load(source)
        }
    }

    // -----------------------------------------------------------------------------------------
    // Phần truy vấn được (queryable) — phơi bày cho UI (task 21)
    // -----------------------------------------------------------------------------------------

    /**
     * Tập **chuỗi quyền** Health_Connect cần yêu cầu cho [selection] (Requirement 1.2). UI dùng tập
     * này để `launch` hợp đồng quyền. Đây là phần truy vấn được, độc lập với `Activity`.
     */
    fun buildRequestPermissions(selection: MetricSelection): Set<String> =
        HealthConnectPermissionMapping.requestPermissionStrings(selection)

    /** Chuỗi quyền đọc nền (Requirements 1.5, 1.10), để UI thêm vào yêu cầu khi cấu hình nền. */
    fun backgroundReadPermission(): String =
        HealthConnectPermissionMapping.backgroundReadPermission

    /**
     * Hợp đồng `ActivityResultContract` để UI (task 21) đăng ký và khởi chạy luồng cấp quyền
     * tương tác (`PermissionController.createRequestPermissionResultContract`). Đây là seam launch
     * mà tầng `:data` không thể tự thực thi (cần `Activity`).
     */
    fun createRequestPermissionContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract(providerPackageName)

    // -----------------------------------------------------------------------------------------
    // Nội bộ
    // -----------------------------------------------------------------------------------------

    /**
     * Thực thi một yêu cầu quyền: dùng [requester] nếu có (kèm timeout 30s, giữ tập cũ khi lỗi —
     * Requirement 1.9), ngược lại đồng bộ tập hiện có. Sau khi có grants mới, lưu lại
     * (Requirement 1.3) và phân loại Granted/Denied theo [requestStrings].
     */
    private suspend fun runRequest(
        selection: MetricSelection,
        requestStrings: Set<String>,
    ): PermissionRequestResult {
        val source = DataSourceId.HEALTH_CONNECT
        val grantedStrings: Set<String> = if (requester != null) {
            try {
                withTimeout(requestTimeoutMillis) { requester.request(requestStrings) }
            } catch (timeout: TimeoutCancellationException) {
                // Giữ nguyên tập đã lưu trước đó; báo timeout (Requirement 1.9).
                return PermissionRequestResult.Failed(
                    reason = "Yêu cầu quyền Health Connect đã hết thời gian chờ (30s).",
                    timedOut = true,
                )
            } catch (error: Throwable) {
                return PermissionRequestResult.Failed(
                    reason = "Yêu cầu quyền Health Connect không thành công: " +
                        (error.message ?: error.javaClass.simpleName),
                )
            }
        } else {
            // Chưa nối seam UI: đồng bộ lại tập hiện có (vẫn truy vấn được, không tự prompt).
            try {
                grantedPermissionsReader()
            } catch (error: Throwable) {
                return PermissionRequestResult.Failed(
                    reason = "Không đọc được trạng thái quyền Health Connect: " +
                        (error.message ?: error.javaClass.simpleName),
                )
            }
        }

        // Lưu tập quyền mới (Requirement 1.3).
        val domain = HealthConnectPermissionMapping.toDomainPermissions(grantedStrings)
        store.save(source, domain)

        val deniedMetrics = HealthConnectPermissionMapping.deniedMetrics(selection, grantedStrings)
        val allRequested = requestStrings.all { it in grantedStrings }
        return if (allRequested && deniedMetrics.isEmpty()) {
            PermissionRequestResult.Granted(granted = domain)
        } else {
            PermissionRequestResult.Denied(granted = domain, deniedMetrics = deniedMetrics)
        }
    }

    companion object {
        /** Hạn chờ mặc định cho yêu cầu quyền Health_Connect (Requirement 1.9). */
        const val DEFAULT_REQUEST_TIMEOUT_MILLIS: Long = 30_000L

        /** Đọc tập chuỗi quyền đã cấp từ permission controller của Health_Connect. */
        private suspend fun defaultGrantedPermissionsReader(
            context: Context,
            providerPackageName: String,
        ): Set<String> {
            if (HealthConnectClient.getSdkStatus(context, providerPackageName) !=
                HealthConnectClient.SDK_AVAILABLE
            ) {
                return emptySet()
            }
            val client = HealthConnectClient.getOrCreate(context, providerPackageName)
            return client.permissionController.getGrantedPermissions()
        }
    }
}

/**
 * Seam **launch tương tác** cho yêu cầu quyền Health_Connect, do tầng UI (task 21) hiện thực bằng
 * cách `launch` hợp đồng từ [HealthConnectPermissionManager.createRequestPermissionContract] và
 * chờ kết quả.
 *
 * Tách thành một functional interface giúp `:data` không phụ thuộc `Activity` mà vẫn cho phép
 * [HealthConnectPermissionManager.requestReadPermissions] điều phối timeout/persistence quanh việc
 * launch (Requirements 1.2, 1.9).
 */
fun interface HealthConnectPermissionRequester {

    /**
     * Khởi chạy luồng cấp quyền tương tác cho [permissions] và trả về tập chuỗi quyền **được cấp**
     * sau khi người dùng phản hồi.
     */
    suspend fun request(permissions: Set<String>): Set<String>
}
