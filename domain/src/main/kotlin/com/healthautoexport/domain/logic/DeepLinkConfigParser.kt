package com.healthautoexport.domain.logic

import com.healthautoexport.domain.model.AggregationPeriod
import com.healthautoexport.domain.model.DestinationType
import com.healthautoexport.domain.model.ExportFormat

/**
 * Các trường cấu hình Automation được đề xuất sau khi phân tích thành công một deep link
 * (Requirements 14.6, 14.8).
 *
 * Đây **chưa phải** một [com.healthautoexport.domain.model.Automation] hoàn chỉnh: nó chỉ mang
 * các trường đã phân tích/kiểm tra hợp lệ để **trình bày cho người dùng xác nhận trước khi lưu**
 * (Requirement 14.6). Không có bước persist nào xảy ra ở tầng này.
 *
 * @property name tên Automation đề xuất (chưa kiểm tra trùng — việc đó do [AutomationNameValidator]).
 * @property exportFormat định dạng xuất đã hợp lệ.
 * @property aggregationPeriod mức tổng hợp đã hợp lệ.
 * @property destinationType loại Destination đã hợp lệ.
 * @property scheduleIntervalMinutes khoảng lặp lịch (phút) đã hợp lệ, nằm trong dải cho phép.
 */
data class ProposedAutomationConfig(
    val name: String,
    val exportFormat: ExportFormat,
    val aggregationPeriod: AggregationPeriod,
    val destinationType: DestinationType,
    val scheduleIntervalMinutes: Long,
)

/**
 * Kết quả phân tích một deep link cấu hình Automation (Requirement 14.8).
 *
 * Là kiểu **sealed**: phân tích chỉ có thể **thành công** ([Success], mang cấu hình đề xuất) hoặc
 * **thất bại** ([Failure], mang lý do mô tả). Khi thất bại, **không** có Automation nào được tạo.
 */
sealed interface ParsedDeepLink {

    /** `true` nếu phân tích thành công. */
    val isSuccess: Boolean get() = this is Success

    /**
     * Phân tích thành công.
     *
     * @property config các trường đề xuất để người dùng xác nhận trước khi lưu (Requirement 14.6).
     */
    data class Success(val config: ProposedAutomationConfig) : ParsedDeepLink

    /**
     * Phân tích thất bại — App từ chối điền tự động và không tạo Automation (Requirement 14.8).
     *
     * @property reason loại lỗi ([Reason]).
     * @property param tên tham số gây lỗi (nếu xác định được); `null` cho lỗi không gắn với một
     *   tham số cụ thể.
     * @property message mô tả người dùng đọc được chỉ ra tham số không hợp lệ (Requirement 14.8).
     */
    data class Failure(
        val reason: Reason,
        val param: String?,
        val message: String,
    ) : ParsedDeepLink

    /** Các nguyên nhân khiến deep link bị từ chối. */
    enum class Reason {
        /** Một tham số bắt buộc bị thiếu (Requirement 14.8). */
        MISSING_PARAM,

        /** Giá trị tham số sai định dạng (vd: số không parse được) (Requirement 14.8). */
        MALFORMED_VALUE,

        /** Giá trị nằm ngoài tập/dải giá trị được hỗ trợ (Requirement 14.8). */
        OUT_OF_RANGE,
    }
}

/**
 * Parser **thuần** (không I/O, không phụ thuộc Android) cho deep link cấu hình Automation
 * (Requirements 14.6, 14.8) — nền tảng cho Property 51.
 *
 * Nhận một `Map<String, String>` các tham số deep link và cố dựng một [ProposedAutomationConfig].
 * Với **mỗi** tham số:
 * - Tham số bắt buộc bị thiếu ⇒ [ParsedDeepLink.Failure] (MISSING_PARAM).
 * - `exportFormat` phải là tên hợp lệ của [ExportFormat]; `aggregationPeriod` của [AggregationPeriod];
 *   `destination` của [DestinationType] — nếu ngoài tập ⇒ Failure (OUT_OF_RANGE).
 * - `scheduleIntervalMinutes` phải parse được sang số (nếu không ⇒ MALFORMED_VALUE) và nằm trong
 *   dải [MIN_SCHEDULE_INTERVAL_MINUTES]..[MAX_SCHEDULE_INTERVAL_MINUTES] (nếu không ⇒ OUT_OF_RANGE).
 *
 * Nếu **bất kỳ** tham số nào thiếu/sai/ngoài tập, parser trả về [ParsedDeepLink.Failure] **mà
 * không tạo** Automation (Requirement 14.8). Chỉ khi tất cả hợp lệ mới trả [ParsedDeepLink.Success].
 *
 * Ghi chú thiết kế:
 * - Tên enum được so khớp **phân biệt hoa/thường** đúng theo hằng định danh (vd `"JSON"`, `"DAY"`,
 *   `"REST_API"`), để tránh nhập nhằng và giữ tính xác định.
 * - Dải khoảng lặp lịch trùng với Requirement 15.3 (15 phút .. 30 ngày = 43200 phút).
 * - Thứ tự kiểm tra tham số là cố định (name → format → period → destination → interval) nên với
 *   nhiều lỗi đồng thời, lỗi báo cáo là xác định (lỗi đầu tiên theo thứ tự này).
 */
object DeepLinkConfigParser {

    /** Khóa tham số: tên Automation. */
    const val PARAM_NAME: String = "name"

    /** Khóa tham số: định dạng xuất ([ExportFormat]). */
    const val PARAM_EXPORT_FORMAT: String = "exportFormat"

    /** Khóa tham số: mức tổng hợp ([AggregationPeriod]). */
    const val PARAM_AGGREGATION_PERIOD: String = "aggregationPeriod"

    /** Khóa tham số: loại Destination ([DestinationType]). */
    const val PARAM_DESTINATION: String = "destination"

    /** Khóa tham số: khoảng lặp lịch tính bằng phút. */
    const val PARAM_SCHEDULE_INTERVAL: String = "scheduleIntervalMinutes"

    /** Khoảng lặp lịch tối thiểu: 15 phút (Requirement 15.3). */
    const val MIN_SCHEDULE_INTERVAL_MINUTES: Long = 15

    /** Khoảng lặp lịch tối đa: 30 ngày = 43200 phút (Requirement 15.3). */
    const val MAX_SCHEDULE_INTERVAL_MINUTES: Long = 30L * 24 * 60

    /**
     * Phân tích [params] thành [ProposedAutomationConfig].
     *
     * @param params bản đồ tham số deep link (đã giải mã URL ở tầng gọi).
     * @return [ParsedDeepLink.Success] khi mọi tham số hợp lệ; ngược lại [ParsedDeepLink.Failure]
     *   chỉ rõ tham số vi phạm, **không** tạo Automation.
     */
    fun parse(params: Map<String, String>): ParsedDeepLink {
        // 1) name — bắt buộc, độ dài 1..100 (Requirements 14.1, 14.8).
        val name = params[PARAM_NAME]
            ?: return missing(PARAM_NAME)
        if (name.length < AutomationNameValidator.MIN_LENGTH ||
            name.length > AutomationNameValidator.MAX_LENGTH
        ) {
            return ParsedDeepLink.Failure(
                reason = ParsedDeepLink.Reason.OUT_OF_RANGE,
                param = PARAM_NAME,
                message = "Tham số '$PARAM_NAME' phải dài " +
                    "${AutomationNameValidator.MIN_LENGTH}..${AutomationNameValidator.MAX_LENGTH} ký tự.",
            )
        }

        // 2) exportFormat — phải thuộc tập ExportFormat.
        val formatRaw = params[PARAM_EXPORT_FORMAT]
            ?: return missing(PARAM_EXPORT_FORMAT)
        val exportFormat = ExportFormat.entries.firstOrNull { it.name == formatRaw }
            ?: return outOfSet(PARAM_EXPORT_FORMAT, formatRaw, ExportFormat.entries.map { it.name })

        // 3) aggregationPeriod — phải thuộc tập AggregationPeriod.
        val periodRaw = params[PARAM_AGGREGATION_PERIOD]
            ?: return missing(PARAM_AGGREGATION_PERIOD)
        val aggregationPeriod = AggregationPeriod.entries.firstOrNull { it.name == periodRaw }
            ?: return outOfSet(
                PARAM_AGGREGATION_PERIOD,
                periodRaw,
                AggregationPeriod.entries.map { it.name },
            )

        // 4) destination — phải thuộc tập DestinationType.
        val destinationRaw = params[PARAM_DESTINATION]
            ?: return missing(PARAM_DESTINATION)
        val destinationType = DestinationType.entries.firstOrNull { it.name == destinationRaw }
            ?: return outOfSet(
                PARAM_DESTINATION,
                destinationRaw,
                DestinationType.entries.map { it.name },
            )

        // 5) scheduleIntervalMinutes — số hợp lệ trong dải cho phép.
        val intervalRaw = params[PARAM_SCHEDULE_INTERVAL]
            ?: return missing(PARAM_SCHEDULE_INTERVAL)
        val intervalMinutes = intervalRaw.toLongOrNull()
            ?: return ParsedDeepLink.Failure(
                reason = ParsedDeepLink.Reason.MALFORMED_VALUE,
                param = PARAM_SCHEDULE_INTERVAL,
                message = "Tham số '$PARAM_SCHEDULE_INTERVAL' không phải số nguyên hợp lệ: '$intervalRaw'.",
            )
        if (intervalMinutes < MIN_SCHEDULE_INTERVAL_MINUTES ||
            intervalMinutes > MAX_SCHEDULE_INTERVAL_MINUTES
        ) {
            return ParsedDeepLink.Failure(
                reason = ParsedDeepLink.Reason.OUT_OF_RANGE,
                param = PARAM_SCHEDULE_INTERVAL,
                message = "Tham số '$PARAM_SCHEDULE_INTERVAL' phải nằm trong " +
                    "$MIN_SCHEDULE_INTERVAL_MINUTES..$MAX_SCHEDULE_INTERVAL_MINUTES phút.",
            )
        }

        return ParsedDeepLink.Success(
            ProposedAutomationConfig(
                name = name,
                exportFormat = exportFormat,
                aggregationPeriod = aggregationPeriod,
                destinationType = destinationType,
                scheduleIntervalMinutes = intervalMinutes,
            ),
        )
    }

    private fun missing(param: String): ParsedDeepLink.Failure =
        ParsedDeepLink.Failure(
            reason = ParsedDeepLink.Reason.MISSING_PARAM,
            param = param,
            message = "Thiếu tham số bắt buộc '$param'.",
        )

    private fun outOfSet(
        param: String,
        value: String,
        allowed: List<String>,
    ): ParsedDeepLink.Failure =
        ParsedDeepLink.Failure(
            reason = ParsedDeepLink.Reason.OUT_OF_RANGE,
            param = param,
            message = "Tham số '$param' có giá trị '$value' ngoài tập hợp lệ ${allowed.joinToString()}.",
        )
}
