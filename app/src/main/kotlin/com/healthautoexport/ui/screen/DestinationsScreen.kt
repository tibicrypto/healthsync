package com.healthautoexport.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.healthautoexport.domain.model.DestinationType
import com.healthautoexport.ui.viewmodel.DestinationsUiState
import com.healthautoexport.ui.viewmodel.DestinationsViewModel

/**
 * Màn hình cấu hình Destination (Requirements 16–21).
 *
 * Trọng tâm UI của task 21: form cho REST API + Home Assistant hiển thị **cảnh báo non-HTTPS**
 * sau khi lưu (Requirements 16.4, 20.2); form MQTT chặn cổng ngoài `[1, 65535]` và hiển thị thông
 * báo xác thực (Requirement 19.1). Người dùng chọn loại Destination làm đích cho Quick_Export.
 */
@Composable
fun DestinationsScreen(
    modifier: Modifier = Modifier,
    viewModel: DestinationsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DestinationsContent(
        state = state,
        onSaveRest = viewModel::saveRestApi,
        onSaveHomeAssistant = viewModel::saveHomeAssistant,
        onSaveMqtt = viewModel::saveMqtt,
        onSaveLocalStorage = viewModel::saveLocalStorage,
        onSelect = viewModel::select,
        modifier = modifier,
    )
}

@Composable
private fun DestinationsContent(
    state: DestinationsUiState,
    onSaveRest: (String, String, Map<String, String>) -> Boolean,
    onSaveHomeAssistant: (String) -> Unit,
    onSaveMqtt: (String, Int, String, Int, Boolean) -> Boolean,
    onSaveLocalStorage: (String) -> Unit,
    onSelect: (DestinationType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
    ) {
        Text("Đích đến", style = MaterialTheme.typography.titleLarge)

        // Cảnh báo non-HTTPS (Requirements 16.4, 20.2).
        state.nonHttpsWarning?.let { warning ->
            Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(
                    text = warning,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
        // Thông báo xác thực chung (vd cổng MQTT, URL REST — Requirements 16.1, 19.1).
        state.validationMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        state.savedType?.let { type ->
            Text(
                text = "Đã lưu cấu hình ${type.name}.",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        RestApiForm(onSaveRest)
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        HomeAssistantForm(onSaveHomeAssistant)
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        MqttForm(onSaveMqtt)
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        LocalStorageForm(onSaveLocalStorage)

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        Text("Chọn đích cho Xuất nhanh", style = MaterialTheme.typography.titleMedium)
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            DestinationType.entries.forEach { type ->
                if (type in state.configs) {
                    FilterChip(
                        selected = state.selectedType == type,
                        onClick = { onSelect(type) },
                        label = { Text(type.name) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RestApiForm(onSave: (String, String, Map<String, String>) -> Boolean) {
    var url by remember { mutableStateOf("https://") }
    var method by remember { mutableStateOf("POST") }
    Column {
        Text("REST API", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("URL") },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            singleLine = true,
        )
        OutlinedTextField(
            value = method,
            onValueChange = { method = it },
            label = { Text("Phương thức HTTP") },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            singleLine = true,
        )
        Button(
            onClick = { onSave(url, method, emptyMap()) },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Lưu") }
    }
}

@Composable
private fun HomeAssistantForm(onSave: (String) -> Unit) {
    var baseUrl by remember { mutableStateOf("https://") }
    Column {
        Text("Home Assistant", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("Base URL") },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            singleLine = true,
        )
        Button(onClick = { onSave(baseUrl) }, modifier = Modifier.padding(top = 8.dp)) {
            Text("Lưu")
        }
    }
}

@Composable
private fun MqttForm(onSave: (String, Int, String, Int, Boolean) -> Boolean) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("1883") }
    var topic by remember { mutableStateOf("") }
    Column {
        Text("MQTT", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Broker host") },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            singleLine = true,
        )
        OutlinedTextField(
            value = port,
            onValueChange = { port = it },
            label = { Text("Port (1–65535)") },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            singleLine = true,
        )
        OutlinedTextField(
            value = topic,
            onValueChange = { topic = it },
            label = { Text("Topic") },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            singleLine = true,
        )
        Button(
            onClick = { onSave(host, port.toIntOrNull() ?: -1, topic, 1, false) },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Lưu") }
    }
}

@Composable
private fun LocalStorageForm(onSave: (String) -> Unit) {
    var treeUri by remember { mutableStateOf("") }
    Column {
        Text("Local Storage", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = treeUri,
            onValueChange = { treeUri = it },
            label = { Text("Thư mục (SAF tree URI)") },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            singleLine = true,
        )
        Button(onClick = { onSave(treeUri) }, modifier = Modifier.padding(top = 8.dp)) {
            Text("Lưu")
        }
    }
}
