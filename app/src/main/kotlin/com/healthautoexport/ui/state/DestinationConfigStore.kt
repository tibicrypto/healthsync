package com.healthautoexport.ui.state

import com.healthautoexport.domain.model.DestinationType
import com.healthautoexport.domain.port.DestinationConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kho lưu cấu hình của sáu loại [Destination][DestinationConfig], dùng chung giữa
 * `DestinationsViewModel` (ghi cấu hình) và `QuickExportViewModel` (đọc cấu hình để dựng
 * `ExportJobConfig`) (Requirements 16–21).
 *
 * ### Phạm vi trong task 21
 * Đây là một hiện thực **trong bộ nhớ** tối giản: nó giữ tối đa một [DestinationConfig] cho mỗi
 * [DestinationType] cùng loại đang được chọn làm đích hiện hành. Việc **bền vững hóa** cấu hình
 * (DataStore) và lưu credential mã hóa (Android Keystore) là trách nhiệm của tầng `:data` và bước
 * ráp nối Hilt (task 22.1); khi đó một hiện thực bền vững có thể thay thế lớp này. Lớp là một
 * `@Singleton` cụ thể với hàm dựng `@Inject` để Hilt tự cung cấp mà không cần module.
 *
 * Cấu hình ở đây **không** chứa bí mật (theo hợp đồng của [DestinationConfig]); token/mật khẩu đi
 * qua `CredentialStore` riêng (Requirement 22.9).
 */
@Singleton
class DestinationConfigStore @Inject constructor() {

    private val _configs = MutableStateFlow<Map<DestinationType, DestinationConfig>>(emptyMap())

    /** Luồng bản đồ cấu hình theo từng loại Destination đã được cấu hình. */
    val configs: StateFlow<Map<DestinationType, DestinationConfig>> = _configs.asStateFlow()

    private val _selectedType = MutableStateFlow<DestinationType?>(null)

    /** Loại Destination hiện được chọn làm đích cho Quick_Export, hoặc `null` nếu chưa chọn. */
    val selectedType: StateFlow<DestinationType?> = _selectedType.asStateFlow()

    /** Lưu/cập nhật cấu hình cho một loại Destination. */
    fun save(config: DestinationConfig) {
        _configs.update { it + (config.type to config) }
    }

    /** Xóa cấu hình của một loại Destination. */
    fun remove(type: DestinationType) {
        _configs.update { it - type }
        if (_selectedType.value == type) {
            _selectedType.value = null
        }
    }

    /** Chọn loại Destination dùng làm đích hiện hành cho Quick_Export. */
    fun select(type: DestinationType?) {
        _selectedType.value = type
    }

    /** Đọc cấu hình của loại Destination hiện được chọn, hoặc `null` nếu chưa cấu hình. */
    fun selectedConfig(): DestinationConfig? {
        val type = _selectedType.value ?: return null
        return _configs.value[type]
    }
}
