package com.healthautoexport.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.healthautoexport.domain.model.AggregationPeriod
import com.healthautoexport.domain.model.ExportFormat
import com.healthautoexport.domain.model.ExportStatus
import com.healthautoexport.ui.viewmodel.QuickExportUiState
import com.healthautoexport.ui.viewmodel.QuickExportViewModel

/**
 * Màn hình Quick_Export (Requirement 13): chọn định dạng/mức tổng hợp, hiển thị thanh tiến trình
 * 0..100 (Requirement 13.2), nút hủy (Requirement 13.6), và xác nhận thành công/thất bại
 * (Requirements 13.3, 13.4). Cũng hiển thị thông báo khi từ chối yêu cầu vì đang chạy
 * (Requirement 13.5).
 */
@Composable
fun QuickExportScreen(
    modifier: Modifier = Modifier,
    viewModel: QuickExportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    QuickExportContent(
        state = state,
        onSetFormat = viewModel::setFormat,
        onSetPeriod = viewModel::setPeriod,
        onRun = viewModel::runQuickExport,
        onCancel = viewModel::cancel,
        modifier = modifier,
    )
}

@Composable
private fun QuickExportContent(
    state: QuickExportUiState,
    onSetFormat: (ExportFormat) -> Unit,
    onSetPeriod: (AggregationPeriod) -> Unit,
    onRun: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Xuất nhanh", style = MaterialTheme.typography.titleLarge)

        Text(
            text = "Định dạng",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ExportFormat.entries.toList()) { format ->
                FilterChip(
                    selected = state.format == format,
                    onClick = { onSetFormat(format) },
                    label = { Text(format.name) },
                    enabled = !state.isRunning,
                )
            }
        }

        Text(
            text = "Mức tổng hợp",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(AggregationPeriod.entries.toList()) { period ->
                FilterChip(
                    selected = state.period == period,
                    onClick = { onSetPeriod(period) },
                    label = { Text(period.name) },
                    enabled = !state.isRunning,
                )
            }
        }

        if (state.dateRangeAdjusted) {
            Text(
                text = "Thời điểm kết thúc đã được điều chỉnh về hiện tại (Requirement 9.6).",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        state.validationMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        state.alreadyRunningMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onRun, enabled = !state.isRunning) {
                Text(if (state.isRunning) "Đang xuất…" else "Bắt đầu xuất")
            }
            if (state.isRunning) {
                OutlinedButton(onClick = onCancel) { Text("Hủy") }
            }
        }

        if (state.isRunning) {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                LinearProgressIndicator(
                    progress = { state.progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "${state.progressPercent}% — ${state.progressStage?.name ?: ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        state.result?.let { result ->
            ResultCard(result.status, result.message)
        }
    }
}

@Composable
private fun ResultCard(status: ExportStatus, message: String) {
    val color = when (status) {
        ExportStatus.SUCCESS -> MaterialTheme.colorScheme.primary
        ExportStatus.FAILURE -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.secondary
    }
    Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(status.name, color = color, fontWeight = FontWeight.SemiBold)
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
