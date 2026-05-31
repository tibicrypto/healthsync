package com.healthautoexport.data.destination

/**
 * Cổng (port) nội bộ bọc Google Drive REST v3 (file-creation scope) cho [GoogleDriveDestination]
 * (Requirement 17).
 *
 * Việc trừu tượng hóa Drive sau interface này giúp build/test **không** cần SDK độc quyền của
 * Google: hiện thực thật (Drive REST client) được tiêm về sau, còn [NoOpDriveClient] đóng vai trò
 * mặc định an toàn cho tới khi người dùng hoàn tất ủy quyền (Requirement 17.1).
 */
interface DriveClient {

    /**
     * Trả về tập tên tệp hiện có trong thư mục [folderId], dùng để tránh ghi đè khi trùng tên
     * (Requirement 17.5).
     *
     * @throws java.io.IOException khi gặp lỗi mạng/I-O trong lúc liệt kê.
     */
    suspend fun listNames(folderId: String): Set<String>

    /**
     * Tải [bytes] lên [folderId] dưới dạng tệp mới tên [name] với kiểu nội dung [contentType].
     *
     * Hiện thực **không** được ghi đè tệp trùng tên — việc chọn tên duy nhất do
     * [GoogleDriveDestination] đảm nhiệm trước khi gọi (Requirement 17.5).
     *
     * @return [Result.success] mang định danh tệp đã tạo trên Drive khi thành công;
     *   [Result.failure] mang ngoại lệ (vd lỗi mạng) khi thất bại (Requirement 17.6).
     */
    suspend fun upload(
        folderId: String,
        name: String,
        bytes: ByteArray,
        contentType: String,
    ): Result<String>

    /**
     * Cho biết ủy quyền Drive hiện còn hiệu lực hay không. Trả về `false` khi thiếu hoặc hết hạn,
     * buộc [GoogleDriveDestination] nhắc người dùng ủy quyền lại (Requirement 17.3).
     */
    fun isAuthorized(): Boolean
}

/**
 * Hiện thực mặc định **no-op** của [DriveClient]: luôn coi như chưa ủy quyền.
 *
 * Được Hilt bind làm mặc định để module compile/verify mà không kéo theo Google Drive SDK; nhờ
 * [isAuthorized] trả về `false`, [GoogleDriveDestination.send] dừng sớm với thất bại ủy quyền và
 * không phát sinh kết nối mạng nào (phù hợp Requirement 22.4). Hiện thực thật thay thế client này
 * sau khi tích hợp Drive.
 */
class NoOpDriveClient : DriveClient {

    override suspend fun listNames(folderId: String): Set<String> = emptySet()

    override suspend fun upload(
        folderId: String,
        name: String,
        bytes: ByteArray,
        contentType: String,
    ): Result<String> = Result.failure(UnsupportedOperationException("DriveClient chưa được cấu hình"))

    override fun isAuthorized(): Boolean = false
}
