package com.healthautoexport.serialization.gpx

import com.healthautoexport.domain.model.RoutePoint
import com.healthautoexport.domain.model.Workout
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Cảnh báo về một Workout bị loại khỏi đầu ra GPX vì không có tuyến đường GPS đã ghi
 * (Requirement 5.6 / Requirement 12).
 *
 * Người gọi (use case điều phối Export_Job) dùng danh sách cảnh báo này để ghi vào
 * `Sync_Log`, theo đúng yêu cầu "ghi lại việc loại trừ vào Sync_Log".
 *
 * @property workoutId định danh Workout bị loại trừ.
 * @property message mô tả người đọc được về lý do loại trừ.
 */
data class GpxExclusionWarning(
    val workoutId: String,
    val message: String,
)

/**
 * Kết quả của một lần tuần tự hóa GPX.
 *
 * Bên cạnh tài liệu [xml], kết quả mang theo [excludedWorkouts] — danh sách các Workout
 * bị loại khỏi đầu ra vì không có tuyến đường GPS — để người gọi ghi `Sync_Log`
 * (Requirement 5.6). Thiết kế gốc mô tả `GpxSerializer.serialize(...) : String`; ở đây
 * giá trị trả về được mở rộng thành một đối tượng kết quả để bề mặt được các cảnh báo
 * loại trừ mà không cần kênh phụ.
 *
 * @property xml tài liệu GPX 1.1 đã tuần tự hóa (UTF-8, không BOM khi ghi ra byte).
 * @property excludedWorkouts danh sách Workout bị loại trừ kèm lý do.
 */
data class GpxSerializationResult(
    val xml: String,
    val excludedWorkouts: List<GpxExclusionWarning>,
)

/**
 * Tuần tự hóa danh sách [Workout] thành một tài liệu **GPX 1.1** (Requirement 12).
 *
 * Quy tắc tuần tự hóa:
 * - Mỗi Workout **có tuyến đường** sinh ra đúng một phần tử `<trk>`, theo đúng thứ tự các
 *   Workout được cung cấp (Requirements 12.1, 12.5). Định danh Workout được ghi vào phần tử
 *   con `<name>` của track để [GpxParser] có thể khôi phục liên kết ngược.
 * - Mỗi `<trk>` chứa **đúng một** `<trkseg>` (Requirement 12.1).
 * - Mỗi điểm tuyến đường sinh ra đúng một `<trkpt>`, theo đúng thứ tự chuỗi gốc
 *   (Requirement 12.2); vĩ độ/kinh độ là **thuộc tính** `lat`/`lon`, còn độ cao/dấu thời gian
 *   là **phần tử con** `<ele>`/`<time>` (Requirement 12.3).
 * - Dấu thời gian được định dạng theo **ISO 8601 UTC ở độ chính xác giây** (Requirement 12.4).
 * - Một điểm không có độ cao ([RoutePoint.altitudeMeters] `== null`) vẫn được giữ lại nhưng
 *   bỏ qua phần tử `<ele>` của riêng điểm đó (Requirement 5.3 / 12.3).
 * - Một Workout **không có tuyến đường GPS đã ghi** (route `null` hoặc rỗng) bị **loại** khỏi
 *   đầu ra GPX và được báo qua [GpxSerializationResult.excludedWorkouts] để ghi `Sync_Log`
 *   (Requirement 5.6).
 *
 * Tọa độ/độ cao được định dạng bằng [BigDecimal] với [RoundingMode.HALF_UP] và dùng dấu chấm
 * thập phân độc lập với locale (không phụ thuộc `Locale` mặc định của thiết bị): vĩ độ/kinh độ
 * làm tròn tới 6 chữ số thập phân (độ), độ cao tới 2 chữ số thập phân (mét) — đúng độ chính xác
 * mà thuộc tính khứ hồi (Property 5) yêu cầu.
 */
class GpxSerializer {

    /**
     * Tuần tự hóa [workouts] thành một tài liệu GPX 1.1 duy nhất.
     *
     * @param workouts danh sách Workout theo thứ tự mong muốn cho đầu ra (Requirement 12.5).
     * @return [GpxSerializationResult] gồm tài liệu XML và danh sách Workout bị loại trừ.
     */
    fun serialize(workouts: List<Workout>): GpxSerializationResult {
        val excluded = mutableListOf<GpxExclusionWarning>()
        val sb = StringBuilder()

        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append(
            "<gpx version=\"$GPX_VERSION\" creator=\"$CREATOR\" xmlns=\"$GPX_1_1_NAMESPACE\">",
        ).append('\n')

        for (workout in workouts) {
            val route = workout.route
            if (route.isNullOrEmpty()) {
                excluded += GpxExclusionWarning(
                    workoutId = workout.id,
                    message = "Workout '${workout.id}' không có tuyến đường GPS đã ghi; " +
                        "đã loại khỏi đầu ra GPX.",
                )
                continue
            }
            appendTrack(sb, workout.id, route)
        }

        sb.append("</gpx>").append('\n')
        return GpxSerializationResult(xml = sb.toString(), excludedWorkouts = excluded)
    }

    private fun appendTrack(sb: StringBuilder, workoutId: String, route: List<RoutePoint>) {
        sb.append("  <trk>").append('\n')
        sb.append("    <name>").append(escapeXmlText(workoutId)).append("</name>").append('\n')
        sb.append("    <trkseg>").append('\n')
        for (point in route) {
            appendTrackPoint(sb, point)
        }
        sb.append("    </trkseg>").append('\n')
        sb.append("  </trk>").append('\n')
    }

    private fun appendTrackPoint(sb: StringBuilder, point: RoutePoint) {
        sb.append("      <trkpt lat=\"")
            .append(formatCoordinate(point.latitude))
            .append("\" lon=\"")
            .append(formatCoordinate(point.longitude))
            .append("\">")
            .append('\n')

        // <ele> bị bỏ qua khi điểm không có độ cao, nhưng điểm vẫn được giữ (Requirement 5.3).
        point.altitudeMeters?.let { altitude ->
            sb.append("        <ele>")
                .append(formatElevation(altitude))
                .append("</ele>")
                .append('\n')
        }

        sb.append("        <time>")
            .append(ISO_UTC_SECONDS.format(point.timestamp))
            .append("</time>")
            .append('\n')

        sb.append("      </trkpt>").append('\n')
    }

    private companion object {
        const val GPX_VERSION = "1.1"
        const val GPX_1_1_NAMESPACE = "http://www.topografix.com/GPX/1/1"
        const val CREATOR = "HealthAutoExport"

        /**
         * ISO 8601 UTC ở độ chính xác giây, ví dụ `2023-01-15T10:30:45Z` (Requirement 12.4).
         * Phần giây phụ (nano) bị lược bỏ khi định dạng theo mẫu `ss`.
         */
        val ISO_UTC_SECONDS: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

        /** Vĩ độ/kinh độ: 6 chữ số thập phân (độ), dấu chấm thập phân độc lập locale. */
        fun formatCoordinate(value: Double): String =
            BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).toPlainString()

        /** Độ cao: 2 chữ số thập phân (mét), dấu chấm thập phân độc lập locale. */
        fun formatElevation(value: Double): String =
            BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toPlainString()

        /** Escape các ký tự đặc biệt cho nội dung văn bản XML (dùng cho `<name>`). */
        fun escapeXmlText(text: String): String {
            val out = StringBuilder(text.length)
            for (ch in text) {
                when (ch) {
                    '&' -> out.append("&amp;")
                    '<' -> out.append("&lt;")
                    '>' -> out.append("&gt;")
                    '"' -> out.append("&quot;")
                    '\'' -> out.append("&apos;")
                    else -> out.append(ch)
                }
            }
            return out.toString()
        }
    }
}
