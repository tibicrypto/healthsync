package com.healthautoexport.domain.pipeline

import com.healthautoexport.domain.model.Automation
import com.healthautoexport.domain.model.DateRange
import com.healthautoexport.domain.model.UnifiedRecord
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Bộ phân giải [DateRange] thuần (pure) cho pipeline xuất (Requirement 9).
 *
 * Thành phần này tập trung toàn bộ logic về Date_Range tách khỏi I/O nên **xác định
 * (deterministic)** và dễ kiểm thử bằng property-based testing: mọi tham chiếu tới "thời điểm
 * hiện tại" đều đi qua [clock] được tiêm vào (Requirements 9.6, 9.7, 9.8, 9.9), không gọi
 * `Instant.now()` toàn cục. Với cùng một [Clock] và cùng đầu vào, kết quả luôn như nhau.
 *
 * Mọi mốc thời gian được diễn giải theo **UTC** (Requirement 9.4); việc lọc bao gồm **cả hai
 * đầu mút** `[start, end]` (Requirement 9.5).
 *
 * @property clock đồng hồ cung cấp "thời điểm hiện tại"; tiêm vào để kiểm thử xác định. Mặc định
 *   [Clock.systemUTC] cho mã sản xuất.
 */
class DateRangeResolver(
    private val clock: Clock = Clock.systemUTC(),
) {

    /**
     * Xác thực thứ tự của một Date_Range do người dùng nhập (Requirements 9.2, 9.3).
     *
     * Chấp nhận **khi và chỉ khi** `end ≥ start`, trong đó khoảng bằng không (`end == start`)
     * được xem là hợp lệ (Requirement 9.3); ngược lại (`end` đứng trước `start`) bị từ chối
     * (Requirement 9.2). Hàm không ném ngoại lệ: trả về [DateRangeValidation.Invalid] kèm lý do
     * thay vì để khối `init` của [DateRange] ném `IllegalArgumentException`, nhờ đó tầng trên có
     * thể giữ nguyên giá trị người dùng nhập để chỉnh sửa.
     *
     * @param start thời điểm bắt đầu (UTC).
     * @param end thời điểm kết thúc (UTC).
     * @return [DateRangeValidation.Valid] bọc [DateRange] khi hợp lệ; [DateRangeValidation.Invalid]
     *   kèm thông báo lỗi thứ tự khi không hợp lệ.
     */
    fun validate(start: Instant, end: Instant): DateRangeValidation =
        if (end.isBefore(start)) {
            DateRangeValidation.Invalid(
                "Thời điểm kết thúc ($end) đứng trước thời điểm bắt đầu ($start) (Requirement 9.2)",
            )
        } else {
            DateRangeValidation.Valid(DateRange(start, end))
        }

    /**
     * Lọc các bản ghi theo [range], chỉ giữ bản ghi có dấu thời gian nằm trong khoảng đóng
     * `[start, end]` **bao gồm cả hai đầu mút** (Requirements 9.4, 9.5).
     *
     * Phép so sánh thực hiện trên [Instant] — vốn là một điểm trên trục thời gian theo UTC — nên
     * việc so sánh tự động theo UTC, độc lập với `zoneOffset` hiển thị của từng bản ghi. Thứ tự
     * các bản ghi được giữ nguyên như đầu vào.
     *
     * @param records danh sách bản ghi đã chuẩn hóa.
     * @param range khoảng thời gian áp dụng.
     * @return danh sách con gồm các bản ghi thỏa `start ≤ timestamp ≤ end`.
     */
    fun filter(records: List<UnifiedRecord>, range: DateRange): List<UnifiedRecord> =
        records.filter { record -> isWithinClosedRange(record.timestamp, range) }

    /**
     * Điều chỉnh (clamp) thời điểm kết thúc về "thời điểm hiện tại" nếu nó nằm ở tương lai
     * (Requirement 9.6).
     *
     * Nếu `end` đứng sau thời điểm hiện tại (theo [clock]), thời điểm kết thúc hiệu lực được đặt
     * bằng hiện tại; ngược lại giữ nguyên. Trường hợp hiếm khi **toàn bộ** khoảng nằm ở tương lai
     * (`start` cũng sau hiện tại), thời điểm bắt đầu cũng được kéo về hiện tại để bảo toàn bất biến
     * `end ≥ start` của [DateRange], tạo ra một khoảng bằng không tại hiện tại. Cờ điều chỉnh giúp
     * tầng UI hiển thị thông báo "đã áp dụng điều chỉnh" (Requirement 9.6).
     *
     * @param range khoảng thời gian cần kiểm tra/điều chỉnh.
     * @return [ClampResult] gồm khoảng (đã hoặc chưa) điều chỉnh và cờ [ClampResult.adjusted].
     */
    fun clampFutureEnd(range: DateRange): ClampResult {
        val now = Instant.now(clock)
        if (!range.endUtc.isAfter(now)) {
            return ClampResult(range, adjusted = false)
        }
        // end ở tương lai -> kéo về hiện tại; nếu start cũng ở tương lai thì kéo cả start để giữ
        // bất biến end ≥ start (khoảng bằng không tại hiện tại).
        val clampedStart = if (range.startUtc.isAfter(now)) now else range.startUtc
        return ClampResult(DateRange(clampedStart, now), adjusted = true)
    }

    /**
     * Date_Range mặc định cho một Quick_Export khi người dùng không chỉ định (Requirement 9.7).
     *
     * Thời điểm bắt đầu là `00:00:00` của ngày theo lịch hiện tại tính theo **UTC**; thời điểm kết
     * thúc là hiện tại. Cả hai mốc lấy từ [clock] nên hàm xác định theo đồng hồ được tiêm.
     *
     * @return [DateRange] từ đầu ngày UTC hôm nay tới hiện tại.
     */
    fun defaultQuickExportRange(): DateRange {
        val now = Instant.now(clock)
        val startOfTodayUtc = now.atZone(ZoneOffset.UTC)
            .toLocalDate()
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
        return DateRange(startOfTodayUtc, now)
    }

    /**
     * Date_Range cho một lần chạy Automation theo lịch, dùng cửa sổ nối tiếp (Requirements 9.8,
     * 9.9).
     *
     * - Nếu tồn tại ít nhất một Export_Job thành công trước đó
     *   ([Automation.lastSuccessfulEndUtc] khác `null`): thời điểm bắt đầu bằng thời điểm kết thúc
     *   của lần thành công gần nhất (Requirement 9.8).
     * - Ngược lại: thời điểm bắt đầu bằng thời điểm Automation được kích hoạt lần đầu
     *   ([Automation.firstActivatedAtUtc], Requirement 9.9).
     *
     * Thời điểm kết thúc luôn là thời điểm chạy hiện tại (theo [clock]). Để bảo toàn bất biến
     * `end ≥ start` trước hiện tượng lệch đồng hồ / dữ liệu cũ (mốc bắt đầu lỡ sau hiện tại), nếu
     * `start` đứng sau hiện tại thì khoảng trở thành khoảng bằng không tại `start`.
     *
     * @param automation Automation đang chạy theo lịch.
     * @return [DateRange] cửa sổ nối tiếp cho lần chạy.
     * @throws IllegalArgumentException nếu không có lần chạy thành công trước đó **và**
     *   [Automation.firstActivatedAtUtc] là `null` (Automation chưa từng được kích hoạt) — đây là
     *   tiền điều kiện vi phạm vì một Automation chạy theo lịch phải có thời điểm kích hoạt.
     */
    fun automationRange(automation: Automation): DateRange {
        val now = Instant.now(clock)
        val start = automation.lastSuccessfulEndUtc
            ?: requireNotNull(automation.firstActivatedAtUtc) {
                "Automation '${automation.id}' chạy theo lịch nhưng thiếu cả lastSuccessfulEndUtc " +
                    "lẫn firstActivatedAtUtc (Requirements 9.8, 9.9)"
            }
        val end = if (start.isAfter(now)) start else now
        return DateRange(start, end)
    }

    /** Kiểm tra `timestamp` có nằm trong khoảng đóng `[start, end]` hay không (so sánh UTC). */
    private fun isWithinClosedRange(timestamp: Instant, range: DateRange): Boolean =
        !timestamp.isBefore(range.startUtc) && !timestamp.isAfter(range.endUtc)
}

/**
 * Kết quả xác thực thứ tự một Date_Range (Requirements 9.2, 9.3).
 *
 * Dùng kiểu sealed thay cho ngoại lệ để tầng trên giữ nguyên giá trị người dùng nhập khi không
 * hợp lệ và hiển thị thông báo xác thực.
 */
sealed interface DateRangeValidation {

    /**
     * Date_Range hợp lệ (`end ≥ start`).
     *
     * @property range khoảng thời gian đã được xác thực.
     */
    data class Valid(val range: DateRange) : DateRangeValidation

    /**
     * Date_Range không hợp lệ do thời điểm kết thúc đứng trước thời điểm bắt đầu.
     *
     * @property reason thông báo lỗi thứ tự để hiển thị cho người dùng.
     */
    data class Invalid(val reason: String) : DateRangeValidation
}

/**
 * Kết quả của thao tác clamp thời điểm kết thúc tương lai (Requirement 9.6).
 *
 * @property range khoảng thời gian hiệu lực sau khi xét điều chỉnh.
 * @property adjusted `true` nếu đã clamp thời điểm kết thúc về hiện tại; `false` nếu giữ nguyên.
 */
data class ClampResult(
    val range: DateRange,
    val adjusted: Boolean,
)
