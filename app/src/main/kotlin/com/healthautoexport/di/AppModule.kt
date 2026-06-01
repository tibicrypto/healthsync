package com.healthautoexport.di

import com.healthautoexport.domain.model.DataSourceId
import com.healthautoexport.domain.model.DestinationType
import com.healthautoexport.domain.model.MetricCatalog
import com.healthautoexport.domain.model.SourcePriority
import com.healthautoexport.domain.pipeline.Aggregator
import com.healthautoexport.domain.pipeline.DataMerger
import com.healthautoexport.domain.pipeline.DateRangeResolver
import com.healthautoexport.domain.pipeline.ZoneIdProvider
import com.healthautoexport.domain.port.AutomationRepository
import com.healthautoexport.domain.port.CredentialStore
import com.healthautoexport.domain.port.Destination
import com.healthautoexport.domain.port.ExportSerializer
import com.healthautoexport.domain.port.PermissionManager
import com.healthautoexport.domain.port.Scheduler
import com.healthautoexport.domain.port.SourceDataReader
import com.healthautoexport.domain.port.SyncLogRepository
import com.healthautoexport.domain.usecase.AutomationRunCanceller
import com.healthautoexport.domain.usecase.ConfigureAutomationUseCase
import com.healthautoexport.domain.usecase.DataWipeUseCase
import com.healthautoexport.domain.usecase.DeepLinkHandler
import com.healthautoexport.domain.usecase.RunExportJobUseCase
import com.healthautoexport.serialization.ExportSerializerAdapter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.ZoneId
import javax.inject.Singleton

/**
 * Hilt module ráp nối cấp ứng dụng (task 22.1): cung cấp **pipeline thuần** (`DataMerger`,
 * `Aggregator`), các seam thời gian/múi giờ (`Clock`, `ZoneIdProvider`), bộ phân giải Date_Range,
 * adapter tuần tự hóa, và các **use case** điều phối — tất cả dựng từ các Port đã được bind ở
 * `:data` (Requirements 7, 8, 9, 10–12, 13, 14, 22).
 *
 * Các binding tầng dữ liệu (repositories, CredentialStore, Scheduler, SourceDataReader, các bản đồ
 * Data_Source/Permission_Manager/Destination) do các module của `:data`
 * ([com.healthautoexport.data.di.DataInfraModule],
 * [com.healthautoexport.data.di.DataBindingModule],
 * [com.healthautoexport.data.di.DataDestinationModule]) cung cấp; module này chỉ **tiêu thụ** chúng
 * qua kiểu Port thuần domain để giữ `:app` mỏng.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // -------------------------------------------------------------------------------------------
    // Seam thời gian & múi giờ
    // -------------------------------------------------------------------------------------------

    /** [Clock] hệ thống theo UTC, dùng cho dấu thời gian job/Sync_Log (Requirements 9.x, 23.1). */
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()

    /**
     * [ZoneIdProvider] đọc múi giờ thiết bị để căn ranh giới lịch khi tổng hợp (Requirement 8.3).
     * Đặt sau port để `Aggregator` vẫn thuần (pure) và kiểm thử xác định được.
     */
    @Provides
    @Singleton
    fun provideZoneIdProvider(): ZoneIdProvider = ZoneIdProvider { ZoneId.systemDefault() }

    /** Bộ phân giải Date_Range thuần (Requirement 9), tiêm [Clock] để xác định. */
    @Provides
    @Singleton
    fun provideDateRangeResolver(clock: Clock): DateRangeResolver = DateRangeResolver(clock)

    // -------------------------------------------------------------------------------------------
    // Pipeline thuần (Merge → Aggregate)
    // -------------------------------------------------------------------------------------------

    /**
     * [DataMerger] đã cấu hình bảng dung sai mặc định từ [MetricCatalog] (Requirement 7.2).
     *
     * ### Đơn giản hóa ưu tiên nguồn ở task 22.1
     * `SourcePriority` do người dùng cấu hình được lưu trong [com.healthautoexport.domain.port.SourceToggleStore]
     * và đọc qua hàm **suspend** `sourcePriority()`, nên không lấy được trong một `@Provides` đồng
     * bộ mà không chặn luồng. Ở bước ráp nối này ta cung cấp [DataMerger] với một [SourcePriority]
     * **rỗng** (không nguồn nào được gán thứ hạng): khi đó mọi nguồn có cùng mức ưu tiên thấp nhất
     * và việc giải quyết trùng lặp **chỉ** dựa trên tie-break theo [DataSourceId.id] tăng dần theo
     * bảng chữ cái (Requirement 7.5) — `health_connect` thắng `huawei_health_kit`. Đây là hành vi
     * xác định, an toàn và đúng đặc tả khi người dùng chưa đặt ưu tiên.
     *
     * Để tôn trọng ưu tiên người dùng đặt (Requirement 7.4), bước nâng cao sẽ dựng `DataMerger`
     * theo từng job từ `SourcePriority` đã đọc (vd trong `RunExportJobUseCase`/`ExportWorker` nơi
     * có ngữ cảnh coroutine để gọi hàm suspend), thay cho singleton rỗng này.
     */
    @Provides
    @Singleton
    fun provideDataMerger(): DataMerger =
        DataMerger(
            tolerances = MetricCatalog.defaultToleranceTable(),
            priority = SourcePriority(emptyMap()),
        )

    /** [Aggregator] thuần theo Aggregation_Period (Requirement 8). */
    @Provides
    @Singleton
    fun provideAggregator(): Aggregator = Aggregator()

    // -------------------------------------------------------------------------------------------
    // Serializer adapter (:serialization)
    // -------------------------------------------------------------------------------------------

    /**
     * [ExportSerializer] hiện thực bởi [ExportSerializerAdapter] ở `:serialization`, chọn bộ tuần
     * tự hóa theo [com.healthautoexport.domain.model.ExportFormat] (Requirements 10, 11, 12).
     */
    @Provides
    @Singleton
    fun provideExportSerializer(): ExportSerializer = ExportSerializerAdapter()

    // -------------------------------------------------------------------------------------------
    // Use cases
    // -------------------------------------------------------------------------------------------

    /**
     * [RunExportJobUseCase] — trung tâm pipeline Export_Job (Requirement 13). Ráp các Port đã bind
     * với pipeline thuần; dùng chung cho cả Quick_Export và xuất theo lịch.
     */
    @Provides
    @Singleton
    fun provideRunExportJobUseCase(
        sourceDataReader: SourceDataReader,
        permissionManagers: Map<DataSourceId, @JvmSuppressWildcards PermissionManager>,
        dataMerger: DataMerger,
        aggregator: Aggregator,
        zoneIdProvider: ZoneIdProvider,
        exportSerializer: ExportSerializer,
        destinations: Map<DestinationType, @JvmSuppressWildcards Destination>,
        syncLogRepository: SyncLogRepository,
        clock: Clock,
    ): RunExportJobUseCase = RunExportJobUseCase(
        sourceDataReader = sourceDataReader,
        permissionManagers = permissionManagers,
        dataMerger = dataMerger,
        aggregator = aggregator,
        zoneIdProvider = zoneIdProvider,
        exportSerializer = exportSerializer,
        destinations = destinations,
        syncLogRepository = syncLogRepository,
        clock = clock,
    )

    /**
     * [ConfigureAutomationUseCase] CRUD Automation (Requirement 14). Khi xóa một Automation đang
     * chạy giữa chừng, phát tín hiệu hủy qua [Scheduler.cancel] để gỡ lịch và dừng lần chạy
     * (Requirement 14.9); `RunExportJobUseCase` bảo đảm không để lại partial khi bị hủy (Req 13.6).
     */
    @Provides
    @Singleton
    fun provideConfigureAutomationUseCase(
        automationRepository: AutomationRepository,
        scheduler: Scheduler,
    ): ConfigureAutomationUseCase =
        ConfigureAutomationUseCase(
            repository = automationRepository,
            runCanceller = AutomationRunCanceller { automationId -> scheduler.cancel(automationId) },
        )

    /** [DeepLinkHandler] phân tích deep link cấu hình Automation (Requirements 14.6, 14.8). */
    @Provides
    @Singleton
    fun provideDeepLinkHandler(): DeepLinkHandler = DeepLinkHandler()

    /** [DataWipeUseCase] xóa Automation + credential + Sync_Log (Requirements 22.6–22.8). */
    @Provides
    @Singleton
    fun provideDataWipeUseCase(
        automationRepository: AutomationRepository,
        credentialStore: CredentialStore,
        syncLogRepository: SyncLogRepository,
    ): DataWipeUseCase = DataWipeUseCase(
        automationRepository = automationRepository,
        credentialStore = credentialStore,
        syncLogRepository = syncLogRepository,
    )
}
