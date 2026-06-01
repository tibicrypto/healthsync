package com.healthautoexport.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.healthautoexport.ui.screen.AutomationsScreen
import com.healthautoexport.ui.screen.DestinationsScreen
import com.healthautoexport.ui.screen.MetricsScreen
import com.healthautoexport.ui.screen.PermissionsScreen
import com.healthautoexport.ui.screen.QuickExportScreen
import com.healthautoexport.ui.screen.SettingsScreen
import com.healthautoexport.ui.screen.SyncLogScreen
import com.healthautoexport.ui.theme.HealthExportTheme

/**
 * Điểm vào (entry composable) của toàn bộ UI (task 21.2).
 *
 * `MainActivity` (tạo ở task 22.1) gọi `AppRoot(navController)` bên trong `setContent`. `AppRoot`
 * bọc cây UI trong [HealthExportTheme] và dựng [AppScaffold] với thanh điều hướng dưới + [AppNavGraph].
 *
 * @param navController controller điều hướng; mặc định tạo mới bằng [rememberNavController] để
 *   preview/độc lập.
 * @param onOpenInstallLink callback mở liên kết cài đặt Health_Connect (Requirement 1.8), chuyển
 *   tiếp tới màn hình Permissions; mặc định no-op.
 */
@Composable
fun AppRoot(
    navController: NavHostController = rememberNavController(),
    onOpenInstallLink: (String) -> Unit = {},
    startRoute: String = TopDestination.START.route,
) {
    HealthExportTheme {
        AppScaffold(
            navController = navController,
            onOpenInstallLink = onOpenInstallLink,
            startRoute = startRoute,
        )
    }
}

/**
 * Khung màn hình với [NavigationBar] dưới và vùng nội dung là [AppNavGraph].
 */
@Composable
private fun AppScaffold(
    navController: NavHostController,
    onOpenInstallLink: (String) -> Unit,
    startRoute: String,
) {
    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            NavigationBar {
                TopDestination.bottomBar.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {},
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        AppNavGraph(
            navController = navController,
            onOpenInstallLink = onOpenInstallLink,
            startRoute = startRoute,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

/**
 * `NavHost` định tuyến tới các màn hình của App (task 21.2). Điểm khởi đầu mặc định là
 * [TopDestination.START] (Quick_Export), nhưng có thể bị ghi đè bằng [startRoute] — ví dụ
 * `MainActivity` đặt điểm khởi đầu là màn hình Automations khi App được mở từ một deep link cấu
 * hình Automation (Requirement 14.6).
 *
 * @param navController controller điều hướng.
 * @param onOpenInstallLink callback mở liên kết cài đặt cho màn hình Permissions (Requirement 1.8).
 * @param startRoute route khởi đầu của `NavHost`; mặc định [TopDestination.START].
 * @param modifier modifier áp cho `NavHost` (nhận padding của Scaffold).
 */
@Composable
fun AppNavGraph(
    navController: NavHostController,
    onOpenInstallLink: (String) -> Unit = {},
    startRoute: String = TopDestination.START.route,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startRoute,
        modifier = modifier,
    ) {
        composable(TopDestination.QUICK_EXPORT.route) { QuickExportScreen() }
        composable(TopDestination.METRICS.route) { MetricsScreen() }
        composable(TopDestination.AUTOMATIONS.route) { AutomationsScreen() }
        composable(TopDestination.DESTINATIONS.route) { DestinationsScreen() }
        composable(TopDestination.PERMISSIONS.route) {
            PermissionsScreen(onOpenInstallLink = onOpenInstallLink)
        }
        composable(TopDestination.SYNC_LOG.route) { SyncLogScreen() }
        composable(TopDestination.SETTINGS.route) { SettingsScreen() }
    }
}
