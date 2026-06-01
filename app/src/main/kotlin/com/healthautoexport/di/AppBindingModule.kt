package com.healthautoexport.di

import com.healthautoexport.data.scheduler.DestinationConfigResolver
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module của `:app` bind các Port mà **hiện thực sống ở `:app`** (tầng Presentation).
 *
 * Hiện chỉ có [DestinationConfigResolver] → [DestinationConfigStoreResolver]: bộ phân giải cấu hình
 * Destination cho `ExportWorker` đọc từ kho cấu hình trong bộ nhớ của tầng UI (task 21). Xem
 * [DestinationConfigStoreResolver] để biết đơn giản hóa và lộ trình thay bằng persistence bền vững.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindingModule {

    /** Bind [DestinationConfigResolver] tới hiện thực đọc [DestinationConfigStoreResolver]. */
    @Binds
    @Singleton
    abstract fun bindDestinationConfigResolver(
        impl: DestinationConfigStoreResolver,
    ): DestinationConfigResolver
}
