package com.healthautoexport

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import com.healthautoexport.data.healthconnect.HealthConnectPermissionContractFactory
import com.healthautoexport.data.healthconnect.HealthConnectPermissionRequesterRelay
import com.healthautoexport.domain.usecase.DeepLinkHandler
import com.healthautoexport.ui.navigation.AppRoot
import com.healthautoexport.ui.navigation.TopDestination
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CompletableDeferred
import javax.inject.Inject

/**
 * `Activity` đơn (single-activity) chứa toàn bộ UI Compose của App và là **điểm vào điều hướng**
 * (task 22.1).
 *
 * Trách nhiệm:
 * 1. **Dựng UI**: `setContent { AppRoot(...) }` — [AppRoot] (task 21.2) bọc theme + Navigation host.
 * 2. **Seam quyền Health_Connect** (Requirements 1.2, 1.9): đăng ký
 *    `registerForActivityResult` với hợp đồng từ [HealthConnectPermissionContractFactory], rồi gắn
 *    một delegate vào [HealthConnectPermissionRequesterRelay] để
 *    `HealthConnectPermissionManager` (singleton ở `:data`) khởi chạy luồng cấp quyền **qua**
 *    `Activity` này. Delegate dùng [CompletableDeferred] để biến callback của Activity Result API
 *    thành một lời gọi `suspend` trả về tập quyền được cấp.
 * 3. **Deep link cấu hình Automation** (Requirement 14.6): phân tích `Uri` của `Intent` thành
 *    `Map<String, String>` rồi đưa qua [DeepLinkHandler]; kết quả (đề xuất chờ xác nhận hoặc bị từ
 *    chối) được đẩy vào [DeepLinkCoordinator] để tầng UI trình bày — **không** tạo Automation tại
 *    đây (Requirement 14.8).
 *
 * `@AndroidEntryPoint` cho phép Hilt tiêm các phụ thuộc field vào Activity.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Relay nối seam launch quyền Health_Connect với manager singleton (Requirements 1.2, 1.9). */
    @Inject
    lateinit var permissionRequesterRelay: HealthConnectPermissionRequesterRelay

    /** Factory tạo hợp đồng quyền Health_Connect (vì `:app` không có SDK Health_Connect). */
    @Inject
    lateinit var permissionContractFactory: HealthConnectPermissionContractFactory

    /** Bộ xử lý deep link cấu hình Automation (Requirements 14.6, 14.8). */
    @Inject
    lateinit var deepLinkHandler: DeepLinkHandler

    /** Cầu nối trạng thái deep link tới tầng UI (Requirement 14.6). */
    @Inject
    lateinit var deepLinkCoordinator: DeepLinkCoordinator

    /**
     * Deferred của yêu cầu quyền đang chờ kết quả. Activity Result API trả kết quả qua callback,
     * nên ta dùng một [CompletableDeferred] để "nối" callback đó về lời gọi `suspend` của manager.
     */
    @Volatile
    private var pendingPermissionRequest: CompletableDeferred<Set<String>>? = null

    /**
     * Launcher đã đăng ký cho hợp đồng quyền Health_Connect.
     *
     * Được đăng ký **trong** [onCreate] (sau `super.onCreate()`), không phải ở field initializer:
     * hợp đồng lấy từ [permissionContractFactory] vốn là field do Hilt tiêm **trong**
     * `super.onCreate()`, nên field initializer (chạy lúc dựng đối tượng) sẽ thấy nó chưa khởi tạo.
     * Activity Result API cho phép đăng ký khi Activity còn ở trạng thái CREATED (trước STARTED),
     * nên đăng ký ngay đầu [onCreate] là hợp lệ.
     */
    private lateinit var permissionLauncher: ActivityResultLauncher<Set<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionLauncher = registerForActivityResult(
            permissionContractFactory.create(),
            ActivityResultCallback { granted: Set<String> ->
                // Trả tập quyền được cấp về lời gọi suspend đang chờ (Requirement 1.2).
                pendingPermissionRequest?.complete(granted)
                pendingPermissionRequest = null
            },
        )

        // Gắn delegate launch quyền vào relay: biến callback Activity Result thành suspend
        // (Requirements 1.2, 1.9). Manager đã tự áp timeout 30s và giữ tập cũ khi lỗi/timeout.
        permissionRequesterRelay.attach { permissions ->
            val deferred = CompletableDeferred<Set<String>>()
            pendingPermissionRequest = deferred
            permissionLauncher.launch(permissions)
            deferred.await()
        }

        // Deep link khi App khởi động từ một Intent VIEW (Requirements 14.6, 14.8).
        val deepLinked = handleDeepLink(intent)

        // Khi mở từ deep link cấu hình Automation, khởi đầu ngay ở màn hình Automations để người
        // dùng xác nhận đề xuất (Requirement 14.6); ngược lại dùng điểm khởi đầu mặc định.
        val startRoute = if (deepLinked) TopDestination.AUTOMATIONS.route else TopDestination.START.route

        setContent {
            AppRoot(startRoute = startRoute)
        }
    }

    /**
     * Xử lý deep link khi App đang chạy nhận `Intent` mới (singleTop). Cập nhật [setIntent] để
     * trạng thái Intent hiện hành phản ánh deep link mới.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    /**
     * Phân tích deep link cấu hình Automation từ [intent] (Requirements 14.6, 14.8).
     *
     * Chỉ xử lý `Intent` có action `VIEW` và một `data` `Uri`; chuyển các tham số truy vấn của URI
     * thành `Map<String, String>` rồi đưa qua [DeepLinkHandler]. Kết quả đẩy vào
     * [DeepLinkCoordinator] để tầng UI trình bày xác nhận (đề xuất) hoặc thông báo lỗi (từ chối);
     * **không** tạo Automation tại đây.
     *
     * @return `true` nếu [intent] là một deep link cấu hình Automation đã được xử lý.
     */
    private fun handleDeepLink(intent: Intent?): Boolean {
        if (intent?.action != Intent.ACTION_VIEW) return false
        val uri = intent.data ?: return false
        val params = parseQueryParams(uri)
        if (params.isEmpty()) return false
        deepLinkCoordinator.submit(deepLinkHandler.handle(params))
        return true
    }

    /**
     * Trích các cặp khóa-giá trị từ phần query của [uri] thành `Map<String, String>` (đã giải mã
     * URL bởi [Uri.getQueryParameterNames]/[Uri.getQueryParameter]). Tham số trùng khóa lấy giá
     * trị đầu tiên (đủ cho cấu hình Automation).
     */
    private fun parseQueryParams(uri: Uri): Map<String, String> {
        if (uri.isOpaque) return emptyMap()
        return uri.queryParameterNames
            .mapNotNull { name -> uri.getQueryParameter(name)?.let { name to it } }
            .toMap()
    }

    override fun onDestroy() {
        // Gỡ delegate để relay không giữ tham chiếu tới Activity đã hủy (tránh rò rỉ).
        permissionRequesterRelay.detach()
        pendingPermissionRequest?.cancel()
        pendingPermissionRequest = null
        super.onDestroy()
    }
}
