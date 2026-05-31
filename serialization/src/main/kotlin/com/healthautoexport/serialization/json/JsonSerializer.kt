package com.healthautoexport.serialization.json

import com.healthautoexport.domain.model.ExportDataset

/**
 * JSON_Serializer — chuyển một [ExportDataset] hợp nhất thành Export_Format JSON của App
 * (Requirement 10).
 *
 * Đầu ra là một đối tượng cấp cao nhất `data` **luôn** chứa đủ tám mảng (`metrics`, `workouts`,
 * `stateOfMind`, `medications`, `symptoms`, `cycleTracking`, `ecg`, `heartRateNotifications`);
 * danh mục rỗng được phát ra là `[]` chứ không bị bỏ khóa hay đặt `null` (Requirements 10.1,
 * 10.3). Văn bản trả về là chuỗi Kotlin thuần — khi ghi ra byte ở tầng Destination, chuỗi được
 * mã hóa UTF-8 **không kèm BOM** (Requirement 10.2), vì một [String] không tự mang BOM và bộ
 * tuần tự hóa này không bao giờ thêm ký tự U+FEFF dẫn đầu.
 *
 * Giá trị `qty` được ghi theo ký pháp thập phân (không khoa học), giữ nguyên dấu và **tối thiểu
 * 6 chữ số sau dấu thập phân** mà không làm tròn mất dữ liệu (Requirement 10.5). Dấu thời gian
 * theo mẫu `yyyy-MM-dd HH:mm:ss Z` (Requirements 10.4, 10.7). Các chỉ số có lược đồ riêng
 * (huyết áp, thống kê nhịp tim, giấc ngủ, ECG, cảnh báo nhịp tim) được phát theo lược đồ tài
 * liệu hóa tương ứng (Requirement 10.6).
 *
 * Cùng với [JsonParser], bộ tuần tự hóa này bảo toàn thuộc tính **round-trip**: serialize rồi
 * parse cho ra một dataset bằng dataset ban đầu (Requirement 10.8, Property 1).
 */
interface JsonSerializer {

    /**
     * Tuần tự hóa [dataset] thành văn bản JSON theo Export_Format của App.
     *
     * @param dataset tập dữ liệu hợp nhất cần tuần tự hóa (đủ tám danh mục, danh mục rỗng là
     *   danh sách rỗng).
     * @return chuỗi JSON với envelope `data` đầy đủ tám mảng (Requirements 10.1, 10.3).
     */
    fun serialize(dataset: ExportDataset): String
}
