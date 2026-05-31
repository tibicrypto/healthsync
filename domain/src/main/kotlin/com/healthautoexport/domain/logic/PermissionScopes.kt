package com.healthautoexport.domain.logic

import com.healthautoexport.domain.model.DataSourceId
import com.healthautoexport.domain.model.HealthMetricType
import com.healthautoexport.domain.model.HealthPermission
import com.healthautoexport.domain.model.MetricSelection
import com.healthautoexport.domain.model.PermissionState
import com.healthautoexport.domain.model.WorkoutType

/**
 * Logic **thuần** ánh xạ một [MetricSelection] sang tập quyền/scope đọc và sang trạng thái quyền
 * theo từng metric, độc lập với SDK của bất kỳ Data_Source nào (Requirements 1.2, 1.7, 2.2, 2.7).
 *
 * Đây là "nguồn sự thật" cho việc một lựa chọn cần đúng những quyền nào; bộ điều hợp tầng dữ liệu
 * (task 13/14) chỉ việc dịch mỗi [ReadScope] thành chuỗi quyền tương ứng của Health_Connect hoặc
 * Huawei_Health_Kit. Vì hàm thuần và xác định nên kiểm thử được Property 29 và Property 30.
 */
object PermissionScopes {

    /**
     * Ánh xạ chuẩn từ một [HealthMetricType] sang [HealthPermission] đọc trên [source].
     *
     * Quan hệ là **một-một**: mỗi metric ứng với đúng một quyền đọc thường (không nền). Đây là
     * điểm tập trung duy nhất của phép ánh xạ metric→permission để các hàm khác tái sử dụng và
     * để bảo đảm tính nhất quán (không thừa/thiếu — Property 29).
     *
     * @param metric loại Health_Metric đã chọn.
     * @param source nguồn dữ liệu cần quyền.
     * @return quyền đọc (thường) tương ứng.
     */
    fun permissionFor(metric: HealthMetricType, source: DataSourceId): HealthPermission =
        HealthPermission(source = source, metric = metric, background = false)

    /**
     * Tập quyền/scope đọc **chính xác** cần yêu cầu cho [selection] trên [source]
     * (Property 29, Requirements 1.2, 2.2).
     *
     * Kết quả bằng đúng hợp của:
     * - một [ReadScope.Metric] cho mỗi [HealthMetricType] đã chọn, và
     * - một [ReadScope.Workout] cho mỗi [WorkoutType] đã chọn.
     *
     * Không thêm quyền nào ngoài lựa chọn và không bỏ sót quyền nào trong lựa chọn. Với lựa chọn
     * rỗng, trả về tập rỗng. Hàm thuần, không phụ thuộc thứ tự lặp.
     *
     * @param selection lựa chọn metric/workout của người dùng.
     * @param source nguồn dữ liệu cần yêu cầu quyền.
     * @return tập [ReadScope] tương ứng đúng-một-một với các phần tử của [selection].
     */
    fun permissionsForSelection(
        selection: MetricSelection,
        source: DataSourceId,
    ): Set<ReadScope> {
        val metricScopes: Set<ReadScope> = selection.metrics
            .mapTo(mutableSetOf()) { ReadScope.Metric(permissionFor(it, source)) }
        val workoutScopes: Set<ReadScope> = selection.workouts
            .mapTo(mutableSetOf()) { ReadScope.Workout(source, it) }
        return metricScopes + workoutScopes
    }

    /**
     * Chỉ phần quyền **metric** của [permissionsForSelection], dưới dạng tập [HealthPermission].
     *
     * Tiện ích cho các tầng làm việc trực tiếp với mô hình [HealthPermission] (vd so khớp với
     * tập đã cấp trả về bởi `PermissionManager.refreshGrants`).
     *
     * @param selection lựa chọn của người dùng (chỉ phần metric được dùng).
     * @param source nguồn dữ liệu.
     * @return tập quyền đọc metric tương ứng đúng-một-một với [MetricSelection.metrics].
     */
    fun metricPermissionsForSelection(
        selection: MetricSelection,
        source: DataSourceId,
    ): Set<HealthPermission> =
        selection.metrics.mapTo(mutableSetOf()) { permissionFor(it, source) }

    /**
     * Bản đồ trạng thái quyền **toàn phần** theo từng metric đã chọn (Property 30, Requirements
     * 1.7, 2.7).
     *
     * Gán cho **mỗi** metric trong [MetricSelection.metrics] đúng một [PermissionState]:
     * [PermissionState.GRANTED] khi và chỉ khi quyền đọc tương ứng (xem [permissionFor]) nằm
     * trong [grantedPermissions]; ngược lại [PermissionState.NOT_GRANTED].
     *
     * Tính "toàn phần" nghĩa là tập khóa của bản đồ trả về **bằng đúng** [MetricSelection.metrics]
     * — không thiếu metric nào và không thêm metric ngoài lựa chọn. Quyền nền (`background = true`)
     * trong [grantedPermissions] không ảnh hưởng kết quả vì phép so khớp dùng quyền thường.
     *
     * @param selection lựa chọn của người dùng.
     * @param grantedPermissions tập quyền hiện được cấp/ủy quyền (vd từ `refreshGrants`).
     * @param source nguồn dữ liệu cần đánh giá trạng thái.
     * @return bản đồ mỗi metric đã chọn → trạng thái quyền của nó.
     */
    fun grantedStatus(
        selection: MetricSelection,
        grantedPermissions: Set<HealthPermission>,
        source: DataSourceId,
    ): Map<HealthMetricType, PermissionState> =
        selection.metrics.associateWith { metric ->
            val required = permissionFor(metric, source)
            if (required in grantedPermissions) {
                PermissionState.GRANTED
            } else {
                PermissionState.NOT_GRANTED
            }
        }
}
