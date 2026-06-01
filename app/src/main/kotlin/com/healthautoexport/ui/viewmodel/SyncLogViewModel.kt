package com.healthautoexport.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthautoexport.domain.model.SyncLogEntry
import com.healthautoexport.domain.port.SyncLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Trạng thái UI cho màn hình Sync_Log (Requirement 23).
 *
 * @property entries các mục Sync_Log theo thứ tự hiển thị do repository cung cấp (giảm dần
 *   `completionUtc`, tie-break `startUtc` — Requirement 23.3).
 * @property isEmpty `true` khi không còn mục nào (vd sau khi xóa toàn bộ — Requirement 23.6).
 */
data class SyncLogUiState(
    val entries: List<SyncLogEntry> = emptyList(),
    val isEmpty: Boolean = true,
)

/**
 * ViewModel hiển thị Sync_Log và hỗ trợ xóa toàn bộ (Requirements 23.3, 23.6).
 *
 * Quan sát [SyncLogRepository.observeAll] — danh sách đã ở **đúng thứ tự hiển thị** (Requirement
 * 23.3), nên ViewModel không sắp xếp lại. [clear] xóa vĩnh viễn toàn bộ mục để hiển thị một
 * Sync_Log rỗng (Requirement 23.6).
 */
@HiltViewModel
class SyncLogViewModel @Inject constructor(
    private val syncLogRepository: SyncLogRepository,
) : ViewModel() {

    /** Trạng thái UI quan sát được. */
    val uiState: StateFlow<SyncLogUiState> =
        syncLogRepository.observeAll()
            .map { entries -> SyncLogUiState(entries = entries, isEmpty = entries.isEmpty()) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = SyncLogUiState(),
            )

    /** Xóa vĩnh viễn toàn bộ Sync_Log (Requirement 23.6). */
    fun clear() {
        viewModelScope.launch { syncLogRepository.deleteAll() }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
