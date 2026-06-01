package com.healthautoexport.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthautoexport.domain.model.AggregationPeriod
import com.healthautoexport.domain.model.Automation
import com.healthautoexport.domain.model.DestinationType
import com.healthautoexport.domain.model.ExportFormat
import com.healthautoexport.domain.model.MetricSelection
import com.healthautoexport.DeepLinkCoordinator
import com.healthautoexport.domain.logic.ProposedAutomationConfig
import com.healthautoexport.domain.usecase.AutomationSaveResult
import com.healthautoexport.domain.usecase.ConfigureAutomationUseCase
import com.healthautoexport.domain.port.AutomationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Bản nháp Automation đang được tạo/chỉnh sửa trên form (Requirements 14.1–14.4).
 *
 * Giữ id (null = tạo mới), tên, lựa chọn, định dạng/khoảng tổng hợp/lịch và Destination. Form thao
 * tác trên kiểu này rồi gọi [AutomationsViewModel.save] để xác thực + lưu.
 *
 * @property id id của Automation đang sửa, hoặc `null` khi tạo mới.
 * @property name tên đề xuất (1..100 ký tự, Requirement 14.1).
 * @property selection metric/workout được chọn (Requirement 14.4).
 * @property exportFormat định dạng xuất.
 * @property aggregationPeriod mức tổng hợp.
 * @property scheduleIntervalMinutes khoảng lặp lịch (phút, Requirement 15.3).
 * @property enabled trạng thái bật/tắt.
 * @property destinationType loại Destination (Requirement 14.3).
 * @property destinationConfigRef tham chiếu cấu hình Destination; rỗng = chưa chọn.
 */
data class AutomationDraft(
    val id: String? = null,
    val name: String = "",
    val selection: MetricSelection = MetricSelection(),
    val exportFormat: ExportFormat = ExportFormat.JSON,
    val aggregationPeriod: AggregationPeriod = AggregationPeriod.DAY,
    val scheduleIntervalMinutes: Long = 60,
    val enabled: Boolean = true,
    val destinationType: DestinationType = DestinationType.REST_API,
    val destinationConfigRef: String = "",
)

/**
 * Trạng thái UI cho màn hình Automations (Requirement 14).
 *
 * @property automations danh sách Automation đã lưu, phát lại khi thay đổi (Requirement 14.5).
 * @property editing bản nháp đang chỉnh sửa trên form, hoặc `null` khi không mở form.
 * @property validationMessage thông báo xác thực gần nhất khi lưu bị từ chối (Requirements 14.3,
 *   14.4, 14.7), hoặc `null`.
 * @property lastSavedName tên Automation vừa lưu thành công (để hiển thị xác nhận), hoặc `null`.
 */
data class AutomationsUiState(
    val automations: List<Automation> = emptyList(),
    val editing: AutomationDraft? = null,
    val validationMessage: String? = null,
    val lastSavedName: String? = null,
)

/**
 * ViewModel CRUD cho Automation (Requirement 14).
 *
 * Quan sát [AutomationRepository.observeAll] để hiển thị danh sách Automation đã lưu
 * (Requirement 14.5). Thao tác tạo/sửa đi qua [ConfigureAutomationUseCase.save] để áp đầy đủ xác
 * thực (tên 1..100, trùng tên, bắt buộc Destination + ≥1 metric/workout); lỗi xác thực được phơi
 * bày qua [AutomationsUiState.validationMessage] trong khi **giữ nguyên** bản nháp người dùng đã
 * nhập (Requirement 14.7). Bật/tắt/xóa đi qua use case tương ứng (Requirements 14.2, 14.9).
 */
@HiltViewModel
class AutomationsViewModel @Inject constructor(
    private val configureAutomation: ConfigureAutomationUseCase,
    private val automationRepository: AutomationRepository,
    private val deepLinkCoordinator: DeepLinkCoordinator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AutomationsUiState())

    /** Trạng thái UI quan sát được. */
    val uiState: StateFlow<AutomationsUiState> = _uiState.asStateFlow()

    init {
        // Quan sát danh sách Automation đã lưu và phản chiếu vào trạng thái UI (Requirement 14.5).
        viewModelScope.launch {
            automationRepository.observeAll().collect { list ->
                _uiState.update { it.copy(automations = list) }
            }
        }
        // Quan sát đề xuất deep link: khi MainActivity phân tích một deep link hợp lệ, mở form
        // điền sẵn để **người dùng xác nhận trước khi lưu** (Requirement 14.6). Không tự lưu.
        viewModelScope.launch {
            deepLinkCoordinator.pendingProposal.collect { proposal ->
                if (proposal != null) {
                    applyDeepLinkProposal(proposal)
                    deepLinkCoordinator.consumeProposal()
                }
            }
        }
        // Quan sát thông báo từ chối deep link để hiển thị lỗi (Requirement 14.8).
        viewModelScope.launch {
            deepLinkCoordinator.rejectionMessage.collect { message ->
                if (message != null) {
                    _uiState.update { it.copy(validationMessage = message) }
                    deepLinkCoordinator.consumeRejection()
                }
            }
        }
    }

    /**
     * Điền sẵn form từ một [ProposedAutomationConfig] của deep link để người dùng xác nhận
     * (Requirement 14.6). Đặt `destinationConfigRef` theo tên loại Destination để qua được kiểm
     * tra "bắt buộc Destination" của use case khi người dùng nhấn Lưu.
     */
    private fun applyDeepLinkProposal(proposal: ProposedAutomationConfig) {
        _uiState.update {
            it.copy(
                editing = AutomationDraft(
                    name = proposal.name,
                    exportFormat = proposal.exportFormat,
                    aggregationPeriod = proposal.aggregationPeriod,
                    scheduleIntervalMinutes = proposal.scheduleIntervalMinutes,
                    destinationType = proposal.destinationType,
                    destinationConfigRef = proposal.destinationType.name,
                ),
                validationMessage = null,
                lastSavedName = null,
            )
        }
    }

    /** Mở form tạo Automation mới. */
    fun startCreate() {
        _uiState.update {
            it.copy(editing = AutomationDraft(), validationMessage = null, lastSavedName = null)
        }
    }

    /** Mở form chỉnh sửa một Automation hiện có (Requirement 14.2). */
    fun startEdit(automation: Automation) {
        _uiState.update {
            it.copy(
                editing = AutomationDraft(
                    id = automation.id,
                    name = automation.name,
                    selection = automation.selection,
                    exportFormat = automation.exportFormat,
                    aggregationPeriod = automation.aggregationPeriod,
                    scheduleIntervalMinutes = automation.scheduleIntervalMinutes,
                    enabled = automation.enabled,
                    destinationType = automation.destinationType,
                    destinationConfigRef = automation.destinationConfigRef,
                ),
                validationMessage = null,
                lastSavedName = null,
            )
        }
    }

    /** Cập nhật bản nháp đang chỉnh sửa trên form. */
    fun updateDraft(draft: AutomationDraft) {
        _uiState.update { it.copy(editing = draft, validationMessage = null) }
    }

    /** Đóng form mà không lưu. */
    fun cancelEdit() {
        _uiState.update { it.copy(editing = null, validationMessage = null) }
    }

    /**
     * Xác thực + lưu bản nháp hiện tại qua [ConfigureAutomationUseCase.save] (Requirements 14.1,
     * 14.3, 14.4, 14.7). Khi bị từ chối, hiển thị thông báo và **giữ nguyên** bản nháp
     * (Requirement 14.7); khi thành công, đóng form và phơi tên đã lưu để xác nhận.
     */
    fun save() {
        val draft = _uiState.value.editing ?: return
        val automation = Automation(
            id = draft.id ?: UUID.randomUUID().toString(),
            name = draft.name,
            selection = draft.selection,
            exportFormat = draft.exportFormat,
            aggregationPeriod = draft.aggregationPeriod,
            scheduleIntervalMinutes = draft.scheduleIntervalMinutes,
            enabled = draft.enabled,
            destinationType = draft.destinationType,
            destinationConfigRef = draft.destinationConfigRef,
        )
        viewModelScope.launch {
            when (val result = configureAutomation.save(automation)) {
                is AutomationSaveResult.Saved -> {
                    _uiState.update {
                        it.copy(
                            editing = null,
                            validationMessage = null,
                            lastSavedName = result.automation.name,
                        )
                    }
                }

                is AutomationSaveResult.ValidationError -> {
                    // Giữ nguyên bản nháp người dùng đã nhập (Requirement 14.7).
                    _uiState.update { it.copy(validationMessage = result.message) }
                }
            }
        }
    }

    /** Bật một Automation (Requirement 14.2). */
    fun enable(id: String) {
        viewModelScope.launch { configureAutomation.enable(id) }
    }

    /** Tắt một Automation (Requirement 14.2). */
    fun disable(id: String) {
        viewModelScope.launch { configureAutomation.disable(id) }
    }

    /** Xóa một Automation; dừng lần chạy mid-run nếu có (Requirements 14.2, 14.9). */
    fun delete(id: String) {
        viewModelScope.launch { configureAutomation.delete(id) }
    }

    /** Xóa thông báo xác nhận "đã lưu" sau khi hiển thị. */
    fun consumeSavedConfirmation() {
        _uiState.update { it.copy(lastSavedName = null) }
    }
}
