package com.healthautoexport.domain.port

import com.healthautoexport.domain.model.Automation

/**
 * Port lên lịch chạy Automation trong nền (Requirements 15.1–15.3, 14.9).
 *
 * Hiện thực bằng WorkManager `PeriodicWorkRequest` ở tầng dữ liệu (task 20.1): khoảng lặp
 * 15 phút–30 ngày (Requirement 15.3), exponential backoff bắt đầu 30s tối đa 5 lần
 * (Requirement 15.7), unique name = `automationId` với `ExistingPeriodicWorkPolicy.UPDATE`,
 * dedupe lần chạy chồng lấn (Requirement 15.5).
 */
interface Scheduler {

    /** Lên lịch (hoặc cập nhật lịch) cho [automation] (Requirements 15.1–15.3). */
    fun schedule(automation: Automation)

    /** Hủy lịch của Automation theo [automationId] (Requirement 14.9). */
    fun cancel(automationId: String)
}
