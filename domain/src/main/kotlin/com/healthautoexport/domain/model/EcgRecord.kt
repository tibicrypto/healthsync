package com.healthautoexport.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset

/**
 * Bản ghi điện tâm đồ (ECG) ở cấp envelope — một trong tám danh mục của [ExportDataset]
 * (Requirement 10.1), phản chiếu lược đồ `ecg` của Health Auto Export gốc.
 *
 * Khác với [MetricValue.Ecg] (giá trị bên trong một [UnifiedRecord]), `EcgRecord` là mô hình
 * danh mục độc lập đứng trong `ExportDataset.ecg`, mang đầy đủ dấu thời gian, múi giờ và định
 * danh nguồn để tuần tự hóa trực tiếp.
 *
 * Giữ trọn chi tiết thiết bị ghi nhận: phân loại, nhịp tim trung bình, tần số lấy mẫu và
 * **toàn bộ chuỗi mẫu điện áp theo đúng thứ tự ghi nhận** (Requirement 6.2).
 *
 * @property timestamp thời điểm ghi nhận bản ghi ECG, lưu theo UTC (Requirement 9.4).
 * @property zoneOffset độ lệch múi giờ để định dạng dấu thời gian có hậu tố vùng.
 * @property classification chuỗi phân loại ECG do nguồn cung cấp (vd nhịp xoang, rung nhĩ...).
 * @property averageBpm nhịp tim trung bình; SHALL nằm trong khoảng `0..300` bpm (Requirement 6.2).
 * @property samplingHz tần số lấy mẫu (Hz); SHALL là số dương `> 0` (Requirement 6.2).
 * @property voltages chuỗi mẫu điện áp ([CanonicalUnit.MICROVOLT]) **theo thứ tự ghi nhận**,
 *   không sắp xếp lại (Requirement 6.2).
 * @property dataSourceId định danh Data_Source gốc của bản ghi (Requirement 4.5).
 */
data class EcgRecord(
    val timestamp: Instant,
    val zoneOffset: ZoneOffset,
    val classification: String,
    val averageBpm: Int,
    val samplingHz: BigDecimal,
    val voltages: List<BigDecimal>,
    val dataSourceId: DataSourceId,
) {
    init {
        require(averageBpm in 0..300) {
            "EcgRecord.averageBpm phải trong khoảng 0..300 bpm (Requirement 6.2), nhận được $averageBpm"
        }
        require(samplingHz.signum() > 0) {
            "EcgRecord.samplingHz phải > 0 (Requirement 6.2), nhận được $samplingHz"
        }
    }
}
