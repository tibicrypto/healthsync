package com.healthautoexport.serialization

import com.healthautoexport.domain.logic.ContentTypeMapper
import com.healthautoexport.domain.model.ExportDataset
import com.healthautoexport.domain.model.ExportFormat
import com.healthautoexport.domain.port.ExportSerializer
import com.healthautoexport.domain.port.SerializedExport
import com.healthautoexport.serialization.csv.CsvSerializer
import com.healthautoexport.serialization.csv.Rfc4180CsvSerializer
import com.healthautoexport.serialization.gpx.GpxSerializer
import com.healthautoexport.serialization.json.JsonSerializer
import com.healthautoexport.serialization.json.JsonSerializerImpl

/**
 * Adapter hiện thực [ExportSerializer] (port thuần domain) bằng các bộ tuần tự hóa cụ thể của
 * module `:serialization` (Requirements 10, 11, 12).
 *
 * Adapter này sống ở `:serialization` vì đây là nơi **duy nhất** thấy được cả ba bộ tuần tự hóa
 * (`JsonSerializer`, `CsvSerializer`, `GpxSerializer`) lẫn các mô hình `:domain`
 * ([ExportDataset], [ExportFormat]). Nhờ vậy `:domain` (chứa `RunExportJobUseCase`) chỉ phụ thuộc
 * port [ExportSerializer] mà **không** tạo phụ thuộc vòng `:domain → :serialization`.
 *
 * Ánh xạ định dạng → đầu ra:
 * - [ExportFormat.JSON] → [JsonSerializer.serialize] → byte UTF-8 (không BOM), `application/json`
 *   (Requirements 10.1, 10.2).
 * - [ExportFormat.CSV] → [CsvSerializer.serialize] rồi `CsvArchive.toZipBytes()` → `application/zip`
 *   (Requirements 11.6, 11.8). Khi không có metric nào, archive ZIP rỗng vẫn hợp lệ.
 * - [ExportFormat.GPX] → [GpxSerializer.serialize] trên `dataset.workouts`; ghi
 *   [SerializedExport.excludedWorkoutIds] cho các Workout không có tuyến đường GPS
 *   (Requirement 5.6), `application/gpx+xml`.
 *
 * @property jsonSerializer bộ tuần tự hóa JSON (mặc định [JsonSerializerImpl]).
 * @property csvSerializer bộ tuần tự hóa CSV (mặc định [Rfc4180CsvSerializer]).
 * @property gpxSerializer bộ tuần tự hóa GPX (mặc định [GpxSerializer]).
 */
class ExportSerializerAdapter(
    private val jsonSerializer: JsonSerializer = JsonSerializerImpl(),
    private val csvSerializer: CsvSerializer = Rfc4180CsvSerializer(),
    private val gpxSerializer: GpxSerializer = GpxSerializer(),
) : ExportSerializer {

    override fun serialize(dataset: ExportDataset, format: ExportFormat): SerializedExport =
        when (format) {
            ExportFormat.JSON -> {
                val text = jsonSerializer.serialize(dataset)
                SerializedExport(
                    bytes = text.toByteArray(Charsets.UTF_8),
                    contentType = ContentTypeMapper.mediaType(ExportFormat.JSON),
                )
            }

            ExportFormat.CSV -> {
                val archive = csvSerializer.serialize(dataset)
                SerializedExport(
                    bytes = archive.toZipBytes(),
                    contentType = ContentTypeMapper.mediaType(ExportFormat.CSV, archived = true),
                )
            }

            ExportFormat.GPX -> {
                val result = gpxSerializer.serialize(dataset.workouts)
                SerializedExport(
                    bytes = result.xml.toByteArray(Charsets.UTF_8),
                    contentType = ContentTypeMapper.mediaType(ExportFormat.GPX),
                    excludedWorkoutIds = result.excludedWorkouts.map { it.workoutId },
                )
            }
        }
}
