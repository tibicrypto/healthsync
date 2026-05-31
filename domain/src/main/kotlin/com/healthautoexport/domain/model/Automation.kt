package com.healthautoexport.domain.model

import java.time.Instant

/**
 * Cấu hình Automation có thể lưu lại và tái sử dụng cho các Export_Job tự động (Requirement 14).
 *
 * Đây là mô hình **thuần domain** tương ứng với `AutomationEntity` của Room (task 16.1); tầng
 * dữ liệu ánh xạ qua lại. Tên Automation dài 1–100 ký tự, trùng tên không phân biệt hoa/thường
 * bị từ chối (Requirements 14.1, 14.7); một Automation hợp lệ bắt buộc có Destination và ít nhất
 * một metric hoặc workout (Requirement 14.2) — các ràng buộc này do `ConfigureAutomationUseCase`
 * (task 19.2) xác thực.
 *
 * @property id định danh ổn định, dùng làm unique work name của Scheduler (Requirement 15.1).
 * @property name tên hiển thị (1–100 ký tự, Requirement 14.1).
 * @property selection các metric/workout được chọn (Requirement 14.2).
 * @property exportFormat định dạng xuất.
 * @property aggregationPeriod mức tổng hợp.
 * @property scheduleIntervalMinutes khoảng lặp lịch tính bằng phút (15..43200, Requirement 15.3).
 * @property enabled `true` nếu Automation đang bật chạy theo lịch.
 * @property destinationType loại Destination nhận payload (Requirement 14.2).
 * @property destinationConfigRef tham chiếu cấu hình Destination; credential lưu ở
 *   [CredentialStore], không nhúng tại đây (Requirement 22.9).
 * @property firstActivatedAtUtc thời điểm Automation được kích hoạt lần đầu, dùng cho cửa sổ
 *   Date_Range khi chưa có lần chạy thành công (Requirement 9.9); `null` nếu chưa từng kích hoạt.
 * @property lastSuccessfulEndUtc thời điểm kết thúc của Export_Job thành công gần nhất, dùng làm
 *   mốc bắt đầu cửa sổ nối tiếp (Requirement 9.8); `null` nếu chưa có.
 */
data class Automation(
    val id: String,
    val name: String,
    val selection: MetricSelection,
    val exportFormat: ExportFormat,
    val aggregationPeriod: AggregationPeriod,
    val scheduleIntervalMinutes: Long,
    val enabled: Boolean,
    val destinationType: DestinationType,
    val destinationConfigRef: String,
    val firstActivatedAtUtc: Instant? = null,
    val lastSuccessfulEndUtc: Instant? = null,
)
