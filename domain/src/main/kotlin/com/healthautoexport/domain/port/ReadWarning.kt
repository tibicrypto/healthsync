package com.healthautoexport.domain.port

import com.healthautoexport.domain.model.DataSourceId
import com.healthautoexport.domain.model.HealthMetricType

/**
 * Một cảnh báo phát sinh khi đọc dữ liệu từ một Data_Source, để ghi vào Sync_Log mà không hủy
 * Export_Job (Requirements 4.7, 6.6).
 *
 * Ví dụ: bản ghi không ánh xạ được sang metric hỗ trợ hoặc không chuyển được về đơn vị canonical
 * (Requirement 4.7); bản ghi thiếu một trường bắt buộc của loại dữ liệu chuyên biệt nhưng các
 * trường còn lại vẫn được giữ (Requirement 6.6).
 *
 * @property source nguồn phát sinh cảnh báo (Requirement 4.7 yêu cầu kèm định danh nguồn).
 * @property metric loại metric liên quan nếu xác định được, ngược lại `null`.
 * @property message mô tả người dùng đọc được; SHALL KHÔNG chứa dữ liệu thô.
 */
data class ReadWarning(
    val source: DataSourceId,
    val metric: HealthMetricType?,
    val message: String,
)
