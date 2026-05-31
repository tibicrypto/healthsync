package com.healthautoexport.data.destination

import com.healthautoexport.domain.model.ExportFormat
import com.healthautoexport.domain.port.DestinationConfig
import com.healthautoexport.domain.port.DestinationResult
import com.healthautoexport.domain.port.ExportPayload
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.time.Instant

/**
 * Unit test theo ví dụ cho [DropboxDestination] — task 17.3.
 *
 * Dùng [FakeDropboxClient] thay cho Dropbox SDK: kiểm chứng nhắc ủy quyền lại (18.3), tên duy nhất
 * không ghi đè (18.5), thành công ghi tên tệp (18.4), và thử lại lỗi mạng với cờ retry-eligible
 * (18.6, 18.7).
 */
class DropboxDestinationTest : FunSpec({

    val config = DestinationConfig.Dropbox(folderPath = "/exports")

    fun payload(): ExportPayload = ExportPayload(
        bytes = "name,value".toByteArray(),
        contentType = "text/csv",
        jobStartUtc = Instant.parse("2024-01-31T08:00:00Z"),
        format = ExportFormat.CSV,
    )

    test("chưa ủy quyền ⇒ Failure nhắc ủy quyền lại, không đủ điều kiện thử lại") {
        runTest {
            val dest = DropboxDestination(FakeDropboxClient(authorized = false), retrySpacingMillis = 0)
            val result = dest.send(payload(), config)
            result.shouldBeInstanceOf<DestinationResult.Failure>()
            result.retryEligible shouldBe false
            result.reason shouldContain "ủy quyền"
        }
    }

    test("upload thành công ⇒ Success kèm tên tệp") {
        runTest {
            val dest = DropboxDestination(FakeDropboxClient(authorized = true), retrySpacingMillis = 0)
            val result = dest.send(payload(), config)
            result.shouldBeInstanceOf<DestinationResult.Success>()
            result.detail shouldBe "20240131-080000.csv"
        }
    }

    test("trùng tên ⇒ thêm hậu tố phân biệt, không ghi đè") {
        runTest {
            val client = FakeDropboxClient(authorized = true, existing = mutableSetOf("20240131-080000.csv"))
            val dest = DropboxDestination(client, retrySpacingMillis = 0)
            val result = dest.send(payload(), config)
            result.shouldBeInstanceOf<DestinationResult.Success>()
            result.detail shouldBe "20240131-080000-1.csv"
            client.existing shouldBe mutableSetOf("20240131-080000.csv", "20240131-080000-1.csv")
        }
    }

    test("lỗi mạng kéo dài ⇒ thử tối đa 3 lần rồi Failure retry-eligible") {
        runTest {
            val client = FakeDropboxClient(authorized = true, failUploads = Int.MAX_VALUE)
            val dest = DropboxDestination(client, retrySpacingMillis = 0)
            val result = dest.send(payload(), config)
            result.shouldBeInstanceOf<DestinationResult.Failure>()
            result.retryEligible shouldBe true
            client.uploadCalls shouldBe DropboxDestination.MAX_RETRIES
        }
    }

    test("lỗi mạng tạm thời rồi phục hồi ⇒ Success") {
        runTest {
            val client = FakeDropboxClient(authorized = true, failUploads = 1)
            val dest = DropboxDestination(client, retrySpacingMillis = 0)
            val result = dest.send(payload(), config)
            result.shouldBeInstanceOf<DestinationResult.Success>()
            client.uploadCalls shouldBe 2
        }
    }
})

/** Fake [DropboxClient] trong bộ nhớ, không I/O thật. */
private class FakeDropboxClient(
    private val authorized: Boolean,
    val existing: MutableSet<String> = mutableSetOf(),
    private var failUploads: Int = 0,
) : DropboxClient {

    var uploadCalls: Int = 0
        private set

    override suspend fun listNames(folderPath: String): Set<String> = existing.toSet()

    override suspend fun upload(
        folderPath: String,
        name: String,
        bytes: ByteArray,
        contentType: String,
    ): Result<String> {
        uploadCalls++
        if (failUploads > 0) {
            failUploads--
            return Result.failure(IOException("network error"))
        }
        existing.add(name)
        return Result.success("$folderPath/$name")
    }

    override fun isAuthorized(): Boolean = authorized
}
