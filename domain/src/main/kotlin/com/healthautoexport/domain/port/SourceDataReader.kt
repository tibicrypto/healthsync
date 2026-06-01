package com.healthautoexport.domain.port

import com.healthautoexport.domain.model.DateRange
import com.healthautoexport.domain.model.MetricSelection

/**
 * Port **đọc dữ liệu đa nguồn** cho pipeline Export_Job (Requirements 3.3–3.7, 4.x, 5.x, 6.x).
 *
 * ### Vì sao tồn tại port này
 * Bộ điều phối đọc thật sự — `DataReader` — sống ở tầng `:data` (Android: Health_Connect, Huawei),
 * nên `:domain` **không thể** phụ thuộc vào nó (phụ thuộc một chiều `:data → :domain`). Để
 * `RunExportJobUseCase` (thuần domain) vẫn ghép được bước đọc vào pipeline, ta đảo ngược phụ thuộc
 * (dependency inversion) bằng port hàm này: use case chỉ biết tới [SourceDataReader], còn
 * `DataReader` ở `:data` **hiện thực** nó (Requirement 3.3–3.7).
 *
 * Hiện thực có trách nhiệm: đọc theo `SourceToggleStore`, áp timeout 30s/nguồn, coi nguồn quá hạn
 * là không khả dụng và tiếp tục với nguồn còn lại, đồng thời trả đúng một trong các nhánh
 * [ReadOutcome] (Success / NoEnabledSource / AllSourcesUnavailable).
 *
 * Là `fun interface` để dễ cung cấp một lambda trong test (đọc giả lập) mà không cần lớp đầy đủ.
 */
fun interface SourceDataReader {

    /**
     * Đọc dữ liệu cho [selection] trong [range] từ các Data_Source đang bật & khả dụng.
     *
     * @param selection các metric/workout người dùng chọn (Requirements 1.2, 4.4, 5.7).
     * @param range khoảng thời gian đã phân giải (Requirement 9.x).
     * @return [ReadOutcome.Success] với kết quả theo từng nguồn; hoặc [ReadOutcome.NoEnabledSource]
     *   (Requirement 3.7) / [ReadOutcome.AllSourcesUnavailable] (Requirement 3.6).
     */
    suspend fun read(selection: MetricSelection, range: DateRange): ReadOutcome
}
