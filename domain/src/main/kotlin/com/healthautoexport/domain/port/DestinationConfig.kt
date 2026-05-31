package com.healthautoexport.domain.port

import com.healthautoexport.domain.model.DestinationType

/**
 * Cấu hình (không chứa bí mật) của một Destination, truyền vào [Destination.send].
 *
 * Thông tin nhạy cảm (token, mật khẩu, OAuth credential) **không** nằm ở đây mà được lưu mã hóa
 * trong [CredentialStore] và tham chiếu qua [credentialRef] (Requirement 22.9). Mỗi biến thể
 * mang các trường đặc thù theo bảng Destination của design.md (Requirements 16–21).
 */
sealed interface DestinationConfig {

    /** Loại Destination mà cấu hình này áp dụng. */
    val type: DestinationType

    /** Khóa tham chiếu tới credential trong [CredentialStore], hoặc `null` nếu không cần. */
    val credentialRef: String?

    /**
     * REST API (Requirement 16): URL (HTTP/HTTPS, ≤ 2048 ký tự) và các header tùy chỉnh (≤ 50).
     *
     * @property url endpoint nhận payload.
     * @property method phương thức HTTP (vd `"POST"`).
     * @property headers các header tùy chỉnh; SHALL ≤ 50 phần tử (Requirement 16.5).
     */
    data class RestApi(
        val url: String,
        val method: String,
        val headers: Map<String, String>,
        override val credentialRef: String? = null,
    ) : DestinationConfig {
        override val type: DestinationType get() = DestinationType.REST_API
    }

    /**
     * Google Drive (Requirement 17): thư mục đích để upload.
     *
     * @property folderId định danh thư mục đích trên Drive.
     */
    data class GoogleDrive(
        val folderId: String,
        override val credentialRef: String? = null,
    ) : DestinationConfig {
        override val type: DestinationType get() = DestinationType.GOOGLE_DRIVE
    }

    /**
     * Dropbox (Requirement 18): đường dẫn thư mục trong app-folder scope.
     *
     * @property folderPath đường dẫn thư mục đích.
     */
    data class Dropbox(
        val folderPath: String,
        override val credentialRef: String? = null,
    ) : DestinationConfig {
        override val type: DestinationType get() = DestinationType.DROPBOX
    }

    /**
     * MQTT (Requirement 19): host/port/topic, mức QoS và tùy chọn TLS.
     *
     * @property host địa chỉ broker.
     * @property port cổng broker; hợp lệ trong `1..65535` (Requirement 19.1).
     * @property topic chủ đề để publish.
     * @property qos mức QoS (0, 1 hoặc 2) (Requirement 19.4).
     * @property useTls bật TLS hay không (Requirement 19.6).
     */
    data class Mqtt(
        val host: String,
        val port: Int,
        val topic: String,
        val qos: Int,
        val useTls: Boolean,
        override val credentialRef: String? = null,
    ) : DestinationConfig {
        override val type: DestinationType get() = DestinationType.MQTT
    }

    /**
     * Home Assistant (Requirement 20): base URL; long-lived token lưu ở [CredentialStore].
     *
     * @property baseUrl base URL của Home Assistant.
     */
    data class HomeAssistant(
        val baseUrl: String,
        override val credentialRef: String? = null,
    ) : DestinationConfig {
        override val type: DestinationType get() = DestinationType.HOME_ASSISTANT
    }

    /**
     * Local Storage (Requirement 21): thư mục cây qua Storage Access Framework.
     *
     * @property treeUri URI thư mục đích (SAF) dưới dạng chuỗi.
     */
    data class LocalStorage(
        val treeUri: String,
        override val credentialRef: String? = null,
    ) : DestinationConfig {
        override val type: DestinationType get() = DestinationType.LOCAL_STORAGE
    }
}
