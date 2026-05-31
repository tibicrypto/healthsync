package com.healthautoexport.domain.logic

/**
 * Guard dung lượng lưu trữ cho Destination Local Storage (Requirements 21.7, 21.8).
 *
 * Trước khi ghi tệp, App kiểm tra dung lượng khả dụng so với kích thước byte của bản xuất đã tuần
 * tự hóa. Hàm thuần: chỉ so sánh hai số nguyên.
 *
 * Property 42 — *Validates: Requirements 21.8*: App SHALL tiến hành ghi **khi và chỉ khi**
 * `freeSpace >= payloadSize`; ngược lại hủy ghi (không tạo tệp một phần) và ghi Sync_Log.
 */
object StorageGuard {

    /**
     * Trả về `true` nếu được phép ghi (dung lượng khả dụng đủ chứa payload).
     *
     * @param payloadSize kích thước byte của bản xuất đã tuần tự hóa; SHALL `>= 0`.
     * @param freeSpace dung lượng byte khả dụng của thiết bị; SHALL `>= 0`.
     */
    fun canWrite(payloadSize: Long, freeSpace: Long): Boolean {
        require(payloadSize >= 0) { "payloadSize ($payloadSize) phải >= 0" }
        require(freeSpace >= 0) { "freeSpace ($freeSpace) phải >= 0" }
        return freeSpace >= payloadSize
    }
}
