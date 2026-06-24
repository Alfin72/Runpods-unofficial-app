package com.runpods.unofficialapp

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDropdownMenuState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunpodsScreen(
    uiState: StateFlow<RunpodsUiState>,
    onTokenChanged: (String) -> Unit,
    onEndpointChanged: (String) -> Unit,
    onPromptChanged: (String) -> Unit,
    onOutputFormatChanged: (String) -> Unit,
    onSizeChanged: (String) -> Unit,
    onResolutionChanged: (String) -> Unit,
    onAspectRatioChanged: (String) -> Unit,
    onDurationChanged: (String) -> Unit,
    onGenerateAudioChanged: (Boolean) -> Unit,
    onImageUrlChanged: (String) -> Unit,
    onSafetyCheckerChanged: (Boolean) -> Unit,
    onPickImage: () -> Unit,
    onSendRequest: () -> Unit,
    onSendRequestInBackground: () -> Unit,
    onCheckStatus: () -> Unit,
    onSaveResult: () -> Unit,
    onRefreshAvailableEndpoints: () -> Unit
) {
    val state = uiState.collectAsState().value
    val expanded = rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        TopAppBar(title = { Text("Runpods Unofficial") })
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = state.apiToken,
            onValueChange = onTokenChanged,
            label = { Text("API token") },
            placeholder = { Text("Bearer token for runpods.io") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Endpoint")
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onRefreshAvailableEndpoints) {
                        Text("Refresh")
                    }
                }
                OutlinedTextField(
                    value = state.endpointUrl,
                    onValueChange = onEndpointChanged,
                    label = { Text("Endpoint URL") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Available endpoints")
                Spacer(modifier = Modifier.height(4.dp))
                Button(onClick = { expanded.value = true }) {
                    Text("Choose endpoint")
                }
                DropdownMenu(
                    expanded = expanded.value,
                    onDismissRequest = { expanded.value = false }
                ) {
                    state.availableEndpoints.forEach { endpoint ->
                        DropdownMenuItem(
                            text = { Text(endpoint.name) },
                            onClick = {
                                onEndpointChanged(endpoint.url)
                                expanded.value = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = state.prompt,
            onValueChange = onPromptChanged,
            label = { Text("Prompt") },
            placeholder = { Text("Enter a prompt for text-to-image or text generation") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = state.outputFormat,
                onValueChange = onOutputFormatChanged,
                label = { Text("Output format") },
                placeholder = { Text("jpeg") },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = state.size,
                onValueChange = onSizeChanged,
                label = { Text("Size") },
                placeholder = { Text("1024*1024") },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = state.resolution,
            onValueChange = onResolutionChanged,
            label = { Text("Resolution") },
            placeholder = { Text("1k") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = state.aspectRatio,
                onValueChange = onAspectRatioChanged,
                label = { Text("Aspect ratio") },
                placeholder = { Text("16:9") },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = state.duration,
                onValueChange = onDurationChanged,
                label = { Text("Duration") },
                placeholder = { Text("seconds") },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Audio")
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { onGenerateAudioChanged(!state.generateAudio) }) {
                Text(if (state.generateAudio) "Audio ON" else "Audio OFF")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = state.imageUrl,
            onValueChange = onImageUrlChanged,
            label = { Text("Image URL") },
            placeholder = { Text("https://example.com/image.png") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = if (state.enableSafetyChecker) "Enabled" else "Disabled",
                onValueChange = {},
                label = { Text("Safety checker") },
                readOnly = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { onSafetyCheckerChanged(!state.enableSafetyChecker) }, modifier = Modifier.weight(1f)) {
                Text(if (state.enableSafetyChecker) "Disable" else "Enable")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onPickImage, modifier = Modifier.weight(1f)) {
                Text("Upload image")
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (state.selectedImageUri != null) {
                Text("Image ready", color = Color(0xFF2E7D32))
            }
        }

        if (state.selectedImageUri != null) {
            Spacer(modifier = Modifier.height(12.dp))
            AsyncImage(
                model = state.selectedImageUri,
                contentDescription = "Selected image",
                modifier = Modifier.fillMaxWidth().height(220.dp),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onSendRequest, modifier = Modifier.weight(1f)) {
                Text(if (state.isLoading) "Sending..." else "Send request")
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = onSendRequestInBackground, modifier = Modifier.weight(1f)) {
                Text("Run in background")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onCheckStatus, modifier = Modifier.fillMaxWidth()) {
            Text("Check job status")
        }

        if (!state.jobStatus.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Job status: ${state.jobStatus}")
        }

        Spacer(modifier = Modifier.height(12.dp))
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        if (!state.responseMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(state.responseMessage)
        }

        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(onClick = onSaveResult, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.isSaving) "Saving..." else "Save result")
        }

        if (!state.saveMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(state.saveMessage!!)
        }
    }
}
