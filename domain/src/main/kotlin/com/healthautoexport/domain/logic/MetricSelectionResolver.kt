package com.healthautoexport.domain.logic

import com.healthautoexport.domain.model.DataSourceId
import com.healthautoexport.domain.model.HealthMetricType
import com.healthautoexport.domain.model.HealthPermission
import com.healthautoexport.domain.model.MetricCatalog
import com.healthautoexport.domain.model.MetricSelection
import com.healthautoexport.domain.port.ReadWarning

/**
 * Kết quả của [MetricSelectionResolver.effectiveMetrics]: tập metric **hiệu lực** sẽ đưa vào
 * Export_Job cùng danh sách loại trừ có cấu trúc giải thích từng metric bị bỏ (Property 28).
 *
 * @property effective tập metric hiệu lực = `selection ∩ granted ∩ (enabled ∩ available ∩
 *   supported)`.
 * @property exclusions mỗi metric đã chọn nhưng bị loại, kèm lý do; sắp xếp xác định theo tên
 *   metric để kết quả ổn định và kiểm thử được.
 */
data class EffectiveMetrics(
    val effective: Set<HealthMetricType>,
    val exclusions: List<MetricExclusion>,
) {
    /** Toàn bộ cảnh báo loại trừ ở dạng [ReadWarning] để ghi Sync_Log (Requirements 1.4, 2.5, 4.3). */
    val warnings: List<ReadWarning>
        get() = exclusions.flatMap { it.toReadWarnings() }
}

/**
 * Logic **thuần** (xác định, không I/O) tính tập Health_Metric hiệu lực cho một Export_Job và
 * lý do loại trừ cho các metric bị bỏ (Property 28, Requirements 1.4, 1.6, 2.5, 4.3, 4.6).
 *
 * Toàn bộ quyết định chỉ dựa trên đầu vào (lựa chọn, quyền đã cấp theo nguồn, nguồn bật, nguồn
 * khả dụng) và bảng tra cứu tĩnh [MetricCatalog]; không truy cập thiết bị/mạng. Nhờ vậy quy tắc
 * lọc metric được kiểm chứng bằng property-based test trên JVM.
 */
object MetricSelectionResolver {

    /**
     * Tính tập metric hiệu lực và danh sách loại trừ.
     *
     * Một metric đã chọn là **hiệu lực** khi và chỉ khi tồn tại ít nhất một Data_Source `S` thỏa:
     * `S ∈ (enabled ∩ available)` **và** [MetricCatalog.isSupportedBy]`(metric, S)` **và** quyền
     * đọc `(metric, S)` nằm trong [grantedPermissionsBySource]`[S]`. Điều này hiện thực hóa phép
     * giao `selection ∩ granted ∩ (enabled ∩ available ∩ supported)` (Property 28).
     *
     * Với mỗi metric đã chọn nhưng **không** hiệu lực, hàm tạo đúng một [MetricExclusion]:
     * - [ExclusionReason.UNSUPPORTED_ON_DEVICE] nếu không nguồn được truy vấn nào hỗ trợ metric
     *   (Requirements 4.3, 4.6); `relatedSources` = tập nguồn được truy vấn (có thể rỗng).
     * - [ExclusionReason.PERMISSION_NOT_GRANTED] nếu có nguồn hỗ trợ nhưng không nguồn hỗ trợ nào
     *   được cấp quyền (Requirements 1.4, 1.6, 2.5); `relatedSources` = các nguồn hỗ trợ còn thiếu
     *   quyền.
     *
     * Hàm thuần và xác định: tập [EffectiveMetrics.effective] độc lập thứ tự; danh sách
     * [EffectiveMetrics.exclusions] được sắp xếp theo tên metric.
     *
     * @param selection lựa chọn metric/workout của người dùng (chỉ phần metric được dùng ở đây).
     * @param grantedPermissionsBySource quyền đã cấp/ủy quyền theo từng nguồn; nguồn vắng mặt được
     *   coi như không có quyền nào.
     * @param enabledSources tập Data_Source người dùng đã bật (Requirements 3.1, 3.2).
     * @param availableSources tập Data_Source hiện khả dụng tại thời điểm job.
     * @return [EffectiveMetrics] gồm tập hiệu lực và các mục loại trừ.
     */
    fun effectiveMetrics(
        selection: MetricSelection,
        grantedPermissionsBySource: Map<DataSourceId, Set<HealthPermission>>,
        enabledSources: Set<DataSourceId>,
        availableSources: Set<DataSourceId>,
    ): EffectiveMetrics {
        val queried = SourceSelection.queriedSources(enabledSources, availableSources)

        val effective = LinkedHashSet<HealthMetricType>()
        val exclusions = ArrayList<MetricExclusion>()

        // Lặp theo thứ tự tên metric để danh sách loại trừ xác định, ổn định cho kiểm thử.
        for (metric in selection.metrics.sortedBy { it.name }) {
            // Các nguồn được truy vấn có hỗ trợ metric trên thiết bị.
            val supportingSources = queried
                .filter { MetricCatalog.isSupportedBy(metric, it) }
                .toSortedSet(compareBy { it.id })

            if (supportingSources.isEmpty()) {
                exclusions += MetricExclusion(
                    metric = metric,
                    reason = ExclusionReason.UNSUPPORTED_ON_DEVICE,
                    message = unsupportedMessage(metric, queried),
                    relatedSources = queried.toSortedSet(compareBy { it.id }),
                )
                continue
            }

            // Trong các nguồn hỗ trợ, nguồn nào đã cấp quyền đọc cho metric?
            val grantedSources = supportingSources.filterTo(LinkedHashSet()) { source ->
                val required = PermissionScopes.permissionFor(metric, source)
                required in grantedPermissionsBySource.orEmptyFor(source)
            }

            if (grantedSources.isEmpty()) {
                exclusions += MetricExclusion(
                    metric = metric,
                    reason = ExclusionReason.PERMISSION_NOT_GRANTED,
                    message = permissionMessage(metric, supportingSources),
                    relatedSources = supportingSources,
                )
            } else {
                effective += metric
            }
        }

        return EffectiveMetrics(effective = effective, exclusions = exclusions)
    }

    private fun Map<DataSourceId, Set<HealthPermission>>.orEmptyFor(
        source: DataSourceId,
    ): Set<HealthPermission> = this[source] ?: emptySet()

    private fun unsupportedMessage(metric: HealthMetricType, queried: Set<DataSourceId>): String {
        val name = MetricCatalog.spec(metric).canonicalName
        return if (queried.isEmpty()) {
            "Chỉ số '$name' bị loại: không có Data_Source nào được bật và khả dụng để cung cấp."
        } else {
            "Chỉ số '$name' bị loại: không Data_Source đang bật nào hỗ trợ chỉ số này trên thiết bị."
        }
    }

    private fun permissionMessage(metric: HealthMetricType, sources: Set<DataSourceId>): String {
        val name = MetricCatalog.spec(metric).canonicalName
        val ids = sources.joinToString(", ") { it.id }
        return "Chỉ số '$name' bị loại: chưa được cấp quyền đọc trên nguồn hỗ trợ ($ids)."
    }
}
