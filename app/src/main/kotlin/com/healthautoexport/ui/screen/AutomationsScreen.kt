package com.healthautoexport.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.healthautoexport.domain.model.AggregationPeriod
import com.healthautoexport.domain.model.Automation
import com.healthautoexport.domain.model.DestinationType
import com.healthautoexport.domain.model.ExportFormat
import com.healthautoexport.ui.viewmodel.AutomationDraft
import com.healthautoexport.ui.viewmodel.AutomationsUiState
import com.healthautoexport.ui.viewmodel.AutomationsViewModel

/**
 * Màn hình quản lý Automation (Requirement 14): liệt kê các Automation đã lưu với hành động
 * bật/tắt/sửa/xóa, và một form tạo/chỉnh sửa với xác thực (Requirements 14.1–14.4, 14.7).
 *
 * Khi form mở ([AutomationsUiState.editing] khác null), hiển thị form thay vì danh sách. Lỗi xác
 * thực khi lưu được hiển thị ngay trên form, trong khi dữ liệu nhập được giữ nguyên
 * (Requirement 14.7).
 */
@Composable
fun AutomationsScreen(
    modifier: Modifier = Modifier,
    viewModel: AutomationsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editing = state.editing
    if (editing != null) {
        AutomationEditForm(
            draft = editing,
            validationMessage = state.validationMessage,
            onChange = viewModel::updateDraft,
            onSave = viewModel::save,
            onCancel = viewModel::cancelEdit,
            modifier = modifier,
        )
    } else {
        AutomationsList(
            state = state,
            onCreate = viewModel::startCreate,
            onEdit = viewModel::startEdit,
            onEnable = viewModel::enable,
            onDisable = viewModel::disable,
            onDelete = viewModel::delete,
            modifier = modifier,
        )
    }
}

@Composable
private fun AutomationsList(
    state: AutomationsUiState,
    onCreate: () -> Unit,
    onEdit: (Automation) -> Unit,
    onEnable: (String) -> Unit,
    onDisable: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Tự động hóa", style = MaterialTheme.typography.titleLarge)
            Button(onClick = onCreate) { Text("Tạo Automation") }
        }

        state.lastSavedName?.let { name ->
            Text(
                text = "Đã lưu Automation: $name",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (state.automations.isEmpty()) {
            Text(
                text = "Chưa có Automation nào.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
                items(state.automations, key = { it.id }) { automation ->
                    AutomationItem(
                        automation = automation,
                        onEdit = { onEdit(automation) },
                        onToggle = {
                            if (automation.enabled) onDisable(automation.id) else onEnable(automation.id)
                        },
                        onDelete = { onDelete(automation.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AutomationItem(
    automation: Automation,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(automation.name, fontWeight = FontWeight.SemiBold)
            Text(
                text = "${automation.exportFormat.name} · ${automation.aggregationPeriod.name} · " +
                    automation.destinationType.name,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = if (automation.enabled) "Đang bật" else "Đang tắt",
                color = if (automation.enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.labelLarge,
            )
            Row(modifier = Modifier.padding(top = 8.dp)) {
                TextButton(onClick = onEdit) { Text("Chỉnh sửa") }
                TextButton(onClick = onToggle) {
                    Text(if (automation.enabled) "Tắt" else "Bật")
                }
                TextButton(onClick = onDelete) { Text("Xóa") }
            }
        }
    }
}

@Composable
private fun AutomationEditForm(
    draft: AutomationDraft,
    validationMessage: String?,
    onChange: (AutomationDraft) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = if (draft.id == null) "Tạo Automation" else "Chỉnh sửa Automation",
            style = MaterialTheme.typography.titleLarge,
        )

        OutlinedTextField(
            value = draft.name,
            onValueChange = { onChange(draft.copy(name = it)) },
            label = { Text("Tên (1–100 ký tự)") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            singleLine = true,
            isError = validationMessage != null,
        )

        Text("Định dạng", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ExportFormat.entries.toList()) { format ->
                FilterChip(
                    selected = draft.exportFormat == format,
                    onClick = { onChange(draft.copy(exportFormat = format)) },
                    label = { Text(format.name) },
                )
            }
        }

        Text("Mức tổng hợp", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(AggregationPeriod.entries.toList()) { period ->
                FilterChip(
                    selected = draft.aggregationPeriod == period,
                    onClick = { onChange(draft.copy(aggregationPeriod = period)) },
                    label = { Text(period.name) },
                )
            }
        }

        Text("Đích đến", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(DestinationType.entries.toList()) { type ->
                FilterChip(
                    selected = draft.destinationType == type,
                    onClick = {
                        onChange(draft.copy(destinationType = type, destinationConfigRef = type.name))
                    },
                    label = { Text(type.name) },
                )
            }
        }

        OutlinedTextField(
            value = draft.scheduleIntervalMinutes.toString(),
            onValueChange = { value ->
                value.toLongOrNull()?.let { onChange(draft.copy(scheduleIntervalMinutes = it)) }
            },
            label = { Text("Khoảng lặp (phút, 15–43200)") },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            singleLine = true,
        )

        validationMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Row(modifier = Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onSave) { Text("Lưu") }
            OutlinedButton(onClick = onCancel) { Text("Hủy") }
        }
    }
}
