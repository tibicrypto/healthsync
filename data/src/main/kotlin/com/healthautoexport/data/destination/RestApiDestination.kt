package com.healthautoexport.data.destination

import com.healthautoexport.domain.logic.HttpStatusClassifier
import com.healthautoexport.domain.logic.PayloadSizeGuard
import com.healthautoexport.domain.logic.RestConfigValidation
import com.healthautoexport.domain.logic.RestConfigValidator
import com.healthautoexport.domain.model.DestinationType
import com.healthautoexport.domain.port.Destination
import com.healthautoexport.domain.port.DestinationConfig
import com.healthautoexport.domain.port.DestinationResult
import com.healthautoexport.domain.port.ExportPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Hiện thực [Destination] cho đích đến REST API (Requirement 16).
 *
 * Dùng OkHttp gửi [ExportPayload.bytes] làm thân yêu cầu HTTP tới [DestinationConfig.RestApi.url]
 * với [DestinationConfig.RestApi.method] và các header tùy chỉnh. Tiêu đề `Content-Type` được đặt
 * bằng [ExportPayload.contentType] để khớp loại nội dung của Export_Format (Requirements 16.2, 16.3).
 *
 * Hành vi theo Requirement 16:
 * - **16.8**: Nếu kích thước payload vượt giới hạn ([PayloadSizeGuard]), trả về [DestinationResult.Failure]
 *   **mà không gửi** yêu cầu (không đủ điều kiện thử lại — payload quá lớn sẽ vẫn quá lớn).
 * - **16.2, 16.3**: Gửi `payload.bytes` làm body, `Content-Type` = `payload.contentType`, kèm header tùy chỉnh.
 * - **16.7**: Đặt call timeout 30 giây; lỗi kết nối hoặc hết thời gian chờ ⇒
 *   [DestinationResult.Failure] với `retryEligible = true` và nguyên nhân.
 * - **16.5, 16.6**: Mã 2xx ([HttpStatusClassifier.isSuccess]) ⇒ [DestinationResult.Success];
 *   ngoài 2xx ⇒ [DestinationResult.Failure] ghi lại mã trạng thái và thân phản hồi.
 *
 * Lưu ý về Requirement 16.4 (cảnh báo non-HTTPS): đây là mối quan tâm tại **thời điểm lưu cấu hình**
 * ở tầng UI — màn hình Destinations hiển thị cảnh báo "dữ liệu sẽ truyền không mã hóa" trước khi lưu
 * một URL `http://`. Tại thời điểm gửi, lớp này chỉ kiểm tra hợp lệ cấu hình qua [RestConfigValidator]
 * (scheme HTTP/HTTPS, độ dài URL, số header) và từ chối cấu hình không hợp lệ; cả `http` và `https`
 * đều được chấp nhận để gửi.
 *
 * @property client OkHttp client dùng để gửi. Mặc định cấu hình call timeout 30 giây (Requirement 16.7).
 *   Cho phép tiêm để kiểm thử bằng `MockWebServer`.
 */
class RestApiDestination(
    private val client: OkHttpClient = defaultClient(),
) : Destination {

    override val type: DestinationType get() = DestinationType.REST_API

    override suspend fun send(payload: ExportPayload, config: DestinationConfig): DestinationResult {
        require(config is DestinationConfig.RestApi) {
            "RestApiDestination yêu cầu DestinationConfig.RestApi nhưng nhận ${config::class.simpleName}"
        }

        // Requirement 16.1: từ chối cấu hình không hợp lệ (scheme, độ dài URL, số header).
        // Đây là lỗi cấu hình nên không đủ điều kiện thử lại.
        val validation = RestConfigValidator.validate(config.url, config.headers.size)
        if (validation is RestConfigValidation.Invalid) {
            return DestinationResult.Failure(
                reason = "Cấu hình REST API không hợp lệ: ${validation.violations.joinToString()}",
                retryEligible = false,
            )
        }

        // Requirement 16.8: không gửi nếu payload vượt giới hạn 100 MB; coi job là thất bại.
        if (!PayloadSizeGuard.withinLimit(payload.bytes.size.toLong())) {
            return DestinationResult.Failure(
                reason = "Bản xuất (${payload.bytes.size} byte) vượt giới hạn " +
                    "${PayloadSizeGuard.MAX_PAYLOAD_BYTES} byte; không gửi yêu cầu.",
                retryEligible = false,
            )
        }

        val request = buildRequest(payload, config)

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    // Requirements 16.5, 16.6: phân loại theo dải 2xx.
                    if (HttpStatusClassifier.isSuccess(response.code)) {
                        DestinationResult.Success(
                            detail = "REST API trả về HTTP ${response.code}.",
                        )
                    } else {
                        val body = runCatching { response.body?.string() }.getOrNull().orEmpty()
                        DestinationResult.Failure(
                            reason = "REST API trả về HTTP ${response.code}: ${body.ifBlank { "(thân rỗng)" }}",
                            // Lỗi máy chủ 5xx có thể tạm thời; mã 4xx thường do cấu hình.
                            retryEligible = response.code in 500..599,
                        )
                    }
                }
            } catch (e: IOException) {
                // Requirement 16.7: hết thời gian chờ (call timeout) hoặc kết nối thất bại.
                DestinationResult.Failure(
                    reason = "Gửi REST API thất bại: ${e.message ?: e::class.simpleName}",
                    retryEligible = true,
                )
            }
        }
    }

    /**
     * Dựng [Request] OkHttp: body = `payload.bytes` với media type = `payload.contentType`
     * (Requirements 16.2, 16.3), method theo cấu hình, kèm header tùy chỉnh.
     *
     * Mọi header `Content-Type` do người dùng cung cấp bị loại bỏ để media type của body (theo
     * Export_Format) là nguồn sự thật duy nhất cho `Content-Type` (Requirement 16.3).
     */
    private fun buildRequest(payload: ExportPayload, config: DestinationConfig.RestApi): Request {
        val mediaType = payload.contentType.toMediaTypeOrNull()
        val body = payload.bytes.toRequestBody(mediaType)

        val builder = Request.Builder().url(config.url)
        config.headers
            .filterKeys { !it.equals("Content-Type", ignoreCase = true) }
            .forEach { (name, value) -> builder.addHeader(name, value) }

        return builder.method(config.method.uppercase(), body).build()
    }

    private companion object {
        /** Call timeout 30 giây áp cho toàn bộ vòng đời request/response (Requirement 16.7). */
        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .callTimeout(30, TimeUnit.SECONDS)
                .build()
    }
}
