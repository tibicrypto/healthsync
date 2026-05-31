package com.healthautoexport.data.huawei

import com.healthautoexport.domain.model.DataSourceId
import com.healthautoexport.domain.model.DateRange
import com.healthautoexport.domain.model.HealthMetricType
import com.healthautoexport.domain.model.MetricCatalog
import com.healthautoexport.domain.model.WorkoutType
import com.healthautoexport.domain.port.HealthDataSource
import com.healthautoexport.domain.port.SourceAvailability
import com.healthautoexport.domain.port.SourceReadResult

/**
 * Bộ điều hợp [HealthDataSource] cho **Huawei_Health_Kit** (Requirement 2), với mọi lời gọi SDK
 * độc quyền được đặt sau [HuaweiHealthClient] để build/chạy được trên thiết bị không phải Huawei
 * (Requirement 2.1).
 *
 * Hành vi:
 * - [id] = [DataSourceId.HUAWEI_HEALTH_KIT].
 * - [availability]: trả [SourceAvailability.Unavailable] với lý do [HuaweiMessages.UNAVAILABLE_REASON]
 *   khi `client.isHmsAvailable()` là `false` (Requirement 2.1) — App tiếp tục bằng Health_Connect;
 *   ngược lại [SourceAvailability.Available].
 * - [supportedMetrics]: chỉ các metric mà [MetricCatalog] đánh dấu Huawei cung cấp **và** khi HMS
 *   khả dụng; rỗng khi không khả dụng (Requirement 4.6).
 * - [readRecords]: ủy thác cho [client] rồi chuẩn hóa qua [HuaweiMetricMapper] (đơn vị canonical +
 *   `dataSourceId = HUAWEI_HEALTH_KIT`, Requirements 4.2, 4.5). Dưới [NoOpHuaweiHealthClient] trả
 *   về [SourceReadResult] rỗng.
 *
 * Bản hiện thực mặc định nhận [NoOpHuaweiHealthClient]; một Huawei flavor thật chỉ cần bind một
 * `HuaweiHealthClient` thật mà không phải đổi lớp này.
 *
 * @property client ranh giới trừu tượng hóa SDK Huawei (mặc định [NoOpHuaweiHealthClient]).
 * @property mapper bộ ánh xạ DTO trung lập → mô hình hợp nhất.
 */
internal class HuaweiHealthDataSource(
    private val client: HuaweiHealthClient,
    private val mapper: HuaweiMetricMapper = HuaweiMetricMapper(),
) : HealthDataSource {

    override val id: DataSourceId = DataSourceId.HUAWEI_HEALTH_KIT

    override suspend fun availability(): SourceAvailability =
        if (client.isHmsAvailable()) {
            SourceAvailability.Available
        } else {
            // HMS Core/Huawei Health Kit không khả dụng → App tiếp tục bằng Health_Connect (Req 2.1).
            SourceAvailability.Unavailable(reason = HuaweiMessages.UNAVAILABLE_REASON)
        }

    override suspend fun supportedMetrics(): Set<HealthMetricType> {
        if (!client.isHmsAvailable()) return emptySet()
        return HealthMetricType.entries
            .filterTo(mutableSetOf()) { MetricCatalog.isSupportedBy(it, DataSourceId.HUAWEI_HEALTH_KIT) }
    }

    override suspend fun readRecords(
        metrics: Set<HealthMetricType>,
        workouts: Set<WorkoutType>,
        range: DateRange,
    ): SourceReadResult {
        // Khi HMS không khả dụng, không đọc gì — kết quả rỗng (App dựa vào Health_Connect).
        if (!client.isHmsAvailable()) {
            return SourceReadResult(records = emptyList(), workouts = emptyList())
        }

        val rawSamples = if (metrics.isEmpty()) emptyList() else client.readRecords(metrics, range)
        val rawWorkouts = if (workouts.isEmpty()) emptyList() else client.readWorkouts(workouts, range)

        val mappedRecords = mapper.mapRecords(rawSamples)
        val mappedWorkouts = mapper.mapWorkouts(rawWorkouts)

        return SourceReadResult(
            records = mappedRecords.records,
            workouts = mappedWorkouts.workouts,
            warnings = mappedRecords.warnings + mappedWorkouts.warnings,
        )
    }
}
