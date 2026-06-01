package com.healthautoexport.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthautoexport.domain.model.AggregationPeriod
import com.healthautoexport.domain.model.DateRange
import com.healthautoexport.domain.model.DestinationType
import com.healthautoexport.domain.model.ExportFormat
import com.healthautoexport.domain.model.ExportStatus
import com.healthautoexport.domain.pipeline.DateRangeResolver
import com.healthautoexport.domain.pipeline.DateRangeValidation
import com.healthautoexport.domain.port.ExportProgress
import com.healthautoexport.domain.usecase.ExportJobConfig
import com.healthautoexport.domain.usecase.QuickExportOutcome
import com.healthautoexport.domain.usecase.RunExportJobUseCase
import com.healthautoexport.ui.state.DestinationConfigStore
import com.healthautoexport.ui.state.MetricSelectionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * Kết quả cuối cùng của một Quick_Export để hiển thị xác nhận (Requirements 13.3, 13.4, 13.6).
 *
 * @property status trạng thái kết thúc.
 * @property message thông báo người dùng đọc được.
 */
data class QuickExportResult(
    val status: ExportStatus,
    val message: String,
)

/**
 * Trạng thái UI cho màn hình Quick_Export (Requirement 13).
 *
 * @property format định dạng xuất đang chọn.
 * @property period mức tổng hợp đang chọn.
 * @property dateRange khoảng thời gian đã phân giải (đã clamp tương lai), hoặc `null` để dùng mặc
 *   định (Requirements 9.2, 9.6, 9.7).
 * @property isRunning `true` khi một Export_Job đang chạy (Requirements 13.2, 13.5).
 * @property progressPercent phần trăm tiến trình 0..100 (Requirement 13.2).
 * @property progressStage giai đoạn pipeline hiện tại, hoặc `null` khi chưa chạy.
 * @property result kết quả cuối cùng để hiển thị xác nhận, hoặc `null` (Requirements 13.3, 13.4).
 * @property validationMessage thông báo lỗi xác thực Date_Range/lựa chọn, hoặc `null`
 *   (Requirements 4.8, 9.2).
 * @property dateRangeAdjusted `true` nếu thời điểm kết thúc đã bị clamp về hiện tại (Requirement 9.6).
 * @property alreadyRunningMessage thông báo khi từ chối yêu cầu vì đang có job chạy, hoặc `null`
 *   (Requirement 13.5).
 */
data class QuickExportUiState(
    val format: ExportFormat = ExportFormat.JSON,
    val period: AggregationPeriod = AggregationPeriod.DAY,
    val dateRange: DateRange? = null,
    val isRunning: Boolean = false,
    val progressPercent: Int = 0,
    val progressStage: ExportProgress.Stage? = null,
    val result: QuickExportResult? = null,
    val validationMessage: String? = null,
    val dateRangeAdjusted: Boolean = false,
    val alreadyRunningMessage: String? = null,
)

/**
 * ViewModel cấu hình và kích hoạt một Quick_Export (Requirement 13).
 *
 * - **Cấu hình**: định dạng, mức tổng hợp, Date_Range, Destination. Date_Range được xác thực bằng
 *   [DateRangeResolver.validate] (Requirement 9.2) và clamp thời điểm kết thúc tương lai bằng
 *   [DateRangeResolver.clampFutureEnd] (Requirement 9.6).
 * - **Chạy**: gọi [RunExportJobUseCase.runQuickExport] để hưởng ràng buộc một-tại-một-thời-điểm;
 *   nếu đã có job chạy, hiển thị thông báo từ chối (Requirement 13.5). Tiến trình được phát qua
 *   callback `onProgress` (0..100) (Requirement 13.2).
 * - **Hủy**: [cancel] hủy [Job] của lần chạy, dừng job trong vòng 5 giây nhờ hủy hợp tác của use
 *   case; không để lại dữ liệu một phần (Requirement 13.6).
 *
 * Lựa chọn metric/workout lấy từ [MetricSelectionStore]; cấu hình Destination từ
 * [DestinationConfigStore]. Khi lựa chọn rỗng, yêu cầu bị từ chối với thông báo (Requirement 4.8).
 */
@HiltViewModel
class QuickExportViewModel @Inject constructor(
    private val runExportJob: RunExportJobUseCase,
    private val selectionStore: MetricSelectionStore,
    private val destinationConfigStore: DestinationConfigStore,
    private val dateRangeResolver: DateRangeResolver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuickExportUiState())

    /** Trạng thái UI quan sát được. */
    val uiState: StateFlow<QuickExportUiState> = _uiState.asStateFlow()

    /** Job của lần chạy hiện hành, dùng để hủy hợp tác (Requirement 13.6). */
    private var runningJob: Job? = null

    /** Chọn định dạng xuất. */
    fun setFormat(format: ExportFormat) {
        _uiState.update { it.copy(format = format) }
    }

    /** Chọn mức tổng hợp. */
    fun setPeriod(period: AggregationPeriod) {
        _uiState.update { it.copy(period = period) }
    }

    /**
     * Đặt Date_Range từ cặp mốc người dùng nhập (Requirements 9.2, 9.6).
     *
     * Xác thực thứ tự trước (từ chối `end < start`, Requirement 9.2) rồi clamp thời điểm kết thúc
     * tương lai về hiện tại (Requirement 9.6). Khi không hợp lệ, giữ nguyên Date_Range cũ và phơi
     * thông báo để người dùng chỉnh sửa.
     */
    fun setDateRange(start: Instant, end: Instant) {
        when (val validation = dateRangeResolver.validate(start, end)) {
            is DateRangeValidation.Invalid -> {
                _uiState.update { it.copy(validationMessage = validation.reason) }
            }

            is DateRangeValidation.Valid -> {
                val clamp = dateRangeResolver.clampFutureEnd(validation.range)
                _uiState.update {
                    it.copy(
                        dateRange = clamp.range,
                        dateRangeAdjusted = clamp.adjusted,
                        validationMessage = null,
                    )
                }
            }
        }
    }

    /** Xóa Date_Range đã đặt để dùng mặc định Quick_Export (Requirement 9.7). */
    fun clearDateRange() {
        _uiState.update { it.copy(dateRange = null, dateRangeAdjusted = false) }
    }

    /**
     * Kích hoạt một Quick_Export với cấu hình hiện tại (Requirement 13.1).
     *
     * Từ chối khi lựa chọn rỗng (Requirement 4.8) hoặc khi đã có job đang chạy ở ViewModel này.
     * Phát tiến trình 0..100 (Requirement 13.2) và phơi kết quả thành công/thất bại
     * (Requirements 13.3, 13.4). Nếu use case báo [QuickExportOutcome.AlreadyRunning] (một
     * Quick_Export khác đang chạy ở tiến trình), hiển thị thông báo từ chối (Requirement 13.5).
     */
    fun runQuickExport() {
        if (_uiState.value.isRunning || runningJob?.isActive == true) {
            _uiState.update {
                it.copy(alreadyRunningMessage = "Một Export_Job đang chạy (Requirement 13.5).")
            }
            return
        }

        val selection = selectionStore.selection.value
        if (selection.isEmpty) {
            _uiState.update {
                it.copy(
                    validationMessage = "Cần chọn ít nhất một Health_Metric hoặc Workout " +
                        "(Requirement 4.8).",
                )
            }
            return
        }

        val state = _uiState.value
        val range = state.dateRange ?: dateRangeResolver.defaultQuickExportRange()
        val destinationType = destinationConfigStore.selectedType.value ?: DestinationType.LOCAL_STORAGE
        val config = ExportJobConfig(
            selection = selection,
            format = state.format,
            period = state.period,
            dateRange = range,
            destinationType = destinationType,
            destinationConfig = destinationConfigStore.selectedConfig(),
        )

        _uiState.update {
            it.copy(
                isRunning = true,
                progressPercent = 0,
                progressStage = null,
                result = null,
                validationMessage = null,
                alreadyRunningMessage = null,
            )
        }

        runningJob = viewModelScope.launch {
            try {
                val outcome = runExportJob.runQuickExport(config) { progress ->
                    _uiState.update {
                        it.copy(progressPercent = progress.percent, progressStage = progress.stage)
                    }
                }
                when (outcome) {
                    is QuickExportOutcome.Finished -> {
                        _uiState.update {
                            it.copy(
                                isRunning = false,
                                result = QuickExportResult(
                                    status = outcome.report.status,
                                    message = outcome.report.detail,
                                ),
                            )
                        }
                    }

                    QuickExportOutcome.AlreadyRunning -> {
                        _uiState.update {
                            it.copy(
                                isRunning = false,
                                alreadyRunningMessage = "Một Export_Job đang chạy (Requirement 13.5).",
                            )
                        }
                    }
                }
            } finally {
                runningJob = null
            }
        }
    }

    /**
     * Hủy lần chạy hiện hành (Requirement 13.6). Việc hủy [runningJob] kích hoạt hủy hợp tác trong
     * use case (mục Sync_Log CANCELLED được ghi ở đó, không để partial). Cập nhật UI để hiển thị
     * xác nhận đã hủy.
     */
    fun cancel() {
        val job = runningJob ?: return
        job.cancel()
        runningJob = null
        _uiState.update {
            it.copy(
                isRunning = false,
                progressStage = null,
                result = QuickExportResult(
                    status = ExportStatus.CANCELLED,
                    message = "Export_Job đã bị hủy; không để lại dữ liệu một phần (Requirement 13.6).",
                ),
            )
        }
    }

    /** Xóa kết quả/thông báo đã hiển thị. */
    fun consumeResult() {
        _uiState.update { it.copy(result = null, alreadyRunningMessage = null) }
    }
}
