package com.healthautoexport.domain.port

import com.healthautoexport.domain.model.ExportDataset
import com.healthautoexport.domain.model.ExportFormat

/**
 * Kết quả tuần tự hóa một [ExportDataset] sang một định dạng cụ thể, sẵn sàng đóng gói thành
 * [ExportPayload] để gửi tới [Destination].
 *
 * @property bytes nội dung đã tuần tự hóa (UTF-8 không BOM cho JSON/CSV/GPX, hoặc ZIP cho archive
 *   CSV nhiều tài liệu — Requirements 10.2, 11.6, 11.8).
 * @property contentType MIME media type tương ứng định dạng/đóng gói, dùng cho `Content-Type`
 *   khi gửi HTTP (Requirement 16.3).
 * @property excludedWorkoutIds định danh các Workout bị loại khỏi đầu ra GPX vì không có tuyến
 *   đường GPS; rỗng cho JSON/CSV. `RunExportJobUseCase` chuyển danh sách này thành cảnh báo
 *   Sync_Log (Requirement 5.6).
 */
data class SerializedExport(
    val bytes: ByteArray,
    val contentType: String,
    val excludedWorkoutIds: List<String> = emptyList(),
) {
    /** So sánh theo nội dung mảng byte để [SerializedExport] có ngữ nghĩa giá trị đúng đắn. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SerializedExport) return false
        return bytes.contentEquals(other.bytes) &&
            contentType == other.contentType &&
            excludedWorkoutIds == other.excludedWorkoutIds
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + excludedWorkoutIds.hashCode()
        return result
    }
}

/**
 * Port tuần tự hóa một [ExportDataset] sang [ExportFormat] đã chọn (JSON / CSV / GPX) —
 * Requirements 10, 11, 12.
 *
 * ### Vì sao tồn tại port này
 * Các bộ tuần tự hóa cụ thể (`JsonSerializer`, `CsvSerializer`, `GpxSerializer`) sống ở module
 * `:serialization`, vốn **phụ thuộc** `:domain`. Vì phụ thuộc module là một chiều
 * (`:serialization → :domain`), `:domain` **không thể** gọi trực tiếp các lớp đó mà không tạo phụ
 * thuộc vòng. Ta đảo ngược phụ thuộc bằng port này: `RunExportJobUseCase` (thuần domain) chỉ phụ
 * thuộc [ExportSerializer]; một adapter ở `:serialization` (hoặc lớp ráp nối `:data`/`:app`) hiện
 * thực port, chọn bộ tuần tự hóa theo [ExportFormat] và trả [SerializedExport].
 *
 * Adapter dự kiến:
 * - [ExportFormat.JSON] → `JsonSerializer.serialize(dataset)` → byte UTF-8, `application/json`.
 * - [ExportFormat.CSV] → `CsvSerializer.serialize(dataset).toZipBytes()` → `application/zip`.
 * - [ExportFormat.GPX] → `GpxSerializer.serialize(dataset.workouts)`; ghi `excludedWorkoutIds` từ
 *   các workout không route (Requirement 5.6) → `application/gpx+xml`.
 */
fun interface ExportSerializer {

    /**
     * Tuần tự hóa [dataset] theo [format].
     *
     * @param dataset envelope dữ liệu đã hợp nhất/tổng hợp (Requirement 10.1).
     * @param format định dạng đích.
     * @return [SerializedExport] gồm byte, content-type và danh sách workout bị loại (GPX).
     */
    fun serialize(dataset: ExportDataset, format: ExportFormat): SerializedExport
}
