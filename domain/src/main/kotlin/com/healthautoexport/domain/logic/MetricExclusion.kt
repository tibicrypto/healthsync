package com.healthautoexport.domain.logic

import com.healthautoexport.domain.model.DataSourceId
import com.healthautoexport.domain.model.HealthMetricType
import com.healthautoexport.domain.port.ReadWarning

/**
 * Lý do một [HealthMetricType] đã chọn bị loại khỏi Export_Job khi tính tập metric hiệu lực
 * (Property 28).
 *
 * Một enum lý do có cấu trúc (thay vì chỉ chuỗi tự do của [ReadWarning]) giúp logic này
 * **kiểm thử được** chính xác từng nhánh loại trừ — kiểu sẵn có [ReadWarning] không mang trường
 * lý do máy-đọc-được, nên ta bổ sung enum nhỏ này ngay trong package `logic`.
 */
enum class ExclusionReason {

    /**
     * Quyền/scope đọc tương ứng chưa được cấp trên bất kỳ nguồn đang bật & khả dụng nào có hỗ trợ
     * metric (Requirements 1.4, 1.6, 2.5). Bao gồm cả trường hợp người dùng thu hồi quyền đã cấp
     * trước đó, được phát hiện ở Export_Job kế tiếp (Requirements 1.6, 2.6).
     */
    PERMISSION_NOT_GRANTED,

    /**
     * Metric không được bất kỳ Data_Source đang bật & khả dụng nào cung cấp trên thiết bị hiện tại
     * (Requirements 4.3, 4.6). Khi không có nguồn nào được truy vấn, mọi metric đã chọn rơi vào
     * lý do này.
     */
    UNSUPPORTED_ON_DEVICE,
}

/**
 * Một mục loại trừ metric: metric bị loại, lý do có cấu trúc và mô tả người dùng đọc được
 * (Property 28, Requirements 1.4, 1.6, 2.5, 4.3, 4.6).
 *
 * Đây là kết quả **chính** của [MetricSelectionResolver.effectiveMetrics]; nó có thể được chiếu
 * sang [ReadWarning] để ghi Sync_Log qua [toReadWarnings].
 *
 * @property metric metric đã chọn nhưng bị loại khỏi Export_Job.
 * @property reason lý do loại trừ có cấu trúc.
 * @property message mô tả người dùng đọc được; SHALL KHÔNG chứa dữ liệu thô.
 * @property relatedSources các Data_Source đã được xét và liên quan tới quyết định loại trừ —
 *   với [ExclusionReason.PERMISSION_NOT_GRANTED] là các nguồn có hỗ trợ metric nhưng thiếu quyền;
 *   với [ExclusionReason.UNSUPPORTED_ON_DEVICE] là các nguồn được truy vấn (có thể rỗng khi không
 *   có nguồn nào được bật & khả dụng).
 */
data class MetricExclusion(
    val metric: HealthMetricType,
    val reason: ExclusionReason,
    val message: String,
    val relatedSources: Set<DataSourceId> = emptySet(),
) {
    /**
     * Chiếu mục loại trừ này sang các [ReadWarning] để ghi Sync_Log (Requirements 1.4, 2.5, 4.3).
     *
     * Phát ra **một** [ReadWarning] cho mỗi nguồn trong [relatedSources] (mỗi cảnh báo mang một
     * `source` hợp lệ, không rỗng). Khi [relatedSources] rỗng (không có nguồn nào được truy vấn —
     * tình huống được xử lý ở mức nguồn theo Requirements 3.6/3.7), không phát ra `ReadWarning`
     * nào; mục loại trừ vẫn hiện diện trong danh sách [MetricExclusion] có cấu trúc.
     */
    fun toReadWarnings(): List<ReadWarning> =
        relatedSources
            .sortedBy { it.id }
            .map { source -> ReadWarning(source = source, metric = metric, message = message) }
}
