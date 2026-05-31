package com.healthautoexport.domain.port

/**
 * Kết quả của bước đọc dữ liệu đa nguồn do `DataReader` điều phối (Requirements 3.3–3.7).
 */
sealed interface ReadOutcome {

    /**
     * Đọc thành công từ ít nhất một nguồn khả dụng; mỗi phần tử là kết quả của một nguồn.
     *
     * @property perSource danh sách [SourceReadResult] theo từng Data_Source đã đọc.
     */
    data class Success(val perSource: List<SourceReadResult>) : ReadOutcome

    /**
     * Không có Data_Source nào được bật tại thời điểm Export_Job (Requirement 3.7).
     *
     * @property reason lý do người dùng đọc được để ghi Sync_Log.
     */
    data class NoEnabledSource(val reason: String) : ReadOutcome

    /**
     * Tất cả các Data_Source được bật đều không khả dụng (Requirement 3.6).
     *
     * @property reason lý do người dùng đọc được để ghi Sync_Log.
     */
    data class AllSourcesUnavailable(val reason: String) : ReadOutcome
}
