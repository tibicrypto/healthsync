package com.healthautoexport.data.healthconnect

import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.PermissionController
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory tạo `ActivityResultContract` cấp quyền của Health_Connect cho tầng UI (task 22.1).
 *
 * ### Vì sao là một factory thay vì inject thẳng contract
 * `:app` **không** khai báo phụ thuộc `androidx.health.connect:connect-client`, nên không thể tự
 * gọi `PermissionController.createRequestPermissionResultContract(...)`. Mặt khác, nếu cung cấp
 * thẳng `ActivityResultContract<Set<String>, Set<String>>` làm một binding Hilt thì khóa Dagger sẽ
 * dính generic (Set<String>) phiền phức về wildcard. Một **lớp factory không generic** né cả hai
 * vấn đề: nó là một khóa Dagger đơn giản (`HealthConnectPermissionContractFactory`), còn kiểu
 * contract trả về (`androidx.activity...`) nằm sẵn trên classpath của `:app`.
 *
 * `MainActivity` inject factory này, gọi [create] trong `onCreate` để đăng ký
 * `registerForActivityResult`, rồi cầu nối kết quả về [HealthConnectPermissionRequesterRelay]
 * (Requirements 1.2, 1.9).
 */
@Singleton
class HealthConnectPermissionContractFactory @Inject constructor() {

    /**
     * Tạo hợp đồng yêu cầu quyền đọc Health_Connect: đầu vào là tập chuỗi quyền cần xin, đầu ra là
     * tập chuỗi quyền **được cấp** sau khi người dùng phản hồi.
     */
    fun create(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract(
            HealthConnectDataSource.HEALTH_CONNECT_PROVIDER_PACKAGE,
        )
}
