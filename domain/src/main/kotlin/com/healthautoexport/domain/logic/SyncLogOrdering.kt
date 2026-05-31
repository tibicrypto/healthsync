package com.healthautoexport.domain.logic

import com.healthautoexport.domain.model.SyncLogEntry

/**
 * Chính sách **thuần** sắp xếp thứ tự hiển thị Sync_Log (Requirement 23.3) — nền tảng cho
 * Property 53.
 *
 * Quy tắc hiển thị: **giảm dần theo `completionUtc`** (mới hoàn tất nhất lên trước); với các mục
 * cùng `completionUtc`, **tie-break giảm dần theo `startUtc`** (Requirement 23.3).
 *
 * **Xử lý null (`completionUtc == null`):** một mục chưa có `completionUtc` là Export_Job **đang
 * chạy / chưa hoàn tất**. Theo trực giác "mới nhất trước", các mục này được coi là **mới nhất** và
 * được đặt **lên đầu** danh sách (nulls-first). Lựa chọn này:
 * - nhất quán cho mọi tập đầu vào (xác định),
 * - phản ánh trạng thái "in-progress" đáng chú ý nhất cho người dùng,
 * - vẫn áp dụng tie-break `startUtc` giảm dần giữa các mục null với nhau.
 *
 * Thuần và xác định nên dễ kiểm thử dựa-trên-thuộc-tính.
 */
object SyncLogOrdering {

    /**
     * Comparator hiển thị: giảm dần `completionUtc` (null đứng trước), tie-break giảm dần `startUtc`.
     *
     * Lưu ý: comparator này **không** đối xứng với null theo nghĩa "nulls-last"; null được coi là
     * lớn nhất (mới nhất) một cách nhất quán.
     */
    val DISPLAY_ORDER: Comparator<SyncLogEntry> =
        Comparator<SyncLogEntry> { a, b ->
            val ca = a.completionUtc
            val cb = b.completionUtc
            when {
                ca == null && cb == null -> 0
                ca == null -> -1 // a (đang chạy) đứng trước b
                cb == null -> 1 // b (đang chạy) đứng trước a
                else -> cb.compareTo(ca) // giảm dần theo completionUtc
            }
        }.thenComparator { a, b ->
            b.startUtc.compareTo(a.startUtc) // tie-break: giảm dần theo startUtc
        }

    /**
     * Trả về [entries] đã sắp xếp theo thứ tự hiển thị ([DISPLAY_ORDER]) (Requirement 23.3).
     *
     * Hàm thuần: không làm thay đổi danh sách đầu vào, trả về danh sách mới. Sắp xếp **ổn định**
     * (stable) theo `List.sortedWith`, nên các mục bằng nhau hoàn toàn theo comparator giữ nguyên
     * thứ tự tương đối ban đầu.
     *
     * @param entries danh sách mục Sync_Log cần sắp xếp.
     * @return danh sách mới đã sắp theo thứ tự hiển thị.
     */
    fun sortForDisplay(entries: List<SyncLogEntry>): List<SyncLogEntry> =
        entries.sortedWith(DISPLAY_ORDER)
}
