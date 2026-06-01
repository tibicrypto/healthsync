package com.healthautoexport.domain.usecase

import com.healthautoexport.domain.port.AutomationRepository
import com.healthautoexport.domain.port.CredentialStore
import com.healthautoexport.domain.port.SyncLogRepository

/**
 * Một loại dữ liệu App bị xóa trong quá trình Data_Wipe, dùng để liệt kê trong xác nhận
 * (Requirement 22.7).
 */
enum class WipedDataType {
    /** Các Automation đã lưu (Requirement 22.6). */
    AUTOMATIONS,

    /** Thông tin xác thực Destination (Requirement 22.6). */
    DESTINATION_CREDENTIALS,

    /** Toàn bộ mục Sync_Log (Requirement 22.6). */
    SYNC_LOG,
}

/**
 * Kết quả của thao tác Data_Wipe (Requirements 22.6–22.8).
 *
 * Kiểu sealed phân biệt: xóa hoàn tất ([Success], liệt kê từng loại đã xóa — Requirement 22.7) hay
 * xóa thất bại ([Failure], giữ nguyên dữ liệu chưa xóa và báo lỗi — Requirement 22.8).
 */
sealed interface DataWipeResult {

    /**
     * Xóa toàn bộ thành công.
     *
     * @property wiped danh sách loại dữ liệu đã xóa, để hiển thị xác nhận (Requirement 22.7).
     */
    data class Success(val wiped: List<WipedDataType>) : DataWipeResult

    /**
     * Xóa thất bại ở một bước nào đó; các dữ liệu còn lại được giữ nguyên (Requirement 22.8).
     *
     * @property message thông báo lỗi người dùng đọc được cho biết việc xóa chưa hoàn tất.
     * @property cause ngoại lệ gốc gây thất bại, nếu có.
     */
    data class Failure(val message: String, val cause: Throwable? = null) : DataWipeResult
}

/**
 * Use case xóa toàn bộ dữ liệu App theo yêu cầu người dùng (Requirement 22.6–22.8).
 *
 * Ghép ba Port xóa: [AutomationRepository.deleteAll] + [CredentialStore.clear] +
 * [SyncLogRepository.deleteAll]. Khi mọi bước thành công, trả [DataWipeResult.Success] liệt kê
 * từng loại dữ liệu đã xóa (Requirement 22.7). Nếu **bất kỳ** bước nào ném lỗi, dừng ngay và trả
 * [DataWipeResult.Failure]; các dữ liệu chưa kịp xóa được giữ nguyên (Requirement 22.8) — use case
 * không cố tiếp tục để tránh xóa một phần ngoài ý muốn.
 *
 * ### Về giới hạn 10 giây (Requirement 22.6)
 * Yêu cầu xóa "trong vòng 10 giây" là một ràng buộc hiệu năng. Use case giữ logic xóa tối giản và
 * tuần tự; việc cưỡng chế ngân sách thời gian (vd `withTimeout`) thuộc tầng gọi/`:data` nơi biết
 * đặc tính I/O thực tế của Room/EncryptedSharedPreferences. Mỗi hiện thực Port được kỳ vọng hoàn
 * tất nhanh hơn nhiều so với ngưỡng này.
 *
 * @property automationRepository kho Automation (Requirement 22.6).
 * @property credentialStore kho credential mã hóa (Requirements 22.6, 22.9).
 * @property syncLogRepository kho Sync_Log (Requirement 22.6).
 */
class DataWipeUseCase(
    private val automationRepository: AutomationRepository,
    private val credentialStore: CredentialStore,
    private val syncLogRepository: SyncLogRepository,
) {

    /**
     * Thực hiện xóa toàn bộ dữ liệu App.
     *
     * Thứ tự xóa cố định (Automation → credential → Sync_Log) để báo cáo xác định. Nếu một bước
     * thất bại, các bước sau **không** chạy và dữ liệu còn lại được giữ nguyên (Requirement 22.8).
     *
     * @return [DataWipeResult.Success] liệt kê các loại đã xóa (Requirement 22.7); hoặc
     *   [DataWipeResult.Failure] nếu một bước thất bại.
     */
    suspend fun wipe(): DataWipeResult {
        val wiped = mutableListOf<WipedDataType>()
        return try {
            automationRepository.deleteAll()
            wiped += WipedDataType.AUTOMATIONS

            credentialStore.clear()
            wiped += WipedDataType.DESTINATION_CREDENTIALS

            syncLogRepository.deleteAll()
            wiped += WipedDataType.SYNC_LOG

            DataWipeResult.Success(wiped)
        } catch (error: Exception) {
            DataWipeResult.Failure(
                message = "Xóa dữ liệu chưa hoàn tất; dữ liệu còn lại được giữ nguyên " +
                    "(đã xóa: ${wiped.joinToString().ifEmpty { "không có" }}) (Requirement 22.8).",
                cause = error,
            )
        }
    }
}
