package com.healthautoexport.domain.usecase

import com.healthautoexport.domain.logic.DeepLinkConfigParser
import com.healthautoexport.domain.logic.ParsedDeepLink
import com.healthautoexport.domain.logic.ProposedAutomationConfig

/**
 * Kết quả xử lý một deep link cấu hình Automation (Requirements 14.6, 14.8).
 *
 * Kiểu sealed: hoặc đề xuất một cấu hình **chờ người dùng xác nhận** ([Proposal], Requirement 14.6),
 * hoặc báo lỗi ([Rejected], Requirement 14.8). Trong **cả hai** trường hợp, **không** có Automation
 * nào được tạo/lưu ở bước này.
 */
sealed interface DeepLinkResult {

    /**
     * Deep link hợp lệ ⇒ trình bày [config] để người dùng xác nhận trước khi lưu (Requirement 14.6).
     *
     * @property config các trường Automation đã phân tích/hợp lệ, đề xuất cho người dùng.
     */
    data class Proposal(val config: ProposedAutomationConfig) : DeepLinkResult

    /**
     * Deep link không hợp lệ ⇒ từ chối điền tự động, không tạo Automation (Requirement 14.8).
     *
     * @property reason loại lỗi (thiếu/sai định dạng/ngoài tập).
     * @property param tên tham số gây lỗi nếu xác định được; ngược lại `null`.
     * @property message mô tả người dùng đọc được chỉ ra tham số không hợp lệ.
     */
    data class Rejected(
        val reason: ParsedDeepLink.Reason,
        val param: String?,
        val message: String,
    ) : DeepLinkResult
}

/**
 * Xử lý deep link cấu hình Automation (Requirements 14.6, 14.8).
 *
 * `DeepLinkHandler` là một lớp mỏng bao quanh [DeepLinkConfigParser] thuần, đưa kết quả phân tích
 * về dạng [DeepLinkResult] hướng-tới-UI:
 * - Phân tích thành công ⇒ [DeepLinkResult.Proposal] để **người dùng xác nhận trước khi lưu**
 *   (Requirement 14.6). Handler **không** persist; việc lưu sau xác nhận đi qua
 *   [ConfigureAutomationUseCase.save] (nơi áp các xác thực còn lại như trùng tên).
 * - Phân tích thất bại ⇒ [DeepLinkResult.Rejected] mang lý do; **không** tạo Automation
 *   (Requirement 14.8).
 *
 * Lớp này không phụ thuộc Android (nhận sẵn `Map<String, String>` đã giải mã URL ở tầng gọi), nên
 * kiểm thử được trên JVM.
 */
class DeepLinkHandler(
    private val parser: DeepLinkConfigParser = DeepLinkConfigParser,
) {

    /**
     * Phân tích [params] và trả [DeepLinkResult] tương ứng.
     *
     * @param params bản đồ tham số deep link (đã giải mã URL).
     * @return [DeepLinkResult.Proposal] khi hợp lệ (chờ xác nhận); [DeepLinkResult.Rejected] khi
     *   có tham số thiếu/sai/ngoài tập.
     */
    fun handle(params: Map<String, String>): DeepLinkResult =
        when (val parsed = parser.parse(params)) {
            is ParsedDeepLink.Success -> DeepLinkResult.Proposal(parsed.config)
            is ParsedDeepLink.Failure -> DeepLinkResult.Rejected(
                reason = parsed.reason,
                param = parsed.param,
                message = parsed.message,
            )
        }
}
