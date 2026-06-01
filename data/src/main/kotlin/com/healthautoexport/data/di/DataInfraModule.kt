package com.healthautoexport.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.work.WorkManager
import com.healthautoexport.data.healthconnect.DataStorePermissionStore
import com.healthautoexport.data.healthconnect.GrantedPermissionStore
import com.healthautoexport.data.healthconnect.HealthConnectDataSource
import com.healthautoexport.data.healthconnect.HealthConnectPermissionManager
import com.healthautoexport.data.healthconnect.HealthConnectPermissionRequesterRelay
import com.healthautoexport.data.huawei.DataStoreHuaweiScopeStore
import com.healthautoexport.data.huawei.HuaweiHealthDataSource
import com.healthautoexport.data.huawei.HuaweiPermissionManager
import com.healthautoexport.data.huawei.NoOpHuaweiHealthClient
import com.healthautoexport.data.persistence.AppDatabase
import com.healthautoexport.data.persistence.AutomationDao
import com.healthautoexport.data.persistence.SyncLogDao
import com.healthautoexport.data.source.DataStoreSourceToggleStore
import com.healthautoexport.domain.model.DataSourceId
import com.healthautoexport.domain.port.HealthDataSource
import com.healthautoexport.domain.port.PermissionManager
import com.healthautoexport.domain.port.SourceToggleStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/** Delegate DataStore cấp ứng dụng cho lựa chọn/ưu tiên Data_Source (Requirements 3.1, 3.2, 7.8). */
private val Context.sourcePreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "source_preferences",
)

/** Delegate DataStore cấp ứng dụng cho scope đã ủy quyền của Huawei (Requirements 2.3, 2.6). */
private val Context.huaweiScopeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "huawei_authorized_scopes",
)

/**
 * Hilt module của tầng `:data` cung cấp các singleton **phụ thuộc framework Android** mà `:app`
 * không thể tự dựng (vì `:app` không khai báo `androidx.datastore`/`room`/`security`/health-connect
 * trên classpath compile của nó) cũng như các lớp **`internal` của `:data`** (các adapter Huawei).
 *
 * Module này phải sống trong `:data` vì:
 * - Nó dựng [DataStore]<[Preferences]>, [AppDatabase] (Room), và truy cập gói cung cấp
 *   Health_Connect — các kiểu/khóa Dagger này chỉ hiện diện trên classpath của `:data`.
 * - Nó dựng [HuaweiHealthDataSource]/[HuaweiPermissionManager]/[DataStoreHuaweiScopeStore]/
 *   [NoOpHuaweiHealthClient] — tất cả `internal` nên không tham chiếu được từ `:app`.
 *
 * Các binding ở đây **chỉ phơi bày kiểu Port thuần domain** (vd [HealthDataSource],
 * [PermissionManager], [SourceToggleStore]) hoặc kiểu cụ thể của `:data` đã `public`
 * (vd [AppDatabase], DAO), để `:app` ráp phần còn lại (use case, destination) trên cùng đồ thị.
 *
 * Bản đồ multibinding ([provideHealthDataSources]/[providePermissionManagers]) được dựng **trực
 * tiếp** ở đây thay vì dùng `@IntoMap`, để gom cả nguồn Health_Connect (public) lẫn Huawei
 * (internal) vào một nơi mà không lộ lớp internal ra ngoài module.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataInfraModule {

    // -------------------------------------------------------------------------------------------
    // DataStore (Preferences) — một file dùng chung cho toggle nguồn + ưu tiên nguồn
    // -------------------------------------------------------------------------------------------

    /**
     * [DataStore]<[Preferences]> cho [DataStoreSourceToggleStore]. Đặt `@Named` để phân biệt với
     * các DataStore khác trong đồ thị (Hilt phân giải theo qualifier).
     */
    @Provides
    @Singleton
    @Named(SOURCE_PREFS)
    fun provideSourcePreferences(@ApplicationContext context: Context): DataStore<Preferences> =
        context.applicationContext.sourcePreferencesDataStore

    /** [DataStore]<[Preferences]> cho [DataStoreHuaweiScopeStore] (scope Huawei đã ủy quyền). */
    @Provides
    @Singleton
    @Named(HUAWEI_SCOPE_PREFS)
    fun provideHuaweiScopePreferences(@ApplicationContext context: Context): DataStore<Preferences> =
        context.applicationContext.huaweiScopeDataStore

    // -------------------------------------------------------------------------------------------
    // Room — AppDatabase + DAO
    // -------------------------------------------------------------------------------------------

    /** Cơ sở dữ liệu Room của App (Automation + Sync_Log) (Requirements 14.5, 23). */
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME,
        ).build()

    /** DAO bảng `automations`. */
    @Provides
    fun provideAutomationDao(database: AppDatabase): AutomationDao = database.automationDao()

    /** DAO bảng `sync_log`. */
    @Provides
    fun provideSyncLogDao(database: AppDatabase): SyncLogDao = database.syncLogDao()

    // -------------------------------------------------------------------------------------------
    // SourceToggleStore (DataStore-backed)
    // -------------------------------------------------------------------------------------------

    /** [SourceToggleStore] trên DataStore (Requirements 3.1, 3.2, 7.8). */
    @Provides
    @Singleton
    fun provideSourceToggleStore(
        @Named(SOURCE_PREFS) dataStore: DataStore<Preferences>,
    ): SourceToggleStore = DataStoreSourceToggleStore(dataStore)

    // -------------------------------------------------------------------------------------------
    // Health Connect adapter + permission store/manager
    // -------------------------------------------------------------------------------------------

    /** Kho tập quyền Health_Connect đã cấp trên thiết bị (Requirement 1.3). */
    @Provides
    @Singleton
    fun provideGrantedPermissionStore(
        @ApplicationContext context: Context,
    ): GrantedPermissionStore = DataStorePermissionStore(context.applicationContext)

    /** Bộ điều hợp [HealthDataSource] cho Health_Connect (Requirement 1). */
    @Provides
    @Singleton
    fun provideHealthConnectDataSource(
        @ApplicationContext context: Context,
    ): HealthConnectDataSource = HealthConnectDataSource(context.applicationContext)

    /**
     * [PermissionManager] cho Health_Connect, nối seam launch tương tác qua
     * [HealthConnectPermissionRequesterRelay] để `MainActivity` cung cấp `Activity` (Req 1.2, 1.9).
     */
    @Provides
    @Singleton
    fun provideHealthConnectPermissionManager(
        @ApplicationContext context: Context,
        store: GrantedPermissionStore,
        requesterRelay: HealthConnectPermissionRequesterRelay,
    ): HealthConnectPermissionManager =
        HealthConnectPermissionManager(
            context = context.applicationContext,
            store = store,
            requester = requesterRelay,
        )

    // -------------------------------------------------------------------------------------------
    // Bản đồ Data_Source / Permission_Manager theo DataSourceId
    //
    // QUAN TRỌNG: các adapter Huawei ([HuaweiHealthDataSource]/[HuaweiPermissionManager]/
    // [DataStoreHuaweiScopeStore]/[NoOpHuaweiHealthClient]/[HuaweiHealthClient]) là `internal` của
    // `:data`. Chúng **không** được phơi bày làm kiểu tham số/trả về của `@Provides` (vì khi đó trở
    // thành khóa Dagger mà component sinh trong `:app` phải tham chiếu — `:app` không thấy được kiểu
    // internal của `:data`). Vì vậy ta **dựng Huawei trực tiếp trong thân hàm** dưới đây; chỉ kiểu
    // trả về công khai `Map<DataSourceId, HealthDataSource/PermissionManager>` lộ ra ngoài.
    // -------------------------------------------------------------------------------------------

    /**
     * Bản đồ [HealthDataSource] theo [DataSourceId], gom Health_Connect + Huawei (Requirements 3, 4).
     * `DataReader` và `PermissionsViewModel` tiêm đúng bản đồ này. Adapter Huawei (internal) được
     * dựng nội tuyến với [NoOpHuaweiHealthClient] mặc định (HMS không khả dụng — Requirement 2.1).
     */
    @Provides
    @Singleton
    fun provideHealthDataSources(
        healthConnect: HealthConnectDataSource,
    ): Map<DataSourceId, @JvmSuppressWildcards HealthDataSource> {
        val huawei = HuaweiHealthDataSource(NoOpHuaweiHealthClient())
        return mapOf(
            DataSourceId.HEALTH_CONNECT to healthConnect,
            DataSourceId.HUAWEI_HEALTH_KIT to huawei,
        )
    }

    /**
     * Bản đồ [PermissionManager] theo [DataSourceId] (Requirements 1, 2). `RunExportJobUseCase`
     * (refreshGrants đầu job) và `PermissionsViewModel` tiêm bản đồ này. Manager Huawei (internal)
     * được dựng nội tuyến với [NoOpHuaweiHealthClient] + [DataStoreHuaweiScopeStore] trên DataStore
     * scope Huawei (Requirements 2.3, 2.6).
     *
     * @param huaweiScopePreferences DataStore lưu scope Huawei đã ủy quyền.
     */
    @Provides
    @Singleton
    fun providePermissionManagers(
        healthConnect: HealthConnectPermissionManager,
        @Named(HUAWEI_SCOPE_PREFS) huaweiScopePreferences: DataStore<Preferences>,
    ): Map<DataSourceId, @JvmSuppressWildcards PermissionManager> {
        val huawei = HuaweiPermissionManager(
            client = NoOpHuaweiHealthClient(),
            scopeStore = DataStoreHuaweiScopeStore(huaweiScopePreferences),
        )
        return mapOf(
            DataSourceId.HEALTH_CONNECT to healthConnect,
            DataSourceId.HUAWEI_HEALTH_KIT to huawei,
        )
    }

    // -------------------------------------------------------------------------------------------
    // WorkManager + Scheduler
    // -------------------------------------------------------------------------------------------

    /** Thể hiện [WorkManager] dùng để lên lịch/hủy Automation (Requirement 15). */
    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context.applicationContext)

    const val SOURCE_PREFS: String = "source_preferences"
    const val HUAWEI_SCOPE_PREFS: String = "huawei_authorized_scopes"
}
