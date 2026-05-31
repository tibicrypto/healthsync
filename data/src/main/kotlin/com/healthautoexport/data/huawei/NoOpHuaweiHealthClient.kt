package com.healthautoexport.data.huawei

import com.healthautoexport.domain.model.DateRange
import com.healthautoexport.domain.model.HealthMetricType
import com.healthautoexport.domain.model.WorkoutType

/**
 * Bản hiện thực **mặc định, an toàn để build/chạy ở mọi nơi** của [HuaweiHealthClient]: báo rằng
 * HMS Core / Huawei_Health_Kit **không khả dụng** và không thực hiện thao tác nào (Requirement 2.1).
 *
 * ## Vai trò
 *
 * Đây là bản hiện thực được nạp khi App chạy trên thiết bị **không phải Huawei** hoặc trong môi
 * trường kiểm thử không có HMS Core, cũng như trong build mặc định **không** khai báo phụ thuộc
 * `com.huawei.hms:health`. Với client này:
 *
 * - [isHmsAvailable] luôn trả về `false`, khiến [HuaweiHealthDataSource.availability] báo
 *   `Unavailable` và App tiếp tục bằng Health_Connect (Requirement 2.1).
 * - [requestAuthorization] trả về [HuaweiAuthResult.Unavailable]; [HuaweiPermissionManager] do đó
 *   coi mọi metric là "chưa ủy quyền" và không lưu scope nào.
 * - [getAuthorizedScopes] và mọi lần đọc ([readRecords]/[readWorkouts]) trả về rỗng.
 * - [cancelAuthorization] là no-op.
 *
 * ## Drop-in bản thật cho Huawei flavor
 *
 * Khi xây dựng một **Huawei product flavor** (thêm kho Maven của Huawei và phụ thuộc
 * `com.huawei.hms:health`), hãy cung cấp một bản hiện thực `RealHuaweiHealthClient` bọc các API
 * chính thức của SDK và bind nó thay cho `NoOpHuaweiHealthClient` trong Hilt:
 *
 * - `HuaweiApiAvailability` để kiểm tra HMS khả dụng cho [isHmsAvailable];
 * - `HealthKitAuthClient` (từ `com.huawei.hms.hihealth`) để chạy luồng ủy quyền trong
 *   [requestAuthorization]/[getAuthorizedScopes]/[cancelAuthorization];
 * - `DataController` để truy vấn `SampleSet`/`SamplePoint` trong [readRecords]/[readWorkouts],
 *   chuyển chúng thành [HuaweiRawSample]/[HuaweiRawWorkout] trung lập ngay tại ranh giới adapter.
 *
 * Nhờ vậy [HuaweiHealthDataSource] và [HuaweiPermissionManager] **không cần thay đổi** khi chuyển
 * giữa build thường và Huawei flavor.
 */
internal class NoOpHuaweiHealthClient : HuaweiHealthClient {

    /** HMS không khả dụng — luôn `false` (Requirement 2.1). */
    override suspend fun isHmsAvailable(): Boolean = false

    /** Không thể ủy quyền vì HMS không khả dụng (Requirement 2.1). */
    override suspend fun requestAuthorization(scopes: Set<HuaweiScope>): HuaweiAuthResult =
        HuaweiAuthResult.Unavailable

    /** Không có scope nào được ủy quyền dưới NoOp. */
    override suspend fun getAuthorizedScopes(): Set<HuaweiScope> = emptySet()

    /** No-op: không có ủy quyền nào để hủy. */
    override suspend fun cancelAuthorization() = Unit

    /** Không có dữ liệu để đọc dưới NoOp. */
    override suspend fun readRecords(
        metrics: Set<HealthMetricType>,
        range: DateRange,
    ): List<HuaweiRawSample> = emptyList()

    /** Không có phiên tập để đọc dưới NoOp. */
    override suspend fun readWorkouts(
        workouts: Set<WorkoutType>,
        range: DateRange,
    ): List<HuaweiRawWorkout> = emptyList()
}
