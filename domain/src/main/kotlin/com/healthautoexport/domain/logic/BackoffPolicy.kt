package com.healthautoexport.domain.logic

import java.time.Duration

/**
 * Chính sách thử lại exponential backoff cho Export_Job theo lịch do Scheduler (WorkManager) điều
 * phối (Requirement 15.7).
 *
 * Hàm thuần: độ trễ của lần thử thứ `n` (n trong `1..5`) bằng `min(30s × 2^(n-1), 30 phút)`, và tổng
 * số lần thử không vượt [MAX_ATTEMPTS] = 5.
 *
 * Dãy độ trễ tương ứng n = 1..5: 30s, 60s, 120s, 240s, 480s (chưa lần nào chạm trần 30 phút với 5
 * lần thử; trần vẫn được áp dụng để an toàn nếu sau này mở rộng số lần thử).
 *
 * Property 49 — *Validates: Requirements 15.7*.
 */
object BackoffPolicy {

    /** Độ trễ cơ sở của lần thử đầu tiên: 30 giây. */
    val BASE_DELAY: Duration = Duration.ofSeconds(30)

    /** Trần độ trễ cho mỗi lần thử: 30 phút. */
    val MAX_DELAY: Duration = Duration.ofMinutes(30)

    /** Số lần thử lại tối đa (Requirement 15.7). */
    const val MAX_ATTEMPTS: Int = 5

    /**
     * Trả về độ trễ trước lần thử thứ [n].
     *
     * @param n số thứ tự lần thử, SHALL nằm trong `1..`[MAX_ATTEMPTS].
     * @return `min(30s × 2^(n-1), 30 phút)`.
     * @throws IllegalArgumentException nếu [n] ngoài `1..`[MAX_ATTEMPTS].
     */
    fun delayForAttempt(n: Int): Duration {
        require(n in 1..MAX_ATTEMPTS) { "n ($n) phải nằm trong 1..$MAX_ATTEMPTS" }
        // 2^(n-1) với n <= 5 ⇒ tối đa 16, không tràn số.
        val multiplier = 1L shl (n - 1)
        val scaled = BASE_DELAY.multipliedBy(multiplier)
        return if (scaled > MAX_DELAY) MAX_DELAY else scaled
    }

    /**
     * Liệt kê toàn bộ [MAX_ATTEMPTS] độ trễ theo thứ tự lần thử 1..5.
     *
     * @return danh sách 5 [Duration] theo đúng thứ tự lần thử.
     */
    fun delaySchedule(): List<Duration> = (1..MAX_ATTEMPTS).map(::delayForAttempt)
}
