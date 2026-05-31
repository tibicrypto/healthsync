package com.healthautoexport.data.destination

/**
 * Cổng (port) nội bộ bọc Dropbox SDK (app-folder scope) cho [DropboxDestination] (Requirement 18).
 *
 * Trừu tượng hóa Dropbox sau interface này giúp build/test **không** cần SDK Dropbox: hiện thực
 * thật được tiêm về sau, còn [NoOpDropboxClient] là mặc định an toàn cho tới khi người dùng hoàn tất
 * ủy quyền (Requirement 18.1).
 */
interface DropboxClient {

    /**
     * Trả về tập tên tệp hiện có trong thư mục [folderPath], dùng để tránh ghi đè khi trùng tên
     * (Requirement 18.5).
     *
     * @throws java.io.IOException khi gặp lỗi mạng/I-O trong lúc liệt kê.
     */
    suspend fun listNames(folderPath: String): Set<String>

    /**
     * Tải [bytes] lên [folderPath] dưới dạng tệp đơn tên [name] (Requirement 18.2).
     *
     * Hiện thực **không** được ghi đè tệp trùng tên — việc chọn tên duy nhất do [DropboxDestination]
     * đảm nhiệm trước khi gọi (Requirement 18.5).
     *
     * @return [Result.success] mang đường dẫn tệp đã tạo khi thành công; [Result.failure] mang
     *   ngoại lệ (vd lỗi mạng) khi thất bại (Requirement 18.6).
     */
    suspend fun upload(
        folderPath: String,
        name: String,
        bytes: ByteArray,
        contentType: String,
    ): Result<String>

    /**
     * Cho biết ủy quyền Dropbox hiện còn hiệu lực hay không. Trả về `false` khi thiếu hoặc hết hạn,
     * buộc [DropboxDestination] nhắc người dùng ủy quyền lại (Requirement 18.3).
     */
    fun isAuthorized(): Boolean
}

/**
 * Hiện thực mặc định **no-op** của [DropboxClient]: luôn coi như chưa ủy quyền.
 *
 * Được Hilt bind làm mặc định để module compile/verify mà không kéo theo Dropbox SDK; nhờ
 * [isAuthorized] trả về `false`, [DropboxDestination.send] dừng sớm với thất bại ủy quyền và không
 * phát sinh kết nối mạng nào (phù hợp Requirement 22.4). Hiện thực thật thay thế client này sau khi
 * tích hợp Dropbox.
 */
class NoOpDropboxClient : DropboxClient {

    override suspend fun listNames(folderPath: String): Set<String> = emptySet()

    override suspend fun upload(
        folderPath: String,
        name: String,
        bytes: ByteArray,
        contentType: String,
    ): Result<String> = Result.failure(UnsupportedOperationException("DropboxClient chưa được cấu hình"))

    override fun isAuthorized(): Boolean = false
}
