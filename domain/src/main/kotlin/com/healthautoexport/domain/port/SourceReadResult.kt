package com.healthautoexport.domain.port

import com.healthautoexport.domain.model.UnifiedRecord
import com.healthautoexport.domain.model.Workout

/**
 * Kết quả đọc từ **một** Data_Source: các bản ghi đã chuẩn hóa cùng danh sách cảnh báo để ghi
 * Sync_Log (Requirements 4.7, 6.6).
 *
 * @property records các [UnifiedRecord] đã chuẩn hóa về đơn vị canonical (Requirement 4.2).
 * @property workouts các [Workout] đã đọc (Requirement 5.x).
 * @property warnings cảnh báo phát sinh (bản ghi bỏ qua, trường thiếu...) (Requirements 4.7, 6.6).
 */
data class SourceReadResult(
    val records: List<UnifiedRecord>,
    val workouts: List<Workout>,
    val warnings: List<ReadWarning> = emptyList(),
)
