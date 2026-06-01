package com.healthautoexport

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Lớp [Application] gốc của App, là **điểm vào Hilt** (`@HiltAndroidApp`) và là **nhà cung cấp cấu
 * hình WorkManager** (`Configuration.Provider`) — bước ráp nối cuối cùng của App (task 22.1).
 *
 * ### Hilt
 * `@HiltAndroidApp` kích hoạt việc sinh đồ thị phụ thuộc cấp ứng dụng; mọi `@AndroidEntryPoint`
 * (vd [MainActivity]), `@HiltViewModel` và `@HiltWorker` được dựng từ đồ thị này.
 *
 * ### WorkManager on-demand + HiltWorkerFactory (Requirement 15.1)
 * `ExportWorker` ở `:data` dùng `@HiltWorker` với `@AssistedInject`, nên WorkManager **không** tự
 * dựng được bằng factory mặc định. App khắc phục bằng cách:
 * 1. Triển khai [Configuration.Provider] và trả [workManagerConfiguration] dùng [HiltWorkerFactory]
 *    (được Hilt tiêm), để WorkManager biết cách dựng worker có phụ thuộc.
 * 2. **Khởi tạo theo yêu cầu (on-demand)**: trình khởi tạo mặc định `androidx.startup` của
 *    WorkManager bị **gỡ** trong [AndroidManifest.xml] (node `<provider>` với
 *    `tools:node="remove"` cho `androidx.work.WorkManagerInitializer`), nhờ vậy WorkManager dùng
 *    cấu hình từ lớp này thay vì cấu hình mặc định. Đây là mẫu chính thức để tích hợp Hilt + Work.
 *
 * Lưu ý: kể từ WorkManager 2.9, `Configuration.Provider` phơi bày một **thuộc tính**
 * `workManagerConfiguration` (không còn là hàm `getWorkManagerConfiguration()`), nên ở đây ta
 * override một `val`.
 *
 * @property workerFactory factory do Hilt cung cấp, biết cách dựng `@HiltWorker` (ExportWorker).
 */
@HiltAndroidApp
class HealthExportApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /**
     * Cấu hình WorkManager dùng [HiltWorkerFactory] để dựng `@HiltWorker` (Requirement 15.1).
     * WorkManager đọc thuộc tính này khi khởi tạo on-demand (sau khi trình khởi tạo mặc định bị
     * gỡ trong manifest).
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
