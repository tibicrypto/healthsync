package com.healthautoexport.serialization.csv

import com.healthautoexport.domain.model.ExportDataset
import com.healthautoexport.domain.model.ExtraValue
import com.healthautoexport.domain.model.HealthMetricType
import com.healthautoexport.domain.model.MetricCatalog
import com.healthautoexport.domain.model.MetricSchema
import com.healthautoexport.domain.model.MetricSeries
import com.healthautoexport.domain.model.MetricValue
import com.healthautoexport.domain.model.UnifiedRecord

/**
 * CSV_Serializer (Requirement 11): chuyển các [MetricSeries] của một [ExportDataset] thành một
 * [CsvArchive] gồm **một tài liệu CSV cho mỗi loại Health_Metric**.
 *
 * Mỗi tài liệu gồm một dòng tiêu đề theo **thứ tự cột cố định, xác định** rồi **đúng một dòng dữ
 * liệu cho mỗi bản ghi** (Requirements 11.1, 11.2). Giá trị trường chứa dấu phẩy/nháy kép/xuống
 * dòng được escape theo RFC 4180 (Requirement 11.3); trường rỗng/thiếu để trống (Requirement 11.5);
 * dấu thời gian theo `yyyy-MM-dd HH:mm:ss Z` (Requirement 11.4); mọi dòng kết thúc bằng CRLF
 * (Requirement 11.7) và nội dung mã hóa UTF-8 không BOM (Requirement 11.6). Nhiều tài liệu được gom
 * vào một archive ZIP, đặt tên theo định danh metric (Requirement 11.8 — xem [CsvArchive]).
 */
interface CsvSerializer {

    /** Tuần tự hóa các [MetricSeries] của [dataset] thành [CsvArchive] (Requirement 11). */
    fun serialize(dataset: ExportDataset): CsvArchive
}

/**
 * Triển khai thuần JVM của [CsvSerializer] theo RFC 4180.
 *
 * ## Thứ tự cột cố định (Requirements 11.1, 11.2)
 * Mỗi tài liệu có bố cục cột:
 *
 * `date, <cột-giá-trị...>, units, source, <khóa-extras-theo-thứ-tự-bảng-chữ-cái...>`
 *
 * Trong đó `<cột-giá-trị>` phụ thuộc biến thể [MetricValue] của chỉ số (xác định bởi catalog khi
 * chuỗi rỗng, hoặc bởi bản ghi đầu tiên khi đã có dữ liệu):
 * - [MetricValue.Scalar] → `qty`
 * - [MetricValue.BloodPressure] → `systolic, diastolic`
 * - [MetricValue.HeartRateStat] → `min, avg, max`
 * - [MetricValue.StatSummary] → `min, avg, max, count`
 * - [MetricValue.SleepSegment] → `state, duration_seconds`
 * - [MetricValue.Ecg] → `classification, average_bpm, sampling_hz, voltages`
 *
 * Các cột `extras` là hợp của mọi khóa [UnifiedRecord.extras] trong chuỗi, sắp theo thứ tự bảng chữ
 * cái để cố định và xác định; bản ghi thiếu một khóa sẽ để trống ô đó (Requirement 11.5).
 *
 * Mọi số dùng `BigDecimal.toPlainString()` để giữ độ chính xác và tránh ký pháp khoa học, đồng nhất
 * với JSON_Serializer (Requirement 10.5).
 */
class Rfc4180CsvSerializer : CsvSerializer {

    override fun serialize(dataset: ExportDataset): CsvArchive {
        if (dataset.metrics.isEmpty()) return CsvArchive.EMPTY
        val entries = dataset.metrics.map { series ->
            CsvArchive.Entry(
                name = series.name,
                content = serializeSeries(series).toByteArray(Charsets.UTF_8),
            )
        }
        return CsvArchive(entries)
    }

    /**
     * Tuần tự hóa một [MetricSeries] thành chuỗi CSV (UTF-8 khi mã hóa byte sẽ không kèm BOM —
     * Requirement 11.6). Dòng tiêu đề + một dòng dữ liệu/bản ghi, đều kết thúc bằng CRLF
     * (Requirement 11.7).
     */
    internal fun serializeSeries(series: MetricSeries): String {
        val valueColumns: List<String> = if (series.data.isNotEmpty()) {
            valueColumnNames(series.data.first().value)
        } else {
            schemaValueColumnNames(schemaFor(series.name))
        }
        // Hợp các khóa extras, sắp xếp để cố định thứ tự cột (Requirements 11.1, 11.2).
        val extraKeys: List<String> = series.data
            .flatMap { it.extras.keys }
            .distinct()
            .sorted()

        val header = buildList {
            add(COLUMN_DATE)
            addAll(valueColumns)
            add(COLUMN_UNITS)
            add(COLUMN_SOURCE)
            addAll(extraKeys)
        }

        val sb = StringBuilder()
        sb.append(Rfc4180.encodeRow(header)).append(Rfc4180.CRLF)
        for (record in series.data) {
            val row = buildList {
                add(CsvTimestampFormat.format(record.timestamp, record.zoneOffset))
                for (col in valueColumns) add(valueCell(record.value, col) ?: "")
                add(record.unit.symbol)
                add(record.dataSourceId.id)
                for (key in extraKeys) add(renderExtra(record.extras[key]))
            }
            sb.append(Rfc4180.encodeRow(row)).append(Rfc4180.CRLF)
        }
        return sb.toString()
    }

    // --- Bố cục cột theo biến thể giá trị --------------------------------------------------------

    /** Danh sách cột-giá-trị (theo thứ tự cố định) cho một [MetricValue] cụ thể. */
    private fun valueColumnNames(value: MetricValue): List<String> = when (value) {
        is MetricValue.Scalar -> listOf("qty")
        is MetricValue.BloodPressure -> listOf("systolic", "diastolic")
        is MetricValue.HeartRateStat -> listOf("min", "avg", "max")
        is MetricValue.StatSummary -> listOf("min", "avg", "max", "count")
        is MetricValue.SleepSegment -> listOf("state", "duration_seconds")
        is MetricValue.Ecg -> listOf("classification", "average_bpm", "sampling_hz", "voltages")
    }

    /**
     * Giá trị ô cho cột [column] của một [value]; trả `null` nếu cột không áp dụng cho biến thể này
     * (người gọi sẽ để trống ô — Requirement 11.5).
     */
    private fun valueCell(value: MetricValue, column: String): String? = when (value) {
        is MetricValue.Scalar -> if (column == "qty") value.qty.toPlainString() else null
        is MetricValue.BloodPressure -> when (column) {
            "systolic" -> value.systolic.toPlainString()
            "diastolic" -> value.diastolic.toPlainString()
            else -> null
        }
        is MetricValue.HeartRateStat -> when (column) {
            "min" -> value.min.toPlainString()
            "avg" -> value.avg.toPlainString()
            "max" -> value.max.toPlainString()
            else -> null
        }
        is MetricValue.StatSummary -> when (column) {
            "min" -> value.min.toPlainString()
            "avg" -> value.avg.toPlainString()
            "max" -> value.max.toPlainString()
            "count" -> value.count.toString()
            else -> null
        }
        is MetricValue.SleepSegment -> when (column) {
            "state" -> value.state.name
            "duration_seconds" -> value.durationSeconds.toString()
            else -> null
        }
        is MetricValue.Ecg -> when (column) {
            "classification" -> value.classification
            "average_bpm" -> value.averageBpm.toString()
            "sampling_hz" -> value.samplingHz.toPlainString()
            "voltages" -> value.voltages.joinToString(separator = " ") { it.toPlainString() }
            else -> null
        }
    }

    /** Cột-giá-trị cho một chuỗi rỗng, suy ra từ [MetricSchema] của metric. */
    private fun schemaValueColumnNames(schema: MetricSchema): List<String> = when (schema) {
        MetricSchema.STANDARD -> listOf("qty")
        MetricSchema.BLOOD_PRESSURE -> listOf("systolic", "diastolic")
        MetricSchema.SLEEP -> listOf("state", "duration_seconds")
        MetricSchema.ECG -> listOf("classification", "average_bpm", "sampling_hz", "voltages")
        MetricSchema.HEART_RATE_STAT -> listOf("min", "avg", "max")
        MetricSchema.HR_NOTIFICATION -> listOf("qty")
    }

    /** Tra [MetricSchema] theo tên canonical của chuỗi; mặc định [MetricSchema.STANDARD]. */
    private fun schemaFor(canonicalName: String): MetricSchema =
        SCHEMA_BY_CANONICAL_NAME[canonicalName] ?: MetricSchema.STANDARD

    /** Render một giá trị extras thành ô; thiếu khóa → ô rỗng (Requirement 11.5). */
    private fun renderExtra(extra: ExtraValue?): String = when (extra) {
        null -> ""
        is ExtraValue.StringValue -> extra.value
        is ExtraValue.NumberValue -> extra.value.toPlainString()
        is ExtraValue.EnumValue -> extra.name
    }

    private companion object {
        const val COLUMN_DATE = "date"
        const val COLUMN_UNITS = "units"
        const val COLUMN_SOURCE = "source"

        /** Bản đồ tra cứu tên canonical → schema, dựng một lần từ [MetricCatalog]. */
        val SCHEMA_BY_CANONICAL_NAME: Map<String, MetricSchema> =
            HealthMetricType.entries.associate { type ->
                val spec = MetricCatalog.spec(type)
                spec.canonicalName to spec.schema
            }
    }
}
