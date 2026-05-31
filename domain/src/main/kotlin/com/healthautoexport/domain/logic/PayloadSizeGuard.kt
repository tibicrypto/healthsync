package com.healthautoexport.domain.logic

/**
 * Guard kích thước payload cho Destination REST API (Requirement 16.8).
 *
 * Hàm thuần: App SHALL gửi yêu cầu **khi và chỉ khi** kích thước payload `<= 100 MB`; payload vượt
 * giới hạn SHALL không được gửi và ghi lỗi kích thước vào Sync_Log.
 *
 * Property 44 — *Validates: Requirements 16.8*.
 */
object PayloadSizeGuard {

    /** Giới hạn kích thước thân yêu cầu REST: 100 MB = 100 × 1024 × 1024 byte. */
    const val MAX_PAYLOAD_BYTES: Long = 100L * 1024 * 1024

    /**
     * Trả về `true` nếu payload nằm trong giới hạn cho phép (`<= 100 MB`).
     *
     * @param size kích thước byte của bản xuất đã tuần tự hóa; SHALL `>= 0`.
     */
    fun withinLimit(size: Long): Boolean {
        require(size >= 0) { "size ($size) phải >= 0" }
        return size <= MAX_PAYLOAD_BYTES
    }
}
