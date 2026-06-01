package com.healthautoexport.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.healthautoexport.domain.model.PermissionState
import com.healthautoexport.ui.viewmodel.MetricPermissionRow
import com.healthautoexport.ui.viewmodel.PermissionsUiState
import com.healthautoexport.ui.viewmodel.PermissionsViewModel
import com.healthautoexport.ui.viewmodel.SourceAvailabilityUi

/**
 * Màn hình hiển thị trạng thái quyền đọc của từng chỉ số đã chọn trên Health_Connect và Huawei
 * (Requirements 1.7, 2.7), kèm thông báo Health_Connect không khả dụng + liên kết cài đặt
 * (Requirements 1.1, 1.8).
 *
 * @param onOpenInstallLink callback mở liên kết cài đặt Health_Connect (Requirement 1.8); tầng
 *   Activity cung cấp việc mở Intent.
 */
@Composable
fun PermissionsScreen(
    modifier: Modifier = Modifier,
    onOpenInstallLink: (String) -> Unit = {},
    viewModel: PermissionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PermissionsContent(
        state = state,
        onRefresh = viewModel::refresh,
        onOpenInstallLink = onOpenInstallLink,
        modifier = modifier,
    )
}

@Composable
private fun PermissionsContent(
    state: PermissionsUiState,
    onRefresh: () -> Unit,
    onOpenInstallLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize().padding(16.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Trạng thái quyền", style = MaterialTheme.typography.titleLarge)
                Button(onClick = onRefresh, enabled = !state.isRefreshing) {
                    Text("Làm mới")
                }
            }
        }

        item {
            AvailabilityCard(
                name = "Health Connect",
                availability = state.healthConnect,
                onOpenInstallLink = onOpenInstallLink,
            )
        }
        item {
            AvailabilityCard(
                name = "Huawei Health Kit",
                availability = state.huawei,
                onOpenInstallLink = onOpenInstallLink,
            )
        }

        if (state.rows.isEmpty()) {
            item {
                Text(
                    text = "Chưa chọn chỉ số nào để hiển thị trạng thái quyền.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        } else {
            item {
                Text(
                    text = "Quyền theo chỉ số",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                HorizontalDivider()
            }
            items(state.rows, key = { it.metric.name }) { row ->
                PermissionRowItem(row)
            }
        }
    }
}

@Composable
private fun AvailabilityCard(
    name: String,
    availability: SourceAvailabilityUi,
    onOpenInstallLink: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            if (availability.available) {
                Text("Khả dụng", color = MaterialTheme.colorScheme.primary)
            } else {
                Text(
                    text = availability.reason ?: "Không khả dụng",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                val link = availability.installLink
                if (!link.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = { onOpenInstallLink(link) },
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text("Cài đặt / cập nhật")
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRowItem(row: MetricPermissionRow) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(row.label, style = MaterialTheme.typography.bodyLarge)
        Row {
            StateBadge(prefix = "HC", state = row.healthConnectState)
            StateBadge(prefix = "HW", state = row.huaweiState)
        }
    }
}

@Composable
private fun StateBadge(prefix: String, state: PermissionState?) {
    val (label, color) = when (state) {
        PermissionState.GRANTED -> "$prefix: Đã cấp" to MaterialTheme.colorScheme.primary
        PermissionState.NOT_GRANTED -> "$prefix: Chưa cấp" to MaterialTheme.colorScheme.error
        null -> "$prefix: —" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = label,
        color = color,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(start = 12.dp),
    )
}
