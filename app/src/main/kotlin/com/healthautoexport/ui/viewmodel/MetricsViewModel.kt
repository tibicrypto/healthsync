package com.healthautoexport.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthautoexport.domain.model.HealthMetricType
import com.healthautoexport.domain.model.MetricCatalog
import com.healthautoexport.domain.model.MetricGroup
import com.healthautoexport.domain.model.MetricSelection
import com.healthautoexport.domain.model.WorkoutType
import com.healthautoexport.ui.state.MetricSelectionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Trạng thái UI cho màn hình chọn chỉ số (Metrics) (Requirements 4.4, 4.8).
 *
 * @property metricsByGroup các [HealthMetricType] có thể chọn, gom theo [MetricGroup] và kèm tên
 *   canonical để hiển thị.
 * @property workouts toàn bộ [WorkoutType] có thể chọn.
 * @property selection lựa chọn hiện hành (Requirement 4.4).
 * @property isSelectionValid `false` khi lựa chọn rỗng — dùng để chặn khởi tạo Export_Job
 *   (Requirement 4.8).
 */
data class MetricsUiState(
    val metricsByGroup: Map<MetricGroup, List<MetricOption>> = emptyMap(),
    val workouts: List<WorkoutType> = emptyList(),
    val selection: MetricSelection = MetricSelection(),
    val isSelectionValid: Boolean = false,
)

/**
 * Một chỉ số có thể chọn, kèm nhãn hiển thị lấy từ [MetricCatalog].
 *
 * @property type loại chỉ số.
 * @property label tên canonical (snake_case) dùng làm nhãn hiển thị.
 */
data class MetricOption(
    val type: HealthMetricType,
    val label: String,
)

/**
 * ViewModel liệt kê các [HealthMetricType] + [WorkoutType] có thể chọn và quản lý [MetricSelection]
 * hiện hành (Requirements 4.4, 4.8).
 *
 * Lựa chọn được giữ trong [MetricSelectionStore] dùng chung nên `QuickExportViewModel` và
 * `PermissionsViewModel` thấy cùng một lựa chọn. ViewModel phơi bày cờ [MetricsUiState.isSelectionValid]
 * `false` khi lựa chọn rỗng để tầng UI/Quick_Export chặn khởi tạo (Requirement 4.8) — bản thân
 * `RunExportJobUseCase` cũng từ chối lựa chọn rỗng trước khi đọc.
 */
@HiltViewModel
class MetricsViewModel @Inject constructor(
    private val selectionStore: MetricSelectionStore,
) : ViewModel() {

    private val metricsByGroup: Map<MetricGroup, List<MetricOption>> =
        HealthMetricType.entries
            .map { type -> MetricOption(type, MetricCatalog.spec(type).canonicalName) }
            .groupBy { it.type.group }

    /** Trạng thái UI quan sát được, phái sinh từ lựa chọn dùng chung. */
    val uiState: StateFlow<MetricsUiState> =
        selectionStore.selection
            .map { selection ->
                MetricsUiState(
                    metricsByGroup = metricsByGroup,
                    workouts = WorkoutType.entries.toList(),
                    selection = selection,
                    isSelectionValid = !selection.isEmpty,
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = MetricsUiState(
                    metricsByGroup = metricsByGroup,
                    workouts = WorkoutType.entries.toList(),
                    selection = selectionStore.selection.value,
                    isSelectionValid = !selectionStore.selection.value.isEmpty,
                ),
            )

    /** Bật/tắt một chỉ số trong lựa chọn (Requirement 4.4). */
    fun toggleMetric(type: HealthMetricType) = selectionStore.toggleMetric(type)

    /** Bật/tắt một loại Workout trong lựa chọn (Requirement 5.7). */
    fun toggleWorkout(type: WorkoutType) = selectionStore.toggleWorkout(type)

    /** Xóa toàn bộ lựa chọn. */
    fun clear() = selectionStore.clear()

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
