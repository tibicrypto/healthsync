package com.healthautoexport.data.di

import android.content.Context
import com.healthautoexport.data.destination.DriveClient
import com.healthautoexport.data.destination.DropboxClient
import com.healthautoexport.data.destination.DropboxDestination
import com.healthautoexport.data.destination.GoogleDriveDestination
import com.healthautoexport.data.destination.HomeAssistantDestination
import com.healthautoexport.data.destination.LocalStorageDestination
import com.healthautoexport.data.destination.MqttClient
import com.healthautoexport.data.destination.MqttDestination
import com.healthautoexport.data.destination.RestApiDestination
import com.healthautoexport.domain.model.DestinationType
import com.healthautoexport.domain.port.CredentialStore
import com.healthautoexport.domain.port.Destination
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module của `:data` cung cấp **bản đồ sáu Destination** theo [DestinationType] (Requirements
 * 16–21), dùng cho `RunExportJobUseCase.destinations`.
 *
 * ### Vì sao bản đồ này dựng ở `:data` (không phải `:app`)
 * Một số Destination dựng đối tượng phụ thuộc thư viện chỉ khai báo `implementation` ở `:data`
 * (vd OkHttp cho [RestApiDestination]/[HomeAssistantDestination]). Các thư viện đó **không** nằm
 * trên classpath compile của `:app`, nên nếu dựng các lớp này ở `:app` thì trình biên dịch không
 * phân giải được kiểu trong chữ ký hàm dựng (vd `OkHttpClient`). Dựng bản đồ ngay tại `:data` —
 * nơi có đủ các thư viện — tránh hoàn toàn vấn đề này; `:app` chỉ tiêu thụ bản đồ qua kiểu Port
 * thuần domain [Destination].
 *
 * Client đám mây/MQTT ([DriveClient]/[DropboxClient]/[MqttClient]) được
 * [com.healthautoexport.data.destination.DestinationClientModule] bind mặc định no-op để build/test
 * không cần SDK độc quyền hay broker thật; [CredentialStore] do bước ráp nối bind tới
 * `EncryptedCredentialStore` (Requirement 22.9).
 */
@Module
@InstallIn(SingletonComponent::class)
object DataDestinationModule {

    /**
     * Bản đồ đầy đủ sáu [Destination] theo [DestinationType] (Requirements 16–21).
     *
     * `@JvmSuppressWildcards` giữ khóa Dagger là `Map<DestinationType, Destination>` bất biến để
     * khớp với tham số tiêm của `RunExportJobUseCase` ở bước ráp nối.
     *
     * @param driveClient cổng Google Drive (NoOp mặc định) (Requirement 17).
     * @param dropboxClient cổng Dropbox (NoOp mặc định) (Requirement 18).
     * @param mqttClient cổng MQTT (NoOp mặc định) (Requirement 19).
     * @param credentialStore kho credential mã hóa cho MQTT/Home Assistant (Requirement 22.9).
     * @param context application context cho Local Storage (SAF) (Requirement 21).
     */
    @Provides
    @Singleton
    fun provideDestinations(
        driveClient: DriveClient,
        dropboxClient: DropboxClient,
        mqttClient: MqttClient,
        credentialStore: CredentialStore,
        @ApplicationContext context: Context,
    ): Map<DestinationType, @JvmSuppressWildcards Destination> = listOf(
        RestApiDestination(),
        GoogleDriveDestination(driveClient),
        DropboxDestination(dropboxClient),
        MqttDestination(mqttClient, credentialStore),
        HomeAssistantDestination(credentialStore),
        LocalStorageDestination(context.applicationContext),
    ).associateBy { it.type }
}
