package com.healthautoexport.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.healthautoexport.domain.logic.MqttPortValidator
import com.healthautoexport.domain.logic.RestConfigValidation
import com.healthautoexport.domain.logic.RestConfigValidator
import com.healthautoexport.domain.model.DestinationType
import com.healthautoexport.domain.port.DestinationConfig
import com.healthautoexport.ui.state.DestinationConfigStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Trạng thái UI cho màn hình cấu hình Destination (Requirements 16–21).
 *
 * @property configs cấu hình hiện tại theo từng loại Destination.
 * @property selectedType loại Destination được chọn làm đích cho Quick_Export.
 * @property nonHttpsWarning thông báo cảnh báo non-HTTPS khi áp dụng (Requirements 16.4, 20.2),
 *   hoặc `null`.
 * @property validationMessage thông báo lỗi xác thực gần nhất (vd cổng MQTT, URL REST), hoặc `null`
 *   (Requirements 16.1, 19.1).
 * @property savedType loại Destination vừa lưu thành công, hoặc `null`.
 */
data class DestinationsUiState(
    val configs: Map<DestinationType, DestinationConfig> = emptyMap(),
    val selectedType: DestinationType? = null,
    val nonHttpsWarning: String? = null,
    val validationMessage: String? = null,
    val savedType: DestinationType? = null,
)

/**
 * ViewModel cấu hình sáu loại Destination (Requirements 16–21).
 *
 * Trọng tâm của task 21 là **xác thực thuần** trước khi lưu:
 * - REST API: xác thực URL/scheme/độ dài/số header bằng [RestConfigValidator] (Requirement 16.1);
 *   cảnh báo khi URL không dùng HTTPS trước khi lưu (Requirement 16.4).
 * - Home Assistant: cảnh báo non-HTTPS tương tự (Requirement 20.2).
 * - MQTT: xác thực cổng trong `[1, 65535]` bằng [MqttPortValidator] (Requirement 19.1).
 *
 * Cấu hình được giữ trong [DestinationConfigStore] dùng chung để `QuickExportViewModel` đọc khi
 * dựng `ExportJobConfig`. Credential nhạy cảm **không** đi qua đây (Requirement 22.9); lưu bền vững
 * + credential mã hóa là việc của tầng `:data`/task 22.1.
 */
@HiltViewModel
class DestinationsViewModel @Inject constructor(
    private val store: DestinationConfigStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DestinationsUiState())

    /** Trạng thái UI quan sát được. */
    val uiState: StateFlow<DestinationsUiState> = _uiState.asStateFlow()

    init {
        _uiState.update {
            it.copy(configs = store.configs.value, selectedType = store.selectedType.value)
        }
    }

    /**
     * Lưu cấu hình REST API sau khi xác thực URL/header (Requirements 16.1, 16.4).
     *
     * @return `true` nếu đã lưu; `false` nếu xác thực thất bại (thông báo ở [DestinationsUiState]).
     */
    fun saveRestApi(url: String, method: String, headers: Map<String, String>): Boolean {
        when (val validation = RestConfigValidator.validate(url, headers.size)) {
            is RestConfigValidation.Invalid -> {
                _uiState.update {
                    it.copy(
                        validationMessage = "Cấu hình REST không hợp lệ: ${validation.violations.joinToString()}" +
                            " (Requirement 16.1).",
                    )
                }
                return false
            }

            RestConfigValidation.Valid -> Unit
        }
        val config = DestinationConfig.RestApi(url = url, method = method, headers = headers)
        persist(config, warnIfNotHttps(url))
        return true
    }

    /**
     * Lưu cấu hình Home Assistant; cảnh báo nếu base URL không dùng HTTPS (Requirement 20.2).
     */
    fun saveHomeAssistant(baseUrl: String) {
        val config = DestinationConfig.HomeAssistant(baseUrl = baseUrl)
        persist(config, warnIfNotHttps(baseUrl))
    }

    /**
     * Lưu cấu hình MQTT sau khi xác thực cổng trong `[1, 65535]` (Requirement 19.1).
     *
     * @return `true` nếu đã lưu; `false` nếu cổng ngoài phạm vi.
     */
    fun saveMqtt(host: String, port: Int, topic: String, qos: Int, useTls: Boolean): Boolean {
        if (!MqttPortValidator.isValid(port)) {
            _uiState.update {
                it.copy(
                    validationMessage = "Cổng MQTT phải trong " +
                        "${MqttPortValidator.MIN_PORT}..${MqttPortValidator.MAX_PORT} (Requirement 19.1).",
                )
            }
            return false
        }
        val config = DestinationConfig.Mqtt(
            host = host,
            port = port,
            topic = topic,
            qos = qos,
            useTls = useTls,
        )
        persist(config, warning = null)
        return true
    }

    /** Lưu cấu hình Google Drive (thư mục đích) (Requirement 17.1). */
    fun saveGoogleDrive(folderId: String) {
        persist(DestinationConfig.GoogleDrive(folderId = folderId), warning = null)
    }

    /** Lưu cấu hình Dropbox (đường dẫn app-folder) (Requirement 18.1). */
    fun saveDropbox(folderPath: String) {
        persist(DestinationConfig.Dropbox(folderPath = folderPath), warning = null)
    }

    /** Lưu cấu hình Local Storage (thư mục SAF) (Requirement 21.1). */
    fun saveLocalStorage(treeUri: String) {
        persist(DestinationConfig.LocalStorage(treeUri = treeUri), warning = null)
    }

    /** Chọn loại Destination dùng làm đích cho Quick_Export. */
    fun select(type: DestinationType) {
        store.select(type)
        _uiState.update { it.copy(selectedType = type) }
    }

    /** Xóa cấu hình của một loại Destination. */
    fun remove(type: DestinationType) {
        store.remove(type)
        _uiState.update {
            it.copy(configs = store.configs.value, selectedType = store.selectedType.value)
        }
    }

    /** Xóa thông báo cảnh báo/xác thực/xác nhận sau khi hiển thị. */
    fun consumeMessages() {
        _uiState.update {
            it.copy(nonHttpsWarning = null, validationMessage = null, savedType = null)
        }
    }

    private fun persist(config: DestinationConfig, warning: String?) {
        store.save(config)
        _uiState.update {
            it.copy(
                configs = store.configs.value,
                validationMessage = null,
                nonHttpsWarning = warning,
                savedType = config.type,
            )
        }
    }

    /**
     * Trả về thông báo cảnh báo nếu [url] không dùng HTTPS (Requirements 16.4, 20.2), ngược lại
     * `null`. App vẫn cho phép lưu nhưng phải cảnh báo trước.
     */
    private fun warnIfNotHttps(url: String): String? =
        if (!url.trim().lowercase().startsWith("https://")) {
            "Cảnh báo: URL không dùng HTTPS; dữ liệu sẽ được truyền không mã hóa " +
                "(Requirements 16.4, 20.2)."
        } else {
            null
        }
}
