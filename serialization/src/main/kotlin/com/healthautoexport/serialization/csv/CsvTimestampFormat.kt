package com.healthautoexport.serialization.csv

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Định dạng dấu thời gian dùng cho CSV — mẫu `yyyy-MM-dd HH:mm:ss Z` (Requirement 11.4), trùng mẫu
 * mà JSON_Serializer dùng (Requirement 10.7) để giữ định dạng dấu thời gian thống nhất giữa các
 * Serializer (**Property 13**).
 *
 * Phần `Z` là độ lệch múi giờ theo RFC 822 (vd `+0700`, `+0000`), được sinh từ
 * [UnifiedRecord.zoneOffset][com.healthautoexport.domain.model.UnifiedRecord.zoneOffset] để chuỗi
 * sinh ra biểu diễn đúng thời điểm gốc và parse lại cho cùng [Instant].
 */
object CsvTimestampFormat {

    /** Mẫu định dạng chung cho dấu thời gian CSV/JSON (Requirements 11.4, 10.7). */
    const val PATTERN: String = "yyyy-MM-dd HH:mm:ss Z"

    private val FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern(PATTERN, Locale.US)

    /**
     * Định dạng [instant] tại [offset] theo [PATTERN].
     *
     * @param instant mốc thời gian UTC của bản ghi.
     * @param offset độ lệch múi giờ gốc của bản ghi; quyết định phần `Z` và các trường giờ/phút.
     */
    fun format(instant: Instant, offset: ZoneOffset): String =
        FORMATTER.format(instant.atOffset(offset))
}
