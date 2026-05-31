package com.healthautoexport.data.destination

import com.healthautoexport.domain.logic.MqttPortValidator
import com.healthautoexport.domain.model.DestinationType
import com.healthautoexport.domain.port.CredentialStore
import com.healthautoexport.domain.port.Destination
import com.healthautoexport.domain.port.DestinationConfig
import com.healthautoexport.domain.port.DestinationResult
import com.healthautoexport.domain.port.ExportPayload
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * [Destination] công bố bản xuất tới một MQTT broker (Requirement 19).
 *
 * Mọi tương tác broker đi qua [MqttClient] để build/test không cần thư viện MQTT hay broker thật.
 * Luồng `send`:
 * 1. Xác thực cổng `[1, 65535]` qua [MqttPortValidator]; cổng/QoS không hợp lệ ⇒
 *    [DestinationResult.Failure] không đủ điều kiện thử lại (Requirements 19.1, 19.3).
 * 2. Nạp credential tùy chọn từ [CredentialStore] theo [DestinationConfig.credentialRef].
 * 3. Kết nối trong [CONNECT_TIMEOUT_MILLIS] (30s), bật TLS khi `useTls` (Requirements 19.4, 19.5);
 *    quá hạn hoặc lỗi ⇒ [DestinationResult.Failure] đủ điều kiện thử lại (lỗi kết nối tạm thời).
 * 4. Công bố theo QoS:
 *    - QoS 0: fire-and-forget, coi là thành công ngay khi đã truyền (Requirement 19.6).
 *    - QoS 1/2: chờ ack trong [ACK_TIMEOUT_MILLIS] (30s) ⇒ thành công (Requirement 19.7); quá hạn ⇒
 *      thất bại (Requirement 19.8).
 * 5. Luôn [MqttClient.disconnect] khi kết thúc.
 *
 * @property client cổng MQTT (mặc định [NoOpMqttClient] do Hilt bind cho tới khi tích hợp thật).
 * @property credentialStore kho credential để nạp username/password tùy chọn.
 * @property connectTimeoutMillis thời gian chờ kết nối; mặc định 30s (Requirement 19.4).
 * @property ackTimeoutMillis thời gian chờ ack QoS 1/2; mặc định 30s (Requirements 19.7, 19.8).
 */
class MqttDestination(
    private val client: MqttClient,
    private val credentialStore: CredentialStore,
    private val connectTimeoutMillis: Long,
    private val ackTimeoutMillis: Long,
) : Destination {

    /**
     * Constructor cho Hilt: nhận [MqttClient] (NoOp được bind) và [CredentialStore], dùng timeout
     * mặc định 30s cho kết nối và ack. Hilt không dùng tham số mặc định của Kotlin nên ta tách
     * constructor riêng để đồ thị phụ thuộc gọn.
     */
    @Inject
    constructor(
        client: MqttClient,
        credentialStore: CredentialStore,
    ) : this(client, credentialStore, CONNECT_TIMEOUT_MILLIS, ACK_TIMEOUT_MILLIS)

    override val type: DestinationType = DestinationType.MQTT

    override suspend fun send(payload: ExportPayload, config: DestinationConfig): DestinationResult {
        val mqttConfig = config as? DestinationConfig.Mqtt
            ?: return DestinationResult.Failure(
                reason = "Cấu hình không phải MQTT: ${config.type}",
                retryEligible = false,
            )

        // (1) Xác thực cổng và mức QoS (Requirements 19.1, 19.3).
        if (!MqttPortValidator.isValid(mqttConfig.port)) {
            return DestinationResult.Failure(
                reason = "Cổng MQTT không hợp lệ: ${mqttConfig.port} (phải trong " +
                    "${MqttPortValidator.MIN_PORT}..${MqttPortValidator.MAX_PORT}).",
                retryEligible = false,
            )
        }
        if (mqttConfig.qos !in VALID_QOS_LEVELS) {
            return DestinationResult.Failure(
                reason = "Mức QoS MQTT không hợp lệ: ${mqttConfig.qos} (phải là 0, 1 hoặc 2).",
                retryEligible = false,
            )
        }

        // (2) Nạp credential tùy chọn.
        val credentials = loadCredentials(mqttConfig.credentialRef)

        try {
            // (3) Kết nối với timeout 30s, TLS khi cấu hình (Requirements 19.4, 19.5).
            val connectResult = withTimeoutOrNull(connectTimeoutMillis) {
                client.connect(
                    host = mqttConfig.host,
                    port = mqttConfig.port,
                    useTls = mqttConfig.useTls,
                    credentials = credentials,
                )
            }
            when {
                connectResult == null -> return DestinationResult.Failure(
                    reason = "Hết thời gian chờ ($connectTimeoutMillis ms) khi kết nối MQTT broker " +
                        "${mqttConfig.host}:${mqttConfig.port}.",
                    retryEligible = true,
                )

                connectResult.isFailure -> return DestinationResult.Failure(
                    reason = "Không kết nối được MQTT broker " +
                        "${mqttConfig.host}:${mqttConfig.port}: ${connectResult.exceptionOrNull()?.message}",
                    retryEligible = true,
                )
            }

            // (4) Công bố theo mức QoS.
            return if (mqttConfig.qos == QOS_AT_MOST_ONCE) {
                publishFireAndForget(mqttConfig, payload)
            } else {
                publishWithAck(mqttConfig, payload)
            }
        } finally {
            // (5) Luôn ngắt kết nối, kể cả khi gặp lỗi.
            client.disconnect()
        }
    }

    /** QoS 0: coi là thành công ngay khi đã truyền, không chờ ack (Requirement 19.6). */
    private suspend fun publishFireAndForget(
        config: DestinationConfig.Mqtt,
        payload: ExportPayload,
    ): DestinationResult {
        val result = client.publish(config.topic, payload.bytes, config.qos)
        return if (result.isSuccess) {
            DestinationResult.Success(detail = "Đã công bố tới ${config.topic} (QoS 0).")
        } else {
            DestinationResult.Failure(
                reason = "Truyền MQTT thất bại tới ${config.topic}: ${result.exceptionOrNull()?.message}",
                retryEligible = true,
            )
        }
    }

    /** QoS 1/2: chờ ack trong 30s (Requirements 19.7, 19.8). */
    private suspend fun publishWithAck(
        config: DestinationConfig.Mqtt,
        payload: ExportPayload,
    ): DestinationResult {
        val result = withTimeoutOrNull(ackTimeoutMillis) {
            client.publish(config.topic, payload.bytes, config.qos)
        } ?: return DestinationResult.Failure(
            reason = "Hết thời gian chờ ($ackTimeoutMillis ms) xác nhận công bố MQTT tới " +
                "${config.topic} (QoS ${config.qos}).",
            retryEligible = true,
        )

        return if (result.isSuccess) {
            DestinationResult.Success(detail = "Đã công bố tới ${config.topic} (QoS ${config.qos}).")
        } else {
            DestinationResult.Failure(
                reason = "Công bố MQTT thất bại tới ${config.topic}: ${result.exceptionOrNull()?.message}",
                retryEligible = true,
            )
        }
    }

    /**
     * Nạp [MqttCredentials] từ [CredentialStore] khi có [credentialRef], theo quy ước khóa
     * `"<ref>.username"` và `"<ref>.password"`. Trả về `null` nếu không có ref hoặc thiếu username
     * (kết nối ẩn danh — Requirement 19.1: credential là tùy chọn).
     */
    private suspend fun loadCredentials(credentialRef: String?): MqttCredentials? {
        if (credentialRef == null) return null
        val username = credentialStore.get("$credentialRef.username") ?: return null
        val password = credentialStore.get("$credentialRef.password").orEmpty()
        return MqttCredentials(username = username, password = password)
    }

    companion object {
        /** QoS 0 — fire-and-forget (Requirement 19.6). */
        const val QOS_AT_MOST_ONCE: Int = 0

        /** Các mức QoS hợp lệ (Requirement 19.3). */
        val VALID_QOS_LEVELS: Set<Int> = setOf(0, 1, 2)

        /** Thời gian chờ thiết lập kết nối: 30 giây (Requirement 19.4). */
        const val CONNECT_TIMEOUT_MILLIS: Long = 30_000L

        /** Thời gian chờ ack công bố QoS 1/2: 30 giây (Requirements 19.7, 19.8). */
        const val ACK_TIMEOUT_MILLIS: Long = 30_000L
    }
}
