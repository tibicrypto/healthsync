package com.healthautoexport.domain.pipeline

import com.healthautoexport.domain.model.AggregationPeriod
import com.healthautoexport.domain.model.ExtraValue
import com.healthautoexport.domain.model.HealthMetricType
import com.healthautoexport.domain.model.MetricCatalog
import com.healthautoexport.domain.model.MetricKind
import com.healthautoexport.domain.model.MetricValue
import com.healthautoexport.domain.model.SleepState
import com.healthautoexport.domain.model.UnifiedRecord
import java.math.BigDecimal
import java.math.MathContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * `Aggregator` — bước thuần (pure) trong pipeline xuất, nhóm và tóm tắt các [UnifiedRecord] của
 * mỗi Health_Metric theo một [AggregationPeriod] (Requirement 8).
 *
 * ### Hợp đồng (contract)
 * [aggregate] thao tác trên **danh sách bản ghi theo từng metric**
 * (`Map<HealthMetricType, List<UnifiedRecord>>`) thay vì trên một kiểu envelope cụ thể, để nó
 * ghép (compose) được với bất kỳ wrapper `MergedDataset`/`AggregatedDataset` nào mà các thành
 * phần khác định nghĩa. Đầu ra giữ nguyên hình dạng map: mỗi metric ánh xạ tới danh sách bản
 * ghi đã tổng hợp của riêng nó.
 *
 * Hàm là **thuần**: với cùng đầu vào và cùng [ZoneId], kết quả luôn như nhau, không phụ thuộc
 * đồng hồ hệ thống hay múi giờ mặc định. Múi giờ thiết bị tại thời điểm Export_Job được truyền
 * vào dưới dạng tham số (lấy từ [ZoneIdProvider] ở tầng điều phối), giúp logic dễ kiểm thử xác
 * định quanh ranh giới lịch và DST.
 *
 * ### Hành vi tóm tắt (Requirement 8)
 * - **[AggregationPeriod.SECOND]** ⇒ trả về **đúng** các bản ghi đầu vào, không kết hợp
 *   (Requirement 8.7, Property 24).
 * - Với mọi period khác, mỗi metric được nhóm vào các khung nửa mở `[start, end)` **không chồng
 *   lấn**, căn theo ranh giới lịch trong múi giờ thiết bị (Requirements 8.2, 8.3, Property 19, 20):
 *   phút @ giây 00, giờ @ phút 00, ngày @ 00:00:00, tuần @ Thứ Hai 00:00:00, tháng @ ngày 1,
 *   năm @ 1 tháng 1. Một dấu thời gian rơi **đúng** ranh giới thuộc về khung **sau** (start-inclusive).
 * - Metric **[MetricKind.CUMULATIVE]** ⇒ mỗi khung không rỗng phát ra **một** bản ghi có giá trị
 *   là **tổng** ([MetricValue.Scalar]) các bản ghi thành viên, gắn dấu thời gian tại đầu khung
 *   (Requirement 8.4, Property 21).
 * - Metric **[MetricKind.INSTANTANEOUS]** ⇒ mỗi khung không rỗng phát ra **một** bản ghi có giá
 *   trị [MetricValue.StatSummary] `{min, avg, max, count}`, gắn dấu thời gian tại đầu khung
 *   (Requirement 8.5, Property 22).
 * - **Khung rỗng bị bỏ qua hoàn toàn** — không phát ra khung trống hay giá trị 0
 *   (Requirement 8.6, Property 23).
 * - **Giấc ngủ** ([HealthMetricType.SLEEP_ANALYSIS]) ⇒ mỗi khung phát ra **tổng thời lượng ngủ**
 *   (một bản ghi [MetricValue.Scalar]) **cộng** thời lượng theo từng giai đoạn (mỗi
 *   [SleepState] một bản ghi [MetricValue.SleepSegment]); với period [AggregationPeriod.DAY] khung
 *   ngày căn @ 00:00:00 đúng theo Requirement 8.8.
 *
 * ### Phạm vi
 * Các phép tóm tắt số (tổng / `{min,avg,max,count}`) chỉ áp dụng cho metric có giá trị vô hướng
 * ([MetricValue.Scalar], tức lược đồ chuẩn). Metric có **giá trị cấu trúc khác** (huyết áp, ECG,
 * cảnh báo nhịp tim) không được Requirement 8 định nghĩa phép tổng hợp số; để tránh suy diễn sai
 * và **không làm mất dữ liệu**, các bản ghi này được **giữ nguyên (pass-through)** không kết hợp.
 * Giấc ngủ được xử lý riêng như mô tả ở trên.
 *
 * Phân loại [MetricKind] luôn được tra từ `MetricCatalog.spec(type).kind` — nguồn sự thật duy nhất.
 */
class Aggregator {

    /**
     * Tổng hợp [recordsByMetric] theo [period], dùng [zone] làm múi giờ thiết bị để căn ranh giới
     * lịch (Requirements 8.2–8.8).
     *
     * @param recordsByMetric các bản ghi đã hợp nhất/loại trùng, gom theo từng [HealthMetricType].
     * @param period mức độ chi tiết thời gian được người dùng chọn (Requirement 8.1).
     * @param zone múi giờ cục bộ của thiết bị tại thời điểm Export_Job (Requirement 8.3).
     * @return map cùng tập khóa metric; mỗi metric ánh xạ tới danh sách bản ghi đã tổng hợp,
     *   sắp xếp tăng dần theo dấu thời gian đầu khung. Khung rỗng bị bỏ qua (Requirement 8.6).
     */
    fun aggregate(
        recordsByMetric: Map<HealthMetricType, List<UnifiedRecord>>,
        period: AggregationPeriod,
        zone: ZoneId,
    ): Map<HealthMetricType, List<UnifiedRecord>> {
        // SECOND là phép đồng nhất: trả về bản ghi thô không kết hợp (Requirement 8.7).
        if (period == AggregationPeriod.SECOND) return recordsByMetric

        return recordsByMetric.mapValues { (type, records) ->
            if (records.isEmpty()) emptyList() else aggregateMetric(type, records, period, zone)
        }
    }

    /**
     * Biến thể tiện lợi lấy múi giờ thiết bị từ [zoneProvider] (Requirement 8.3). Phần điều phối
     * (vd `RunExportJobUseCase`) dùng cách này để không phải tự đọc múi giờ hệ thống.
     */
    fun aggregate(
        recordsByMetric: Map<HealthMetricType, List<UnifiedRecord>>,
        period: AggregationPeriod,
        zoneProvider: ZoneIdProvider,
    ): Map<HealthMetricType, List<UnifiedRecord>> =
        aggregate(recordsByMetric, period, zoneProvider.zone())

    // ---------------------------------------------------------------------------------------------
    // Tổng hợp theo từng metric
    // ---------------------------------------------------------------------------------------------

    private fun aggregateMetric(
        type: HealthMetricType,
        records: List<UnifiedRecord>,
        period: AggregationPeriod,
        zone: ZoneId,
    ): List<UnifiedRecord> {
        // Giấc ngủ: tổng thời lượng + thời lượng từng giai đoạn cho mỗi khung (Requirement 8.8).
        if (type == HealthMetricType.SLEEP_ANALYSIS) {
            return aggregateSleep(type, records, period, zone)
        }

        val scalarOnly = records.all { it.value is MetricValue.Scalar }
        if (!scalarOnly) {
            // Giá trị cấu trúc không phải Scalar/Sleep: không có phép tổng hợp số được đặc tả.
            // Giữ nguyên bản ghi để không mất dữ liệu (xem phần "Phạm vi" trong KDoc lớp).
            return records
        }

        return when (MetricCatalog.spec(type).kind) {
            MetricKind.CUMULATIVE -> aggregateCumulative(type, records, period, zone)
            MetricKind.INSTANTANEOUS -> aggregateInstantaneous(type, records, period, zone)
        }
    }

    /** Metric tích lũy ⇒ mỗi khung phát ra tổng (sum) các giá trị thành viên (Requirement 8.4). */
    private fun aggregateCumulative(
        type: HealthMetricType,
        records: List<UnifiedRecord>,
        period: AggregationPeriod,
        zone: ZoneId,
    ): List<UnifiedRecord> =
        forEachBucket(records, period, zone) { bucketStart, members ->
            val sum = members.fold(BigDecimal.ZERO) { acc, r -> acc + (r.value as MetricValue.Scalar).qty }
            listOf(outputRecord(type, MetricValue.Scalar(sum), bucketStart, members))
        }

    /** Metric tức thời ⇒ mỗi khung phát ra `{min, avg, max, count}` (Requirement 8.5). */
    private fun aggregateInstantaneous(
        type: HealthMetricType,
        records: List<UnifiedRecord>,
        period: AggregationPeriod,
        zone: ZoneId,
    ): List<UnifiedRecord> =
        forEachBucket(records, period, zone) { bucketStart, members ->
            val qtys = members.map { (it.value as MetricValue.Scalar).qty }
            val min = qtys.minOrNull()!!
            val max = qtys.maxOrNull()!!
            val sum = qtys.fold(BigDecimal.ZERO, BigDecimal::add)
            // Trung bình dùng MathContext.DECIMAL128 (34 chữ số có nghĩa, HALF_EVEN) để xác định
            // và tái lập được; phép chia luôn kết thúc theo độ chính xác này.
            val avg = sum.divide(BigDecimal.valueOf(members.size.toLong()), MathContext.DECIMAL128)
            val summary = MetricValue.StatSummary(
                min = min,
                avg = avg,
                max = max,
                count = members.size.toLong(),
            )
            listOf(outputRecord(type, summary, bucketStart, members))
        }

    /**
     * Giấc ngủ theo khung (Requirement 8.8): với mỗi khung phát ra
     * 1) một bản ghi **tổng thời lượng** ([MetricValue.Scalar], giây) — đánh dấu bằng
     *    `extras[`[SLEEP_TOTAL_EXTRA_KEY]`] = TOTAL`; và
     * 2) một bản ghi **mỗi giai đoạn** ([MetricValue.SleepSegment]) với thời lượng cộng dồn theo
     *    [SleepState], sắp xếp theo thứ tự khai báo của enum để xác định.
     */
    private fun aggregateSleep(
        type: HealthMetricType,
        records: List<UnifiedRecord>,
        period: AggregationPeriod,
        zone: ZoneId,
    ): List<UnifiedRecord> =
        forEachBucket(records, period, zone) { bucketStart, members ->
            val perStage: Map<SleepState, Long> = members
                .mapNotNull { it.value as? MetricValue.SleepSegment }
                .groupBy { it.state }
                .mapValues { (_, segments) -> segments.sumOf { it.durationSeconds } }

            val totalSeconds = perStage.values.sum()

            val totalRecord = outputRecord(
                type = type,
                value = MetricValue.Scalar(BigDecimal.valueOf(totalSeconds)),
                bucketStart = bucketStart,
                members = members,
                extras = mapOf(SLEEP_TOTAL_EXTRA_KEY to ExtraValue.EnumValue(SLEEP_TOTAL_EXTRA_VALUE)),
            )

            val stageRecords = perStage.entries
                .sortedBy { it.key.ordinal }
                .map { (state, duration) ->
                    outputRecord(
                        type = type,
                        value = MetricValue.SleepSegment(state, duration),
                        bucketStart = bucketStart,
                        members = members,
                    )
                }

            listOf(totalRecord) + stageRecords
        }

    // ---------------------------------------------------------------------------------------------
    // Hạ tầng nhóm khung & dựng bản ghi
    // ---------------------------------------------------------------------------------------------

    /**
     * Nhóm [records] vào các khung lịch của [period] theo [zone], rồi áp [build] cho mỗi khung
     * **không rỗng** theo thứ tự đầu khung tăng dần. Khung rỗng tự động bị bỏ qua vì `groupBy`
     * chỉ tạo nhóm cho các khung có bản ghi (Requirement 8.6).
     */
    private fun forEachBucket(
        records: List<UnifiedRecord>,
        period: AggregationPeriod,
        zone: ZoneId,
        build: (bucketStart: ZonedDateTime, members: List<UnifiedRecord>) -> List<UnifiedRecord>,
    ): List<UnifiedRecord> =
        records
            .groupBy { bucketStartOf(it.timestamp, period, zone) }
            .entries
            .sortedBy { it.key.toInstant() }
            .flatMap { (bucketStart, members) -> build(bucketStart, members) }

    /**
     * Tính đầu khung lịch chứa [timestamp] theo [period] và [zone] (Requirements 8.3, 8.8,
     * Property 20). Vì khung là `[start, end)`, phép "làm sàn" này khiến một dấu thời gian rơi
     * **đúng** ranh giới thuộc về khung bắt đầu tại chính nó (khung sau).
     *
     * Với ngày/tuần/tháng/năm dùng `LocalDate.atStartOfDay(zone)` để xử lý đúng khoảng trống DST
     * (khi 00:00 cục bộ không tồn tại, trả về thời điểm ngay sau khoảng trống).
     */
    private fun bucketStartOf(
        timestamp: Instant,
        period: AggregationPeriod,
        zone: ZoneId,
    ): ZonedDateTime {
        val local = timestamp.atZone(zone)
        return when (period) {
            // SECOND không bao giờ tới đây (đã xử lý đồng nhất ở aggregate()).
            AggregationPeriod.SECOND -> local
            AggregationPeriod.MINUTE -> local.truncatedTo(ChronoUnit.MINUTES)
            AggregationPeriod.HOUR -> local.truncatedTo(ChronoUnit.HOURS)
            AggregationPeriod.DAY -> local.toLocalDate().atStartOfDay(zone)
            AggregationPeriod.WEEK -> local.toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(zone)
            AggregationPeriod.MONTH -> local.toLocalDate()
                .withDayOfMonth(1)
                .atStartOfDay(zone)
            AggregationPeriod.YEAR -> local.toLocalDate()
                .withDayOfYear(1)
                .atStartOfDay(zone)
        }
    }

    /**
     * Dựng một [UnifiedRecord] đầu ra cho một khung: gắn dấu thời gian tại đầu khung, đơn vị
     * canonical của metric (từ [MetricCatalog]), và độ lệch múi giờ tại đầu khung.
     */
    private fun outputRecord(
        type: HealthMetricType,
        value: MetricValue,
        bucketStart: ZonedDateTime,
        members: List<UnifiedRecord>,
        extras: Map<String, ExtraValue> = emptyMap(),
    ): UnifiedRecord =
        UnifiedRecord(
            metric = type,
            value = value,
            unit = MetricCatalog.spec(type).unit,
            timestamp = bucketStart.toInstant(),
            zoneOffset = bucketStart.offset,
            dataSourceId = representativeSource(members),
            extras = extras,
        )

    /**
     * Chọn Data_Source đại diện cho một khung tổng hợp một cách xác định: vì tổng hợp gộp các bản
     * ghi có thể đến từ nhiều nguồn, ta chọn `DataSourceId` có `id` đứng trước theo thứ tự bảng
     * chữ cái — nhất quán với quy ước tie-break của `Data_Merger` (Requirement 7.5). Khi mọi bản
     * ghi cùng một nguồn, kết quả chính là nguồn đó.
     */
    private fun representativeSource(members: List<UnifiedRecord>) =
        members.minByOrNull { it.dataSourceId.id }!!.dataSourceId

    companion object {
        /** Khóa `extras` đánh dấu bản ghi giấc ngủ tổng hợp là **tổng thời lượng** của khung. */
        const val SLEEP_TOTAL_EXTRA_KEY: String = "sleep_aggregate"

        /** Giá trị `extras` cho bản ghi tổng thời lượng ngủ của một khung. */
        const val SLEEP_TOTAL_EXTRA_VALUE: String = "TOTAL"
    }
}
