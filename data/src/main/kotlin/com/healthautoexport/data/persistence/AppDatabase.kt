package com.healthautoexport.data.persistence

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Cơ sở dữ liệu Room của App, lưu Automation và Sync_Log (Requirements 14.5, 23).
 *
 * Chỉ chứa dữ liệu cấu trúc trên thiết bị; **không** lưu credential (nằm ở
 * [com.healthautoexport.domain.port.CredentialStore], Requirement 22.9) và **không** lưu giá trị
 * sức khỏe thô trong Sync_Log (Requirement 23.4).
 *
 * [RoomConverters] được đăng ký để (de)serialize các enum domain thành cột TEXT.
 */
@Database(
    entities = [AutomationEntity::class, SyncLogEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {

    /** DAO cho bảng `automations`. */
    abstract fun automationDao(): AutomationDao

    /** DAO cho bảng `sync_log`. */
    abstract fun syncLogDao(): SyncLogDao

    companion object {
        /** Tên file cơ sở dữ liệu trên thiết bị. */
        const val DATABASE_NAME: String = "health_auto_export.db"
    }
}
