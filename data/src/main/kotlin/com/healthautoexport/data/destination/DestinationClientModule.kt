package com.healthautoexport.data.destination

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module cung cấp hiện thực **mặc định no-op** cho các cổng client của Destination đám mây/MQTT.
 *
 * Các đích Google Drive, Dropbox và MQTT đứng sau interface client ([DriveClient], [DropboxClient],
 * [MqttClient]) nên module này bind bản NoOp để app compile/verify mà **không** cần SDK độc quyền
 * (Google Drive API, Dropbox SDK) hay thư viện MQTT/broker thật. Khi tích hợp thật, chỉ cần thay
 * các hàm `@Provides` này bằng hiện thực tương ứng (hoặc `@Binds` sang lớp thật).
 */
@Module
@InstallIn(SingletonComponent::class)
object DestinationClientModule {

    /** Mặc định [NoOpDriveClient] (chưa ủy quyền) cho [GoogleDriveDestination] (Requirement 17). */
    @Provides
    @Singleton
    fun provideDriveClient(): DriveClient = NoOpDriveClient()

    /** Mặc định [NoOpDropboxClient] (chưa ủy quyền) cho [DropboxDestination] (Requirement 18). */
    @Provides
    @Singleton
    fun provideDropboxClient(): DropboxClient = NoOpDropboxClient()

    /** Mặc định [NoOpMqttClient] (không kết nối) cho [MqttDestination] (Requirement 19). */
    @Provides
    @Singleton
    fun provideMqttClient(): MqttClient = NoOpMqttClient()
}
