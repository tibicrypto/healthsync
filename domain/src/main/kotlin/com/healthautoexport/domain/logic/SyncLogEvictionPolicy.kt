package com.healthautoexport.domain.logic

import com.healthautoexport.domain.model.SyncLogEntry

/**
 * Chính sách **thuần** thu hồi (eviction) Sync_Log theo giới hạn cấu hình (Requirement 23.5) —
 * nền tảng cho Property 54.
 *
 * Quy tắc: khi tổng số mục vượt giới hạn tối đa, **xóa lần lượt các mục sớm nhất trước** theo khóa
 * `(completionUtc, startUtc)` tăng dần, cho tới khi tổng số mục ≤ giới hạn (Requirement 23.5).
 * Giới hạn mặc định là [DEFAULT_MAX] (500) và cấu hình được trong dải
 * [MIN_MAX]..[MAX_MAX] (50..5000).
 *
 * **Xử lý null nhất quán với [SyncLogOrdering]:** một mục `completionUtc == null` là job **đang
 * chạy / chưa hoàn tất** và được coi là **mới nhất**; do đó khi xét "sớm nhất" để xóa, các mục
 * null được xem là **muộn nhất** (nulls-last theo thứ tự tăng dần) và bị xóa sau cùng — chúng được
 * ưu tiên **giữ lại**. Tie-break theo `startUtc` tăng dần.
 *
 * Thuần và xác định nên dễ kiểm thử dựa-trên-thuộc-tính.
 */
object SyncLogEvictionPolicy {

    /** Giới hạn mặc định số mục Sync_Log (Requirement 23.5). */
    const val DEFAULT_MAX: Int = 500

    /** Giới hạn cấu hình tối thiểu (Requirement 23.5). */
    const val MIN_MAX: Int = 50

    /** Giới hạn cấu hình tối đa (Requirement 23.5). */
    const val MAX_MAX: Int = 5000

    /**
     * Thứ tự thu hồi: **tăng dần** theo `(completionUtc, startUtc)` — mục "sớm nhất" đứng đầu và bị
     * xóa trước. Mục `completionUtc == null` (đang chạy, mới nhất) được đặt **cuối** (nulls-last),
     * nên được giữ lại lâu nhất.
     */
    val EVICTION_ORDER: Comparator<SyncLogEntry> =
        Comparator<SyncLogEntry> { a, b ->
            val ca = a.completionUtc
            val cb = b.completionUtc
            when {
                ca == null && cb == null -> 0
                ca == null -> 1 // a (đang chạy) là mới nhất -> xếp sau
                cb == null -> -1 // b (đang chạy) là mới nhất -> xếp sau
                else -> ca.compareTo(cb) // tăng dần theo completionUtc
            }
        }.thenComparator { a, b ->
            a.startUtc.compareTo(b.startUtc) // tie-break: tăng dần theo startUtc
        }

    /**
     * Kẹp (clamp) giá trị giới hạn cấu hình về dải hợp lệ [MIN_MAX]..[MAX_MAX] (Requirement 23.5).
     *
     * @param max giá trị giới hạn người dùng cấu hình (có thể ngoài dải).
     * @return giá trị đã kẹp trong [MIN_MAX]..[MAX_MAX].
     */
    fun clampMax(max: Int): Int = max.coerceIn(MIN_MAX, MAX_MAX)

    /**
     * Thu hồi các mục cũ nhất khỏi [entries] cho tới khi số mục ≤ giới hạn (Requirement 23.5).
     *
     * Hàm thuần: không làm thay đổi danh sách đầu vào. [max] được kẹp về dải hợp lệ qua [clampMax]
     * trước khi áp dụng, nên truyền giá trị ngoài dải vẫn an toàn. Các mục bị xóa là các mục sớm
     * nhất theo [EVICTION_ORDER]; các mục giữ lại được trả về theo **thứ tự hiển thị** của
     * [SyncLogOrdering.sortForDisplay] (mới nhất trước) để tiện cho tầng hiển thị.
     *
     * @param entries danh sách mục Sync_Log hiện có (gồm cả mục mới thêm).
     * @param max giới hạn cấu hình; mặc định [DEFAULT_MAX]. Được kẹp về [MIN_MAX]..[MAX_MAX].
     * @return danh sách mục được giữ lại (kích thước ≤ giới hạn đã kẹp), theo thứ tự hiển thị.
     */
    fun evict(entries: List<SyncLogEntry>, max: Int = DEFAULT_MAX): List<SyncLogEntry> {
        val limit = clampMax(max)
        if (entries.size <= limit) {
            return SyncLogOrdering.sortForDisplay(entries)
        }
        val evictCount = entries.size - limit
        val retained = entries
            .sortedWith(EVICTION_ORDER) // sớm nhất trước
            .drop(evictCount) // bỏ `evictCount` mục sớm nhất
        return SyncLogOrdering.sortForDisplay(retained)
    }
}
