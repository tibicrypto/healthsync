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
 * [Destination] tải bản xuất lên Google Drive (Requirement 17).
 *
 * Toàn bộ truy cập Drive đi qua [DriveClient] nên lớp này thuần về mặt nghiệp vụ và kiểm thử được
 * mà không cần SDK Google. Luồng `send`:
 * 1. Nếu chưa ủy quyền ⇒ trả [DestinationResult.Failure] để nhắc ủy quyền lại (Requirements 17.1,
 *    17.3); không đủ điều kiện thử lại vì cần người dùng can thiệp.
 * 2. Sinh tên tệp duy nhất so với các tên đang có trong thư mục (không ghi đè — Requirement 17.5).
 * 3. Upload; thành công ⇒ [DestinationResult.Success] kèm tên tệp (Requirement 17.4).
 * 4. Lỗi mạng ⇒ thử lại tối đa [MAX_RETRIES] lần, mỗi lần cách nhau ≥ [RETRY_SPACING_MILLIS] ms
 *    (Requirement 17.6); cạn số lần ⇒ [DestinationResult.Failure] đủ điều kiện thử lại để Scheduler
 *    (WorkManager) tiếp tục backoff (Requirement 17.7).
 *
 * @property client cổng Drive (mặc định [NoOpDriveClient] do Hilt bind cho tới khi tích hợp thật).
 * @property retrySpacingMillis khoảng nghỉ giữa các lần thử nội bộ; mặc định [RETRY_SPACING_MILLIS]
 *   = 30s (Requirement 17.6). Cho phép tiêm giá trị nhỏ trong test để chạy nhanh.
 */
class GoogleDriveDestination(
    private val client: DriveClient,
    private val retrySpacingMillis: Long,
) : Destination {

    /**
     * Constructor cho Hilt: chỉ nhận [DriveClient] (NoOp được bind), dùng khoảng nghỉ thử lại mặc
     * định 30s. Hilt không dùng tham số mặc định của Kotlin nên ta tách constructor riêng để đồ thị
     * phụ thuộc chỉ cần [DriveClient].
     */
    @Inject
    constructor(client: DriveClient) : this(client, RETRY_SPACING_MILLIS)

    override val type: DestinationType = DestinationType.GOOGLE_DRIVE

    override suspend fun send(payload: ExportPayload, config: DestinationConfig): DestinationResult {
        val driveConfig = config as? DestinationConfig.GoogleDrive
            ?: return DestinationResult.Failure(
                reason = "Cấu hình không phải Google Drive: ${config.type}",
                retryEligible = false,
            )

        // (1) Ủy quyền thiếu/hết hạn ⇒ nhắc người dùng ủy quyền lại (Requirement 17.3).
        if (!client.isAuthorized()) {
            return DestinationResult.Failure(
                reason = "Ủy quyền Google Drive bị thiếu hoặc hết hạn; vui lòng ủy quyền lại.",
                retryEligible = false,
            )
        }

        // (2) Sinh tên duy nhất so với các tên hiện có trong thư mục đích (Requirement 17.5).
        val existingNames = try {
            client.listNames(driveConfig.folderId)
        } catch (e: Exception) {
            // Không liệt kê được do mạng ⇒ coi như lỗi mạng, đủ điều kiện thử lại (Requirement 17.6).
            return DestinationResult.Failure(
                reason = "Lỗi mạng khi đọc thư mục Google Drive: ${e.message}",
                retryEligible = true,
            )
        }
        val uniqueName = FileNameGenerator.generate(
            desiredName = ExportFileNaming.baseName(payload),
            exists = existingNames::contains,
        ) ?: return DestinationResult.Failure(
            reason = "Không tạo được tên tệp Google Drive duy nhất.",
            retryEligible = false,
        )

        // (3)+(4) Upload kèm thử lại nội bộ cho lỗi mạng (Requirements 17.4, 17.6, 17.7).
        var lastError: String? = null
        repeat(MAX_RETRIES) { attempt ->
            if (attempt > 0) delay(retrySpacingMillis)
            val result = client.upload(
                folderId = driveConfig.folderId,
                name = uniqueName,
                bytes = payload.bytes,
                contentType = payload.contentType,
            )
            result.onSuccess {
                return DestinationResult.Success(detail = uniqueName)
            }
            lastError = result.exceptionOrNull()?.message
        }

        // Cạn số lần thử nội bộ ⇒ vẫn đủ điều kiện để Scheduler thử lại (Requirement 17.7).
        return DestinationResult.Failure(
            reason = "Tải lên Google Drive thất bại sau $MAX_RETRIES lần thử: ${lastError ?: "lỗi mạng"}",
            retryEligible = true,
        )
    }

    companion object {
        /** Số lần thử tải lên nội bộ tối đa (Requirements 17.6, 17.7). */
        const val MAX_RETRIES: Int = 3

        /** Khoảng nghỉ tối thiểu giữa các lần thử nội bộ: 30 giây (Requirement 17.6). */
        const val RETRY_SPACING_MILLIS: Long = 30_000L
    }
}
