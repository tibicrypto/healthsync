package com.healthautoexport.data.scheduler

import androidx.work.BackoffPolicy as WorkBackoffPolicy
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.healthautoexport.domain.logic.BackoffPolicy
import com.healthautoexport.domain.logic.ScheduleIntervalValidator
import com.healthautoexport.domain.model.Automation
import com.healthautoexport.domain.port.Scheduler
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hiện thực [Scheduler] bằng WorkManager (Requirements 15.1–15.3, 15.5, 15.7, 14.9).
 *
 * Mỗi [Automation] được lên lịch như một [PeriodicWorkRequest] với:
 * - **Khoảng lặp** = [Automation.scheduleIntervalMinutes] phút, xác thực qua
 *   [ScheduleIntervalValidator] thuộc `[15 phút, 30 ngày]` (Requirements 15.3, 15.4). Khoảng ngoài
 *   phạm vi bị **từ chối** (không lên lịch) — xem [schedule].
 * - **Exponential backoff** bắt đầu [BackoffPolicy.BASE_DELAY] = 30s (Requirement 15.7); số lần thử
 *   tối đa ([BackoffPolicy.MAX_ATTEMPTS] = 5) do [ExportWorker] tự kiểm soát bằng `runAttemptCount`
 *   (Requirement 15.8), vì WorkManager không có giới hạn số lần thử dựng sẵn.
 * - **Input data** mang [ExportWorker.KEY_AUTOMATION_ID] để worker biết Automation nào cần chạy.
 * - **Unique periodic work** với tên = [Automation.id] và [ExistingPeriodicWorkPolicy.UPDATE]:
 *   bảo đảm mỗi Automation chỉ có **một** chuỗi công việc; một lần chạy theo lịch chồng lấn với
 *   lần đang chạy của cùng Automation được WorkManager khử trùng (Requirements 15.1, 15.5).
 *
 * Khử trùng chạy chồng lấn (Requirement 15.5) dựa trên cơ chế unique periodic work của
 * WorkManager: hệ thống không khởi tạo hai phiên cùng tên chạy song song. Việc ghi mục
 * "bị bỏ qua do trùng lặp" vào Sync_Log do [ExportWorker] đảm nhiệm khi phát hiện trùng lặp.
 *
 * @property workManager thể hiện [WorkManager] dùng để enqueue/cancel; tiêm qua constructor để
 *   kiểm thử bằng `WorkManagerTestInitHelper` (task 20.2). Lớp ráp nối (task 22.1) cung cấp
 *   `WorkManager.getInstance(context)`.
 */
@Singleton
class WorkManagerScheduler @Inject constructor(
    private val workManager: WorkManager,
) : Scheduler {

    /**
     * Lên lịch (hoặc cập nhật lịch) cho [automation] (Requirements 15.1–15.3, 15.7).
     *
     * Nếu [Automation.scheduleIntervalMinutes] **ngoài** phạm vi cho phép `[15, 43200]` phút
     * (Requirements 15.3, 15.4), phương thức **không** lên lịch và trả về ngay. Đây là tuyến phòng
     * thủ cuối: việc xác thực và giữ giá trị hợp lệ trước đó vốn do `ConfigureAutomationUseCase`/UI
     * thực hiện trước khi lưu Automation; ở đây ta từ chối thay vì để WorkManager ép giá trị
     * (`PeriodicWorkRequest` tự kẹp khoảng < 15 phút về tối thiểu 15 phút), nhằm tránh chạy với một
     * lịch khác lịch người dùng yêu cầu.
     *
     * @param automation Automation cần lên lịch.
     */
    override fun schedule(automation: Automation) {
        // (Req 15.3, 15.4) Từ chối khoảng lặp ngoài phạm vi: không lên lịch.
        if (!ScheduleIntervalValidator.isValid(automation.scheduleIntervalMinutes)) {
            return
        }

        val inputData: Data = Data.Builder()
            .putString(ExportWorker.KEY_AUTOMATION_ID, automation.id)
            .build()

        val request = PeriodicWorkRequest.Builder(
            ExportWorker::class.java,
            automation.scheduleIntervalMinutes,
            TimeUnit.MINUTES,
        )
            .setInputData(inputData)
            // (Req 15.7) Exponential backoff bắt đầu 30s; trần do hệ thống áp ở mức an toàn và
            // BackoffPolicy domain giới hạn 30 phút khi worker tự tính.
            .setBackoffCriteria(
                WorkBackoffPolicy.EXPONENTIAL,
                BackoffPolicy.BASE_DELAY.seconds,
                TimeUnit.SECONDS,
            )
            .addTag(TAG_EXPORT)
            .build()

        // (Req 15.1, 15.5) Unique periodic work theo automationId: một chuỗi công việc/Automation;
        // UPDATE để chỉnh lịch khi người dùng sửa khoảng lặp mà vẫn giữ tính duy nhất.
        workManager.enqueueUniquePeriodicWork(
            automation.id,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /**
     * Hủy lịch của Automation theo [automationId] (Requirement 14.9).
     *
     * Hủy theo unique work name đã dùng khi lên lịch ([Automation.id]); mọi lần chạy theo lịch
     * tương lai bị gỡ. Lần chạy đang diễn ra (nếu có) sẽ bị WorkManager báo dừng — `RunExportJobUseCase`
     * bảo đảm không để lại dữ liệu một phần khi bị hủy (Requirement 13.6).
     *
     * @param automationId định danh Automation cần hủy lịch.
     */
    override fun cancel(automationId: String) {
        workManager.cancelUniqueWork(automationId)
    }

    private companion object {
        /** Tag chung cho mọi công việc xuất theo lịch, tiện cho quan sát/hủy hàng loạt nếu cần. */
        const val TAG_EXPORT = "health-auto-export-scheduled"
    }
}
