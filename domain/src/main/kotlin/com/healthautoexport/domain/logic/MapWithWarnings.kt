package com.healthautoexport.domain.logic

import com.healthautoexport.domain.model.DataSourceId
import com.healthautoexport.domain.model.UnifiedRecord
import com.healthautoexport.domain.port.ReadWarning

/**
 * Logic **thuần, không bao giờ ném ngoại lệ** hiện thực hóa quy tắc "bỏ-qua-và-tiếp-tục" khi ánh
 * xạ bản ghi nguồn thô sang mô hình domain (Property 34, Requirements 4.5, 4.7, 6.6).
 *
 * `Data_Reader` của mỗi nguồn thường phải ánh xạ một hỗn hợp bản ghi: bản ánh xạ được, bản không
 * ánh xạ được, và bản thiếu trường bắt buộc. Yêu cầu là: giữ lại đúng các bản/các trường khả dụng,
 * ghi một cảnh báo cho mỗi bản/trường bị bỏ, và **không** để một bản ghi lỗi làm hủy cả Export_Job
 * (Requirements 4.7, 6.6). Tách logic này thành hàm thuần cho phép kiểm thử Property 34 mà không
 * cần nguồn thật.
 *
 * Bất biến (invariant):
 * - Hàm **không bao giờ** ném ngoại lệ ra ngoài: ngay cả khi mapper do người gọi cung cấp ném,
 *   ngoại lệ được bắt và chuyển thành một cảnh báo [ReadWarning] (bản ghi bị bỏ).
 * - Thứ tự được bảo toàn: [MapWithWarningsResult.kept] và [MapWithWarningsResult.warnings] theo
 *   đúng thứ tự duyệt đầu vào.
 * - Mỗi bản ghi đóng góp 0..1 giá trị giữ lại và 0..n cảnh báo.
 */
object MapWithWarnings {

    /**
     * Ánh xạ tổng quát từng phần tử của [raw] qua [map], gom giá trị giữ lại và cảnh báo, đảm bảo
     * không ném ngoại lệ (Property 34, Requirement 4.7).
     *
     * Với mỗi phần tử:
     * - Nếu [map] trả về [MapOutcome.Kept], giữ lại giá trị và cộng dồn cảnh báo của nó.
     * - Nếu [map] trả về [MapOutcome.Skipped], bỏ qua và cộng dồn cảnh báo của nó.
     * - Nếu [map] **ném** một [Throwable] (kể cả lỗi không lường trước), bắt lại và tạo một cảnh
     *   báo qua [onError] rồi tiếp tục — Export_Job không bị hủy.
     *
     * @param R kiểu bản ghi nguồn thô.
     * @param T kiểu giá trị domain được tạo ra.
     * @param raw danh sách bản ghi nguồn cần ánh xạ.
     * @param onError tạo [ReadWarning] mô tả lỗi khi [map] ném ngoại lệ cho một bản ghi; SHALL
     *   không chứa dữ liệu thô.
     * @param map hàm ánh xạ một bản ghi sang [MapOutcome]; có thể ném ngoại lệ.
     * @return [MapWithWarningsResult] gồm các giá trị giữ lại và toàn bộ cảnh báo, đúng thứ tự.
     */
    fun <R, T> collect(
        raw: List<R>,
        onError: (record: R, error: Throwable) -> ReadWarning,
        map: (R) -> MapOutcome<T>,
    ): MapWithWarningsResult<T> {
        val kept = ArrayList<T>(raw.size)
        val warnings = ArrayList<ReadWarning>()
        for (record in raw) {
            val outcome: MapOutcome<T> = try {
                map(record)
            } catch (error: Throwable) {
                MapOutcome.Skipped(listOf(onError(record, error)))
            }
            when (outcome) {
                is MapOutcome.Kept -> {
                    kept += outcome.value
                    warnings += outcome.warnings
                }
                is MapOutcome.Skipped -> warnings += outcome.warnings
            }
        }
        return MapWithWarningsResult(kept = kept, warnings = warnings)
    }

    /**
     * Biến thể chuyên cho [UnifiedRecord] **bảo toàn định danh nguồn gốc** (Property 33,
     * Requirement 4.5) kết hợp bỏ-qua-và-tiếp-tục (Property 34, Requirements 4.7, 6.6).
     *
     * Mọi bản ghi giữ lại được **đóng dấu** [DataSourceId] của nguồn đang đọc ([source]) bằng
     * cách sao chép với `dataSourceId = source`. Vì `Data_Reader` luôn biết nó đang đọc từ nguồn
     * nào, việc đóng dấu này bảo đảm mỗi [UnifiedRecord] mang một `dataSourceId` không rỗng và
     * đúng nguồn gốc — bất biến cần được giữ qua các bước merge/aggregate sau này (Requirement 4.5).
     *
     * Hàm thuần và không bao giờ ném ngoại lệ (xem [collect]).
     *
     * @param raw danh sách bản ghi nguồn thô.
     * @param source nguồn đang đọc; mỗi bản ghi giữ lại sẽ mang đúng định danh này.
     * @param onError tạo [ReadWarning] khi mapper ném ngoại lệ (mặc định dùng [source]).
     * @param map hàm ánh xạ một bản ghi sang [MapOutcome] của [UnifiedRecord]; có thể ném ngoại lệ.
     * @return [MapWithWarningsResult] với mọi bản ghi giữ lại mang `dataSourceId == source`.
     */
    fun <R> mapRecords(
        raw: List<R>,
        source: DataSourceId,
        onError: (record: R, error: Throwable) -> ReadWarning = { _, error ->
            ReadWarning(
                source = source,
                metric = null,
                message = "Bỏ qua một bản ghi không ánh xạ được: ${error.message ?: error.javaClass.simpleName}",
            )
        },
        map: (R) -> MapOutcome<UnifiedRecord>,
    ): MapWithWarningsResult<UnifiedRecord> {
        val result = collect(raw = raw, onError = onError, map = map)
        // Đóng dấu nguồn gốc lên mọi bản ghi giữ lại để bảo toàn dataSourceId (Requirement 4.5).
        val stamped = result.kept.map { record ->
            if (record.dataSourceId == source) record else record.copy(dataSourceId = source)
        }
        return MapWithWarningsResult(kept = stamped, warnings = result.warnings)
    }
}
