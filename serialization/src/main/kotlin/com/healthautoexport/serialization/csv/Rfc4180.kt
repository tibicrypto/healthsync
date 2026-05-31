package com.healthautoexport.serialization.csv

/**
 * Tiện ích mã hóa/giải mã CSV theo [RFC 4180](https://www.rfc-editor.org/rfc/rfc4180), dùng chung
 * bởi [CsvSerializer] (ghi) và bởi property test round-trip ô (đọc lại) — **Property 8**.
 *
 * Quy ước escaping (Requirement 11.3):
 * - Một ô chứa dấu phẩy (`,`), dấu nháy kép (`"`), hoặc ký tự xuống dòng (`\r`/`\n`) SHALL được
 *   bao trong cặp nháy kép.
 * - Mọi dấu nháy kép bên trong ô được thoát bằng cách nhân đôi (`"` → `""`).
 * - Ô rỗng được ghi là chuỗi rỗng, không bao nháy (Requirement 11.5) và đọc lại đúng là rỗng.
 *
 * Mọi dòng (tiêu đề lẫn dữ liệu) do [CsvSerializer] phát ra đều kết thúc bằng [CRLF]
 * (Requirement 11.7). Bộ đọc bên dưới chấp nhận cả `\r\n` và `\n` đơn lẻ làm ranh giới bản ghi
 * để bền vững khi đọc lại, nhưng `\r`/`\n` nằm trong ô đã bao nháy luôn được giữ nguyên là nội
 * dung — nhờ đó việc serialize rồi parse một dòng khôi phục **chính xác** các giá trị trường gốc.
 */
object Rfc4180 {

    /** Ký tự kết thúc dòng bắt buộc của định dạng CSV: carriage return + line feed. */
    const val CRLF: String = "\r\n"

    private const val QUOTE = '"'
    private const val COMMA = ','
    private const val CR = '\r'
    private const val LF = '\n'

    /**
     * Mã hóa một giá trị ô đơn theo RFC 4180.
     *
     * Bao [raw] trong nháy kép và nhân đôi nháy bên trong khi (và chỉ khi) ô chứa dấu phẩy, nháy
     * kép, hoặc ký tự xuống dòng; ngược lại trả về nguyên văn (kể cả chuỗi rỗng). Requirement 11.3,
     * 11.5.
     */
    fun encodeField(raw: String): String {
        val mustQuote = raw.any { it == COMMA || it == QUOTE || it == CR || it == LF }
        if (!mustQuote) return raw
        val escaped = raw.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    /**
     * Mã hóa một danh sách trường thành một dòng CSV (chưa kèm ký tự kết thúc dòng).
     *
     * Các trường được nối bằng dấu phẩy, mỗi trường đi qua [encodeField]. Người gọi tự thêm [CRLF]
     * vào cuối dòng (Requirement 11.7).
     */
    fun encodeRow(fields: List<String>): String =
        fields.joinToString(separator = ",") { encodeField(it) }

    /**
     * Phân tích toàn bộ một tài liệu CSV thành danh sách bản ghi, mỗi bản ghi là danh sách trường.
     *
     * Hỗ trợ trường được bao nháy chứa dấu phẩy, nháy kép thoát (`""`) và ký tự xuống dòng nhúng.
     * Ranh giới bản ghi là `\r\n` (hoặc `\r`/`\n` đơn lẻ) khi đang ở ngoài ô bao nháy. Tài liệu rỗng
     * trả về danh sách rỗng.
     */
    fun parse(document: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var fields = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        val n = document.length

        fun endField() {
            fields.add(cell.toString())
            cell.setLength(0)
        }

        fun endRow() {
            endField()
            rows.add(fields)
            fields = mutableListOf()
        }

        while (i < n) {
            val c = document[i]
            if (inQuotes) {
                when {
                    c == QUOTE && i + 1 < n && document[i + 1] == QUOTE -> {
                        cell.append(QUOTE); i += 2
                    }
                    c == QUOTE -> {
                        inQuotes = false; i++
                    }
                    else -> {
                        cell.append(c); i++
                    }
                }
            } else {
                when (c) {
                    QUOTE -> {
                        inQuotes = true; i++
                    }
                    COMMA -> {
                        endField(); i++
                    }
                    CR -> {
                        // Tiêu thụ CRLF như một ranh giới bản ghi; CR đơn lẻ cũng kết thúc bản ghi.
                        if (i + 1 < n && document[i + 1] == LF) i += 2 else i++
                        endRow()
                    }
                    LF -> {
                        endRow(); i++
                    }
                    else -> {
                        cell.append(c); i++
                    }
                }
            }
        }

        // Bản ghi cuối không kết thúc bằng ký tự xuống dòng (hoặc trường đang dở) vẫn được thu nhận.
        if (cell.isNotEmpty() || fields.isNotEmpty()) {
            endRow()
        }
        return rows
    }

    /**
     * Phân tích một dòng CSV đơn (có thể chứa ký tự xuống dòng nhúng trong ô bao nháy) thành các
     * trường của nó. Dùng cho round-trip ô của **Property 8**: `parseRow(encodeRow(fields))` khôi
     * phục đúng `fields`. Chuỗi rỗng tương ứng một bản ghi gồm đúng một trường rỗng.
     */
    fun parseRow(row: String): List<String> {
        val rows = parse(row)
        return if (rows.isEmpty()) listOf("") else rows.first()
    }
}
