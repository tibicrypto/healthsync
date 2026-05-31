package com.healthautoexport.domain.model

/**
 * Trạng thái hiển thị của một quyền/scope đọc cho một Health_Metric đã chọn.
 *
 * Dùng cho màn hình trạng thái quyền của cả hai nguồn:
 * - Health_Connect (Requirement 1.7): "đã cấp" / "chưa cấp".
 * - Huawei_Health_Kit (Requirement 2.7): "Đã ủy quyền" / "Chưa ủy quyền".
 *
 * Mỗi Health_Metric đã chọn ánh xạ tới đúng một trong hai giá trị này.
 */
enum class PermissionState {
    /** Quyền/scope đọc tương ứng hiện đã được cấp/ủy quyền. */
    GRANTED,

    /** Quyền/scope đọc tương ứng chưa được cấp/ủy quyền. */
    NOT_GRANTED,
}
