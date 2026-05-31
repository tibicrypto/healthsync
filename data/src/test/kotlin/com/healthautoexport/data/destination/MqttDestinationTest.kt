package com.healthautoexport.data.destination

import com.healthautoexport.domain.model.ExportFormat
import com.healthautoexport.domain.port.CredentialStore
import com.healthautoexport.domain.port.DestinationConfig
import com.healthautoexport.domain.port.DestinationResult
import com.healthautoexport.domain.port.ExportPayload
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import java.time.Instant

/**
 * Unit test theo ví dụ cho [MqttDestination] — task 17.4.
 *
 * Dùng [FakeMqttClient] thay cho thư viện MQTT/broker: kiểm chứng xác thực cổng (19.1), thất bại
 * kết nối/timeout (19.4), QoS 0 fire-and-forget (19.6), QoS 1/2 chờ ack thành công (19.7) và
 * timeout ack (19.8), cùng việc bật TLS (19.5).
 */
class MqttDestinationTest : FunSpec({

    fun payload(): ExportPayload = ExportPayload(
        bytes = "{}".toByteArray(),
        contentType = "application/json",
        jobStartUtc = Instant.parse("2024-01-31T08:00:00Z"),
        format = ExportFormat.JSON,
    )

    fun mqtt(
        port: Int = 1883,
        qos: Int = 0,
        useTls: Boolean = false,
    ): DestinationConfig.Mqtt = DestinationConfig.Mqtt(
        host = "broker.local",
        port = port,
        topic = "health/export",
        qos = qos,
        useTls = useTls,
    )

    val noCreds = EmptyCredentialStore

    test("cổng ngoài [1,65535] ⇒ Failure không đủ điều kiện thử lại, không kết nối") {
        runTest {
            val client = FakeMqttClient()
            val dest = MqttDestination(client, noCreds, connectTimeoutMillis = 1000, ackTimeoutMillis = 1000)
            val result = dest.send(payload(), mqtt(port = 70000))
            result.shouldBeInstanceOf<DestinationResult.Failure>()
            result.retryEligible shouldBe false
            client.connectCalls shouldBe 0
        }
    }

    test("QoS không hợp lệ ⇒ Failure không đủ điều kiện thử lại") {
        runTest {
            val dest = MqttDestination(FakeMqttClient(), noCreds, 1000, 1000)
            val result = dest.send(payload(), mqtt(qos = 3))
            result.shouldBeInstanceOf<DestinationResult.Failure>()
            result.retryEligible shouldBe false
        }
    }

    test("kết nối thất bại ⇒ Failure retry-eligible") {
        runTest {
            val client = FakeMqttClient(connectResult = Result.failure(RuntimeException("refused")))
            val dest = MqttDestination(client, noCreds, 1000, 1000)
            val result = dest.send(payload(), mqtt(qos = 1))
            result.shouldBeInstanceOf<DestinationResult.Failure>()
            result.retryEligible shouldBe true
            client.disconnectCalls shouldBe 1
        }
    }

    test("QoS 0 fire-and-forget ⇒ Success không cần ack") {
        runTest {
            val client = FakeMqttClient()
            val dest = MqttDestination(client, noCreds, 1000, 1000)
            val result = dest.send(payload(), mqtt(qos = 0))
            result.shouldBeInstanceOf<DestinationResult.Success>()
            client.publishCalls shouldBe 1
            client.disconnectCalls shouldBe 1
        }
    }

    test("QoS 1 nhận ack trong hạn ⇒ Success") {
        runTest {
            val client = FakeMqttClient()
            val dest = MqttDestination(client, noCreds, 1000, 1000)
            val result = dest.send(payload(), mqtt(qos = 1))
            result.shouldBeInstanceOf<DestinationResult.Success>()
        }
    }

    test("QoS 2 ack quá hạn ⇒ Failure retry-eligible") {
        runTest {
            // publish trễ 60s nhưng ack timeout chỉ 30s ⇒ withTimeoutOrNull trả null.
            val client = FakeMqttClient(publishDelayMillis = 60_000)
            val dest = MqttDestination(client, noCreds, connectTimeoutMillis = 30_000, ackTimeoutMillis = 30_000)
            val result = dest.send(payload(), mqtt(qos = 2))
            result.shouldBeInstanceOf<DestinationResult.Failure>()
            result.retryEligible shouldBe true
            client.disconnectCalls shouldBe 1
        }
    }

    test("kết nối quá hạn ⇒ Failure retry-eligible") {
        runTest {
            val client = FakeMqttClient(connectDelayMillis = 60_000)
            val dest = MqttDestination(client, noCreds, connectTimeoutMillis = 30_000, ackTimeoutMillis = 30_000)
            val result = dest.send(payload(), mqtt(qos = 1))
            result.shouldBeInstanceOf<DestinationResult.Failure>()
            result.retryEligible shouldBe true
        }
    }

    test("useTls=true được truyền tới client") {
        runTest {
            val client = FakeMqttClient()
            val dest = MqttDestination(client, noCreds, 1000, 1000)
            dest.send(payload(), mqtt(qos = 0, useTls = true))
            client.lastUseTls shouldBe true
        }
    }
})

/** Fake [MqttClient] trong bộ nhớ với độ trễ mô phỏng để kiểm thử timeout. */
private class FakeMqttClient(
    private val connectResult: Result<Unit> = Result.success(Unit),
    private val publishResult: Result<Unit> = Result.success(Unit),
    private val connectDelayMillis: Long = 0,
    private val publishDelayMillis: Long = 0,
) : MqttClient {

    var connectCalls: Int = 0
        private set
    var publishCalls: Int = 0
        private set
    var disconnectCalls: Int = 0
        private set
    var lastUseTls: Boolean? = null
        private set

    override suspend fun connect(
        host: String,
        port: Int,
        useTls: Boolean,
        credentials: MqttCredentials?,
    ): Result<Unit> {
        connectCalls++
        lastUseTls = useTls
        if (connectDelayMillis > 0) delay(connectDelayMillis)
        return connectResult
    }

    override suspend fun publish(topic: String, bytes: ByteArray, qos: Int): Result<Unit> {
        publishCalls++
        if (publishDelayMillis > 0) delay(publishDelayMillis)
        return publishResult
    }

    override suspend fun disconnect() {
        disconnectCalls++
    }
}

/** [CredentialStore] rỗng cho test kết nối ẩn danh. */
private object EmptyCredentialStore : CredentialStore {
    override suspend fun put(key: String, secret: String) = Unit
    override suspend fun get(key: String): String? = null
    override suspend fun remove(key: String) = Unit
    override suspend fun clear() = Unit
}
