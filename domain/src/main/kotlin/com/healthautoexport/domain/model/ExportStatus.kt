package com.healthautoexport.domain.model

/**
 * Kết quả của một Export_Job được ghi vào Sync_Log (Requirement 23).
 *
 * Mỗi lần thực thi Export_Job tạo ra đúng một mục Sync_Log mang một trong các trạng thái này.
 */
enum class ExportStatus {
    /** Export_Job hoàn tất và payload đã được gửi tới Destination thành công. */
    SUCCESS,

    /** Export_Job thất bại (lỗi đọc dữ liệu, tuần tự hóa, hoặc gửi tới Destination). */
    FAILURE,

    /**
     * Export_Job bị bỏ qua, ví dụ một lần chạy theo lịch trùng lặp với lần đang chạy
     * (Requirement 15.5).
     */
    SKIPPED,

    /**
     * Export_Job hoàn tất nhưng không khớp bản ghi nào, tạo ra tập kết quả rỗng
     * (Requirement 5.8).
     */
    EMPTY,

    /** Export_Job bị người dùng hủy trước khi hoàn tất (Requirement 13.5). */
    CANCELLED,
}
