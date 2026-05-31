package com.healthautoexport.serialization.json

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonUnquotedLiteral
import java.math.BigDecimal
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/**
 * Các tiện ích định dạng/đọc dùng chung cho JSON_Serializer và JSON_Parser, tập trung tại một
 * nơi để bảo đảm serialize và parse luôn nhất quán (điều kiện cần cho round-trip — Property 1).
 *
 * Hai mối quan tâm chính:
 * - **Dấu thời gian** theo mẫu `yyyy-MM-dd HH:mm:ss Z` (Requirements 10.4, 10.7); offset được giữ
 *   để khôi phục cả [Instant] lẫn [ZoneOffset] gốc khi parse.
 * - **Số `qty`** (và các đại lượng [BigDecimal] khác) ghi theo **ký pháp thập phân** (không khoa
 *   học), giữ nguyên dấu và **tối thiểu 6 chữ số sau dấu thập phân** mà không làm tròn mất dữ liệu
 *   (Requirement 10.5).
 */
internal object JsonFormat {

    /** Số chữ số thập phân tối thiểu của `qty` theo Requirement 10.5. */
    const val MIN_FRACTION_DIGITS: Int = 6

    /**
     * Bộ định dạng dấu thời gian `yyyy-MM-dd HH:mm:ss Z` (Requirements 10.4, 10.7).
     *
     * Mẫu `Z` (RFC-822) xuất offset dạng `+HHMM` (vd `+0000`, `+0700`), nhờ đó offset của bản ghi
     * được nhúng vào chuỗi và khôi phục được khi parse. Dùng [Locale.ROOT] để tránh phụ thuộc
     * ngôn ngữ thiết bị.
     */
    val TIMESTAMP_FORMATTER: DateTimeFormatter =
        DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss Z")
            .toFormatter(Locale.ROOT)

    /**
     * Định dạng một [Instant] tại [offset] thành chuỗi `yyyy-MM-dd HH:mm:ss Z`.
     *
     * @param instant mốc thời gian (UTC).
     * @param offset độ lệch múi giờ dùng để hiển thị thời gian cục bộ và hậu tố offset.
     */
    fun formatTimestamp(instant: Instant, offset: ZoneOffset): String =
        TIMESTAMP_FORMATTER.format(instant.atOffset(offset))

    /**
     * Định dạng một [Instant] ở UTC (offset `+0000`). Dùng cho các mốc thời gian không mang
     * [ZoneOffset] riêng trong mô hình (vd thời điểm Workout, điểm tuyến đường, mẫu nhịp tim).
     */
    fun formatTimestampUtc(instant: Instant): String =
        formatTimestamp(instant, ZoneOffset.UTC)

    /**
     * Phân tích một chuỗi `yyyy-MM-dd HH:mm:ss Z` thành [OffsetDateTime], từ đó suy ra cả
     * [Instant] (`.toInstant()`) lẫn [ZoneOffset] (`.offset`).
     *
     * @throws java.time.format.DateTimeParseException nếu chuỗi không khớp mẫu — caller chịu
     *   trách nhiệm bao lại thành [JsonParseException] kèm con trỏ vị trí (Requirement 10.9).
     */
    fun parseTimestamp(text: String): OffsetDateTime =
        OffsetDateTime.parse(text, TIMESTAMP_FORMATTER)

    /**
     * Biểu diễn [value] dưới dạng chuỗi số thập phân (không khoa học), giữ nguyên dấu và **tối
     * thiểu 6 chữ số sau dấu thập phân** mà không làm tròn mất dữ liệu (Requirement 10.5).
     *
     * Khi `scale < 6`, mở rộng scale lên 6 bằng [BigDecimal.setScale] (không cần làm tròn vì chỉ
     * thêm chữ số 0). Khi `scale ≥ 6`, giữ nguyên toàn bộ chữ số. [BigDecimal.toPlainString] bảo
     * đảm không dùng ký pháp lũy thừa kể cả với scale âm.
     */
    fun formatDecimal(value: BigDecimal): String {
        val normalized = if (value.scale() < MIN_FRACTION_DIGITS) {
            value.setScale(MIN_FRACTION_DIGITS)
        } else {
            value
        }
        return normalized.toPlainString()
    }

    /**
     * [JsonElement] số (literal không bao nháy) cho một [BigDecimal], định dạng theo
     * [formatDecimal]. Dùng [JsonUnquotedLiteral] để viết đúng chuỗi số mong muốn thay vì để
     * thư viện tự định dạng [Double] (tránh ký pháp khoa học và mất chính xác — Requirement 10.5).
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun decimalElement(value: BigDecimal): JsonElement =
        JsonUnquotedLiteral(formatDecimal(value))
}
