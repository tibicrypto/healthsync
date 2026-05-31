package com.healthautoexport.data.destination

import com.healthautoexport.domain.logic.HttpStatusClassifier
import com.healthautoexport.domain.model.DestinationType
import com.healthautoexport.domain.port.CredentialStore
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
 * Hiện thực [Destination] cho đích đến Home Assistant (Requirement 20).
 *
 * Dùng OkHttp POST dữ liệu đã tuần tự hóa tới endpoint Home Assistant, ủy quyền bằng một
 * **long-lived access token** lấy từ [CredentialStore] thông qua
 * [DestinationConfig.HomeAssistant.credentialRef], đặt vào header `Authorization: Bearer <token>`
 * (Requirement 20.2). `Content-Type` được đặt bằng [ExportPayload.contentType].
 *
 * Hành vi theo Requirement 20:
 * - **20.3**: Đặt call timeout 30 giây cho mỗi request.
 * - **20.4**: Lỗi xác thực (HTTP 401/403) ⇒ [DestinationResult.Failure] với lý do nhắc cập nhật
 *   token (không đủ điều kiện thử lại — cần can thiệp của người dùng).
 * - **20.5**: Hết thời gian chờ / lỗi mạng / lỗi máy chủ (5xx) ⇒ [DestinationResult.Failure] với
 *   `retryEligible = true`, giữ nguyên dữ liệu để thử lại.
 * - **20.6**: Mã 2xx ([HttpStatusClassifier.isSuccess]) ⇒ [DestinationResult.Success].
 *
 * Lưu ý về Requirement 20.2 (cảnh báo non-HTTPS base URL): đây là mối quan tâm tại **thời điểm lưu
 * cấu hình** ở tầng UI — màn hình Destinations cảnh báo khi base URL không dùng HTTPS trước khi lưu;
 * lớp này không chặn gửi theo scheme.
 *
 * @property credentialStore nơi lấy long-lived token theo `credentialRef`.
 * @property client OkHttp client với call timeout 30 giây (Requirement 20.3). Cho phép tiêm để test.
 */
class HomeAssistantDestination(
    private val credentialStore: CredentialStore,
    private val client: OkHttpClient = defaultClient(),
) : Destination {

    override val type: DestinationType get() = DestinationType.HOME_ASSISTANT

    override suspend fun send(payload: ExportPayload, config: DestinationConfig): DestinationResult {
        require(config is DestinationConfig.HomeAssistant) {
            "HomeAssistantDestination yêu cầu DestinationConfig.HomeAssistant nhưng nhận ${config::class.simpleName}"
        }

        // Requirement 20.2: cần long-lived token để ủy quyền. Thiếu token ⇒ nhắc cập nhật token.
        val credentialRef = config.credentialRef
        val token = credentialRef?.let { credentialStore.get(it) }
        if (token.isNullOrBlank()) {
            return DestinationResult.Failure(
                reason = "Thiếu access token Home Assistant; vui lòng cập nhật access token.",
                retryEligible = false,
            )
        }

        val request = buildRequest(payload, config, token)

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    when {
                        // Requirement 20.6: chấp nhận dữ liệu (2xx) ⇒ thành công.
                        HttpStatusClassifier.isSuccess(response.code) ->
                            DestinationResult.Success(
                                detail = "Home Assistant trả về HTTP ${response.code}.",
                            )

                        // Requirement 20.4: lỗi xác thực ⇒ nhắc cập nhật token.
                        response.code == 401 || response.code == 403 ->
                            DestinationResult.Failure(
                                reason = "Home Assistant từ chối xác thực (HTTP ${response.code}); " +
                                    "vui lòng cập nhật access token.",
                                retryEligible = false,
                            )

                        // Requirement 20.5: lỗi máy chủ ⇒ giữ dữ liệu, đủ điều kiện thử lại.
                        else -> {
                            val body = runCatching { response.body?.string() }.getOrNull().orEmpty()
                            DestinationResult.Failure(
                                reason = "Home Assistant trả về HTTP ${response.code}: " +
                                    body.ifBlank { "(thân rỗng)" },
                                retryEligible = response.code in 500..599,
                            )
                        }
                    }
                }
            } catch (e: IOException) {
                // Requirement 20.5: hết thời gian chờ hoặc lỗi mạng ⇒ giữ dữ liệu để thử lại.
                DestinationResult.Failure(
                    reason = "Gửi Home Assistant thất bại: ${e.message ?: e::class.simpleName}",
                    retryEligible = true,
                )
            }
        }
    }

    /**
     * Dựng yêu cầu POST tới [DestinationConfig.HomeAssistant.baseUrl] với header
     * `Authorization: Bearer <token>` (Requirement 20.2) và body = `payload.bytes` kèm
     * `Content-Type` = `payload.contentType`.
     */
    private fun buildRequest(
        payload: ExportPayload,
        config: DestinationConfig.HomeAssistant,
        token: String,
    ): Request {
        val mediaType = payload.contentType.toMediaTypeOrNull()
        val body = payload.bytes.toRequestBody(mediaType)

        return Request.Builder()
            .url(config.baseUrl)
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()
    }

    private companion object {
        /** Call timeout 30 giây cho mỗi request Home Assistant (Requirement 20.3). */
        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .callTimeout(30, TimeUnit.SECONDS)
                .build()
    }
}
