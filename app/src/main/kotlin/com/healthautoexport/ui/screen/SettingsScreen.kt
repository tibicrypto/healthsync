package com.healthautoexport.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.healthautoexport.domain.model.DataSourceId
import com.healthautoexport.domain.usecase.WipedDataType
import com.healthautoexport.ui.viewmodel.SettingsUiState
import com.healthautoexport.ui.viewmodel.SettingsViewModel

/**
 * Màn hình Settings (Requirements 3.1, 7.8, 15.10, 22.6, 22.7).
 *
 * Cho phép bật/tắt từng Data_Source (Requirement 3.1), điều chỉnh ưu tiên nguồn (Requirement 7.8),
 * hiển thị hướng dẫn hạn chế nền (Requirement 15.10), và xóa toàn bộ dữ liệu qua hộp thoại xác
 * nhận; sau khi xóa, hiển thị xác nhận liệt kê từng loại dữ liệu đã xóa (Requirements 22.6, 22.7).
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsContent(
        state = state,
        onSetSourceEnabled = viewModel::setSourceEnabled,
        onWipe = viewModel::wipeData,
        modifier = modifier,
    )
}

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    onSetSourceEnabled: (DataSourceId, Boolean) -> Unit,
    onWipe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showWipeConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
    ) {
        Text("Cài đặt", style = MaterialTheme.typography.titleLarge)

        // --- Nguồn dữ liệu (Requirement 3.1) ---
        Text(
            text = "Nguồn dữ liệu",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )
        HorizontalDivider()
        DataSourceId.entries.forEach { source ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(sourceLabel(source), style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = source in state.enabledSources,
                    onCheckedChange = { enabled -> onSetSourceEnabled(source, enabled) },
                )
            }
        }

        // --- Ưu tiên nguồn (Requirement 7.8) ---
        Text(
            text = "Ưu tiên nguồn (số nhỏ = ưu tiên cao)",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )
        HorizontalDivider()
        DataSourceId.entries.forEach { source ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(sourceLabel(source), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "Rank: ${state.sourcePriority.rankOf(source).let { if (it == Int.MAX_VALUE) "—" else it.toString() }}",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        // --- Hạn chế chạy nền (Requirement 15.10) ---
        Text(
            text = "Hạn chế chạy nền",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )
        HorizontalDivider()
        Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(
                text = state.backgroundRestrictionGuidance,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp),
            )
        }

        // --- Xóa dữ liệu (Requirements 22.6, 22.7) ---
        Text(
            text = "Quyền riêng tư",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )
        HorizontalDivider()
        Button(
            onClick = { showWipeConfirm = true },
            enabled = !state.isWiping,
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Xóa toàn bộ dữ liệu") }

        state.wipeConfirmation?.let { wiped ->
            Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Đã xóa dữ liệu:",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    wiped.forEach { type ->
                        Text("• ${wipedLabel(type)}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        state.wipeError?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    if (showWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            title = { Text("Xác nhận xóa dữ liệu") },
            text = {
                Text(
                    "Hành động này sẽ xóa các Automation, thông tin xác thực Destination và " +
                        "Sync_Log. Tiếp tục? (Requirements 22.6, 22.7)",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showWipeConfirm = false
                    onWipe()
                }) { Text("Xóa") }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirm = false }) { Text("Hủy") }
            },
        )
    }
}

private fun sourceLabel(source: DataSourceId): String = when (source) {
    DataSourceId.HEALTH_CONNECT -> "Google Health Connect"
    DataSourceId.HUAWEI_HEALTH_KIT -> "Huawei Health Kit"
}

private fun wipedLabel(type: WipedDataType): String = when (type) {
    WipedDataType.AUTOMATIONS -> "Automation"
    WipedDataType.DESTINATION_CREDENTIALS -> "Thông tin xác thực Destination"
    WipedDataType.SYNC_LOG -> "Sync_Log"
}
