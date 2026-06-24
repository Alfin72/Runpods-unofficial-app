package com.runpods.unofficialapp

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RunpodsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PreferencesRepository(application)
    private val runpodsClient = RunpodsClient(application.contentResolver)

    private val _uiState = MutableStateFlow(RunpodsUiState())
    val uiState: StateFlow<RunpodsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.tokenFlow.collect { token ->
                _uiState.value = _uiState.value.copy(apiToken = token)
            }
        }
        viewModelScope.launch {
            repository.endpointFlow.collect { endpoint ->
                _uiState.value = _uiState.value.copy(endpointUrl = endpoint)
            }
        }
    }

    fun refreshAvailableEndpoints() {
        _uiState.value = _uiState.value.copy(
            availableEndpoints = DefaultEndpoints.list,
            responseMessage = "Endpoint list refreshed",
            saveMessage = null
        )
    }

    fun updateApiToken(token: String) {
        _uiState.value = _uiState.value.copy(apiToken = token)
        viewModelScope.launch { repository.updateToken(token) }
    }

    fun updateEndpoint(endpoint: String) {
        _uiState.value = _uiState.value.copy(endpointUrl = endpoint)
        viewModelScope.launch { repository.updateEndpoint(endpoint) }
    }

    fun updatePrompt(prompt: String) {
        _uiState.value = _uiState.value.copy(prompt = prompt)
    }

    fun updateOutputFormat(outputFormat: String) {
        _uiState.value = _uiState.value.copy(outputFormat = outputFormat)
    }

    fun updateSize(size: String) {
        _uiState.value = _uiState.value.copy(size = size)
    }

    fun updateResolution(resolution: String) {
        _uiState.value = _uiState.value.copy(resolution = resolution)
    }

    fun updateSafetyChecker(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(enableSafetyChecker = enabled)
    }

    fun updateAspectRatio(aspectRatio: String) {
        _uiState.value = _uiState.value.copy(aspectRatio = aspectRatio)
    }

    fun updateDuration(duration: String) {
        _uiState.value = _uiState.value.copy(duration = duration)
    }

    fun updateGenerateAudio(generateAudio: Boolean) {
        _uiState.value = _uiState.value.copy(generateAudio = generateAudio)
    }

    fun updateImageUrl(imageUrl: String) {
        _uiState.value = _uiState.value.copy(imageUrl = imageUrl)
    }

    fun onImageSelected(uri: Uri) {
        _uiState.value = _uiState.value.copy(selectedImageUri = uri, responseMessage = null)
    }

    fun sendRequest() {
        val state = _uiState.value
        val token = state.apiToken.trim()
        if (token.isBlank()) {
            _uiState.value = state.copy(responseMessage = "Enter an API token to authenticate.")
            return
        }
        if (state.endpointUrl.isBlank()) {
            _uiState.value = state.copy(responseMessage = "Enter a valid endpoint URL.")
            return
        }
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, responseMessage = null, saveMessage = null)
            val result = runpodsClient.invokeEndpoint(
                endpointUrl = state.endpointUrl,
                apiKey = token,
                prompt = state.prompt,
                outputFormat = state.outputFormat,
                size = state.size,
                resolution = state.resolution,
                enableSafetyChecker = state.enableSafetyChecker,
                aspectRatio = state.aspectRatio,
                duration = state.duration.toIntOrNull(),
                generateAudio = state.generateAudio,
                imageUrl = state.imageUrl,
                selectedUri = state.selectedImageUri
            )
            when (result) {
                is RunpodsClient.RunpodsResponse.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        latestResponseData = result.data,
                        latestMimeType = result.mimeType,
                        responseMessage = "Request succeeded. Tap save to download.",
                        saveMessage = null,
                        jobId = null,
                        jobStatus = null
                    )
                }
                is RunpodsClient.RunpodsResponse.DownloadUrl -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        latestResponseData = result.url.toByteArray(),
                        latestMimeType = result.mimeType,
                        responseMessage = "Received download URL. Tap save to fetch and store it.",
                        saveMessage = null,
                        jobId = null,
                        jobStatus = null
                    )
                }
                is RunpodsClient.RunpodsResponse.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, responseMessage = result.errorMessage)
                }
                is RunpodsClient.RunpodsResponse.JobQueued -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        responseMessage = "Job queued: ${result.jobId}",
                        jobId = result.jobId,
                        jobStatus = "Queued",
                        saveMessage = null
                    )
                }
            }
        }
    }

    fun checkJobStatus() {
        val state = _uiState.value
        val token = state.apiToken.trim()
        val jobId = state.jobId
        if (token.isBlank() || jobId.isNullOrBlank()) {
            _uiState.value = state.copy(responseMessage = "No job ID available to check status.")
            return
        }
        if (state.endpointUrl.isBlank()) {
            _uiState.value = state.copy(responseMessage = "Enter a valid endpoint URL.")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, responseMessage = null)
            val result = runpodsClient.checkJobStatus(
                endpointUrl = state.endpointUrl,
                apiKey = token,
                jobId = jobId
            )
            when (result) {
                is RunpodsClient.RunpodsResponse.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        latestResponseData = result.data,
                        latestMimeType = result.mimeType,
                        responseMessage = "Job complete. Tap save to download.",
                        saveMessage = null,
                        jobStatus = "Complete"
                    )
                }
                is RunpodsClient.RunpodsResponse.DownloadUrl -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        latestResponseData = result.url.toByteArray(),
                        latestMimeType = result.mimeType,
                        responseMessage = "Job complete. Download URL received. Tap save to fetch and store it.",
                        jobStatus = "Complete"
                    )
                }
                is RunpodsClient.RunpodsResponse.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        responseMessage = result.errorMessage,
                        jobStatus = "Error"
                    )
                }
                is RunpodsClient.RunpodsResponse.JobQueued -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        jobStatus = result.jobId,
                        responseMessage = "Job still queued: ${result.jobId}"
                    )
                }
            }
        }
    }

    fun saveLatestResult() {
        val state = _uiState.value
        val bytes = state.latestResponseData
        val mimeType = state.latestMimeType
        if (bytes == null || bytes.isEmpty()) {
            _uiState.value = state.copy(saveMessage = "No response payload available to save.")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, saveMessage = null)
            val fileName = "runpods-output-${System.currentTimeMillis()}"
            val result = saveToDownloads(bytes, fileName, mimeType ?: "application/octet-stream")
            _uiState.value = when (result) {
                is SaveResult.Success -> state.copy(isSaving = false, saveMessage = "Saved to ${result.path}")
                is SaveResult.Error -> state.copy(isSaving = false, saveMessage = result.message)
            }
        }
    }

    fun sendRequestInBackground() {
        val state = _uiState.value
        val token = state.apiToken.trim()
        if (token.isBlank()) {
            _uiState.value = state.copy(responseMessage = "Enter an API token to authenticate.")
            return
        }
        if (state.endpointUrl.isBlank()) {
            _uiState.value = state.copy(responseMessage = "Enter a valid endpoint URL.")
            return
        }

        val workData = workDataOf(
            BackgroundRequestWorker.KEY_ENDPOINT_URL to state.endpointUrl,
            BackgroundRequestWorker.KEY_API_TOKEN to token,
            BackgroundRequestWorker.KEY_PROMPT to state.prompt,
            BackgroundRequestWorker.KEY_OUTPUT_FORMAT to state.outputFormat,
            BackgroundRequestWorker.KEY_SIZE to state.size,
            BackgroundRequestWorker.KEY_RESOLUTION to state.resolution,
            BackgroundRequestWorker.KEY_ENABLE_SAFETY_CHECKER to state.enableSafetyChecker,
            BackgroundRequestWorker.KEY_ASPECT_RATIO to state.aspectRatio,
            BackgroundRequestWorker.KEY_DURATION to state.duration,
            BackgroundRequestWorker.KEY_GENERATE_AUDIO to state.generateAudio,
            BackgroundRequestWorker.KEY_IMAGE_URL to state.imageUrl,
            BackgroundRequestWorker.KEY_IMAGE_URI to state.selectedImageUri?.toString()
        )

        val workRequest = OneTimeWorkRequestBuilder<BackgroundRequestWorker>()
            .setInputData(workData)
            .build()

        WorkManager.getInstance(getApplication()).enqueue(workRequest)
        _uiState.value = state.copy(
            responseMessage = "Background request scheduled. Results will save automatically when complete.",
            saveMessage = null
        )
    }

    private suspend fun saveToDownloads(data: ByteArray, fileName: String, mimeType: String): SaveResult {
        val resolver: ContentResolver = getApplication<Application>().contentResolver
        return runpodsClient.saveBytesToDownloads(resolver, data, fileName, mimeType)
    }
}

data class RunpodsUiState(
    val apiToken: String = "",
    val endpointUrl: String = DefaultEndpoints.list.first().url,
    val prompt: String = "",
    val outputFormat: String = "jpeg",
    val size: String = "1024*1024",
    val resolution: String = "",
    val aspectRatio: String = "",
    val duration: String = "",
    val generateAudio: Boolean = false,
    val imageUrl: String = "",
    val enableSafetyChecker: Boolean = true,
    val selectedImageUri: Uri? = null,
    val jobId: String? = null,
    val jobStatus: String? = null,
    val responseMessage: String? = null,
    val saveMessage: String? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val latestResponseData: ByteArray? = null,
    val latestMimeType: String? = null,
    val availableEndpoints: List<EndpointItem> = DefaultEndpoints.list
)

data class EndpointItem(val name: String, val url: String)

sealed interface SaveResult {
    data class Success(val path: String) : SaveResult
    data class Error(val message: String) : SaveResult
}
