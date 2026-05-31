package com.healthautoexport.data.persistence

import com.healthautoexport.domain.model.Automation
import com.healthautoexport.domain.model.HealthMetricType
import com.healthautoexport.domain.model.MetricSelection
import com.healthautoexport.domain.model.SyncLogEntry
import com.healthautoexport.domain.model.WorkoutType
import java.time.Instant
import java.util.Locale
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Ánh xạ hai chiều giữa mô hình lưu trữ Room ([AutomationEntity], [SyncLogEntity]) và mô hình
 * domain ([Automation], [SyncLogEntry]).
 *
 * Quy ước chuyển đổi:
 * - **Instant ⇄ epoch-millis `Long`** qua [Instant.toEpochMilli] / [Instant.ofEpochMilli].
 * - **Tập metric/workout ⇄ JSON**: encode tập tên enum bằng kotlinx.serialization. Lưu **tên**
 *   enum (qua `String` serializer + `valueOf`) thay vì `ordinal` để bền vững trước thay đổi thứ
 *   tự khai báo enum; phần tử không nhận diện được khi đọc lại sẽ bị bỏ qua (xem [decodeMetrics]).
 * - **nameLower**: thường-hóa [Automation.name] theo [Locale.ROOT] để so trùng không phân biệt
 *   hoa/thường (Requirement 14.7).
 */
object PersistenceMappers {

    /**
     * Cấu hình JSON dùng cho cột tập metric/workout. `ignoreUnknownKeys` không áp dụng cho mảng
     * chuỗi, nên việc bỏ qua phần tử lạ được xử lý thủ công trong [decodeMetrics]/[decodeWorkouts].
     */
    private val json: Json = Json { encodeDefaults = true }

    private val stringSetSerializer = SetSerializer(String.serializer())

    // --- Automation ---

    /** Chuyển [Automation] domain sang [AutomationEntity] Room. */
    fun toEntity(automation: Automation): AutomationEntity =
        AutomationEntity(
            id = automation.id,
            name = automation.name,
            nameLower = normalizeName(automation.name),
            selectedMetricsJson = encodeMetrics(automation.selection.metrics),
            selectedWorkoutsJson = encodeWorkouts(automation.selection.workouts),
            exportFormat = automation.exportFormat,
            aggregationPeriod = automation.aggregationPeriod,
            scheduleIntervalMinutes = automation.scheduleIntervalMinutes,
            enabled = automation.enabled,
            destinationType = automation.destinationType,
            destinationConfigRef = automation.destinationConfigRef,
            firstActivatedAtUtc = automation.firstActivatedAtUtc?.toEpochMilli(),
            lastSuccessfulEndUtc = automation.lastSuccessfulEndUtc?.toEpochMilli(),
        )

    /** Chuyển [AutomationEntity] Room sang [Automation] domain. */
    fun toDomain(entity: AutomationEntity): Automation =
        Automation(
            id = entity.id,
            name = entity.name,
            selection = MetricSelection(
                metrics = decodeMetrics(entity.selectedMetricsJson),
                workouts = decodeWorkouts(entity.selectedWorkoutsJson),
            ),
            exportFormat = entity.exportFormat,
            aggregationPeriod = entity.aggregationPeriod,
            scheduleIntervalMinutes = entity.scheduleIntervalMinutes,
            enabled = entity.enabled,
            destinationType = entity.destinationType,
            destinationConfigRef = entity.destinationConfigRef,
            firstActivatedAtUtc = entity.firstActivatedAtUtc?.let(Instant::ofEpochMilli),
            lastSuccessfulEndUtc = entity.lastSuccessfulEndUtc?.let(Instant::ofEpochMilli),
        )

    /** Thường-hóa tên cho chỉ mục unique không phân biệt hoa/thường (Requirement 14.7). */
    fun normalizeName(name: String): String = name.lowercase(Locale.ROOT)

    // --- SyncLogEntry ---

    /** Chuyển [SyncLogEntry] domain sang [SyncLogEntity] Room. */
    fun toEntity(entry: SyncLogEntry): SyncLogEntity =
        SyncLogEntity(
            id = entry.id,
            startUtc = entry.startUtc.toEpochMilli(),
            completionUtc = entry.completionUtc?.toEpochMilli(),
            automationId = entry.automationId,
            exportFormat = entry.exportFormat,
            destinationType = entry.destinationType,
            status = entry.status,
            message = entry.message,
        )

    /** Chuyển [SyncLogEntity] Room sang [SyncLogEntry] domain. */
    fun toDomain(entity: SyncLogEntity): SyncLogEntry =
        SyncLogEntry(
            id = entity.id,
            startUtc = Instant.ofEpochMilli(entity.startUtc),
            completionUtc = entity.completionUtc?.let(Instant::ofEpochMilli),
            automationId = entity.automationId,
            exportFormat = entity.exportFormat,
            destinationType = entity.destinationType,
            status = entity.status,
            message = entity.message,
        )

    // --- JSON encode/decode cho tập enum ---

    private fun encodeMetrics(metrics: Set<HealthMetricType>): String =
        json.encodeToString(stringSetSerializer, metrics.map { it.name }.toSet())

    private fun encodeWorkouts(workouts: Set<WorkoutType>): String =
        json.encodeToString(stringSetSerializer, workouts.map { it.name }.toSet())

    private val metricsByName: Map<String, HealthMetricType> =
        HealthMetricType.entries.associateBy { it.name }

    private val workoutsByName: Map<String, WorkoutType> =
        WorkoutType.entries.associateBy { it.name }

    private fun decodeMetrics(jsonText: String): Set<HealthMetricType> =
        json.decodeFromString(stringSetSerializer, jsonText)
            .mapNotNull { metricsByName[it] }
            .toSet()

    private fun decodeWorkouts(jsonText: String): Set<WorkoutType> =
        json.decodeFromString(stringSetSerializer, jsonText)
            .mapNotNull { workoutsByName[it] }
            .toSet()
}
