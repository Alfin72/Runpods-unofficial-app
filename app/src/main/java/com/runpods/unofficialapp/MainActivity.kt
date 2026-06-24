package com.runpods.unofficialapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val viewModel: RunpodsViewModel by viewModels()
    private val pickImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            viewModel.onImageSelected(it)
        }
    }

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        if (!granted) {
            // No-op: background notifications will be disabled if permission is denied.
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationsPermissionIfNeeded()
        setContent {
            RunpodsUnofficialAppTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    RunpodsScreen(
                        uiState = viewModel.uiState,
                        onTokenChanged = viewModel::updateApiToken,
                        onEndpointChanged = viewModel::updateEndpoint,
                        onPromptChanged = viewModel::updatePrompt,
                        onOutputFormatChanged = viewModel::updateOutputFormat,
                        onSizeChanged = viewModel::updateSize,
                        onResolutionChanged = viewModel::updateResolution,
                        onAspectRatioChanged = viewModel::updateAspectRatio,
                        onDurationChanged = viewModel::updateDuration,
                        onGenerateAudioChanged = viewModel::updateGenerateAudio,
                        onImageUrlChanged = viewModel::updateImageUrl,
                        onSafetyCheckerChanged = viewModel::updateSafetyChecker,
                        onPickImage = { pickImage.launch(arrayOf("image/*")) },
                        onSendRequest = viewModel::sendRequest,
                        onSendRequestInBackground = viewModel::sendRequestInBackground,
                        onCheckStatus = viewModel::checkJobStatus,
                        onSaveResult = viewModel::saveLatestResult,
                        onRefreshAvailableEndpoints = viewModel::refreshAvailableEndpoints
                    )
                }
            }
        }
    }

    private fun requestNotificationsPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(permission)
            }
        }
    }
}
