package com.healthautoexport.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthautoexport.domain.model.DataSourceId
import com.healthautoexport.domain.model.HealthMetricType
import com.healthautoexport.domain.model.MetricCatalog
import com.healthautoexport.domain.model.MetricSelection
import com.healthautoexport.domain.model.PermissionState
import com.healthautoexport.domain.port.HealthDataSource
import com.healthautoexport.domain.port.PermissionManager
import com.healthautoexport.domain.port.SourceAvailability
import com.healthautoexport.ui.state.MetricSelectionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Khả dụng của một Data_Source để hiển thị (Requirements 1.1, 1.8, 2.1).
 *
 * @property available `true` nếu nguồn sẵn sàng.
 * @property reason lý do không khả dụng (nếu có).
 * @property installLink liên kết cài đặt/cập nhật (Health_Connect → Play Store, Requirement 1.8).
 */
data class SourceAvailabilityUi(
    val available: Boolean,
    val reason: String? = null,
    val installLink: String? = null,
)

/**
 * Hàng trạng thái quyền của một chỉ số đã chọn, cho từng nguồn (Requirements 1.7, 2.7).
 *
 * @property metric loại chỉ số.
 * @property label nhãn hiển thị (tên canonical).
 * @property healthConnectState trạng thái quyền Health_Connect, hoặc `null` nếu nguồn không cung
 *   cấp chỉ số này.
 * @property huaweiState trạng thái ủy quyền Huawei, hoặc `null` nếu nguồn không cung cấp.
 */
data class MetricPermissionRow(
    val metric: HealthMetricType,
    val label: String,
    val healthConnectState: PermissionState?,
    val huaweiState: PermissionState?,
)

/**
 * Trạng thái UI cho màn hình quyền (Permissions) (Requirements 1.1, 1.7, 1.8, 2.7).
 *
 * @property healthConnect khả dụng + link cài đặt Health_Connect (Requirements 1.1, 1.8).
 * @property huawei khả dụng Huawei_Health_Kit (Requirement 2.1).
 * @property rows trạng thái quyền theo từng chỉ số đã chọn (Requirements 1.7, 2.7).
 * @property isRefreshing `true` khi đang làm mới trạng thái.
 */
data class PermissionsUiState(
    val healthConnect: SourceAvailabilityUi = SourceAvailabilityUi(available = false),
    val huawei: SourceAvailabilityUi = SourceAvailabilityUi(available = false),
    val rows: List<MetricPermissionRow> = emptyList(),
    val isRefreshing: Boolean = false,
)

/**
 * ViewModel hiển thị trạng thái quyền đọc cho từng chỉ số đã chọn trên cả hai nguồn
 * (Requirements 1.7, 2.7) và khả dụng + liên kết cài đặt Health_Connect (Requirements 1.1, 1.8).
 *
 * Khi màn hình mở hoặc người dùng làm mới, [refresh] gọi [PermissionManager.grantedStatus] cho
 * từng nguồn và [HealthDataSource.availability] để cập nhật ngay trong vòng 5 giây
 * (Requirements 1.7, 2.7); việc làm mới chạy bất đồng bộ trên [viewModelScope] và phát trạng thái
 * mới ngay khi sẵn sàng.
 *
 * Các phụ thuộc là bản đồ Port theo [DataSourceId] mà bước ráp nối Hilt (task 22.1) cung cấp.
 */
@HiltViewModel
class PermissionsViewModel @Inject constructor(
    private val permissionManagers: Map<DataSourceId, @JvmSuppressWildcards PermissionManager>,
    private val dataSources: Map<DataSourceId, @JvmSuppressWildcards HealthDataSource>,
    private val selectionStore: MetricSelectionStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PermissionsUiState())

    /** Trạng thái UI quan sát được. */
    val uiState: StateFlow<PermissionsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /**
     * Làm mới khả dụng nguồn và trạng thái quyền theo từng chỉ số đã chọn (Requirements 1.1, 1.7,
     * 1.8, 2.7). Gọi khi mở màn hình hoặc người dùng nhấn làm mới.
     */
    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val selection = selectionStore.selection.value

            val hcAvailability = availabilityOf(DataSourceId.HEALTH_CONNECT)
            val huaweiAvailability = availabilityOf(DataSourceId.HUAWEI_HEALTH_KIT)

            val hcStatus = grantedStatusOf(DataSourceId.HEALTH_CONNECT, selection)
            val huaweiStatus = grantedStatusOf(DataSourceId.HUAWEI_HEALTH_KIT, selection)

            val rows = selection.metrics
                .sortedBy { MetricCatalog.spec(it).canonicalName }
                .map { metric ->
                    MetricPermissionRow(
                        metric = metric,
                        label = MetricCatalog.spec(metric).canonicalName,
                        healthConnectState = hcStatus[metric],
                        huaweiState = huaweiStatus[metric],
                    )
                }

            _uiState.value = PermissionsUiState(
                healthConnect = hcAvailability,
                huawei = huaweiAvailability,
                rows = rows,
                isRefreshing = false,
            )
        }
    }

    private suspend fun availabilityOf(source: DataSourceId): SourceAvailabilityUi {
        val dataSource = dataSources[source]
            ?: return SourceAvailabilityUi(available = false, reason = "Nguồn không được cấu hình.")
        return when (val availability = dataSource.availability()) {
            SourceAvailability.Available -> SourceAvailabilityUi(available = true)
            is SourceAvailability.Unavailable -> SourceAvailabilityUi(
                available = false,
                reason = availability.reason,
                installLink = availability.installLink,
            )
        }
    }

    private suspend fun grantedStatusOf(
        source: DataSourceId,
        selection: MetricSelection,
    ): Map<HealthMetricType, PermissionState> {
        val manager = permissionManagers[source] ?: return emptyMap()
        return manager.grantedStatus(source, selection)
    }
}
