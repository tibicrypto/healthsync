package com.healthautoexport.ui.state

import com.healthautoexport.domain.model.HealthMetricType
import com.healthautoexport.domain.model.MetricSelection
import com.healthautoexport.domain.model.WorkoutType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kho lưu **trong bộ nhớ** [MetricSelection] hiện hành, dùng chung giữa nhiều ViewModel của tầng
 * Presentation (Requirements 4.4, 5.7).
 *
 * `MetricsViewModel` ghi lựa chọn; `QuickExportViewModel` và `PermissionsViewModel` đọc lựa chọn
 * để dựng `ExportJobConfig` và tính trạng thái quyền theo từng metric. Lớp là một `@Singleton`
 * cụ thể với hàm dựng `@Inject` nên Hilt tự cung cấp mà không cần module (việc ráp nối Hilt nâng
 * cao thuộc task 22.1).
 *
 * Đây cố ý là trạng thái phiên (session) trong bộ nhớ: lựa chọn metric của một Quick_Export không
 * cần bền vững qua các phiên (khác với cấu hình Automation, vốn được lưu qua Room).
 */
@Singleton
class MetricSelectionStore @Inject constructor() {

    private val _selection = MutableStateFlow(MetricSelection())

    /** Luồng [MetricSelection] hiện hành (Requirement 4.4). */
    val selection: StateFlow<MetricSelection> = _selection.asStateFlow()

    /** Bật/tắt một [HealthMetricType] trong lựa chọn hiện hành. */
    fun toggleMetric(type: HealthMetricType) {
        _selection.update { current ->
            val metrics = if (type in current.metrics) current.metrics - type else current.metrics + type
            current.copy(metrics = metrics)
        }
    }

    /** Bật/tắt một [WorkoutType] trong lựa chọn hiện hành. */
    fun toggleWorkout(type: WorkoutType) {
        _selection.update { current ->
            val workouts = if (type in current.workouts) current.workouts - type else current.workouts + type
            current.copy(workouts = workouts)
        }
    }

    /** Thay thế toàn bộ lựa chọn (vd khi nạp từ một Automation). */
    fun setSelection(selection: MetricSelection) {
        _selection.value = selection
    }

    /** Xóa toàn bộ lựa chọn. */
    fun clear() {
        _selection.value = MetricSelection()
    }
}
