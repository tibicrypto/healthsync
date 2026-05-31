package com.healthautoexport.domain.logic

import com.healthautoexport.domain.port.ReadWarning

/**
 * Kết quả của việc ánh xạ **một** bản ghi nguồn thô sang giá trị domain, dùng cho logic
 * "bỏ-qua-và-tiếp-tục" (Property 34, Requirements 4.7, 6.6).
 *
 * Một mapper trả về:
 * - [Kept] khi ánh xạ thành công — kèm 0..n cảnh báo (vd bản ghi thiếu một trường không bắt buộc
 *   nhưng các trường còn lại vẫn giữ được — Requirement 6.6); hoặc
 * - [Skipped] khi bản ghi không thể ánh xạ được (vd không nhận diện được loại metric, không
 *   chuyển được về đơn vị canonical — Requirement 4.7) — kèm cảnh báo giải thích.
 *
 * @param T kiểu giá trị domain được tạo ra (vd [com.healthautoexport.domain.model.UnifiedRecord]).
 */
sealed interface MapOutcome<out T> {

    /** Cảnh báo (nếu có) phát sinh khi xử lý bản ghi này. */
    val warnings: List<ReadWarning>

    /**
     * Ánh xạ thành công; giữ lại [value]. Có thể kèm cảnh báo cho phần dữ liệu bị thiếu/khuyết.
     *
     * @property value giá trị domain được giữ lại.
     * @property warnings cảnh báo về phần dữ liệu thiếu nhưng không làm bỏ bản ghi (Requirement 6.6).
     */
    data class Kept<out T>(
        val value: T,
        override val warnings: List<ReadWarning> = emptyList(),
    ) : MapOutcome<T>

    /**
     * Bỏ qua bản ghi vì không ánh xạ được; ghi cảnh báo và tiếp tục (Requirement 4.7).
     *
     * @property warnings cảnh báo giải thích vì sao bản ghi bị bỏ (kèm định danh nguồn gốc).
     */
    data class Skipped(
        override val warnings: List<ReadWarning>,
    ) : MapOutcome<Nothing>
}

/**
 * Kết quả tổng hợp của [MapWithWarnings.collect]: các giá trị giữ lại và toàn bộ cảnh báo gom được.
 *
 * @param T kiểu giá trị domain được giữ lại.
 * @property kept các giá trị ánh xạ thành công, theo đúng thứ tự đầu vào.
 * @property warnings toàn bộ cảnh báo (từ cả [MapOutcome.Kept] lẫn [MapOutcome.Skipped] lẫn các
 *   ngoại lệ bị bắt), theo đúng thứ tự đầu vào.
 */
data class MapWithWarningsResult<T>(
    val kept: List<T>,
    val warnings: List<ReadWarning>,
)
