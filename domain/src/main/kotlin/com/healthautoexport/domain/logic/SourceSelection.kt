package com.healthautoexport.domain.logic

import com.healthautoexport.domain.model.DataSourceId

/**
 * Logic **thuần** (xác định, không I/O) xác định tập Data_Source mà `Data_Reader` sẽ thực sự
 * truy vấn cho một Export_Job (Requirements 3.3, 3.4).
 *
 * Tách phần quyết định "đọc từ nguồn nào" ra khỏi phần đọc thực tế (vốn là I/O nằm sau Port
 * `HealthDataSource`) cho phép kiểm thử dựa-trên-thuộc-tính (Property 31) trên JVM mà không cần
 * thiết bị.
 */
object SourceSelection {

    /**
     * Tập Data_Source được truy vấn = `enabled ∩ available` (Property 31, Requirements 3.3, 3.4).
     *
     * - Khi chỉ một nguồn được bật và khả dụng, kết quả là đúng một nguồn đó (Requirement 3.3).
     * - Khi cả hai nguồn được bật và khả dụng, kết quả gồm cả hai (Requirement 3.4).
     * - Nguồn bị tắt hoặc không khả dụng đều bị loại khỏi tập truy vấn; nguồn quá hạn/không phản
     *   hồi được tầng `Data_Reader` loại khỏi [available] trước khi gọi hàm này (Requirement 3.5).
     *
     * Hàm thuần: kết quả chỉ phụ thuộc đầu vào, không thay đổi tham số.
     *
     * @param enabled tập Data_Source mà người dùng đã bật (Requirements 3.1, 3.2).
     * @param available tập Data_Source hiện khả dụng (SDK cài đặt, dịch vụ phản hồi).
     * @return giao của [enabled] và [available].
     */
    fun queriedSources(
        enabled: Set<DataSourceId>,
        available: Set<DataSourceId>,
    ): Set<DataSourceId> = enabled intersect available
}
