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
 * Unit test theo ví dụ cho [GoogleDriveDestination] — task 17.2.
 *
 * Dùng [FakeDriveClient] thay cho Google Drive SDK: kiểm chứng nhắc ủy quyền lại (17.3), đặt tên
 * duy nhất không ghi đè (17.5), thành công ghi tên tệp (17.4), và thử lại khi lỗi mạng với cờ
 * retry-eligible (17.6, 17.7).
 */
class GoogleDriveDestinationTest : FunSpec({

    val config = DestinationConfig.GoogleDrive(folderId = "folder-1")

    fun payload(): ExportPayload = ExportPayload(
        bytes = "{}".toByteArray(),
        contentType = "application/json",
        jobStartUtc = Instant.parse("2024-01-31T08:00:00Z"),
        format = ExportFormat.JSON,
    )

    test("chưa ủy quyền ⇒ Failure nhắc ủy quyền lại, không đủ điều kiện thử lại") {
        runTest {
            val client = FakeDriveClient(authorized = false)
            val dest = GoogleDriveDestination(client, retrySpacingMillis = 0)

            val result = dest.send(payload(), config)

            result.shouldBeInstanceOf<DestinationResult.Failure>()
            result.retryEligible shouldBe false
            result.reason shouldContain "ủy quyền"
            client.uploadCalls shouldBe 0
        }
    }

    test("upload thành công ⇒ Success kèm tên tệp theo YYYYMMDD-HHMMSS") {
        runTest {
            val client = FakeDriveClient(authorized = true)
            val dest = GoogleDriveDestination(client, retrySpacingMillis = 0)

            val result = dest.send(payload(), config)

            result.shouldBeInstanceOf<DestinationResult.Success>()
            result.detail shouldBe "20240131-080000.json"
        }
    }

    test("trùng tên ⇒ thêm hậu tố số, không ghi đè tệp hiện có") {
        runTest {
            val client = FakeDriveClient(
                authorized = true,
                existing = mutableSetOf("20240131-080000.json"),
            )
            val dest = GoogleDriveDestination(client, retrySpacingMillis = 0)

            val result = dest.send(payload(), config)

            result.shouldBeInstanceOf<DestinationResult.Success>()
            result.detail shouldBe "20240131-080000-1.json"
            // Tệp gốc vẫn còn (không ghi đè) — Requirement 17.5.
            client.existing shouldBe mutableSetOf("20240131-080000.json", "20240131-080000-1.json")
        }
    }

    test("lỗi mạng kéo dài ⇒ thử tối đa 3 lần rồi Failure retry-eligible") {
        runTest {
            val client = FakeDriveClient(authorized = true, failUploads = Int.MAX_VALUE)
            val dest = GoogleDriveDestination(client, retrySpacingMillis = 0)

            val result = dest.send(payload(), config)

            result.shouldBeInstanceOf<DestinationResult.Failure>()
            result.retryEligible shouldBe true
            client.uploadCalls shouldBe GoogleDriveDestination.MAX_RETRIES
        }
    }

    test("lỗi mạng tạm thời rồi phục hồi ⇒ Success trong số lần thử cho phép") {
        runTest {
            val client = FakeDriveClient(authorized = true, failUploads = 2)
            val dest = GoogleDriveDestination(client, retrySpacingMillis = 0)

            val result = dest.send(payload(), config)

            result.shouldBeInstanceOf<DestinationResult.Success>()
            client.uploadCalls shouldBe 3
        }
    }

    test("lỗi mạng khi liệt kê thư mục ⇒ Failure retry-eligible") {
        runTest {
            val client = FakeDriveClient(authorized = true, failListing = true)
            val dest = GoogleDriveDestination(client, retrySpacingMillis = 0)

            val result = dest.send(payload(), config)

            result.shouldBeInstanceOf<DestinationResult.Failure>()
            result.retryEligible shouldBe true
        }
    }

    test("cấu hình sai loại ⇒ Failure không đủ điều kiện thử lại") {
        runTest {
            val dest = GoogleDriveDestination(FakeDriveClient(authorized = true), retrySpacingMillis = 0)
            val result = dest.send(payload(), DestinationConfig.Dropbox(folderPath = "/x"))
            result.shouldBeInstanceOf<DestinationResult.Failure>()
            result.retryEligible shouldBe false
        }
    }
})

/** Fake [DriveClient] trong bộ nhớ, không I/O thật. */
private class FakeDriveClient(
    private val authorized: Boolean,
    val existing: MutableSet<String> = mutableSetOf(),
    private var failUploads: Int = 0,
    private val failListing: Boolean = false,
) : DriveClient {

    var uploadCalls: Int = 0
        private set

    override suspend fun listNames(folderId: String): Set<String> {
        if (failListing) throw IOException("network down")
        return existing.toSet()
    }

    override suspend fun upload(
        folderId: String,
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
        return Result.success("drive-id-$name")
    }

    override fun isAuthorized(): Boolean = authorized
}
