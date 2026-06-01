package com.healthautoexport.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthautoexport.domain.model.DataSourceId
import com.healthautoexport.domain.model.SourcePriority
import com.healthautoexport.domain.port.SourceToggleStore
import com.healthautoexport.domain.usecase.DataWipeResult
import com.healthautoexport.domain.usecase.DataWipeUseCase
import com.healthautoexport.domain.usecase.WipedDataType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Trạng thái UI cho màn hình Settings (Requirements 3.1, 7.8, 15.10, 22.6, 22.7).
 *
 * @property enabledSources tập Data_Source đang bật (Requirement 3.1).
 * @property sourcePriority thứ hạng ưu tiên nguồn để giải quyết trùng lặp (Requirement 7.8).
 * @property backgroundRestrictionGuidance hướng dẫn xin miễn trừ hạn chế nền (Requirement 15.10).
 * @property wipeConfirmation thông báo xác nhận sau khi xóa dữ liệu, liệt kê từng loại đã xóa
 *   (Requirements 22.6, 22.7), hoặc `null`.
 * @property wipeError thông báo lỗi nếu xóa dữ liệu thất bại (Requirement 22.8), hoặc `null`.
 * @property isWiping `true` khi đang thực hiện xóa dữ liệu.
 */
data class SettingsUiState(
    val enabledSources: Set<DataSourceId> = emptySet(),
    val sourcePriority: SourcePriority = SourcePriority(emptyMap()),
    val backgroundRestrictionGuidance: String = DEFAULT_BACKGROUND_GUIDANCE,
    val wipeConfirmation: List<WipedDataType>? = null,
    val wipeError: String? = null,
    val isWiping: Boolean = false,
) {
    companion object {
        /** Hướng dẫn mặc định để xin miễn trừ hạn chế thực thi nền (Requirement 15.10). */
        const val DEFAULT_BACKGROUND_GUIDANCE: String =
            "Để xuất nền chạy đúng lịch, hãy tắt tối ưu hóa pin cho ứng dụng trong " +
                "Cài đặt > Ứng dụng > Pin và cho phép hoạt động nền (Requirement 15.10)."
    }
}

/**
 * ViewModel cho màn hình Settings (Requirements 3.1, 7.8, 15.10, 22.6–22.8).
 *
 * - **Bật/tắt nguồn**: ghi qua [SourceToggleStore.setEnabled] và quan sát
 *   [SourceToggleStore.observeEnabledSources] để khôi phục đúng qua các phiên (Requirements 3.1,
 *   3.2).
 * - **Ưu tiên nguồn**: đặt thứ hạng qua [SourceToggleStore.setSourcePriority] (Requirement 7.8).
 * - **Xóa dữ liệu**: [wipeData] gọi [DataWipeUseCase.wipe] và phơi xác nhận liệt kê từng loại đã
 *   xóa (Requirements 22.6, 22.7) hoặc lỗi khi thất bại (Requirement 22.8).
 * - **Hạn chế nền**: phơi văn bản hướng dẫn xin miễn trừ (Requirement 15.10).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sourceToggleStore: SourceToggleStore,
    private val dataWipeUseCase: DataWipeUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())

    /** Trạng thái UI quan sát được. */
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // Quan sát tập nguồn đang bật (Requirements 3.1, 3.2).
        viewModelScope.launch {
            sourceToggleStore.observeEnabledSources().collect { enabled ->
                _uiState.update { it.copy(enabledSources = enabled) }
            }
        }
        // Nạp thứ hạng ưu tiên nguồn ban đầu (Requirement 7.8).
        viewModelScope.launch {
            _uiState.update { it.copy(sourcePriority = sourceToggleStore.sourcePriority()) }
        }
    }

    /** Bật/tắt một Data_Source (Requirement 3.1). */
    fun setSourceEnabled(source: DataSourceId, enabled: Boolean) {
        viewModelScope.launch { sourceToggleStore.setEnabled(source, enabled) }
    }

    /** Đặt thứ hạng ưu tiên nguồn để giải quyết trùng lặp (Requirement 7.8). */
    fun setSourcePriority(priority: SourcePriority) {
        viewModelScope.launch {
            sourceToggleStore.setSourcePriority(priority)
            _uiState.update { it.copy(sourcePriority = priority) }
        }
    }

    /**
     * Thực hiện Data_Wipe (Requirements 22.6–22.8).
     *
     * Khi thành công, phơi danh sách loại dữ liệu đã xóa để hiển thị xác nhận (Requirement 22.7);
     * khi thất bại, phơi thông báo lỗi và dữ liệu còn lại được giữ nguyên (Requirement 22.8).
     */
    fun wipeData() {
        _uiState.update { it.copy(isWiping = true, wipeConfirmation = null, wipeError = null) }
        viewModelScope.launch {
            when (val result = dataWipeUseCase.wipe()) {
                is DataWipeResult.Success -> _uiState.update {
                    it.copy(isWiping = false, wipeConfirmation = result.wiped, wipeError = null)
                }

                is DataWipeResult.Failure -> _uiState.update {
                    it.copy(isWiping = false, wipeError = result.message)
                }
            }
        }
    }

    /** Xóa thông báo xác nhận/lỗi xóa dữ liệu sau khi hiển thị. */
    fun consumeWipeMessages() {
        _uiState.update { it.copy(wipeConfirmation = null, wipeError = null) }
    }
}
