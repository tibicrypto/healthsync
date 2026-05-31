package com.healthautoexport.data.huawei

import com.healthautoexport.domain.logic.ReadScope
import com.healthautoexport.domain.model.DataSourceId
import com.healthautoexport.domain.model.HealthMetricType
import com.healthautoexport.domain.model.MetricSelection
import com.healthautoexport.domain.model.WorkoutType

/**
 * Ánh xạ **hai chiều, xác định** giữa các phạm vi đọc của domain ([ReadScope]) và các
 * [HuaweiScope] trung lập của adapter.
 *
 * Mapper này là điểm tập trung dịch metric/workout đã chọn sang chuỗi scope ổn định và ngược lại.
 * Nó **không** tự quyết định metric nào cần scope nào theo lựa chọn — việc đó do
 * [com.healthautoexport.domain.logic.PermissionScopes] (logic thuần domain) đảm nhiệm để bảo đảm
 * "đúng-một-một với lựa chọn" (Requirement 2.2, Property 29). `HuaweiScopeMapper` chỉ chịu trách
 * nhiệm phần **biểu diễn chuỗi** đặc thù Huawei.
 *
 * Bản hiện thực thật cho Huawei flavor có thể thay/đắp thêm bằng cách dịch [HuaweiScope.value]
 * sang URL scope chính thức của Huawei; ở đây dùng tiền tố ổn định nội bộ.
 */
internal object HuaweiScopeMapper {

    private const val METRIC_PREFIX = "huawei.read.metric."
    private const val WORKOUT_PREFIX = "huawei.read.workout."

    /** [HuaweiScope] đọc tương ứng cho một [HealthMetricType]. */
    fun scopeFor(metric: HealthMetricType): HuaweiScope =
        HuaweiScope(METRIC_PREFIX + metric.name)

    /** [HuaweiScope] đọc tương ứng cho một [WorkoutType]. */
    fun scopeFor(workout: WorkoutType): HuaweiScope =
        HuaweiScope(WORKOUT_PREFIX + workout.name)

    /**
     * Dịch một [ReadScope] của domain sang [HuaweiScope] trung lập.
     *
     * Lưu ý: chỉ các [ReadScope] thuộc Huawei_Health_Kit mới có ý nghĩa ở đây; người gọi nên cung
     * cấp scope của đúng nguồn (xem [scopesForSelection]).
     */
    fun toHuaweiScope(scope: ReadScope): HuaweiScope = when (scope) {
        is ReadScope.Metric -> scopeFor(scope.permission.metric)
        is ReadScope.Workout -> scopeFor(scope.workout)
    }

    /**
     * Tập [HuaweiScope] cần yêu cầu cho [selection], suy ra từ tập [ReadScope] mà
     * [com.healthautoexport.domain.logic.PermissionScopes.permissionsForSelection] sinh ra cho
     * [DataSourceId.HUAWEI_HEALTH_KIT] (Requirement 2.2).
     *
     * Tách qua `PermissionScopes` để giữ một nguồn sự thật duy nhất cho quan hệ lựa chọn→scope.
     *
     * @param selection lựa chọn metric/workout của người dùng.
     * @param scopes tập [ReadScope] do `PermissionScopes` sinh ra (chỉ phần của Huawei được dùng).
     * @return tập [HuaweiScope] đúng-một-một với [selection].
     */
    fun toHuaweiScopes(scopes: Set<ReadScope>): Set<HuaweiScope> =
        scopes.mapTo(mutableSetOf()) { toHuaweiScope(it) }

    /**
     * `true` nếu [HuaweiScope] đã ủy quyền tương ứng với quyền đọc của [metric]; dùng để dựng bản
     * đồ trạng thái "Đã/Chưa ủy quyền" theo từng metric (Requirement 2.7).
     */
    fun isMetricAuthorized(metric: HealthMetricType, authorized: Set<HuaweiScope>): Boolean =
        scopeFor(metric) in authorized

    /**
     * Khôi phục tập [HealthMetricType] từ một tập [HuaweiScope] đã ủy quyền (chiều ngược của
     * [scopeFor]); dùng cho `refreshGrants` để dựng tập [com.healthautoexport.domain.model.HealthPermission]
     * (Requirements 2.6, 2.7).
     *
     * Chỉ các scope **metric** được khôi phục; scope workout bị bỏ qua vì mô hình `HealthPermission`
     * thuần domain hiện chỉ biểu diễn được quyền đọc theo metric (nhất quán với
     * [com.healthautoexport.domain.logic.PermissionScopes.metricPermissionsForSelection]).
     */
    fun metricsFromScopes(authorized: Set<HuaweiScope>): Set<HealthMetricType> {
        val byScopeName: Map<String, HealthMetricType> =
            HealthMetricType.entries.associateBy { scopeFor(it).value }
        return authorized.mapNotNullTo(mutableSetOf()) { byScopeName[it.value] }
    }

    /**
     * Bộ lọc tiện ích: từ một [MetricSelection], trả về tập metric đã được ủy quyền theo
     * [authorized] (Requirement 2.7).
     */
    fun authorizedMetrics(
        selection: MetricSelection,
        authorized: Set<HuaweiScope>,
    ): Set<HealthMetricType> =
        selection.metrics.filterTo(mutableSetOf()) { isMetricAuthorized(it, authorized) }
}
