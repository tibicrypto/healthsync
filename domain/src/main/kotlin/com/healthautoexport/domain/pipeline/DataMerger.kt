package com.healthautoexport.domain.pipeline

import com.healthautoexport.domain.model.DataSourceId
import com.healthautoexport.domain.model.DuplicateTolerance
import com.healthautoexport.domain.model.DuplicateToleranceTable
import com.healthautoexport.domain.model.HealthMetricType
import com.healthautoexport.domain.model.MergedDataset
import com.healthautoexport.domain.model.MetricValue
import com.healthautoexport.domain.model.SourcePriority
import com.healthautoexport.domain.model.UnifiedRecord
import com.healthautoexport.domain.model.Workout
import com.healthautoexport.domain.port.SourceReadResult
import java.math.BigDecimal
import java.time.Duration

/**
 * Data_Merger — hợp nhất các bản ghi đọc từ nhiều Data_Source thành **một tập dữ liệu hợp nhất
 * duy nhất** và loại bỏ trùng lặp một cách **xác định (deterministic) và thuần (pure)**
 * (Requirement 7).
 *
 * Đây là một thành phần thuộc pipeline thuần (Merge → Aggregate → Serialize), **không side-effect**
 * và **không phụ thuộc Android**, nên có thể kiểm thử dựa-trên-thuộc-tính (PBT) nhanh trên JVM.
 * Toàn bộ logic chỉ dựa vào đầu vào ([merge]), [tolerances] và [priority]; cùng đầu vào luôn cho
 * cùng đầu ra.
 *
 * Thuật toán (cho **mỗi** [HealthMetricType], xem [merge]):
 * 1. Gộp toàn bộ bản ghi từ mọi nguồn thành một danh sách (Requirement 7.1).
 * 2. Sắp xếp theo khóa tổng `(timestamp, dataSourceId, value)` tăng dần (Requirement 7.6).
 * 3. Loại trùng: hai bản ghi là trùng nếu `|Δtimestamp| ≤ tolTime(metric)` **và**
 *    `|Δvalue| ≤ tolValue(metric)` (Requirement 7.3); giữ lại đúng một bản — bản có ưu tiên nguồn
 *    cao nhất, hòa thì theo [DataSourceId.id] đứng trước theo bảng chữ cái (Requirements 7.4, 7.5).
 * 4. Nếu `|Δtimestamp| ≤ tolTime` nhưng `|Δvalue| > tolValue`, giữ **cả hai**; mỗi bản vẫn mang
 *    [UnifiedRecord.dataSourceId] gốc làm nhãn nguồn (Requirement 7.7).
 * 5. Kết quả đã ở dạng chuẩn tắc (đã sắp xếp + đã loại trùng) nên `merge(merge(x)) == merge(x)`
 *    chính xác — **idempotence** (Requirement 7.9).
 *
 * @property tolerances bảng dung sai loại trùng theo từng metric (Requirement 7.2); metric không
 *   có trong bảng dùng dung sai mặc định bằng 0 (chỉ trùng khi cùng dấu thời gian và giá trị
 *   bằng nhau chính xác trong phạm vi 0).
 * @property priority thứ hạng ưu tiên nguồn do người dùng cấu hình (Requirements 7.4, 7.8).
 */
class DataMerger(
    private val tolerances: DuplicateToleranceTable,
    private val priority: SourcePriority,
) {

    /**
     * Hợp nhất các kết quả đọc theo-từng-nguồn thành một [MergedDataset] đã loại trùng và sắp xếp
     * xác định (Requirements 7.1, 7.3–7.7, 7.9).
     *
     * Các [Workout] được chuyển tiếp nguyên trạng theo thứ tự nguồn cung cấp (việc loại trùng ở
     * đây chỉ áp dụng cho bản ghi metric — Requirement 7).
     *
     * Hàm **thuần**: không đọc/ghi I/O, không phụ thuộc trạng thái ngoài; gọi lại trên cùng đầu
     * vào luôn cho cùng đầu ra, và gọi lại trên chính kết quả của nó cho ra kết quả y hệt
     * (idempotence — Requirement 7.9).
     *
     * @param perSource danh sách kết quả đọc, mỗi phần tử ứng với một Data_Source.
     * @return [MergedDataset] với `recordsByMetric` đã loại trùng + sắp xếp, và `workouts` gộp
     *   nguyên trạng.
     */
    fun merge(perSource: List<SourceReadResult>): MergedDataset {
        // (1) Gộp toàn bộ bản ghi từ mọi nguồn (Requirement 7.1) và gom theo metric.
        val allRecords: List<UnifiedRecord> = perSource.flatMap { it.records }
        val grouped: Map<HealthMetricType, List<UnifiedRecord>> = allRecords.groupBy { it.metric }

        // Duyệt khóa theo thứ tự khai báo của enum để thứ tự map xác định, không phụ thuộc
        // thứ tự xuất hiện trong dữ liệu đầu vào.
        val recordsByMetric = LinkedHashMap<HealthMetricType, List<UnifiedRecord>>()
        for (metric in HealthMetricType.entries) {
            val records = grouped[metric] ?: continue
            recordsByMetric[metric] = mergeMetric(metric, records)
        }

        val workouts: List<Workout> = perSource.flatMap { it.workouts }
        return MergedDataset(recordsByMetric = recordsByMetric, workouts = workouts)
    }

    /**
     * Hợp nhất + loại trùng + sắp xếp các bản ghi của **một** [metric].
     *
     * Dùng chiến lược **tham lam theo thứ tự ưu tiên (best-first greedy)**:
     * - Xét các bản ghi theo thứ tự ưu tiên giảm dần ([preferenceComparator]); một bản ghi được
     *   **giữ lại** khi và chỉ khi nó **không trùng** với bất kỳ bản đã được giữ trước đó.
     *
     * Tính chất bảo đảm:
     * - Bản sống sót trong mỗi cụm trùng luôn là bản ưu tiên cao nhất (Requirements 7.4, 7.5 /
     *   Property 15) vì ta xét bản tốt nhất trước.
     * - Tập sống sót **đôi một không trùng nhau** (Requirement 7.3 / Property 14) vì mỗi bản mới
     *   chỉ được nhận nếu không trùng bản đã nhận.
     * - Bản phân kỳ giá trị (cùng thời gian, khác giá trị > tol) **không** trùng nhau nên đều được
     *   giữ, mỗi bản mang [UnifiedRecord.dataSourceId] gốc làm nhãn (Requirement 7.7 / Property 16).
     * - Cách làm này **không loại oan** bản ghi chỉ trùng với một bản đã bị loại (chuỗi trùng không
     *   bắc cầu A~B, B~C nhưng A≁C): vì ta luôn so với *bản đã giữ*, không phải bản gốc đã loại.
     *
     * Đầu ra được sắp xếp lại theo khóa tổng `(timestamp, dataSourceId, value)` (Requirement 7.6),
     * và vì kết quả đã chuẩn tắc nên chạy lại cho ra y hệt — idempotence (Requirement 7.9 /
     * Property 18).
     */
    private fun mergeMetric(metric: HealthMetricType, records: List<UnifiedRecord>): List<UnifiedRecord> {
        if (records.size <= 1) return records

        val tolerance = tolerances[metric] ?: DEFAULT_TOLERANCE
        val timeWindow = Duration.ofSeconds(tolerance.timeSeconds)
        val valueTol = tolerance.valueMagnitude

        // (2) Sắp xếp theo khóa tổng (timestamp, dataSourceId, value) tăng dần (Requirement 7.6).
        // Vị trí trong danh sách này (composite index) vừa là thứ tự đầu ra, vừa là tie-break cuối
        // cùng cho thứ tự ưu tiên — bảo đảm một thứ tự toàn phần xác định trên các phần tử.
        val sorted = records.sortedWith(compositeComparator)
        val indexed = sorted.mapIndexed { index, record -> IndexedRecord(index, record) }

        // Xét theo ưu tiên giảm dần (bản tốt nhất trước).
        val byPreference = indexed.sortedWith(preferenceComparator)

        val kept = BooleanArray(sorted.size)
        val survivors = ArrayList<IndexedRecord>(sorted.size)
        for (candidate in byPreference) {
            val duplicateOfKept = survivors.any { keptItem ->
                isDuplicate(keptItem.record, candidate.record, timeWindow, valueTol)
            }
            if (!duplicateOfKept) {
                survivors.add(candidate)
                kept[candidate.index] = true
            }
        }

        // (3)(4) Phát ra các bản được giữ theo đúng thứ tự đã sắp (Requirement 7.6).
        val result = ArrayList<UnifiedRecord>(survivors.size)
        for (i in sorted.indices) {
            if (kept[i]) result.add(sorted[i])
        }
        return result
    }

    /** Cặp (vị trí trong danh sách đã sắp theo khóa tổng, bản ghi) — dùng cho tie-break xác định. */
    private class IndexedRecord(val index: Int, val record: UnifiedRecord)

    /**
     * Hai bản ghi có **trùng** không (Requirement 7.3): `|Δtimestamp| ≤ tolTime` **và**
     * `|Δvalue| ≤ tolValue`.
     */
    private fun isDuplicate(
        a: UnifiedRecord,
        b: UnifiedRecord,
        timeWindow: Duration,
        valueTol: BigDecimal,
    ): Boolean = withinTime(a, b, timeWindow) && valueWithinTolerance(a.value, b.value, valueTol)

    /** `|Δtimestamp| ≤ [timeWindow]` (so sánh theo độ chính xác của [java.time.Instant]). */
    private fun withinTime(a: UnifiedRecord, b: UnifiedRecord, timeWindow: Duration): Boolean {
        val delta = Duration.between(a.timestamp, b.timestamp)
        val absDelta = if (delta.isNegative) delta.negated() else delta
        return absDelta <= timeWindow
    }

    /**
     * `|Δvalue| ≤ [valueTol]` theo ngữ nghĩa loại trùng:
     * - Hai giá trị vô hướng ([MetricValue.Scalar]): so sánh `|a.qty − b.qty| ≤ valueTol`
     *   (so sánh độ lớn, không phụ thuộc scale của [BigDecimal]).
     * - Các biến thể có cấu trúc không có một vô hướng duy nhất (huyết áp, giấc ngủ, ECG...):
     *   coi là trùng chỉ khi **bằng nhau chính xác** theo giá trị (dung sai giá trị không áp dụng).
     */
    private fun valueWithinTolerance(a: MetricValue, b: MetricValue, valueTol: BigDecimal): Boolean =
        if (a is MetricValue.Scalar && b is MetricValue.Scalar) {
            a.qty.subtract(b.qty).abs().compareTo(valueTol) <= 0
        } else {
            a == b
        }

    /**
     * Bộ so sánh **ưu tiên** để chọn bản sống sót trong một cụm trùng (Requirements 7.4, 7.5):
     * bản đứng **trước** (compare < 0) được ưu tiên giữ lại.
     *
     * Thứ tự khóa:
     * 1. ưu tiên nguồn cao nhất trước — rank nhỏ hơn ([SourcePriority.rankOf]) đứng trước (7.4);
     * 2. [DataSourceId.id] tăng dần theo bảng chữ cái (7.5);
     * 3. vị trí theo khóa tổng ([IndexedRecord.index]) làm tie-break cuối cùng — vì các phần tử có
     *    chỉ số khác nhau nên đây là một **thứ tự toàn phần** xác định, không phụ thuộc thứ tự đầu
     *    vào và xử lý đúng cả các bản ghi giống hệt nhau (giữ đúng một bản — Property 14).
     */
    private val preferenceComparator: Comparator<IndexedRecord> =
        Comparator<IndexedRecord> { a, b ->
            priority.rankOf(a.record.dataSourceId).compareTo(priority.rankOf(b.record.dataSourceId))
        }
            .thenComparator { a, b -> a.record.dataSourceId.id.compareTo(b.record.dataSourceId.id) }
            .thenComparator { a, b -> a.index.compareTo(b.index) }

    companion object {
        /** Dung sai mặc định khi metric không có trong bảng: chỉ trùng khi giống hệt (0/0). */
        private val DEFAULT_TOLERANCE = DuplicateTolerance(timeSeconds = 0L, valueMagnitude = BigDecimal.ZERO)

        /**
         * Khóa sắp xếp tổng cho đầu ra (Requirement 7.6): `(timestamp, dataSourceId, value)` tăng
         * dần. Lưu ý: thứ tự **đầu ra** không dùng ưu tiên nguồn — ưu tiên chỉ dùng để chọn bản
         * sống sót (xem [preferenceComparator]).
         */
        private val compositeComparator: Comparator<UnifiedRecord> =
            Comparator<UnifiedRecord> { a, b -> a.timestamp.compareTo(b.timestamp) }
                .thenComparator { a, b -> a.dataSourceId.id.compareTo(b.dataSourceId.id) }
                .thenComparator { a, b -> compareValues(a.value, b.value) }

        /**
         * Thứ tự toàn phần xác định trên [MetricValue] dùng cho khóa "value" trong sắp xếp.
         *
         * Trước tiên so theo *hạng kiểu* để các biến thể khác nhau vẫn có thứ tự nhất quán (trong
         * cùng một metric thường chỉ có một loại giá trị), sau đó so theo các trường bên trong.
         */
        internal fun compareValues(a: MetricValue, b: MetricValue): Int {
            val rankCmp = valueTypeRank(a).compareTo(valueTypeRank(b))
            if (rankCmp != 0) return rankCmp
            return when (a) {
                is MetricValue.Scalar -> a.qty.compareTo((b as MetricValue.Scalar).qty)
                is MetricValue.BloodPressure -> {
                    b as MetricValue.BloodPressure
                    compareBy2(a.systolic, a.diastolic, b.systolic, b.diastolic)
                }
                is MetricValue.HeartRateStat -> {
                    b as MetricValue.HeartRateStat
                    compareBy3(a.min, a.avg, a.max, b.min, b.avg, b.max)
                }
                is MetricValue.StatSummary -> {
                    b as MetricValue.StatSummary
                    val c = compareBy3(a.min, a.avg, a.max, b.min, b.avg, b.max)
                    if (c != 0) c else a.count.compareTo(b.count)
                }
                is MetricValue.SleepSegment -> {
                    b as MetricValue.SleepSegment
                    val c = a.state.ordinal.compareTo(b.state.ordinal)
                    if (c != 0) c else a.durationSeconds.compareTo(b.durationSeconds)
                }
                is MetricValue.Ecg -> {
                    b as MetricValue.Ecg
                    var c = a.classification.compareTo(b.classification)
                    if (c != 0) return c
                    c = a.averageBpm.compareTo(b.averageBpm)
                    if (c != 0) return c
                    c = a.samplingHz.compareTo(b.samplingHz)
                    if (c != 0) return c
                    compareBigDecimalLists(a.voltages, b.voltages)
                }
            }
        }

        private fun valueTypeRank(v: MetricValue): Int = when (v) {
            is MetricValue.Scalar -> 0
            is MetricValue.BloodPressure -> 1
            is MetricValue.HeartRateStat -> 2
            is MetricValue.StatSummary -> 3
            is MetricValue.SleepSegment -> 4
            is MetricValue.Ecg -> 5
        }

        private fun compareBy2(a1: BigDecimal, a2: BigDecimal, b1: BigDecimal, b2: BigDecimal): Int {
            val c = a1.compareTo(b1)
            return if (c != 0) c else a2.compareTo(b2)
        }

        private fun compareBy3(
            a1: BigDecimal,
            a2: BigDecimal,
            a3: BigDecimal,
            b1: BigDecimal,
            b2: BigDecimal,
            b3: BigDecimal,
        ): Int {
            var c = a1.compareTo(b1)
            if (c != 0) return c
            c = a2.compareTo(b2)
            if (c != 0) return c
            return a3.compareTo(b3)
        }

        private fun compareBigDecimalLists(a: List<BigDecimal>, b: List<BigDecimal>): Int {
            val min = minOf(a.size, b.size)
            for (i in 0 until min) {
                val c = a[i].compareTo(b[i])
                if (c != 0) return c
            }
            return a.size.compareTo(b.size)
        }
    }
}
