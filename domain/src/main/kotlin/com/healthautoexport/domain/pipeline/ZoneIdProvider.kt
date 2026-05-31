package com.healthautoexport.domain.pipeline

import java.time.ZoneId

/**
 * Cổng (port) cung cấp **múi giờ cục bộ của thiết bị** dùng cho việc căn ranh giới lịch khi tổng
 * hợp dữ liệu (Requirements 8.3, 8.8).
 *
 * `Aggregator` cần biết múi giờ thiết bị **tại thời điểm Export_Job** để xác định ranh giới khung
 * ngày/tuần/tháng/năm. Việc đọc múi giờ hệ thống là một tác vụ phụ thuộc môi trường (impure), nên
 * được tách ra sau interface này: tầng `:data`/DI cung cấp một hiện thực đọc
 * [java.time.ZoneId.systemDefault], còn trong test có thể truyền một múi giờ cố định để kiểm thử
 * xác định.
 *
 * Nhờ đó bản thân logic tổng hợp ([Aggregator.aggregate]) vẫn **thuần (pure)**: với cùng đầu vào và
 * cùng [java.time.ZoneId], kết quả luôn như nhau.
 */
fun interface ZoneIdProvider {

    /** Trả về múi giờ cục bộ của thiết bị dùng để căn ranh giới lịch (Requirement 8.3). */
    fun zone(): ZoneId
}
