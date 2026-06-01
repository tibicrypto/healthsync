package com.healthautoexport.data.scheduler

import com.healthautoexport.domain.model.Automation
import com.healthautoexport.domain.model.DestinationType
import com.healthautoexport.domain.port.DestinationConfig
import com.healthautoexport.domain.port.CredentialStore

/**
 * Bộ phân giải [DestinationConfig] cụ thể từ tham chiếu cấu hình của một [Automation]
 * ([Automation.destinationConfigRef] + [Automation.destinationType]).
 *
 * ### Vì sao cần một port riêng ở `:data`
 * Một [Automation] (cả ở domain lẫn `AutomationEntity` của Room) **không** nhúng trực tiếp
 * [DestinationConfig]; nó chỉ mang [Automation.destinationType] và một **tham chiếu** chuỗi
 * [Automation.destinationConfigRef] tới cấu hình đã lưu, còn credential nằm tách biệt trong
 * [CredentialStore] (Requirement 22.9). Khi [ExportWorker] chạy theo lịch trong nền, nó cần dựng
 * lại một [DestinationConfig] hoàn chỉnh để truyền cho `RunExportJobUseCase`.
 *
 * Tại thời điểm hiện tại (task 20.1) **chưa** tồn tại một kho lưu/“resolver” cho
 * [DestinationConfig] trong `:data`, nên ta khai báo port nhỏ này và tiêm nó vào [ExportWorker].
 * Lớp ráp nối (task 22.1) sẽ cung cấp hiện thực thật: đọc cấu hình (không bí mật) đã lưu theo
 * [Automation.destinationConfigRef] (vd qua DataStore/Room), nạp credential tương ứng từ
 * [CredentialStore] nếu cần, rồi trả về biến thể [DestinationConfig] đúng [DestinationType].
 *
 * ### Hợp đồng (contract)
 * - Trả về [DestinationConfig] đã dựng đầy đủ nếu phân giải được.
 * - Trả về `null` nếu **không** tìm thấy cấu hình cho [destinationConfigRef] (vd người dùng đã xóa
 *   cấu hình). Khi đó `RunExportJobUseCase` coi như **chưa cấu hình Destination** và kết thúc job
 *   thất bại mà không phát sinh kết nối mạng đi (Requirement 22.4) — đây là thất bại **không** đủ
 *   điều kiện thử lại vì retry không làm cấu hình xuất hiện trở lại.
 * - Hiện thực **không** được ghi giá trị bí mật ra log (Requirements 22.9, 23.4).
 */
interface DestinationConfigResolver {

    /**
     * Phân giải cấu hình Destination cho một lần chạy theo lịch.
     *
     * @param destinationType loại Destination của Automation (Requirement 14.2).
     * @param destinationConfigRef tham chiếu tới cấu hình đã lưu (Requirement 22.9).
     * @return [DestinationConfig] tương ứng, hoặc `null` nếu không phân giải được.
     */
    suspend fun resolve(
        destinationType: DestinationType,
        destinationConfigRef: String,
    ): DestinationConfig?
}
