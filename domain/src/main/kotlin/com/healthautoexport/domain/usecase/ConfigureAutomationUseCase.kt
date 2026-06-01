package com.healthautoexport.domain.usecase

import com.healthautoexport.domain.logic.AutomationNameValidation
import com.healthautoexport.domain.logic.AutomationNameValidator
import com.healthautoexport.domain.model.Automation
import com.healthautoexport.domain.port.AutomationRepository

/**
 * Kết quả của một thao tác tạo/cập nhật Automation (Requirements 14.1–14.4, 14.7).
 *
 * Kiểu sealed buộc gọi-bên xử lý đủ hai nhánh: lưu thành công ([Saved]) hoặc bị từ chối do vi phạm
 * xác thực ([ValidationError]); khi bị từ chối, App giữ nguyên dữ liệu người dùng đã nhập
 * (Requirement 14.7) — việc giữ nguyên thuộc tầng UI, use case chỉ báo lỗi và **không** persist.
 */
sealed interface AutomationSaveResult {

    /**
     * Đã lưu Automation thành công.
     *
     * @property automation bản Automation đã được lưu (đã chuẩn hóa/đầy đủ).
     */
    data class Saved(val automation: Automation) : AutomationSaveResult

    /**
     * Từ chối lưu vì vi phạm xác thực (Requirements 14.1, 14.3, 14.4, 14.7).
     *
     * @property reason loại vi phạm.
     * @property message thông báo xác thực người dùng đọc được chỉ ra phần còn thiếu/không hợp lệ.
     */
    data class ValidationError(val reason: Reason, val message: String) : AutomationSaveResult {

        /** Các nguyên nhân từ chối lưu Automation. */
        enum class Reason {
            /** Tên rỗng hoặc dài quá 100 ký tự (Requirement 14.1). */
            INVALID_NAME,

            /** Tên trùng (không phân biệt hoa/thường) với Automation đã tồn tại (Requirement 14.7). */
            DUPLICATE_NAME,

            /** Thiếu Destination (Requirement 14.3). */
            MISSING_DESTINATION,

            /** Không chọn metric lẫn workout nào (Requirement 14.4). */
            MISSING_SELECTION,
        }
    }
}

/**
 * Cổng (port) phát tín hiệu **hủy** một lần chạy Automation đang diễn ra (Requirement 14.9).
 *
 * Khi xóa một Automation đang chạy giữa chừng, việc dừng lần chạy + dọn dữ liệu một phần là trách
 * nhiệm của tầng thực thi nền (ExportWorker/WorkManager ở `:data`). Use case không tự hủy coroutine
 * của worker; nó **phát tín hiệu** qua port này để tầng `:data` thực hiện hủy thật (đồng thời
 * `RunExportJobUseCase` đã bảo đảm không để lại partial khi bị hủy — Requirement 13.6).
 *
 * Là `fun interface` để dễ cung cấp lambda/`Scheduler.cancel` ở tầng ráp nối.
 */
fun interface AutomationRunCanceller {

    /**
     * Yêu cầu hủy lần chạy hiện hành của Automation [automationId] (nếu có) và dọn mọi partial.
     *
     * @param automationId định danh Automation cần hủy lần chạy.
     */
    suspend fun cancelRun(automationId: String)
}

/**
 * Use case CRUD cho Automation: tạo/cập nhật có xác thực, bật/tắt, và xóa (Requirement 14).
 *
 * Toàn bộ ràng buộc xác thực được kiểm tra **trước khi** chạm tới [AutomationRepository], theo đúng
 * thứ tự xác định: tên (1..100, Requirement 14.1) → trùng tên không phân biệt hoa/thường
 * (Requirement 14.7) → bắt buộc Destination (Requirement 14.3) → ≥ 1 metric/workout
 * (Requirement 14.4). Khi bất kỳ ràng buộc nào vi phạm, use case trả [AutomationSaveResult.ValidationError]
 * **mà không** lưu, để App giữ nguyên dữ liệu người dùng nhập (Requirement 14.7).
 *
 * Xóa một Automation đang chạy giữa chừng sẽ phát tín hiệu hủy qua [runCanceller] **trước** khi xóa
 * khỏi kho, để dừng lần chạy và dọn partial (Requirement 14.9).
 *
 * @property repository kho lưu Automation (Room ở `:data`) (Requirement 14.5).
 * @property runCanceller seam phát tín hiệu hủy lần chạy mid-run; mặc định no-op cho ngữ cảnh
 *   không có worker (Requirement 14.9).
 */
class ConfigureAutomationUseCase(
    private val repository: AutomationRepository,
    private val runCanceller: AutomationRunCanceller = AutomationRunCanceller { },
) {

    /**
     * Tạo hoặc cập nhật một [Automation] sau khi xác thực đầy đủ (Requirements 14.1–14.4, 14.7).
     *
     * Khi cập nhật một Automation đã tồn tại (cùng [Automation.id]), bản trùng tên chính là chính
     * nó **không** bị coi là vi phạm: kiểm tra trùng tên bỏ qua bản ghi có cùng id.
     *
     * @param automation Automation đề xuất lưu.
     * @return [AutomationSaveResult.Saved] khi hợp lệ và đã lưu; ngược lại
     *   [AutomationSaveResult.ValidationError].
     */
    suspend fun save(automation: Automation): AutomationSaveResult {
        // 1) Tên 1..100 ký tự (Requirement 14.1) + duy nhất không phân biệt hoa/thường (14.7).
        //    Loại chính bản đang cập nhật khỏi tập tên hiện có để không tự coi là trùng.
        val existing = repository.findByNameIgnoreCase(automation.name)
        val existingNames = if (existing != null && existing.id != automation.id) {
            setOf(existing.name)
        } else {
            emptySet()
        }
        when (val nameResult = AutomationNameValidator.validate(automation.name, existingNames)) {
            is AutomationNameValidation.Invalid -> return nameValidationError(nameResult.reason)
            AutomationNameValidation.Valid -> Unit
        }

        // 2) Bắt buộc có ít nhất một metric hoặc workout (Requirement 14.4).
        if (automation.selection.isEmpty) {
            return AutomationSaveResult.ValidationError(
                reason = AutomationSaveResult.ValidationError.Reason.MISSING_SELECTION,
                message = "Cần chọn ít nhất một Health_Metric hoặc Workout (Requirement 14.4).",
            )
        }

        // 3) Bắt buộc có Destination (Requirement 14.3): tham chiếu cấu hình không được rỗng.
        if (automation.destinationConfigRef.isBlank()) {
            return AutomationSaveResult.ValidationError(
                reason = AutomationSaveResult.ValidationError.Reason.MISSING_DESTINATION,
                message = "Cần chọn một Destination cho Automation (Requirement 14.3).",
            )
        }

        repository.upsert(automation)
        return AutomationSaveResult.Saved(automation)
    }

    /**
     * Bật một Automation đang tắt (Requirement 14.2). Không làm gì nếu Automation không tồn tại.
     *
     * @param id định danh Automation.
     * @return Automation đã bật, hoặc `null` nếu không tìm thấy.
     */
    suspend fun enable(id: String): Automation? = setEnabled(id, enabled = true)

    /**
     * Tắt một Automation đang bật (Requirement 14.2). Không làm gì nếu Automation không tồn tại.
     *
     * @param id định danh Automation.
     * @return Automation đã tắt, hoặc `null` nếu không tìm thấy.
     */
    suspend fun disable(id: String): Automation? = setEnabled(id, enabled = false)

    /**
     * Xóa một Automation; nếu nó đang chạy giữa chừng, phát tín hiệu hủy lần chạy và dọn partial
     * **trước** khi xóa khỏi kho (Requirements 14.2, 14.9).
     *
     * @param id định danh Automation cần xóa.
     */
    suspend fun delete(id: String) {
        // Phát tín hiệu hủy lần chạy mid-run trước (Requirement 14.9); seam ở :data hủy thật.
        runCanceller.cancelRun(id)
        repository.delete(id)
    }

    private suspend fun setEnabled(id: String, enabled: Boolean): Automation? {
        val current = repository.findById(id) ?: return null
        if (current.enabled == enabled) return current
        val updated = current.copy(enabled = enabled)
        repository.upsert(updated)
        return updated
    }

    private fun nameValidationError(reason: AutomationNameValidation.Reason): AutomationSaveResult.ValidationError =
        when (reason) {
            AutomationNameValidation.Reason.EMPTY -> AutomationSaveResult.ValidationError(
                reason = AutomationSaveResult.ValidationError.Reason.INVALID_NAME,
                message = "Tên Automation phải dài ít nhất ${AutomationNameValidator.MIN_LENGTH} ký tự (Requirement 14.1).",
            )

            AutomationNameValidation.Reason.TOO_LONG -> AutomationSaveResult.ValidationError(
                reason = AutomationSaveResult.ValidationError.Reason.INVALID_NAME,
                message = "Tên Automation không được dài quá ${AutomationNameValidator.MAX_LENGTH} ký tự (Requirement 14.1).",
            )

            AutomationNameValidation.Reason.DUPLICATE -> AutomationSaveResult.ValidationError(
                reason = AutomationSaveResult.ValidationError.Reason.DUPLICATE_NAME,
                message = "Đã tồn tại một Automation có tên trùng (không phân biệt hoa/thường) (Requirement 14.7).",
            )
        }
}
