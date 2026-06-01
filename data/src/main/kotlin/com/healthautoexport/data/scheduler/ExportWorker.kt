package com.healthautoexport.data.scheduler

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.healthautoexport.domain.logic.BackoffPolicy
import com.healthautoexport.domain.model.Automation
import com.healthautoexport.domain.model.ExportStatus
import com.healthautoexport.domain.model.SyncLogEntry
import com.healthautoexport.domain.pipeline.DateRangeResolver
import com.healthautoexport.domain.port.AutomationRepository
import com.healthautoexport.domain.port.DestinationConfig
import com.healthautoexport.domain.port.SyncLogRepository
import com.healthautoexport.domain.usecase.ExportJobConfig
import com.healthautoexport.domain.usecase.RunExportJobUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * [CoroutineWorker] thực thi một Export_Job theo lịch trong nền cho một [Automation]
 * (Requirements 15.1, 15.2, 15.5, 15.6, 15.7, 15.8, 15.9, 9.8, 9.9).
 *
 * Worker tái sử dụng **đúng** `RunExportJobUseCase` mà Quick_Export dùng, nên logic xuất giống hệt
 * nhau giữa thủ công và theo lịch (design.md, mục Concurrency & lifecycle). WorkManager cung cấp
 * đặc tính nền tin cậy: chạy khi App ở nền (Requirement 15.2), exponential backoff khi thất bại
 * tạm thời (Requirement 15.7), và unique periodic work để khử trùng lần chạy chồng lấn
 * (Requirement 15.5).
 *
 * ### Luồng [doWork]
 * 1. Đọc `automationId` từ [getInputData]; thiếu ⇒ [Result.failure] (cấu hình sai, không thử lại).
 * 2. Nạp [Automation] qua [AutomationRepository]; không tồn tại (đã xóa) hoặc đang tắt ⇒
 *    [Result.success] (không có gì để làm).
 * 3. **Khử trùng trong tiến trình** (Requirement 15.5): nếu cùng `automationId` đang chạy, ghi mục
 *    "bị bỏ qua do trùng lặp" vào Sync_Log và trả [Result.success] mà không khởi tạo job thứ hai.
 * 4. Phân giải [com.healthautoexport.domain.model.DateRange] cửa sổ nối tiếp qua
 *    [DateRangeResolver.automationRange] (Requirements 9.8, 9.9).
 * 5. Phân giải [DestinationConfig] qua [DestinationConfigResolver] từ
 *    [Automation.destinationConfigRef] (Requirement 22.9).
 * 6. Gọi [RunExportJobUseCase.run]; use case tự ghi **đúng một** mục Sync_Log cho lần chạy
 *    (Requirements 23.1, 23.2).
 * 7. Ánh xạ [com.healthautoexport.domain.port.JobReport.status] sang [Result] (xem [mapToResult]).
 *
 * ### Quyền nền thiếu (Requirements 15.6, 1.10) — seam tài liệu hóa
 * Khi một quyền cần thiết để đọc dữ liệu ở chế độ nền chưa được cấp, lần đọc trong
 * [RunExportJobUseCase] (sau `refreshGrants`) sẽ không lấy được dữ liệu và job kết thúc thất bại,
 * đồng thời ghi Sync_Log. Một **kiểm tra tiền điều kiện tường minh** cho riêng quyền-đọc-nền
 * (`PermissionManager.requestBackgroundReadPermission`/`grantedStatus`) cùng **thông báo người
 * dùng** (Requirement 15.6) là một *seam* được ráp ở tầng `:app` (task 22.1): tầng đó tiêm một
 * notifier và, nếu muốn, một `PermissionManager` để chặn sớm và phát thông báo "thiếu quyền".
 * Worker này cố tình **không** tự xin quyền (không thể hiện UI từ nền) — nó chạy và báo cáo.
 *
 * ### Hạn chế thực thi nền của thiết bị (Requirement 15.10) — seam tài liệu hóa
 * Việc phát hiện thiết bị hạn chế nền và hiển thị **hướng dẫn xin miễn trừ** là việc của UI/`:app`
 * (màn hình Settings/Permissions, task 21.2). Worker chỉ đơn thuần chạy khi được WorkManager kích
 * hoạt; nếu hệ thống trì hoãn do hạn chế nền, WorkManager sẽ chạy lại khi đủ điều kiện.
 *
 * ### Cấu hình WorkManager + HiltWorkerFactory (task 22.1) — ghi chú
 * Để `@HiltWorker` hoạt động, `:app` SHALL cấu hình WorkManager dùng [androidx.hilt.work.HiltWorkerFactory]
 * (thường qua `Configuration.Provider` trên lớp `Application` `@HiltAndroidApp`, bật on-demand
 * initialization). Nếu không, WorkManager mặc định không biết cách dựng worker có `@AssistedInject`.
 *
 * @property runExportJobUseCase use case điều phối pipeline, dùng chung với Quick_Export.
 * @property automationRepository nạp/cập nhật [Automation] (cập nhật mốc cửa sổ nối tiếp, Req 9.8).
 * @property dateRangeResolver phân giải Date_Range cửa sổ nối tiếp (Requirements 9.8, 9.9).
 * @property destinationConfigResolver dựng [DestinationConfig] từ tham chiếu của Automation.
 * @property syncLogRepository ghi mục Sync_Log cho các sự kiện do **worker** phát sinh (bỏ qua do
 *   trùng lặp — Req 15.5; vượt số lần thử lại — Req 15.8); job thường do use case tự ghi.
 */
@HiltWorker
class ExportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val runExportJobUseCase: RunExportJobUseCase,
    private val automationRepository: AutomationRepository,
    private val dateRangeResolver: DateRangeResolver,
    private val destinationConfigResolver: DestinationConfigResolver,
    private val syncLogRepository: SyncLogRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val automationId = inputData.getString(KEY_AUTOMATION_ID)
            // Thiếu automationId ⇒ worker bị enqueue sai; thử lại cũng vô ích.
            ?: return Result.failure()

        val automation = automationRepository.findById(automationId)
            // Automation đã bị xóa giữa chừng (Requirement 14.9) ⇒ không còn gì để chạy.
            ?: return Result.success()

        // Automation đang tắt ⇒ không chạy theo lịch (Requirement 14.2 cho phép bật/tắt).
        if (!automation.enabled) {
            return Result.success()
        }

        // (Req 15.5) Khử trùng trong tiến trình: nếu cùng Automation đang chạy, bỏ qua lần này.
        // Lớp khử trùng chính là unique periodic work của WorkManager; đây là tuyến phát hiện thứ
        // hai trong cùng tiến trình để có thể ghi rõ "bị bỏ qua do trùng lặp" vào Sync_Log.
        if (!runningAutomations.add(automationId)) {
            logSkippedDuplicate(automation)
            return Result.success()
        }

        return try {
            runScheduledExport(automation, automationId)
        } finally {
            runningAutomations.remove(automationId)
        }
    }

    /**
     * Dựng cấu hình và chạy pipeline cho một lần xuất theo lịch, rồi ánh xạ kết quả sang [Result].
     */
    private suspend fun runScheduledExport(automation: Automation, automationId: String): Result {
        // (Req 9.9) Bảo đảm có mốc kích hoạt cho cửa sổ nối tiếp khi lần đầu chạy.
        val anchored = ensureActivationAnchor(automation)

        // (Req 9.8, 9.9) Cửa sổ nối tiếp: start = lần thành công gần nhất hoặc mốc kích hoạt đầu;
        // end = thời điểm chạy hiện tại.
        val dateRange = dateRangeResolver.automationRange(anchored)

        // (Req 22.9) Dựng DestinationConfig đầy đủ từ tham chiếu đã lưu; null nghĩa là cấu hình
        // không còn tồn tại — RunExportJobUseCase sẽ coi như chưa cấu hình Destination và thất bại
        // mà không phát sinh egress (Requirement 22.4).
        val destinationConfig: DestinationConfig? = destinationConfigResolver.resolve(
            destinationType = anchored.destinationType,
            destinationConfigRef = anchored.destinationConfigRef,
        )

        val config = ExportJobConfig(
            selection = anchored.selection,
            format = anchored.exportFormat,
            period = anchored.aggregationPeriod,
            dateRange = dateRange,
            destinationType = anchored.destinationType,
            destinationConfig = destinationConfig,
            automationId = automationId,
        )

        // RunExportJobUseCase ghi đúng một mục Sync_Log cho lần chạy này (Requirements 23.1, 23.2).
        val report = runExportJobUseCase.run(config)

        return mapToResult(
            status = report.status,
            automation = anchored,
            windowEndUtc = dateRange.endUtc,
            destinationResolved = destinationConfig != null,
        )
    }

    /**
     * Ánh xạ [ExportStatus] của job sang [Result] của WorkManager (Requirements 15.7, 15.8, 9.8).
     *
     * - [ExportStatus.SUCCESS] / [ExportStatus.EMPTY] ⇒ [Result.success]; đồng thời đẩy mốc cửa sổ
     *   nối tiếp [Automation.lastSuccessfulEndUtc] = [windowEndUtc] để lần sau đọc tiếp tục
     *   (Requirement 9.8). EMPTY vẫn là một lần chạy hoàn tất thành công (không có dữ liệu trong
     *   cửa sổ), nên cũng đẩy mốc để tránh đọc lại cùng cửa sổ.
     * - [ExportStatus.FAILURE] ⇒ quyết định thử lại trong [decideFailure].
     * - [ExportStatus.SKIPPED] ⇒ [Result.success] (đã bị bỏ qua có chủ đích).
     * - [ExportStatus.CANCELLED] ⇒ [Result.failure] (đã bị hủy; không thử lại).
     *
     * ### Ghi chú về tính đủ-điều-kiện-thử-lại
     * `JobReport` hiện không phơi bày cờ `retryEligible` (cờ này chỉ tồn tại ở
     * [com.healthautoexport.domain.port.DestinationResult.Failure] bên trong use case). Vì vậy
     * worker áp dụng heuristic an toàn: một [ExportStatus.FAILURE] **có** Destination đã phân giải
     * được coi là **lỗi tạm thời (transient)** và đủ điều kiện thử lại (Requirement 15.7); một
     * FAILURE khi **không** phân giải được Destination được coi là **không thể thử lại** (thử lại
     * không làm cấu hình xuất hiện) ⇒ [Result.failure] ngay.
     */
    private suspend fun mapToResult(
        status: ExportStatus,
        automation: Automation,
        windowEndUtc: Instant,
        destinationResolved: Boolean,
    ): Result = when (status) {
        ExportStatus.SUCCESS, ExportStatus.EMPTY -> {
            // (Req 9.8) Đẩy mốc cửa sổ nối tiếp về cuối cửa sổ vừa xuất.
            automationRepository.upsert(automation.copy(lastSuccessfulEndUtc = windowEndUtc))
            Result.success()
        }

        ExportStatus.FAILURE -> decideFailure(automation, retryEligible = destinationResolved)

        ExportStatus.SKIPPED -> Result.success()

        ExportStatus.CANCELLED -> Result.failure()
    }

    /**
     * Quyết định [Result.retry] hay [Result.failure] cho một lần chạy thất bại (Requirements 15.7,
     * 15.8).
     *
     * WorkManager tự áp exponential backoff (bắt đầu 30s) giữa các lần thử nhờ `setBackoffCriteria`
     * mà [WorkManagerScheduler] cấu hình. Worker giới hạn **tổng số lần thử** ở
     * [BackoffPolicy.MAX_ATTEMPTS] = 5 bằng cách so [getRunAttemptCount] (0-based): khi còn lượt
     * thử và lỗi đủ điều kiện thử lại ⇒ [Result.retry]; ngược lại ⇒ ghi "đã vượt số lần thử lại"
     * (Requirement 15.8) và [Result.failure].
     *
     * @param automation Automation đang chạy (để ghi Sync_Log khi hết lượt).
     * @param retryEligible lỗi có thuộc loại tạm thời/đủ điều kiện thử lại hay không.
     */
    private suspend fun decideFailure(automation: Automation, retryEligible: Boolean): Result {
        // runAttemptCount 0-based: lần đầu = 0 ⇒ số thứ tự lần thử (1-based) = runAttemptCount + 1.
        val attemptNumber = runAttemptCount + 1
        return if (retryEligible && attemptNumber < BackoffPolicy.MAX_ATTEMPTS) {
            // (Req 15.7) Còn lượt: nhờ WorkManager thử lại với backoff theo cấp số nhân.
            Result.retry()
        } else {
            // (Req 15.8) Hết lượt thử lại (hoặc không thể thử lại): dừng và ghi nhận.
            if (retryEligible) {
                logRetriesExhausted(automation)
            }
            Result.failure()
        }
    }

    /**
     * Bảo đảm [Automation.firstActivatedAtUtc] tồn tại trước khi phân giải cửa sổ nối tiếp
     * (Requirement 9.9).
     *
     * Thông thường mốc kích hoạt được đặt khi người dùng bật Automation. Nếu vì lý do nào đó cả
     * [Automation.firstActivatedAtUtc] lẫn [Automation.lastSuccessfulEndUtc] đều `null` tại lần
     * chạy đầu, ta coi **chính lần chạy theo lịch này** là thời điểm kích hoạt: ghi mốc rồi dùng
     * nó, để [DateRangeResolver.automationRange] có mốc bắt đầu hợp lệ (tránh ném ngoại lệ).
     */
    private suspend fun ensureActivationAnchor(automation: Automation): Automation {
        if (automation.firstActivatedAtUtc != null || automation.lastSuccessfulEndUtc != null) {
            return automation
        }
        val anchored = automation.copy(firstActivatedAtUtc = Instant.now())
        automationRepository.upsert(anchored)
        return anchored
    }

    /** Ghi mục Sync_Log "bị bỏ qua do trùng lặp" cho lần chạy chồng lấn (Requirement 15.5). */
    private suspend fun logSkippedDuplicate(automation: Automation) {
        appendLog(
            automation = automation,
            status = ExportStatus.SKIPPED,
            message = "Lần chạy theo lịch bị bỏ qua do trùng lặp: một Export_Job của Automation " +
                "'${automation.id}' vẫn đang chạy (Requirement 15.5).",
        )
    }

    /** Ghi mục Sync_Log "đã vượt số lần thử lại cho phép" khi hết lượt (Requirement 15.8). */
    private suspend fun logRetriesExhausted(automation: Automation) {
        appendLog(
            automation = automation,
            status = ExportStatus.FAILURE,
            message = "Export_Job theo lịch đã vượt số lần thử lại cho phép " +
                "(${BackoffPolicy.MAX_ATTEMPTS} lần) và bị dừng (Requirement 15.8).",
        )
    }

    /**
     * Ghi một mục Sync_Log do worker phát sinh (không thuộc luồng ghi của use case). Mục chỉ chứa
     * metadata, không chứa dữ liệu thô (Requirement 23.4).
     */
    private suspend fun appendLog(automation: Automation, status: ExportStatus, message: String) {
        val now = Instant.now()
        val entry = SyncLogEntry(
            id = UUID.randomUUID().toString(),
            startUtc = now,
            completionUtc = now,
            automationId = automation.id,
            exportFormat = automation.exportFormat,
            destinationType = automation.destinationType,
            status = status,
            message = message,
        )
        syncLogRepository.append(entry, ExportJobConfig.DEFAULT_MAX_SYNC_LOG_ENTRIES)
    }

    companion object {
        /** Khóa input data mang định danh Automation cần chạy (đặt bởi [WorkManagerScheduler]). */
        const val KEY_AUTOMATION_ID: String = "automation_id"

        /**
         * Tập định danh Automation đang chạy trong tiến trình hiện tại, dùng để phát hiện lần chạy
         * chồng lấn (Requirement 15.5). Process-wide vì các worker chạy trong cùng tiến trình App;
         * lớp khử trùng đáng tin cậy hơn vẫn là unique periodic work của WorkManager.
         */
        private val runningAutomations: MutableSet<String> = ConcurrentHashMap.newKeySet()
    }
}
