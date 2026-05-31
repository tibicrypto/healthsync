package com.healthautoexport.domain.logic

import com.healthautoexport.domain.model.DataSourceId
import com.healthautoexport.domain.model.HealthPermission
import com.healthautoexport.domain.model.WorkoutType

/**
 * Một **phạm vi đọc** (read permission/scope) cần được yêu cầu cho một Data_Source, tổng quát
 * hóa hai loại đối tượng có thể chọn trong một [com.healthautoexport.domain.model.MetricSelection]:
 * Health_Metric và Workout (Requirements 1.2, 2.2).
 *
 * Mô hình [HealthPermission] hiện chỉ biểu diễn được quyền đọc cho một
 * [com.healthautoexport.domain.model.HealthMetricType]; nó **không** mô tả được phạm vi đọc cho
 * một [WorkoutType] (ví dụ scope `ExerciseSession` của Health_Connect). Vì vậy `ReadScope` bọc
 * `HealthPermission` cho metric và bổ sung một biến thể riêng cho workout — đúng theo gợi ý
 * "thêm một sealed type nhỏ trong cùng package logic khi kiểu sẵn có không vừa".
 *
 * Tập `ReadScope` do [PermissionScopes.permissionsForSelection] sinh ra là **chính xác** ánh xạ
 * từ lựa chọn của người dùng — không thừa, không thiếu (Property 29).
 */
sealed interface ReadScope {

    /** Nguồn dữ liệu mà phạm vi đọc này thuộc về. */
    val source: DataSourceId

    /**
     * Phạm vi đọc cho một Health_Metric, bọc một [HealthPermission] thuần domain.
     *
     * @property permission quyền đọc metric tương ứng (mang theo `source`, `metric`).
     */
    data class Metric(val permission: HealthPermission) : ReadScope {
        override val source: DataSourceId get() = permission.source
    }

    /**
     * Phạm vi đọc cho một loại Workout.
     *
     * @property source nguồn dữ liệu.
     * @property workout loại Workout cần quyền đọc.
     */
    data class Workout(
        override val source: DataSourceId,
        val workout: WorkoutType,
    ) : ReadScope
}
