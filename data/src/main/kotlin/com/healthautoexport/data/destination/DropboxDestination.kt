package com.healthautoexport.data.destination

import com.healthautoexport.domain.logic.FileNameGenerator
import com.healthautoexport.domain.model.DestinationType
import com.healthautoexport.domain.port.Destination
import com.healthautoexport.domain.port.DestinationConfig
import com.healthautoexport.domain.port.DestinationResult
import com.healthautoexport.domain.port.ExportPayload
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * [Destination] tải bản xuất lên Dropbox trong app-folder scope (Requirement 18).
 *
 * Cấu trúc tương tự [GoogleDriveDestination] nhưng theo đặc thù Dropbox. Toàn bộ truy cập đi qua
 * [DropboxClient] để build/test không cần SDK Dropbox. Luồng `send`:
 * 1. Chưa ủy quyền ⇒ [DestinationResult.Failure] nhắc ủy quyền lại (Requirements 18.1, 18.3).
 * 2. Sinh tên tệp duy nhất so với các tên đang có (không ghi đè — Requirement 18.5).
 * 3. Upload; thành công ⇒ [DestinationResult.Success] kèm tên tệp (Requirements 18.2, 18.4).
 * 4. Lỗi mạng ⇒ thử lại tối đa [MAX_RETRIES] lần, mỗi lần cách nhau ≥ [RETRY_SPACING_MILLIS] ms
 *    (Requirement 18.7); cạn số lần ⇒ [DestinationResult.Failure] đủ điều kiện thử lại
 *    (Requirements 18.6, 18.7).
 *
 * @property client cổng Dropbox (mặc định [NoOpDropboxClient] do Hilt bind cho tới khi tích hợp thật).
 * @property retrySpacingMillis khoảng nghỉ giữa các lần thử nội bộ; mặc định [RETRY_SPACING_MILLIS]
 *   = 5s (Requirement 18.7). Cho phép tiêm giá trị nhỏ trong test để chạy nhanh.
 */
class DropboxDestination(
    private val client: DropboxClient,
    private val retrySpacingMillis: Long,
) : Destination {

    /**
     * Constructor cho Hilt: chỉ nhận [DropboxClient] (NoOp được bind), dùng khoảng nghỉ thử lại mặc
     * định 5s. Hilt không dùng tham số mặc định của Kotlin nên ta tách constructor riêng.
     */
    @Inject
    constructor(client: DropboxClient) : this(client, RETRY_SPACING_MILLIS)

    override val type: DestinationType = DestinationType.DROPBOX

    override suspend fun send(payload: ExportPayload, config: DestinationConfig): DestinationResult {
        val dropboxConfig = config as? DestinationConfig.Dropbox
            ?: return DestinationResult.Failure(
                reason = "Cấu hình không phải Dropbox: ${config.type}",
                retryEligible = false,
            )

        // (1) Ủy quyền thiếu/hết hạn ⇒ nhắc người dùng ủy quyền lại (Requirement 18.3).
        if (!client.isAuthorized()) {
            return DestinationResult.Failure(
                reason = "Ủy quyền Dropbox bị thiếu hoặc hết hạn; vui lòng ủy quyền lại.",
                retryEligible = false,
            )
        }

        // (2) Sinh tên duy nhất so với các tên hiện có trong thư mục đích (Requirement 18.5).
        val existingNames = try {
            client.listNames(dropboxConfig.folderPath)
        } catch (e: Exception) {
            return DestinationResult.Failure(
                reason = "Lỗi mạng khi đọc thư mục Dropbox: ${e.message}",
                retryEligible = true,
            )
        }
        val uniqueName = FileNameGenerator.generate(
            desiredName = ExportFileNaming.baseName(payload),
            exists = existingNames::contains,
        ) ?: return DestinationResult.Failure(
            reason = "Không tạo được tên tệp Dropbox duy nhất.",
            retryEligible = false,
        )

        // (3)+(4) Upload kèm thử lại nội bộ cho lỗi mạng (Requirements 18.4, 18.6, 18.7).
        var lastError: String? = null
        repeat(MAX_RETRIES) { attempt ->
            if (attempt > 0) delay(retrySpacingMillis)
            val result = client.upload(
                folderPath = dropboxConfig.folderPath,
                name = uniqueName,
                bytes = payload.bytes,
                contentType = payload.contentType,
            )
            result.onSuccess {
                return DestinationResult.Success(detail = uniqueName)
            }
            lastError = result.exceptionOrNull()?.message
        }

        return DestinationResult.Failure(
            reason = "Tải lên Dropbox thất bại sau $MAX_RETRIES lần thử: ${lastError ?: "lỗi mạng"}",
            retryEligible = true,
        )
    }

    companion object {
        /** Số lần thử tải lên nội bộ tối đa (Requirement 18.7). */
        const val MAX_RETRIES: Int = 3

        /** Khoảng nghỉ tối thiểu giữa các lần thử nội bộ: 5 giây (Requirement 18.7). */
        const val RETRY_SPACING_MILLIS: Long = 5_000L
    }
}
