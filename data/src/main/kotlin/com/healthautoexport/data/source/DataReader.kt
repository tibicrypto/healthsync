package com.healthautoexport.data.source

import com.healthautoexport.domain.logic.SourceSelection
import com.healthautoexport.domain.model.DataSourceId
import com.healthautoexport.domain.model.DateRange
import com.healthautoexport.domain.model.MetricSelection
import com.healthautoexport.domain.port.HealthDataSource
import com.healthautoexport.domain.port.ReadOutcome
import com.healthautoexport.domain.port.ReadWarning
import com.healthautoexport.domain.port.SourceAvailability
import com.healthautoexport.domain.port.SourceReadResult
import com.healthautoexport.domain.port.SourceToggleStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Điều phối đọc dữ liệu từ nhiều [HealthDataSource] theo lựa chọn bật/tắt của người dùng — task
 * 15.1 (Requirements 3.3–3.7, 4.7).
 *
 * Quy trình cho mỗi Export_Job:
 * 1. Lấy tập nguồn đang bật từ [SourceToggleStore] (Requirements 3.1, 3.2) và giao với tập nguồn
 *    thực sự có bộ điều hợp đăng ký ([sources]).
 * 2. Nếu **không** nguồn nào được bật ⇒ [ReadOutcome.NoEnabledSource] (Requirement 3.7).
 * 3. Với mỗi nguồn được bật, kiểm tra [HealthDataSource.availability] rồi
 *    [HealthDataSource.readRecords] **trong một timeout 30 giây/nguồn** ([perSourceTimeout]) bằng
 *    [withTimeoutOrNull]. Nguồn quá hạn **hoặc** báo [SourceAvailability.Unavailable] đều bị coi
 *    là **không khả dụng**: job tiếp tục với nguồn còn lại và ghi một [ReadWarning] để đưa vào
 *    Sync_Log (Requirement 3.5).
 * 4. Tập nguồn thực sự đọc được = `enabled ∩ available`, tính qua
 *    [SourceSelection.queriedSources]: một nguồn được bật ⇒ đọc độc quyền từ nó (Requirement 3.3);
 *    cả hai nguồn được bật ⇒ đọc từ cả hai (Requirement 3.4).
 * 5. Nếu **mọi** nguồn được bật đều không khả dụng (`queried` rỗng) ⇒
 *    [ReadOutcome.AllSourcesUnavailable] (Requirement 3.6). Ngược lại ⇒ [ReadOutcome.Success].
 *
 * Cảnh báo bản ghi không ánh xạ được (Requirement 4.7) đã nằm sẵn trong [SourceReadResult.warnings]
 * do từng nguồn trả về; [DataReader] chỉ **tổng hợp** chúng cùng cảnh báo không-khả-dụng của
 * riêng mình mà không diễn giải lại.
 *
 * Các nguồn được kiểm tra/đọc **đồng thời** (mỗi nguồn một coroutine con với timeout riêng) để một
 * nguồn chậm không làm trễ nguồn còn lại; kết quả được sắp xếp lại theo [DataSourceId.id] nhằm bảo
 * đảm thứ tự **xác định** bất kể nguồn nào hoàn tất trước.
 *
 * @property sources bản đồ bộ điều hợp [HealthDataSource] theo [DataSourceId] (đăng ký ở DI).
 * @property sourceToggles nguồn sự thật cho lựa chọn bật/tắt mỗi Data_Source (Requirements 3.1, 3.2).
 * @property perSourceTimeout thời gian chờ tối đa cho mỗi nguồn; mặc định 30 giây (Requirement 3.5).
 */
@Singleton
class DataReader(
    private val sources: Map<DataSourceId, HealthDataSource>,
    private val sourceToggles: SourceToggleStore,
    private val perSourceTimeout: Duration,
) {

    /**
     * Constructor cho DI (Hilt): dùng timeout mặc định 30 giây/nguồn (Requirement 3.5). Bộ
     * điều hợp được nối ở task 22.1. Tham số [perSourceTimeout] được tách sang constructor chính
     * để kiểm thử có thể tiêm timeout ngắn mà không cần DI cấp một binding cho [Duration].
     */
    @Inject
    constructor(
        sources: Map<DataSourceId, HealthDataSource>,
        sourceToggles: SourceToggleStore,
    ) : this(sources, sourceToggles, DEFAULT_PER_SOURCE_TIMEOUT)

    /**
     * Đọc dữ liệu cho [selection] trong [range] từ các Data_Source đang bật và khả dụng.
     *
     * @return [ReadOutcome.Success] khi ≥ 1 nguồn được bật đọc được; [ReadOutcome.NoEnabledSource]
     *   khi không nguồn nào được bật (Requirement 3.7); [ReadOutcome.AllSourcesUnavailable] khi mọi
     *   nguồn được bật đều không khả dụng (Requirement 3.6).
     */
    suspend fun read(selection: MetricSelection, range: DateRange): ReadOutcome {
        // Chỉ xét các nguồn vừa được người dùng bật vừa có bộ điều hợp đăng ký.
        val enabled = sourceToggles.enabledSources()
            .filter { it in sources.keys }
            .toSet()

        if (enabled.isEmpty()) {
            return ReadOutcome.NoEnabledSource(
                reason = "Không có Data_Source nào được bật cho Export_Job (Requirement 3.7).",
            )
        }

        // Kiểm tra khả dụng + đọc đồng thời: mỗi nguồn một coroutine con với timeout độc lập
        // (Requirement 3.5).
        val outcomeBySource: Map<DataSourceId, PerSourceOutcome> = coroutineScope {
            enabled
                .map { id -> async { id to readSingleSource(id, selection, range) } }
                .awaitAll()
                .toMap()
        }

        // Tập nguồn khả dụng (đọc được) và tập nguồn thực sự truy vấn = enabled ∩ available
        // (Property 31, Requirements 3.3, 3.4).
        val available = outcomeBySource
            .filterValues { it is PerSourceOutcome.Read }
            .keys
        val queried = SourceSelection.queriedSources(enabled, available)

        // Mọi nguồn được bật đều không khả dụng ⇒ hủy Export_Job (Requirement 3.6).
        if (queried.isEmpty()) {
            return ReadOutcome.AllSourcesUnavailable(
                reason = "Tất cả Data_Source được bật đều không khả dụng tại thời điểm " +
                    "Export_Job (Requirement 3.6).",
            )
        }

        // Tổng hợp theo thứ tự xác định (DataSourceId.id): kết quả đọc được của nguồn truy vấn,
        // kèm các kết quả chỉ-cảnh-báo cho nguồn được bật nhưng không khả dụng (Requirement 3.5).
        val perSource = enabled
            .sortedBy { it.id }
            .map { id -> outcomeBySource.getValue(id).toResult(id) }

        return ReadOutcome.Success(perSource = perSource)
    }

    /**
     * Thực hiện kiểm tra khả dụng + đọc cho **một** nguồn trong [perSourceTimeout].
     *
     * Trả về [PerSourceOutcome.TimedOut] khi quá hạn (kết quả `null` của [withTimeoutOrNull]),
     * [PerSourceOutcome.Unavailable] khi nguồn tự báo không khả dụng, hoặc [PerSourceOutcome.Read]
     * kèm [SourceReadResult] khi đọc thành công.
     */
    private suspend fun readSingleSource(
        id: DataSourceId,
        selection: MetricSelection,
        range: DateRange,
    ): PerSourceOutcome {
        val source = sources.getValue(id)
        return withTimeoutOrNull(perSourceTimeout) {
            when (val availability = source.availability()) {
                is SourceAvailability.Available ->
                    PerSourceOutcome.Read(
                        source.readRecords(selection.metrics, selection.workouts, range),
                    )

                is SourceAvailability.Unavailable ->
                    PerSourceOutcome.Unavailable(availability.reason)
            }
        } ?: PerSourceOutcome.TimedOut
    }

    /** Kết quả trung gian cho mỗi nguồn trước khi tổng hợp thành [ReadOutcome]. */
    private sealed interface PerSourceOutcome {

        /** Đọc thành công (nguồn khả dụng, hoàn tất trong timeout). */
        data class Read(val result: SourceReadResult) : PerSourceOutcome

        /** Nguồn tự báo [SourceAvailability.Unavailable] kèm [reason]. */
        data class Unavailable(val reason: String) : PerSourceOutcome

        /** Nguồn không phản hồi trong timeout (Requirement 3.5). */
        data object TimedOut : PerSourceOutcome

        /**
         * Chuẩn hóa thành [SourceReadResult] để đưa vào [ReadOutcome.Success]: nguồn đọc được trả
         * thẳng kết quả; nguồn không khả dụng/quá hạn trả kết quả rỗng kèm một [ReadWarning] để ghi
         * Sync_Log (Requirement 3.5).
         */
        fun toResult(id: DataSourceId): SourceReadResult = when (this) {
            is Read -> result
            is Unavailable -> warningOnlyResult(id, reason)
            TimedOut -> warningOnlyResult(
                id,
                "Nguồn không phản hồi trong thời gian chờ và bị coi là không khả dụng " +
                    "(Requirement 3.5).",
            )
        }

        /** Kết quả rỗng chỉ mang một [ReadWarning] cho nguồn không khả dụng. */
        private fun warningOnlyResult(id: DataSourceId, reason: String): SourceReadResult =
            SourceReadResult(
                records = emptyList(),
                workouts = emptyList(),
                warnings = listOf(ReadWarning(source = id, metric = null, message = reason)),
            )
    }

    companion object {
        /** Timeout mặc định cho mỗi nguồn: 30 giây (Requirement 3.5). */
        val DEFAULT_PER_SOURCE_TIMEOUT: Duration = 30.seconds
    }
}
