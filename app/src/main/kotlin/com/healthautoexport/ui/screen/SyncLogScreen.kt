package com.healthautoexport.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
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
import com.healthautoexport.domain.model.SyncLogEntry
import com.healthautoexport.ui.viewmodel.SyncLogUiState
import com.healthautoexport.ui.viewmodel.SyncLogViewModel

/**
 * Màn hình Sync_Log (Requirements 23.3, 23.6): hiển thị các mục theo thứ tự hiển thị do repository
 * cung cấp (đã giảm dần `completionUtc`, tie-break `startUtc`) và cho phép xóa toàn bộ nhật ký.
 */
@Composable
fun SyncLogScreen(
    modifier: Modifier = Modifier,
    viewModel: SyncLogViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SyncLogContent(state = state, onClear = viewModel::clear, modifier = modifier)
}

@Composable
private fun SyncLogContent(
    state: SyncLogUiState,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Nhật ký đồng bộ", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = onClear, enabled = !state.isEmpty) {
                Text("Xóa nhật ký")
            }
        }

        if (state.isEmpty) {
            Text(
                text = "Nhật ký trống.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
                items(state.entries, key = { it.id }) { entry ->
                    SyncLogItem(entry)
                }
            }
        }
    }
}

@Composable
private fun SyncLogItem(entry: SyncLogEntry) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(entry.status.name, fontWeight = FontWeight.SemiBold)
                Text(
                    text = entry.completionUtc?.toString() ?: entry.startUtc.toString(),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            val subtitle = listOfNotNull(
                entry.exportFormat?.name,
                entry.destinationType?.name,
            ).joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
            entry.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
