package com.healthautoexport.data.destination

/**
 * Thông tin xác thực tùy chọn khi kết nối tới MQTT broker (Requirement 19.1).
 *
 * @property username tên đăng nhập broker.
 * @property password mật khẩu broker (được nạp từ [com.healthautoexport.domain.port.CredentialStore]).
 */
data class MqttCredentials(
    val username: String,
    val password: String,
)

/**
 * Cổng (port) nội bộ bọc MQTT client (HiveMQ/Paho) cho [MqttDestination] (Requirement 19).
 *
 * Trừu tượng hóa broker sau interface này giúp build/test **không** cần thư viện MQTT hay broker
 * thật: hiện thực thật được tiêm về sau, còn [NoOpMqttClient] là mặc định an toàn (không kết nối).
 *
 * Lưu ý ngữ nghĩa thời gian chờ: [MqttDestination] tự áp timeout 30s cho [connect] và cho việc chờ
 * ack ở QoS 1/2 bằng `withTimeoutOrNull` (Requirements 19.4, 19.7, 19.8), nên hiện thực client không
 * cần tự quản lý timeout.
 */
interface MqttClient {

    /**
     * Thiết lập kết nối tới broker [host]:[port], dùng TLS nếu [useTls] (Requirement 19.5) và xác
     * thực bằng [credentials] nếu khác `null`.
     *
     * @return [Result.success] khi kết nối thành công; [Result.failure] khi thất bại
     *   (Requirement 19.4).
     */
    suspend fun connect(
        host: String,
        port: Int,
        useTls: Boolean,
        credentials: MqttCredentials?,
    ): Result<Unit>

    /**
     * Công bố [bytes] tới [topic] với mức [qos].
     *
     * Với QoS 1/2, [Result] chỉ hoàn tất khi broker đã xác nhận (ack) việc công bố; với QoS 0,
     * [Result] hoàn tất ngay sau khi truyền (fire-and-forget) (Requirements 19.6, 19.7).
     *
     * @return [Result.success] khi công bố (và ack nếu QoS ≥ 1) thành công; [Result.failure] khi lỗi.
     */
    suspend fun publish(topic: String, bytes: ByteArray, qos: Int): Result<Unit>

    /** Ngắt kết nối và giải phóng tài nguyên broker. */
    suspend fun disconnect()
}

/**
 * Hiện thực mặc định **no-op** của [MqttClient]: kết nối luôn thất bại, không phát sinh I/O.
 *
 * Được Hilt bind làm mặc định để module compile/verify mà không kéo theo thư viện MQTT; nhờ
 * [connect] trả về [Result.failure], [MqttDestination.send] dừng sớm với thất bại kết nối. Hiện
 * thực thật thay thế client này sau khi tích hợp HiveMQ/Paho.
 */
class NoOpMqttClient : MqttClient {

    override suspend fun connect(
        host: String,
        port: Int,
        useTls: Boolean,
        credentials: MqttCredentials?,
    ): Result<Unit> = Result.failure(UnsupportedOperationException("MqttClient chưa được cấu hình"))

    override suspend fun publish(topic: String, bytes: ByteArray, qos: Int): Result<Unit> =
        Result.failure(UnsupportedOperationException("MqttClient chưa được cấu hình"))

    override suspend fun disconnect() = Unit
}
