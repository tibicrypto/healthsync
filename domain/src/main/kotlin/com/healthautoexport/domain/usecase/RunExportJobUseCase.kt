package com.healthautoexport.domain.usecase

import com.healthautoexport.domain.logic.NetworkEgressGuard
import com.healthautoexport.domain.model.DataSourceId
import com.healthautoexport.domain.model.DestinationType
import com.healthautoexport.domain.model.ExportDataset
import com.healthautoexport.domain.model.ExportStatus
import com.healthautoexport.domain.model.HealthMetricType
import com.healthautoexport.domain.model.MetricCatalog
import com.healthautoexport.domain.model.MetricSeries
import com.healthautoexport.domain.model.SyncLogEntry
import com.healthautoexport.domain.model.UnifiedRecord
import com.healthautoexport.domain.model.Workout
import com.healthautoexport.domain.pipeline.Aggregator
import com.healthautoexport.domain.pipeline.DataMerger
import com.healthautoexport.domain.pipeline.ZoneIdProvider
import com.healthautoexport.domain.port.Destination
import com.healthautoexport.domain.port.DestinationResult
import com.healthautoexport.domain.port.ExportPayload
import com.healthautoexport.domain.port.ExportProgress
import com.healthautoexport.domain.port.ExportSerializer
import com.healthautoexport.domain.port.JobReport
import com.healthautoexport.domain.port.PermissionManager
import com.healthautoexport.domain.port.ReadOutcome
import com.healthautoexport.domain.port.ReadWarning
import com.healthautoexport.domain.port.SourceDataReader
import com.healthautoexport.domain.port.SourceReadResult
import com.healthautoexport.domain.port.SyncLogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Kết quả của một lần khởi chạy **Quick_Export** (Requirement 13.5).
 *
 * Dùng kiểu sealed để phân biệt rõ "đã chạy xong" với "bị từ chối vì đang có job khác chạy", thay
 * vì nhồi cả hai vào [JobReport].
 */
sealed interface QuickExportOutcome {

    /**
     * Quick_Export đã chạy tới khi kết thúc; [report] mang trạng thái và cảnh báo cuối cùng.
     */
    data class Finished(val report: JobReport) : QuickExportOutcome

    /**
     * Một Quick_Export khác đang chạy nên yêu cầu mới bị từ chối; job đang chạy được giữ nguyên và
     * **không** có job thứ hai được khởi tạo, **không** ghi Sync_Log (Requirement 13.5).
     */
    data object AlreadyRunning : QuickExportOutcome
}

/**
 * Use case điều phối toàn bộ pipeline Export_Job — trung tâm của App (Requirement 13).
 *
 * Use case ráp các thành phần thuần (Merge → Aggregate → Serialize) với các Port I/O (đọc nguồn,
 * gửi đích, ghi log) đã được tiêm vào, theo đúng sơ đồ pipeline trong design.md:
 *
 * ```
 * refreshGrants → SourceDataReader.read → DataMerger.merge → Aggregator.aggregate
 *   → build ExportDataset → ExportSerializer.serialize → NetworkEgressGuard → Destination.send
 *   → ghi đúng MỘT mục Sync_Log
 * ```
 *
 * ### Bất biến quan trọng
 * - **Đúng một mục Sync_Log** cho mỗi job đã *khởi tạo* (từ bước đọc trở đi), bất kể thành công,
 *   rỗng, thất bại hay bị hủy (Requirements 23.1, 23.2). Lỗi xác thực *trước khi* khởi tạo (lựa
 *   chọn rỗng — Requirement 4.8) bị từ chối **mà không** ghi Sync_Log vì job chưa từng chạy.
 * - **Không để lại dữ liệu một phần** khi lỗi/hủy: pipeline chỉ gọi `Destination.send` **một lần**
 *   với payload đã tuần tự hóa hoàn chỉnh; không có bước ghi từng phần (Requirements 13.4, 13.6).
 *   Tính nguyên tử "không partial" tại đích là trách nhiệm của từng `Destination` (ghi tạm rồi
 *   commit) theo Error Handling trong design.md.
 * - **Egress chỉ khi có Destination** (Requirement 22.4): trước khi gửi, hỏi [NetworkEgressGuard];
 *   nếu chưa cấu hình Destination, job thất bại mà không phát sinh kết nối ra.
 * - **Múi giờ thuần**: ranh giới tổng hợp lấy từ [ZoneIdProvider] để `Aggregator` vẫn thuần.
 *
 * ### Tiến trình & hủy (Requirements 13.2, 13.4, 13.6)
 * [run] phát [ExportProgress] (0..100) qua callback [run]'s `onProgress` tại mỗi giai đoạn; vì các
 * giai đoạn được phát tuần tự, UI cập nhật ít nhất một lần khi chuyển giai đoạn (thỏa "≥ mỗi 2s"
 * trong thực tế). Hủy là **hợp tác**: use case kiểm tra [ensureActive] giữa các bước và dựa vào
 * cấu trúc concurrency, nên một lệnh hủy dừng job kịp thời (trong 5s). Khi bị hủy, một mục
 * Sync_Log trạng thái [ExportStatus.CANCELLED] được ghi (dưới [NonCancellable]) rồi
 * [CancellationException] được ném lại để tôn trọng structured concurrency.
 *
 * ### Đồng thời (Requirement 13.5)
 * [runQuickExport] dùng [Mutex.tryLock] để bảo đảm **chỉ một Quick_Export tại một thời điểm**; nếu
 * đã có job chạy, trả [QuickExportOutcome.AlreadyRunning] mà không khởi tạo job thứ hai. Xuất theo
 * lịch dùng [run] trực tiếp (việc chống chạy chồng lấn do WorkManager unique work đảm nhiệm —
 * Requirement 15.5).
 *
 * @property sourceDataReader port đọc đa nguồn; `:data`'s `DataReader` hiện thực (Requirements
 *   3.3–3.7). Đặt sau port để `:domain` không phụ thuộc `:data`.
 * @property permissionManagers bản đồ [PermissionManager] theo từng [DataSourceId]; gọi
 *   `refreshGrants` đầu job để phát hiện thu hồi quyền (Requirements 1.6, 2.6).
 * @property dataMerger bộ hợp nhất/loại trùng đã cấu hình dung sai + ưu tiên (Requirement 7).
 * @property aggregator bộ tổng hợp theo Aggregation_Period (Requirement 8).
 * @property zoneIdProvider cung cấp múi giờ thiết bị để căn ranh giới lịch (Requirement 8.3).
 * @property exportSerializer port tuần tự hóa theo định dạng; adapter ở `:serialization`
 *   (Requirements 10, 11, 12). Đặt sau port để tránh phụ thuộc vòng `:domain → :serialization`.
 * @property destinations bản đồ [Destination] theo từng loại (Requirements 16–21).
 * @property syncLogRepository nơi ghi mục Sync_Log (Requirement 23).
 * @property clock đồng hồ tạo dấu thời gian bắt đầu/hoàn tất job; tiêm để kiểm thử xác định.
 * @property idGenerator sinh id mục Sync_Log; mặc định UUID ngẫu nhiên.
 */
class RunExportJobUseCase(
    private val sourceDataReader: SourceDataReader,
    private val permissionManagers: Map<DataSourceId, PermissionManager>,
    private val dataMerger: DataMerger,
    private val aggregator: Aggregator,
    private val zoneIdProvider: ZoneIdProvider,
    private val exportSerializer: ExportSerializer,
    private val destinations: Map<DestinationType, Destination>,
    private val syncLogRepository: SyncLogRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {

    /** Khóa bảo đảm chỉ một Quick_Export chạy tại một thời điểm (Requirement 13.5). */
    private val quickExportMutex = Mutex()

    /**
     * Chạy một Quick_Export với ràng buộc **một-tại-một-thời-điểm** (Requirement 13.5).
     *
     * Nếu một Quick_Export đang chạy, trả [QuickExportOutcome.AlreadyRunning] ngay mà không khởi
     * tạo job mới và không ghi Sync_Log; job đang chạy được giữ nguyên. Ngược lại, khóa mutex,
     * chạy [run], rồi mở khóa (kể cả khi lỗi/hủy).
     *
     * @param config cấu hình job.
     * @param onProgress callback nhận [ExportProgress] tại mỗi giai đoạn (Requirement 13.2).
     * @return [QuickExportOutcome.Finished] kèm [JobReport] khi đã chạy; hoặc
     *   [QuickExportOutcome.AlreadyRunning] khi bị từ chối.
     */
    suspend fun runQuickExport(
        config: ExportJobConfig,
        onProgress: suspend (ExportProgress) -> Unit = {},
    ): QuickExportOutcome {
        // tryLock không-chặn: nếu đang khóa nghĩa là có Quick_Export khác chạy (Requirement 13.5).
        if (!quickExportMutex.tryLock()) {
            return QuickExportOutcome.AlreadyRunning
        }
        try {
            return QuickExportOutcome.Finished(run(config, onProgress))
        } finally {
            quickExportMutex.unlock()
        }
    }

    /**
     * Phát tiến trình của một Export_Job dưới dạng `Flow<ExportProgress>` cho UI (Requirement 13.2).
     *
     * Đây là một cold flow: mỗi lần thu (collect) sẽ chạy pipeline qua [run] và phát [ExportProgress]
     * tại mỗi giai đoạn. Hủy việc thu flow sẽ hủy job một cách hợp tác (Requirement 13.6). Mục
     * Sync_Log vẫn được ghi bên trong [run]; để lấy [JobReport] cuối cùng, dùng [run] hoặc
     * [runQuickExport]. Để cưỡng chế "một Quick_Export tại một thời điểm", thu flow này bên trong
     * [runQuickExport] hoặc tự bảo vệ bằng mutex tương ứng ở tầng gọi.
     *
     * @param config cấu hình job.
     * @return flow phát [ExportProgress] 0..100 theo từng giai đoạn pipeline.
     */
    fun runWithProgress(config: ExportJobConfig): Flow<ExportProgress> = channelFlow {
        run(config) { progress -> send(progress) }
    }

    /**
     * Chạy toàn bộ pipeline Export_Job tới khi kết thúc và ghi **đúng một** mục Sync_Log
     * (Requirements 13.1, 23.1, 23.2). Đây là API lõi, được cả Quick_Export ([runQuickExport]) lẫn
     * xuất theo lịch (ExportWorker ở `:data`) dùng chung để bảo đảm logic giống hệt nhau.
     *
     * Luồng xử lý và ánh xạ yêu cầu:
     * 1. **Tiền điều kiện (Requirement 4.8)**: lựa chọn rỗng ⇒ từ chối khởi tạo, **không** ghi
     *    Sync_Log (job chưa chạy).
     * 2. **refreshGrants** mỗi nguồn để phát hiện thu hồi quyền (Requirements 1.6, 2.6).
     * 3. **Đọc** qua [SourceDataReader]; [ReadOutcome.NoEnabledSource]/[ReadOutcome.AllSourcesUnavailable]
     *    ⇒ [ExportStatus.FAILURE] + Sync_Log (Requirements 3.6, 3.7).
     * 4. **Merge** (Requirement 7) → **Aggregate** (Requirement 8) → dựng [ExportDataset].
     * 5. **Kết quả rỗng** (không bản ghi/Workout) ⇒ [ExportStatus.EMPTY] + Sync_Log
     *    (Requirements 5.8, 6.5).
     * 6. **Tuần tự hóa** theo định dạng (Requirements 10–12); cảnh báo loại trừ Workout không route
     *    của GPX được gom vào [JobReport.warnings] (Requirement 5.6).
     * 7. **NetworkEgressGuard**: chưa cấu hình Destination ⇒ chặn egress, [ExportStatus.FAILURE]
     *    (Requirement 22.4).
     * 8. **Gửi** một lần tới [Destination] (Requirements 16–21) → trạng thái cuối + Sync_Log.
     *
     * Hủy hợp tác: [ensureActive] giữa các bước; khi bị hủy ghi mục [ExportStatus.CANCELLED]
     * (dưới [NonCancellable]) rồi ném lại [CancellationException] (Requirements 13.4, 13.6).
     *
     * @param config cấu hình job.
     * @param onProgress callback nhận [ExportProgress] tại mỗi giai đoạn (Requirement 13.2).
     * @return [JobReport] mô tả kết quả; cũng đã được phản chiếu vào Sync_Log.
     */
    suspend fun run(
        config: ExportJobConfig,
        onProgress: suspend (ExportProgress) -> Unit = {},
    ): JobReport {
        // (1) Tiền điều kiện: lựa chọn rỗng bị từ chối trước khi khởi tạo job (Requirement 4.8).
        if (config.selection.isEmpty) {
            return JobReport(
                status = ExportStatus.FAILURE,
                detail = "Cần chọn ít nhất một Health_Metric hoặc Workout (Requirement 4.8).",
                warnings = emptyList(),
                recordCount = 0,
            )
        }

        val jobStart = Instant.now(clock)
        val warnings = mutableListOf<ReadWarning>()

        try {
            // (2) Stage READING — refreshGrants để phát hiện thu hồi quyền (Requirements 1.6, 2.6).
            onProgress(ExportProgress(PERCENT_READING, ExportProgress.Stage.READING))
            refreshAllGrants()

            currentCoroutineContext().ensureActive()
            // (3) Đọc đa nguồn (Requirements 3.3–3.7).
            val perSource: List<SourceReadResult> = when (val outcome = sourceDataReader.read(config.selection, config.dateRange)) {
                is ReadOutcome.NoEnabledSource ->
                    return finalize(ExportStatus.FAILURE, outcome.reason, warnings, recordCount = 0, config, jobStart)

                is ReadOutcome.AllSourcesUnavailable ->
                    return finalize(ExportStatus.FAILURE, outcome.reason, warnings, recordCount = 0, config, jobStart)

                is ReadOutcome.Success -> outcome.perSource
            }
            // Thu thập cảnh báo đọc (bản ghi bỏ qua, trường thiếu...) (Requirements 4.7, 6.6).
            warnings += perSource.flatMap { it.warnings }

            currentCoroutineContext().ensureActive()
            // (4) Merge (Requirement 7).
            onProgress(ExportProgress(PERCENT_MERGING, ExportProgress.Stage.MERGING))
            val merged = dataMerger.merge(perSource)

            currentCoroutineContext().ensureActive()
            // (4) Aggregate (Requirement 8) — múi giờ thiết bị từ ZoneIdProvider (Requirement 8.3).
            onProgress(ExportProgress(PERCENT_AGGREGATING, ExportProgress.Stage.AGGREGATING))
            val aggregated = aggregator.aggregate(merged.recordsByMetric, config.period, zoneIdProvider.zone())

            // Dựng envelope ExportDataset (gom metric thành MetricSeries theo MetricCatalog).
            val dataset = buildDataset(aggregated, merged.workouts)
            val totalRecords = aggregated.values.sumOf { it.size }

            // (5) Kết quả rỗng ⇒ EMPTY nhưng vẫn ghi Sync_Log (Requirements 5.8, 6.5).
            if (totalRecords == 0 && merged.workouts.isEmpty()) {
                return finalize(
                    status = ExportStatus.EMPTY,
                    detail = "Export_Job hoàn tất với tập kết quả rỗng: không khớp bản ghi hoặc Workout nào.",
                    warnings = warnings,
                    recordCount = 0,
                    config = config,
                    jobStart = jobStart,
                )
            }

            currentCoroutineContext().ensureActive()
            // (6) Tuần tự hóa theo định dạng (Requirements 10–12).
            onProgress(ExportProgress(PERCENT_SERIALIZING, ExportProgress.Stage.SERIALIZING))
            val serialized = exportSerializer.serialize(dataset, config.format)
            warnings += gpxExclusionWarnings(serialized.excludedWorkoutIds, merged.workouts)

            // (7) NetworkEgressGuard: chưa cấu hình Destination ⇒ chặn egress (Requirement 22.4).
            if (config.destinationConfig == null ||
                !NetworkEgressGuard.allowEgress(listOfNotNull(config.destinationConfig))
            ) {
                return finalize(
                    status = ExportStatus.FAILURE,
                    detail = "Chưa cấu hình Destination; không khởi tạo kết nối mạng đi (Requirement 22.4).",
                    warnings = warnings,
                    recordCount = totalRecords,
                    config = config,
                    jobStart = jobStart,
                )
            }

            val destination = destinations[config.destinationType]
                ?: return finalize(
                    status = ExportStatus.FAILURE,
                    detail = "Không tìm thấy hiện thực Destination cho loại ${config.destinationType}.",
                    warnings = warnings,
                    recordCount = totalRecords,
                    config = config,
                    jobStart = jobStart,
                )

            currentCoroutineContext().ensureActive()
            // (8) Gửi một lần tới Destination (Requirements 16–21).
            onProgress(ExportProgress(PERCENT_SENDING, ExportProgress.Stage.SENDING))
            val payload = ExportPayload(
                bytes = serialized.bytes,
                contentType = serialized.contentType,
                jobStartUtc = jobStart,
                format = config.format,
            )
            val sendResult = destination.send(payload, config.destinationConfig)

            onProgress(ExportProgress(PERCENT_COMPLETED, ExportProgress.Stage.COMPLETED))
            return when (sendResult) {
                is DestinationResult.Success -> finalize(
                    status = ExportStatus.SUCCESS,
                    detail = sendResult.detail,
                    warnings = warnings,
                    recordCount = totalRecords,
                    config = config,
                    jobStart = jobStart,
                )

                is DestinationResult.Failure -> finalize(
                    status = ExportStatus.FAILURE,
                    detail = sendResult.reason,
                    warnings = warnings,
                    recordCount = totalRecords,
                    config = config,
                    jobStart = jobStart,
                )
            }
        } catch (cancellation: CancellationException) {
            // Hủy hợp tác: không để partial; ghi mục CANCELLED rồi ném lại (Requirements 13.4, 13.6).
            withContext(NonCancellable) {
                finalize(
                    status = ExportStatus.CANCELLED,
                    detail = "Export_Job đã bị hủy trước khi hoàn tất; không để lại dữ liệu một phần.",
                    warnings = warnings,
                    recordCount = 0,
                    config = config,
                    jobStart = jobStart,
                )
            }
            throw cancellation
        } catch (error: Exception) {
            // Mọi lỗi không lường trước ⇒ thất bại có kiểm soát + Sync_Log (Requirements 13.4, 23.2).
            return finalize(
                status = ExportStatus.FAILURE,
                detail = "Export_Job thất bại: ${error.message ?: error::class.simpleName}",
                warnings = warnings,
                recordCount = 0,
                config = config,
                jobStart = jobStart,
            )
        }
    }

    /**
     * Gọi `refreshGrants` cho mỗi [PermissionManager] đã tiêm để phát hiện quyền bị thu hồi đầu
     * mỗi job (Requirements 1.6, 2.6). Việc loại metric bị ảnh hưởng do [SourceDataReader] (đọc
     * theo tập quyền mới) đảm nhiệm.
     */
    private suspend fun refreshAllGrants() {
        for ((source, manager) in permissionManagers) {
            currentCoroutineContext().ensureActive()
            manager.refreshGrants(source)
        }
    }

    /**
     * Dựng [ExportDataset] từ kết quả tổng hợp: mỗi [HealthMetricType] có bản ghi trở thành một
     * [MetricSeries] với tên canonical + đơn vị lấy từ [MetricCatalog]; [Workout] được đính kèm.
     * Các danh mục chuyên biệt khác (stateOfMind, medications...) giữ rỗng vì pipeline này thao tác
     * trên [UnifiedRecord]/[Workout] (ECG/giấc ngủ nằm trong chuỗi metric tương ứng).
     */
    private fun buildDataset(
        aggregated: Map<HealthMetricType, List<UnifiedRecord>>,
        workouts: List<Workout>,
    ): ExportDataset {
        val series = aggregated.entries
            .filter { it.value.isNotEmpty() }
            .map { (type, records) ->
                val spec = MetricCatalog.spec(type)
                MetricSeries(name = spec.canonicalName, units = spec.unit.symbol, data = records)
            }
        return ExportDataset(metrics = series, workouts = workouts)
    }

    /**
     * Chuyển các id Workout bị loại khỏi GPX thành [ReadWarning] để ghi Sync_Log (Requirement 5.6).
     * Nguồn của cảnh báo lấy theo `dataSourceId` của Workout tương ứng (luôn nằm trong [workouts]).
     */
    private fun gpxExclusionWarnings(
        excludedWorkoutIds: List<String>,
        workouts: List<Workout>,
    ): List<ReadWarning> {
        if (excludedWorkoutIds.isEmpty()) return emptyList()
        val byId = workouts.associateBy { it.id }
        return excludedWorkoutIds.map { id ->
            ReadWarning(
                source = byId[id]?.dataSourceId ?: DataSourceId.HEALTH_CONNECT,
                metric = null,
                message = "Workout '$id' không có tuyến đường GPS; đã loại khỏi đầu ra GPX (Requirement 5.6).",
            )
        }
    }

    /**
     * Ghi **đúng một** mục Sync_Log cho job và trả về [JobReport] tương ứng (Requirements 23.1,
     * 23.2, 23.5). Mục log chỉ chứa metadata, không chứa dữ liệu thô (Requirement 23.4).
     */
    private suspend fun finalize(
        status: ExportStatus,
        detail: String,
        warnings: List<ReadWarning>,
        recordCount: Int,
        config: ExportJobConfig,
        jobStart: Instant,
    ): JobReport {
        val entry = SyncLogEntry(
            id = idGenerator(),
            startUtc = jobStart,
            completionUtc = Instant.now(clock),
            automationId = config.automationId,
            exportFormat = config.format,
            destinationType = config.destinationType,
            status = status,
            message = detail,
        )
        syncLogRepository.append(entry, config.maxSyncLogEntries)
        return JobReport(status = status, detail = detail, warnings = warnings, recordCount = recordCount)
    }

    private companion object {
        // Mốc phần trăm tiến trình theo từng giai đoạn (Requirement 13.2). Phát tăng dần để UI
        // luôn thấy tiến triển khi chuyển giai đoạn.
        const val PERCENT_READING = 5
        const val PERCENT_MERGING = 45
        const val PERCENT_AGGREGATING = 65
        const val PERCENT_SERIALIZING = 80
        const val PERCENT_SENDING = 92
        const val PERCENT_COMPLETED = 100
    }
}
