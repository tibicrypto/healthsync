package com.healthautoexport.data.di

import android.content.Context
import com.healthautoexport.data.credential.EncryptedCredentialStore
import com.healthautoexport.data.persistence.AutomationRepositoryImpl
import com.healthautoexport.data.persistence.SyncLogRepositoryImpl
import com.healthautoexport.data.scheduler.WorkManagerScheduler
import com.healthautoexport.data.source.DataReader
import com.healthautoexport.domain.port.AutomationRepository
import com.healthautoexport.domain.port.CredentialStore
import com.healthautoexport.domain.port.Scheduler
import com.healthautoexport.domain.port.SourceDataReader
import com.healthautoexport.domain.port.SyncLogRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module của `:data` **bind các Port thuần domain tới hiện thực `:data`** có hàm dựng
 * `@Inject` (Requirements 14.5, 22.9, 23, 15).
 *
 * Đặt các binding này ở `:data` (thay vì `:app`) để mọi phụ thuộc thư viện của hiện thực
 * (Room, `androidx.security`, OkHttp...) được phân giải ngay trên classpath của `:data`, giữ `:app`
 * mỏng và chỉ phụ thuộc Port.
 *
 * - [CredentialStore] → [EncryptedCredentialStore] (AES-256-GCM + Android Keystore, Req 22.9).
 * - [AutomationRepository] → [AutomationRepositoryImpl] (Room, Req 14.5, 14.7).
 * - [SyncLogRepository] → [SyncLogRepositoryImpl] (Room + ordering/eviction thuần, Req 23).
 * - [Scheduler] → [WorkManagerScheduler] (WorkManager periodic work, Req 15).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindingModule {

    /** Bind [AutomationRepository] tới hiện thực Room (Requirements 14.5, 14.7). */
    @Binds
    @Singleton
    abstract fun bindAutomationRepository(impl: AutomationRepositoryImpl): AutomationRepository

    /** Bind [SyncLogRepository] tới hiện thực Room (Requirement 23). */
    @Binds
    @Singleton
    abstract fun bindSyncLogRepository(impl: SyncLogRepositoryImpl): SyncLogRepository

    /** Bind [Scheduler] tới hiện thực WorkManager (Requirement 15). */
    @Binds
    @Singleton
    abstract fun bindScheduler(impl: WorkManagerScheduler): Scheduler

    companion object {

        /**
         * Cung cấp [CredentialStore] mã hóa (Requirement 22.9).
         *
         * Dùng `@Provides` (không `@Binds`) vì hàm dựng `@Inject` của [EncryptedCredentialStore]
         * nhận một `Context` **không có qualifier**, mà `SingletonComponent` chỉ cung cấp
         * `@ApplicationContext Context`. Ở đây ta tiêm `@ApplicationContext` rồi gọi hàm dựng công
         * khai để né lỗi "Context cannot be provided".
         *
         * @param context application context để dựng master key + EncryptedSharedPreferences.
         */
        @Provides
        @Singleton
        fun provideCredentialStore(@ApplicationContext context: Context): CredentialStore =
            EncryptedCredentialStore(context.applicationContext)

        /**
         * Cung cấp [SourceDataReader] (port đọc đa nguồn thuần domain) bằng cách **ủy thác** tới
         * [DataReader] ở `:data` (Requirements 3.3–3.7). `DataReader.read` có cùng chữ ký với
         * [SourceDataReader.read], nên ta gói nó trong một lambda fun-interface; nhờ vậy
         * `RunExportJobUseCase` (thuần domain) chỉ phụ thuộc Port mà vẫn dùng được bộ đọc thật.
         *
         * @param dataReader bộ điều phối đọc đa nguồn (Hilt dựng từ `@Inject` constructor).
         */
        @Provides
        @Singleton
        fun provideSourceDataReader(dataReader: DataReader): SourceDataReader =
            SourceDataReader { selection, range -> dataReader.read(selection, range) }
    }
}
