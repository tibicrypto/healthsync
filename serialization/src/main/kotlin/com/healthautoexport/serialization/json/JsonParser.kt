package com.healthautoexport.serialization.json

import com.healthautoexport.domain.model.ExportDataset

/**
 * JSON_Parser — đọc Export_Format JSON của App trở lại thành một [ExportDataset]
 * (Requirements 10.8, 10.9).
 *
 * Trả về [Result] thay vì ném ngoại lệ xuyên tầng: đầu vào hợp lệ → [Result.success] với dataset
 * khôi phục; đầu vào **không** tuân theo Export_Format của App → [Result.failure] mang một
 * [JsonParseException] mô tả phần tử vi phạm, và **không** tạo ra dataset một phần
 * (Requirement 10.9). Việc phân tích chỉ commit kết quả sau khi toàn bộ envelope được xác thực,
 * nhờ vậy không bao giờ rò rỉ một dataset dựng dở khi gặp lỗi.
 *
 * Cùng với [JsonSerializer], bộ phân tích này bảo toàn thuộc tính **round-trip**: parse kết quả
 * do serializer sinh ra cho lại một dataset bằng dataset ban đầu (Requirement 10.8, Property 1).
 */
interface JsonParser {

    /**
     * Phân tích [text] thành [ExportDataset].
     *
     * @param text văn bản JSON theo Export_Format của App.
     * @return [Result.success] với dataset khôi phục khi hợp lệ; [Result.failure] với
     *   [JsonParseException] chỉ rõ phần tử vi phạm khi không hợp lệ — không tạo dataset một phần
     *   (Requirement 10.9).
     */
    fun parse(text: String): Result<ExportDataset>
}

/**
 * Lỗi phân tích JSON mô tả **phần tử vi phạm** trong đầu vào không tuân thủ Export_Format của App
 * (Requirement 10.9).
 *
 * [pointer] là một đường dẫn dạng JSON-pointer rút gọn tới vị trí lỗi (vd
 * `data/metrics[2]/data[0]/qty`) giúp người tích hợp định vị nhanh phần tử sai.
 *
 * @property pointer đường dẫn tới phần tử vi phạm trong cây JSON.
 * @property detail mô tả người đọc được về lý do phần tử không hợp lệ.
 */
class JsonParseException(
    val pointer: String,
    val detail: String,
) : Exception("JSON không hợp lệ tại '$pointer': $detail")
