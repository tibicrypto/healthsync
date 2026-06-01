package com.healthautoexport.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.healthautoexport.domain.model.MetricGroup
import com.healthautoexport.ui.viewmodel.MetricsUiState
import com.healthautoexport.ui.viewmodel.MetricsViewModel

/**
 * Màn hình chọn chỉ số và loại Workout cho một Export_Job (Requirements 4.4, 4.8).
 *
 * Liệt kê các chỉ số theo nhóm với checkbox; chip lựa chọn cho loại Workout. Khi lựa chọn rỗng,
 * hiển thị cảnh báo cần chọn ít nhất một mục (Requirement 4.8).
 */
@Composable
fun MetricsScreen(
    modifier: Modifier = Modifier,
    viewModel: MetricsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MetricsContent(
        state = state,
        onToggleMetric = viewModel::toggleMetric,
        onToggleWorkout = viewModel::toggleWorkout,
        modifier = modifier,
    )
}

@Composable
private fun MetricsContent(
    state: MetricsUiState,
    onToggleMetric: (com.healthautoexport.domain.model.HealthMetricType) -> Unit,
    onToggleWorkout: (com.healthautoexport.domain.model.WorkoutType) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text(
                text = "Chọn chỉ số",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            if (!state.isSelectionValid) {
                Text(
                    text = "Cần chọn ít nhất một chỉ số hoặc loại bài tập (Requirement 4.8).",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }

        state.metricsByGroup.forEach { (group, options) ->
            item(key = "group_${group.name}") {
                Text(
                    text = groupLabel(group),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                HorizontalDivider()
            }
            items(options, key = { it.type.name }) { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                ) {
                    Checkbox(
                        checked = option.type in state.selection.metrics,
                        onCheckedChange = { onToggleMetric(option.type) },
                    )
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodyLarge,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        item {
            Text(
                text = "Loại bài tập",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
        }
        item {
            Column {
                state.workouts.chunked(2).forEach { rowItems ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        rowItems.forEach { workout ->
                            FilterChip(
                                selected = workout in state.selection.workouts,
                                onClick = { onToggleWorkout(workout) },
                                label = { Text(workout.name) },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Nhãn tiếng Việt cho mỗi nhóm chỉ số (Requirement 4.1). */
private fun groupLabel(group: MetricGroup): String = when (group) {
    MetricGroup.ACTIVITY -> "Vận động"
    MetricGroup.BODY_MEASUREMENT -> "Đo lường cơ thể"
    MetricGroup.HEART -> "Tim mạch"
    MetricGroup.HEARING -> "Thính giác"
    MetricGroup.NUTRITION -> "Dinh dưỡng"
    MetricGroup.MINDFULNESS -> "Chánh niệm"
    MetricGroup.MOBILITY -> "Vận động chức năng"
    MetricGroup.REPRODUCTIVE_HEALTH -> "Sức khỏe sinh sản"
    MetricGroup.RESPIRATORY -> "Hô hấp"
    MetricGroup.SLEEP -> "Giấc ngủ"
    MetricGroup.VITALS -> "Sinh hiệu"
    MetricGroup.OTHER -> "Khác"
}
