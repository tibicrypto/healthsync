package com.healthautoexport

import com.healthautoexport.domain.logic.ProposedAutomationConfig
import com.healthautoexport.domain.usecase.DeepLinkResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cầu nối (coordinator) trạng thái deep link cấu hình Automation giữa [MainActivity] và tầng UI
 * (Requirements 14.6, 14.8).
 *
 * ### Vì sao cần coordinator
 * [MainActivity] là nơi **nhận** `Intent` deep link, nhưng việc **trình bày để người dùng xác nhận**
 * (Requirement 14.6) diễn ra trên màn hình Automations (một `@HiltViewModel` được dựng trong cây
 * Compose). Hai nơi này không tham chiếu trực tiếp nhau. Coordinator là một `@Singleton` trung gian:
 * `MainActivity` đẩy kết quả phân tích vào đây ([submit]); `AutomationsViewModel` và `MainActivity`
 * (để điều hướng) **quan sát** [pendingProposal]/[rejectionMessage].
 *
 * Hợp đồng: **không** Automation nào được tạo ở bước này — coordinator chỉ mang trạng thái "đề xuất
 * chờ xác nhận" (Requirement 14.6) hoặc "đã từ chối deep link" (Requirement 14.8). Việc lưu thật đi
 * qua `ConfigureAutomationUseCase.save` sau khi người dùng xác nhận.
 */
@Singleton
class DeepLinkCoordinator @Inject constructor() {

    private val _pendingProposal = MutableStateFlow<ProposedAutomationConfig?>(null)

    /** Cấu hình Automation đề xuất từ deep link, chờ người dùng xác nhận (Requirement 14.6). */
    val pendingProposal: StateFlow<ProposedAutomationConfig?> = _pendingProposal.asStateFlow()

    private val _rejectionMessage = MutableStateFlow<String?>(null)

    /** Thông báo khi deep link bị từ chối (thiếu/sai/ngoài tập tham số) (Requirement 14.8). */
    val rejectionMessage: StateFlow<String?> = _rejectionMessage.asStateFlow()

    /**
     * Tiếp nhận [result] do [MainActivity] phân tích từ deep link: đề xuất hợp lệ ⇒ đặt
     * [pendingProposal] để UI trình bày xác nhận (Requirement 14.6); bị từ chối ⇒ đặt
     * [rejectionMessage], **không** tạo Automation (Requirement 14.8).
     */
    fun submit(result: DeepLinkResult) {
        when (result) {
            is DeepLinkResult.Proposal -> {
                _pendingProposal.value = result.config
                _rejectionMessage.value = null
            }

            is DeepLinkResult.Rejected -> {
                _pendingProposal.value = null
                _rejectionMessage.value = result.message
            }
        }
    }

    /** Đánh dấu đã tiêu thụ đề xuất sau khi UI đã mở form xác nhận. */
    fun consumeProposal() {
        _pendingProposal.value = null
    }

    /** Đánh dấu đã hiển thị thông báo từ chối. */
    fun consumeRejection() {
        _rejectionMessage.value = null
    }
}
