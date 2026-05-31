package com.healthautoexport.data.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.healthautoexport.domain.model.DataSourceId
import com.healthautoexport.domain.model.DateRange
import com.healthautoexport.domain.model.HealthMetricType
import com.healthautoexport.domain.model.HeartRateSample
import com.healthautoexport.domain.model.UnifiedRecord
import com.healthautoexport.domain.model.Workout
import com.healthautoexport.domain.model.WorkoutType
import com.healthautoexport.domain.port.HealthDataSource
import com.healthautoexport.domain.port.ReadWarning
import com.healthautoexport.domain.port.SourceAvailability
import com.healthautoexport.domain.port.SourceReadResult
import kotlin.reflect.KClass

/**
 * Bộ điều hợp [HealthDataSource] cho **Google Health Connect** (Requirement 1).
 *
 * Lớp này là điểm vào I/O của nguồn Health_Connect: nó lấy [HealthConnectClient] một cách "lười"
 * (lazy) và **chỉ khi** SDK khả dụng (`getSdkStatus == SDK_AVAILABLE`), đọc bản ghi theo
 * [DateRange] qua `TimeRangeFilter`, rồi giao việc chuẩn hóa cho [HealthConnectMetricMapper]
 * (task 13.1). Mọi lỗi đọc của từng metric/workout được **bắt và chuyển thành [ReadWarning]**,
 * không ném ngoại lệ làm hủy Export_Job (Requirements 4.7, 6.6) — bộ điều hợp luôn "kiên cường".
 *
 * Khả dụng (Requirements 1.1, 1.8): khi Health_Connect chưa cài/cần cập nhật, [availability] trả
 * [SourceAvailability.Unavailable] kèm liên kết Play Store để cài/cập nhật gói cung cấp.
 *
 * Các seam [sdkStatusProvider]/[clientFactory] được tách ra để kiểm thử trên JVM (Robolectric)
 * mà không cần thiết bị thật; ở runtime chúng dùng API mặc định của Health_Connect. Hằng
 * `@ApplicationContext` [Context] sẽ được tiêm ở task 22.1 (Hilt).
 *
 * @property context [Context] ứng dụng dùng để truy vấn SDK Health_Connect.
 * @property providerPackageName gói cung cấp Health_Connect (mặc định gói chính thức của Google).
 * @property sdkStatusProvider seam trả mã trạng thái SDK; mặc định `HealthConnectClient.getSdkStatus`.
 * @property clientFactory seam tạo [HealthConnectClient]; mặc định `HealthConnectClient.getOrCreate`.
 */
class HealthConnectDataSource(
    private val context: Context,
    private val providerPackageName: String = HealthConnectClient.DEFAULT_PROVIDER_PACKAGE_NAME,
    private val sdkStatusProvider: (Context, String) -> Int = { ctx, pkg ->
        HealthConnectClient.getSdkStatus(ctx, pkg)
    },
    private val clientFactory: (Context, String) -> HealthConnectClient = { ctx, pkg ->
        HealthConnectClient.getOrCreate(ctx, pkg)
    },
) : HealthDataSource {

    override val id: DataSourceId = DataSourceId.HEALTH_CONNECT

    /** Cache client sau lần tạo đầu để tránh getOrCreate lặp lại. */
    @Volatile
    private var cachedClient: HealthConnectClient? = null

    /**
     * Kiểm tra khả dụng của Health_Connect (Requirements 1.1, 1.8).
     *
     * - `SDK_AVAILABLE` → [SourceAvailability.Available].
     * - `SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED` → [SourceAvailability.Unavailable] kèm liên kết
     *   Play Store để cài/cập nhật gói cung cấp Health_Connect.
     * - `SDK_UNAVAILABLE` (thiết bị không đáp ứng) → [SourceAvailability.Unavailable] không kèm
     *   liên kết (không thể cài trên thiết bị này).
     */
    override suspend fun availability(): SourceAvailability =
        when (sdkStatus()) {
            HealthConnectClient.SDK_AVAILABLE -> SourceAvailability.Available

            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> SourceAvailability.Unavailable(
                reason = "Health Connect chưa được cài đặt hoặc cần được cập nhật để sử dụng.",
                installLink = playStoreInstallLink(),
            )

            else -> SourceAvailability.Unavailable(
                reason = "Health Connect không khả dụng trên thiết bị này.",
                installLink = null,
            )
        }

    /**
     * Tập [HealthMetricType] mà nguồn Health_Connect có thể cung cấp: vừa được mapper hiểu, vừa
     * được catalog khai báo do Health_Connect cung cấp (Requirement 4.6).
     */
    override suspend fun supportedMetrics(): Set<HealthMetricType> =
        HealthConnectRecordTypes.supportedMetrics

    /**
     * Đọc các bản ghi đã chuẩn hóa cho [metrics]/[workouts] trong [range] (Requirements 1.5, 4.2,
     * 5.x, 6.x).
     *
     * Quy trình:
     * 1. Lấy client; nếu SDK không khả dụng, trả kết quả rỗng kèm một cảnh báo (không ném).
     * 2. Với **mỗi** metric đã chọn được mapper hỗ trợ: đọc loại bản ghi tương ứng theo
     *    `TimeRangeFilter`, ánh xạ qua [HealthConnectMetricMapper]; lỗi đọc của một metric chỉ tạo
     *    cảnh báo và **không** dừng các metric khác (Requirement 4.7).
     * 3. Với workout: đọc [ExerciseSessionRecord] và (nếu cần) [HeartRateRecord] trong [range] để
     *    gắn chuỗi nhịp tim ascending theo từng phiên (Requirements 5.1, 5.4).
     *
     * @return [SourceReadResult] gồm bản ghi, workout và toàn bộ cảnh báo gom được.
     */
    override suspend fun readRecords(
        metrics: Set<HealthMetricType>,
        workouts: Set<WorkoutType>,
        range: DateRange,
    ): SourceReadResult {
        val client = obtainClient() ?: return SourceReadResult(
            records = emptyList(),
            workouts = emptyList(),
            warnings = listOf(
                ReadWarning(
                    source = id,
                    metric = null,
                    message = "Health Connect không khả dụng; bỏ qua nguồn này cho Export_Job.",
                ),
            ),
        )

        val allRecords = ArrayList<UnifiedRecord>()
        val warnings = ArrayList<ReadWarning>()

        // --- Đọc từng metric đã chọn (Requirements 4.2, 4.7). ---
        for (metric in metrics) {
            val recordType = HealthConnectRecordTypes.recordTypeFor(metric) ?: continue
            val raw = try {
                readAll(client, recordType, range)
            } catch (error: Throwable) {
                warnings += ReadWarning(
                    source = id,
                    metric = metric,
                    message = "Không đọc được dữ liệu Health Connect cho metric: " +
                        (error.message ?: error.javaClass.simpleName),
                )
                continue
            }
            val mapped = HealthConnectMetricMapper.mapRecords(metric, raw)
            allRecords += mapped.kept
            warnings += mapped.warnings
        }

        // --- Đọc Workout đã chọn (Requirements 5.1–5.5). ---
        val readWorkouts: List<Workout> = if (workouts.isEmpty()) {
            emptyList()
        } else {
            readWorkouts(client, workouts, range, warnings)
        }

        return SourceReadResult(
            records = allRecords,
            workouts = readWorkouts,
            warnings = warnings,
        )
    }

    /**
     * Đọc và ánh xạ các phiên [ExerciseSessionRecord], lọc theo [selectedTypes], kèm chuỗi nhịp
     * tim ascending cho mỗi phiên (Requirements 5.1, 5.4, 5.7). Lỗi đọc được ghi vào [warnings].
     */
    private suspend fun readWorkouts(
        client: HealthConnectClient,
        selectedTypes: Set<WorkoutType>,
        range: DateRange,
        warnings: MutableList<ReadWarning>,
    ): List<Workout> {
        val sessions = try {
            readAll(client, ExerciseSessionRecord::class, range).filterIsInstance<ExerciseSessionRecord>()
        } catch (error: Throwable) {
            warnings += ReadWarning(
                source = id,
                metric = null,
                message = "Không đọc được Workout từ Health Connect: " +
                    (error.message ?: error.javaClass.simpleName),
            )
            return emptyList()
        }

        // Đọc chuỗi nhịp tim một lần cho toàn khoảng, rồi gán cho từng phiên theo cửa sổ thời gian.
        val heartRatesBySession = try {
            heartRatesPerSession(client, sessions, range)
        } catch (error: Throwable) {
            warnings += ReadWarning(
                source = id,
                metric = HealthMetricType.HEART_RATE,
                message = "Không gắn được chuỗi nhịp tim cho Workout: " +
                    (error.message ?: error.javaClass.simpleName),
            )
            emptyMap()
        }

        val mapped = HealthConnectMetricMapper.mapWorkouts(sessions, heartRatesBySession)
        warnings += mapped.warnings
        // Lọc theo loại Workout người dùng đã chọn (Requirement 5.7).
        return mapped.kept.filter { it.type in selectedTypes }
    }

    /**
     * Đọc toàn bộ [HeartRateRecord] trong [range] và phân các mẫu vào từng phiên theo cửa sổ
     * `[start, end]`, đã sắp xếp tăng dần theo dấu thời gian (Requirement 5.4).
     */
    private suspend fun heartRatesPerSession(
        client: HealthConnectClient,
        sessions: List<ExerciseSessionRecord>,
        range: DateRange,
    ): Map<String, List<HeartRateSample>> {
        if (sessions.isEmpty()) return emptyMap()
        val samples = readAll(client, HeartRateRecord::class, range)
            .filterIsInstance<HeartRateRecord>()
            .flatMap { rec -> rec.samples }
        if (samples.isEmpty()) return emptyMap()

        val result = HashMap<String, MutableList<HeartRateSample>>()
        for (session in sessions) {
            val inWindow = samples
                .filter { !it.time.isBefore(session.startTime) && !it.time.isAfter(session.endTime) }
                .map { HeartRateSample(timestamp = it.time, bpm = it.beatsPerMinute.toInt()) }
                .sortedBy { it.timestamp }
            if (inWindow.isNotEmpty()) {
                result[session.metadata.id] = inWindow.toMutableList()
            }
        }
        return result
    }

    /**
     * Đọc **toàn bộ** bản ghi loại [recordType] trong [range], tự động phân trang theo
     * `pageToken` để không bỏ sót dữ liệu khi vượt giới hạn trang mặc định.
     */
    private suspend fun readAll(
        client: HealthConnectClient,
        recordType: KClass<out Record>,
        range: DateRange,
    ): List<Record> {
        val filter = TimeRangeFilter.between(range.startUtc, range.endUtc)
        val collected = ArrayList<Record>()
        var pageToken: String? = null
        do {
            @Suppress("UNCHECKED_CAST")
            val request = ReadRecordsRequest(
                recordType = recordType as KClass<Record>,
                timeRangeFilter = filter,
                pageToken = pageToken,
            )
            val response = client.readRecords(request)
            collected += response.records
            pageToken = response.pageToken
        } while (pageToken != null)
        return collected
    }

    /** Trả [HealthConnectClient] nếu SDK khả dụng, ngược lại `null` (không ném). */
    private fun obtainClient(): HealthConnectClient? {
        cachedClient?.let { return it }
        if (sdkStatus() != HealthConnectClient.SDK_AVAILABLE) return null
        return try {
            clientFactory(context, providerPackageName).also { cachedClient = it }
        } catch (_: Throwable) {
            null
        }
    }

    /** Mã trạng thái SDK; mọi ngoại lệ được coi như không khả dụng để tránh crash. */
    private fun sdkStatus(): Int = try {
        sdkStatusProvider(context, providerPackageName)
    } catch (_: Throwable) {
        HealthConnectClient.SDK_UNAVAILABLE
    }

    /** Liên kết Play Store cài/cập nhật gói cung cấp Health_Connect (Requirement 1.8). */
    private fun playStoreInstallLink(): String =
        "market://details?id=$providerPackageName&url=healthconnect%3A%2F%2Fonboarding"
}
