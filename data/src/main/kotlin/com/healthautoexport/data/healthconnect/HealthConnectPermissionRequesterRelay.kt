package com.healthautoexport.data.healthconnect

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Relay (cầu nối) nối seam launch tương tác [HealthConnectPermissionRequester] — vốn cần một
 * `Activity` — với đồ thị Hilt singleton, để [HealthConnectPermissionManager] (một `@Singleton`)
 * vẫn điều phối được timeout/persistence quanh việc launch (Requirements 1.2, 1.9).
 *
 * ### Vì sao cần relay
 * [HealthConnectPermissionManager] được dựng **một lần** ở tầng DI (singleton), nhưng việc khởi
 * chạy hợp đồng `ActivityResultContract` của Health_Connect chỉ thực hiện được từ một `Activity`
 * đang hiển thị (`MainActivity`, task 22.1). `Activity` có vòng đời ngắn hơn singleton, nên ta
 * **không** thể tiêm thẳng `Activity` vào manager. Relay này là một singleton trung gian: manager
 * nhận relay làm `requester`, còn `MainActivity` **gắn** ([attach]) một delegate launch khi nó
 * được tạo và **gỡ** ([detach]) khi bị hủy.
 *
 * Khi chưa có `Activity` nào gắn delegate (vd App đang ở nền), [request] ném
 * [IllegalStateException]; [HealthConnectPermissionManager] bắt lỗi này và trả
 * [com.healthautoexport.domain.port.PermissionRequestResult.Failed] mà **không** ghi đè tập quyền
 * đã lưu trước đó (Requirement 1.9) — an toàn theo hướng bảo thủ.
 *
 * Là `@Singleton` với hàm dựng `@Inject` để Hilt tự cung cấp; cùng một thể hiện được tiêm vào cả
 * `MainActivity` (để gắn delegate) lẫn provider dựng [HealthConnectPermissionManager].
 */
@Singleton
class HealthConnectPermissionRequesterRelay @Inject constructor() : HealthConnectPermissionRequester {

    @Volatile
    private var delegate: (suspend (Set<String>) -> Set<String>)? = null

    /**
     * Gắn [delegate] khởi chạy luồng cấp quyền tương tác (do `MainActivity` cung cấp): nhận tập
     * chuỗi quyền cần yêu cầu và trả về tập chuỗi quyền **được cấp** sau khi người dùng phản hồi.
     */
    fun attach(delegate: suspend (Set<String>) -> Set<String>) {
        this.delegate = delegate
    }

    /** Gỡ delegate khi `Activity` bị hủy để không giữ tham chiếu tới một `Activity` đã chết. */
    fun detach() {
        this.delegate = null
    }

    override suspend fun request(permissions: Set<String>): Set<String> {
        val active = delegate
            ?: error(
                "Chưa có Activity nào gắn HealthConnectPermissionRequesterRelay; " +
                    "không thể khởi chạy luồng cấp quyền Health Connect lúc này.",
            )
        return active(permissions)
    }
}
