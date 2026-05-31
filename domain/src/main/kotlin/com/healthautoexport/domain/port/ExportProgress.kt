package com.healthautoexport.domain.port

/**
 * Tiến trình của một Export_Job đang chạy, phát qua `Flow<ExportProgress>` để UI hiển thị phần
 * trăm (Requirement 13.2).
 *
 * `RunExportJobUseCase` (task 19.1) cập nhật ít nhất mỗi 2 giây và hỗ trợ hủy hợp tác
 * (Requirement 13.6).
 *
 * @property percent phần trăm hoàn thành trong khoảng `0..100`.
 * @property stage giai đoạn hiện tại của pipeline (đọc/hợp nhất/tổng hợp/tuần tự hóa/gửi).
 */
data class ExportProgress(
    val percent: Int,
    val stage: Stage,
) {
    init {
        require(percent in 0..100) { "ExportProgress.percent phải trong 0..100, nhận được $percent" }
    }

    /** Các giai đoạn của pipeline Export_Job tương ứng sơ đồ pipeline trong design.md. */
    enum class Stage {
        READING,
        MERGING,
        AGGREGATING,
        SERIALIZING,
        SENDING,
        COMPLETED,
    }
}
