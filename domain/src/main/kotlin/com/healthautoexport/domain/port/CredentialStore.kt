package com.healthautoexport.domain.port

/**
 * Port lưu trữ credential của Destination một cách an toàn (Requirement 22.9).
 *
 * Hiện thực bằng `EncryptedSharedPreferences` được bảo vệ bởi Android Keystore (AES-256-GCM) ở
 * tầng dữ liệu (task 16.2). Credential **chỉ** lưu ở đây — không vào Room hay Sync_Log
 * (Requirements 22.9, 23.4). API thao tác theo cặp khóa-giá trị chuỗi; giá trị bí mật không bao
 * giờ được ghi log.
 */
interface CredentialStore {

    /** Lưu (hoặc ghi đè) một bí mật theo [key]. */
    suspend fun put(key: String, secret: String)

    /** Đọc bí mật theo [key], hoặc `null` nếu không tồn tại. */
    suspend fun get(key: String): String?

    /** Xóa bí mật theo [key] (không lỗi nếu khóa không tồn tại). */
    suspend fun remove(key: String)

    /** Xóa toàn bộ credential (Data_Wipe, Requirement 22.6). */
    suspend fun clear()
}
