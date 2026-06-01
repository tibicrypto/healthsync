package com.healthautoexport.di

import com.healthautoexport.data.scheduler.DestinationConfigResolver
import com.healthautoexport.domain.model.DestinationType
import com.healthautoexport.domain.port.DestinationConfig
import com.healthautoexport.ui.state.DestinationConfigStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hiện thực [DestinationConfigResolver] đọc cấu hình Destination từ [DestinationConfigStore] dùng
 * chung của tầng Presentation (Requirements 16–21, 22.9).
 *
 * ### Đơn giản hóa có chủ đích ở task 22.1
 * Persistence cấu hình Destination hiện là **trong bộ nhớ** ([DestinationConfigStore], một
 * `@Singleton` của `:app`, task 21). Vì vậy resolver này phân giải [DestinationConfig] cho một lần
 * chạy theo lịch của `ExportWorker` bằng cách tra cứu store **theo loại** ([DestinationType]):
 * nếu store đang giữ một cấu hình cho đúng loại của Automation, trả về cấu hình đó; ngược lại trả
 * `null` (khi đó `RunExportJobUseCase` coi như chưa cấu hình Destination và kết thúc thất bại mà
 * **không** phát sinh egress — Requirement 22.4).
 *
 * Hệ quả của việc lưu trong bộ nhớ: một lần chạy nền sau khi tiến trình bị thu hồi sẽ không thấy
 * cấu hình đã nhập trước đó. Một hiện thực **bền vững** (DataStore/Room cho cấu hình không-bí-mật
 * + [com.healthautoexport.domain.port.CredentialStore] cho bí mật) sẽ thay thế lớp này khi tính
 * năng persistence Destination được hoàn thiện; chữ ký port [DestinationConfigResolver] **không**
 * đổi nên `ExportWorker` không bị ảnh hưởng. Resolver **không** ghi giá trị bí mật ra log
 * (Requirements 22.9, 23.4).
 *
 * @property store kho cấu hình Destination trong bộ nhớ dùng chung với các ViewModel.
 */
@Singleton
class DestinationConfigStoreResolver @Inject constructor(
    private val store: DestinationConfigStore,
) : DestinationConfigResolver {

    /**
     * Phân giải cấu hình cho [destinationType]. Tham số [destinationConfigRef] hiện chưa dùng vì
     * store khóa theo loại; nó được giữ trong chữ ký để tương thích khi chuyển sang persistence
     * bền vững (khi đó ref định danh bản ghi cấu hình cụ thể).
     */
    override suspend fun resolve(
        destinationType: DestinationType,
        destinationConfigRef: String,
    ): DestinationConfig? = store.configs.value[destinationType]
}
