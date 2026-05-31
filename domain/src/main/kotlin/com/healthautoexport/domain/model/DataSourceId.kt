package com.healthautoexport.domain.model

/**
 * Định danh nền tảng nguồn dữ liệu (Data_Source) mà mỗi bản ghi bắt nguồn.
 *
 * Mỗi [Unified_Record][UnifiedRecord] mang theo một `DataSourceId` để bảo toàn nguồn gốc
 * (Requirement 4.5) và để `Data_Merger` giải quyết trùng lặp một cách xác định.
 *
 * Thuộc tính [id] là một định danh chuỗi **ổn định** dùng làm khóa so sánh khi hai bản ghi
 * trùng nhau có cùng mức ưu tiên nguồn: khi đó bản ghi có `id` đứng trước theo thứ tự bảng
 * chữ cái tăng dần sẽ được giữ lại (Requirement 7.5). Giá trị `id` được cố định bằng tay
 * (thay vì phụ thuộc vào [name] của enum) để bảo đảm thứ tự dedup không thay đổi nếu sau này
 * có người đổi tên hằng số enum.
 *
 * Với giá trị hiện tại, `"health_connect" < "huawei_health_kit"` nên Health_Connect thắng
 * khi hòa ưu tiên — đây là hành vi tie-break đã được tài liệu hóa.
 */
enum class DataSourceId(val id: String) {
    /** Google Health Connect — nguồn dữ liệu mặc định trên thiết bị Android. */
    HEALTH_CONNECT("health_connect"),

    /** Huawei Health Service Kit (HMS Health Kit) — nguồn dữ liệu trên thiết bị Huawei. */
    HUAWEI_HEALTH_KIT("huawei_health_kit"),
}
