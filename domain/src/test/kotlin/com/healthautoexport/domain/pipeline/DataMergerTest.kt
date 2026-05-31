package com.healthautoexport.domain.pipeline

import com.healthautoexport.domain.model.CanonicalUnit
import com.healthautoexport.domain.model.DataSourceId
import com.healthautoexport.domain.model.DuplicateTolerance
import com.healthautoexport.domain.model.DuplicateToleranceTable
import com.healthautoexport.domain.model.HealthMetricType
import com.healthautoexport.domain.model.MetricValue
import com.healthautoexport.domain.model.SourcePriority
import com.healthautoexport.domain.model.UnifiedRecord
import com.healthautoexport.domain.port.SourceReadResult
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset

/**
 * Unit test theo ví dụ (example-based) cho [DataMerger] — task 6.1.
 *
 * Bổ trợ cho property test của task 6.2 (Property 14–18): tập trung các trường hợp biên cụ thể
 * của hợp nhất + loại trùng đa nguồn (Requirement 7): khử trùng theo dung sai, chọn bản sống sót
 * theo ưu tiên/bảng chữ cái, giữ bản phân kỳ giá trị, thứ tự sắp xếp tổng, và idempotence.
 */
class DataMergerTest : FunSpec({

    val baseTime = Instant.parse("2024-06-15T10:00:00Z")

    /** Tạo nhanh một bản ghi STEP_COUNT vô hướng. */
    fun record(
        source: DataSourceId,
        offsetSeconds: Long,
        qty: String,
        metric: HealthMetricType = HealthMetricType.STEP_COUNT,
    ): UnifiedRecord = UnifiedRecord(
        metric = metric,
        value = MetricValue.Scalar(BigDecimal(qty)),
        unit = CanonicalUnit.COUNT,
        timestamp = baseTime.plusSeconds(offsetSeconds),
        zoneOffset = ZoneOffset.UTC,
        dataSourceId = source,
    )

    fun readResult(vararg records: UnifiedRecord): SourceReadResult =
        SourceReadResult(records = records.toList(), workouts = emptyList())

    /** Bảng dung sai: ±5 giây và ±2 đơn vị giá trị cho STEP_COUNT. */
    val tolerances: DuplicateToleranceTable = mapOf(
        HealthMetricType.STEP_COUNT to DuplicateTolerance(timeSeconds = 5, valueMagnitude = BigDecimal("2")),
    )

    // Health_Connect ưu tiên cao hơn (rank 0) so với Huawei (rank 1).
    val hcPriority = SourcePriority(
        ranks = mapOf(
            DataSourceId.HEALTH_CONNECT to 0,
            DataSourceId.HUAWEI_HEALTH_KIT to 1,
        ),
    )

    fun stepRecords(merged: com.healthautoexport.domain.model.MergedDataset): List<UnifiedRecord> =
        merged.recordsByMetric[HealthMetricType.STEP_COUNT].orEmpty()

    // --- Requirement 7.1: gộp nhiều nguồn thành một chuỗi cho mỗi metric ---

    test("gộp bản ghi không trùng từ hai nguồn thành một chuỗi của metric") {
        val merger = DataMerger(tolerances, hcPriority)
        val a = record(DataSourceId.HEALTH_CONNECT, offsetSeconds = 0, qty = "100")
        val b = record(DataSourceId.HUAWEI_HEALTH_KIT, offsetSeconds = 60, qty = "200")

        val merged = merger.merge(listOf(readResult(a), readResult(b)))

        stepRecords(merged) shouldContainExactly listOf(a, b)
    }

    // --- Requirement 7.3 / Property 14: khử trùng theo dung sai (thời gian + giá trị) ---

    test("loại trùng khi |Δt| ≤ tol VÀ |Δvalue| ≤ tol — giữ đúng một bản") {
        val merger = DataMerger(tolerances, hcPriority)
        // Cách nhau 3 giây (≤5) và 1 đơn vị (≤2) ⇒ trùng.
        val hc = record(DataSourceId.HEALTH_CONNECT, offsetSeconds = 0, qty = "100")
        val hw = record(DataSourceId.HUAWEI_HEALTH_KIT, offsetSeconds = 3, qty = "101")

        val merged = merger.merge(listOf(readResult(hc), readResult(hw)))

        // Survivor là Health_Connect (ưu tiên cao hơn) — Property 15.
        stepRecords(merged) shouldContainExactly listOf(hc)
    }

    test("không loại trùng khi chênh giá trị vượt tol dù cùng thời điểm") {
        val merger = DataMerger(tolerances, hcPriority)
        // Cùng thời điểm nhưng chênh 10 đơn vị (>2) ⇒ KHÔNG trùng (Requirement 7.7 / Property 16).
        val hc = record(DataSourceId.HEALTH_CONNECT, offsetSeconds = 0, qty = "100")
        val hw = record(DataSourceId.HUAWEI_HEALTH_KIT, offsetSeconds = 0, qty = "110")

        val merged = merger.merge(listOf(readResult(hc), readResult(hw)))
        val out = stepRecords(merged)

        out.size shouldBe 2
        // Cả hai giữ nhãn nguồn gốc.
        out.map { it.dataSourceId } shouldContainExactly listOf(
            DataSourceId.HEALTH_CONNECT,
            DataSourceId.HUAWEI_HEALTH_KIT,
        )
    }

    test("không loại trùng khi chênh thời gian vượt tol dù giá trị gần nhau") {
        val merger = DataMerger(tolerances, hcPriority)
        // Cách 10 giây (>5) ⇒ KHÔNG trùng.
        val hc = record(DataSourceId.HEALTH_CONNECT, offsetSeconds = 0, qty = "100")
        val hw = record(DataSourceId.HUAWEI_HEALTH_KIT, offsetSeconds = 10, qty = "100")

        val merged = merger.merge(listOf(readResult(hc), readResult(hw)))
        stepRecords(merged).size shouldBe 2
    }

    // --- Requirement 7.4 / Property 15: survivor = ưu tiên nguồn cao nhất ---

    test("survivor là nguồn có ưu tiên cao hơn bất kể thứ tự đầu vào") {
        val merger = DataMerger(tolerances, hcPriority)
        val hc = record(DataSourceId.HEALTH_CONNECT, offsetSeconds = 2, qty = "100")
        val hw = record(DataSourceId.HUAWEI_HEALTH_KIT, offsetSeconds = 0, qty = "100")

        // Huawei xuất hiện trước trong đầu vào, nhưng Health_Connect vẫn thắng theo ưu tiên.
        val merged = merger.merge(listOf(readResult(hw), readResult(hc)))
        stepRecords(merged) shouldContainExactly listOf(hc)
    }

    // --- Requirement 7.5 / Property 15: hòa ưu tiên ⇒ dataSourceId theo bảng chữ cái ---

    test("khi ưu tiên bằng nhau, giữ dataSourceId đứng trước theo bảng chữ cái") {
        // Cả hai nguồn cùng rank 0 ⇒ tie-break theo id: "health_connect" < "huawei_health_kit".
        val equalPriority = SourcePriority(
            ranks = mapOf(
                DataSourceId.HEALTH_CONNECT to 0,
                DataSourceId.HUAWEI_HEALTH_KIT to 0,
            ),
        )
        val merger = DataMerger(tolerances, equalPriority)
        val hc = record(DataSourceId.HEALTH_CONNECT, offsetSeconds = 0, qty = "100")
        val hw = record(DataSourceId.HUAWEI_HEALTH_KIT, offsetSeconds = 1, qty = "100")

        val merged = merger.merge(listOf(readResult(hw), readResult(hc)))
        stepRecords(merged) shouldContainExactly listOf(hc)
    }

    // --- Requirement 7.6 / Property 17: thứ tự sắp xếp tổng (timestamp, dataSourceId, value) ---

    test("đầu ra sắp xếp tăng dần theo (timestamp, dataSourceId, value)") {
        // Dùng tolerance 0 để không có gì bị loại, chỉ kiểm tra thứ tự.
        val zeroTol: DuplicateToleranceTable = mapOf(
            HealthMetricType.STEP_COUNT to DuplicateTolerance(0, BigDecimal.ZERO),
        )
        val merger = DataMerger(zeroTol, hcPriority)

        val t1Hw = record(DataSourceId.HUAWEI_HEALTH_KIT, offsetSeconds = 0, qty = "5")
        val t1HcLow = record(DataSourceId.HEALTH_CONNECT, offsetSeconds = 0, qty = "1")
        val t1HcHigh = record(DataSourceId.HEALTH_CONNECT, offsetSeconds = 0, qty = "9")
        val t2Hw = record(DataSourceId.HUAWEI_HEALTH_KIT, offsetSeconds = 30, qty = "3")

        val merged = merger.merge(listOf(readResult(t2Hw, t1Hw), readResult(t1HcHigh, t1HcLow)))

        // Cùng timestamp t1: health_connect trước huawei; trong health_connect: value tăng dần.
        stepRecords(merged) shouldContainExactly listOf(t1HcLow, t1HcHigh, t1Hw, t2Hw)
    }

    // --- Requirement 7.7 / Property 16: giữ cả hai khi giá trị phân kỳ + gắn nhãn nguồn ---

    test("giữ cả hai bản phân kỳ giá trị trong cùng cửa sổ thời gian với nhãn nguồn") {
        val merger = DataMerger(tolerances, hcPriority)
        val hc = record(DataSourceId.HEALTH_CONNECT, offsetSeconds = 0, qty = "100")
        val hw = record(DataSourceId.HUAWEI_HEALTH_KIT, offsetSeconds = 2, qty = "150")

        val out = stepRecords(merger.merge(listOf(readResult(hc), readResult(hw))))
        out.size shouldBe 2
        out.any { it.dataSourceId == DataSourceId.HEALTH_CONNECT } shouldBe true
        out.any { it.dataSourceId == DataSourceId.HUAWEI_HEALTH_KIT } shouldBe true
    }

    // --- Requirement 7.9 / Property 18: idempotence ---

    test("merge(merge(x)) == merge(x) — không loại thêm, không đổi thứ tự") {
        val merger = DataMerger(tolerances, hcPriority)
        val records = listOf(
            record(DataSourceId.HEALTH_CONNECT, 0, "100"),
            record(DataSourceId.HUAWEI_HEALTH_KIT, 2, "101"), // trùng hc@0
            record(DataSourceId.HEALTH_CONNECT, 30, "100"),
            record(DataSourceId.HUAWEI_HEALTH_KIT, 30, "140"), // phân kỳ giá trị với hc@30
            record(DataSourceId.HEALTH_CONNECT, 60, "200"),
        )
        val once = merger.merge(listOf(readResult(*records.toTypedArray())))

        // Bọc lại đầu ra của lần merge đầu thành đầu vào của lần thứ hai.
        val twice = merger.merge(listOf(SourceReadResult(once.records, emptyList())))

        twice.recordsByMetric shouldBe once.recordsByMetric
    }

    // --- Khử trùng bắc cầu không hoàn hảo: A~B, B~C nhưng A≁C ---

    test("chuỗi trùng không bắc cầu: chỉ loại bản thực sự trùng với bản đã giữ") {
        // tol thời gian 5s, value 2. Ba bản cùng nguồn, cùng giá trị, cách nhau 4s mỗi bước:
        // 0s, 4s, 8s. (0,4) trùng, (4,8) trùng, nhưng (0,8) cách 8s > 5 ⇒ không trùng.
        val merger = DataMerger(tolerances, hcPriority)
        val r0 = record(DataSourceId.HEALTH_CONNECT, 0, "100")
        val r4 = record(DataSourceId.HEALTH_CONNECT, 4, "100")
        val r8 = record(DataSourceId.HEALTH_CONNECT, 8, "100")

        val out = stepRecords(merger.merge(listOf(readResult(r0, r4, r8))))
        // r0 được giữ; r4 trùng r0 ⇒ loại; r8 không trùng r0 (đã giữ) ⇒ giữ.
        out shouldContainExactly listOf(r0, r8)
    }

    // --- Workouts pass-through ---

    test("workouts được chuyển tiếp nguyên trạng và gộp theo thứ tự nguồn") {
        val merger = DataMerger(tolerances, hcPriority)
        val merged = merger.merge(
            listOf(
                SourceReadResult(emptyList(), emptyList()),
                SourceReadResult(emptyList(), emptyList()),
            ),
        )
        merged.workouts shouldBe emptyList()
    }

    test("danh sách nguồn rỗng cho ra MergedDataset rỗng") {
        val merger = DataMerger(tolerances, hcPriority)
        val merged = merger.merge(emptyList())
        merged.recordsByMetric shouldBe emptyMap()
        merged.workouts shouldBe emptyList()
    }
})
