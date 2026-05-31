package com.healthautoexport.domain.port

import com.healthautoexport.domain.model.DestinationType

/**
 * Port trừu tượng hóa một đích đến (Destination) nhận payload đã tuần tự hóa (Requirements 16–21).
 *
 * Mỗi hiện thực (REST API, Google Drive, Dropbox, MQTT, Home Assistant, Local Storage) đóng gói
 * chính sách retry và quy tắc đặt tên/ghi đè riêng; trả về [DestinationResult] để ghi Sync_Log.
 */
interface Destination {

    /** Loại Destination mà hiện thực này phục vụ. */
    val type: DestinationType

    /** Gửi [payload] theo [config]; trả về kết quả để ghi Sync_Log. */
    suspend fun send(payload: ExportPayload, config: DestinationConfig): DestinationResult
}
