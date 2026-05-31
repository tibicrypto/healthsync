package com.healthautoexport.domain.model

/**
 * Một quyền đọc dữ liệu sức khỏe đã được cấp/ủy quyền, ở dạng **thuần domain** (không phụ thuộc
 * Android).
 *
 * Mô hình này thay cho kiểu `HealthPermission` của Health_Connect SDK để `:domain` không kéo
 * theo phụ thuộc Android; bộ điều hợp tầng dữ liệu (task 13/14) ánh xạ qua lại với kiểu SDK.
 * `PermissionManager.refreshGrants` trả về tập các quyền hiện được cấp để phát hiện thu hồi
 * (Requirements 1.6, 2.6).
 *
 * @property source nguồn dữ liệu mà quyền thuộc về.
 * @property metric loại Health_Metric được phép đọc.
 * @property background `true` nếu là quyền đọc **nền** (Requirements 1.5, 1.10), `false` cho
 *   quyền đọc thường.
 */
data class HealthPermission(
    val source: DataSourceId,
    val metric: HealthMetricType,
    val background: Boolean = false,
)
