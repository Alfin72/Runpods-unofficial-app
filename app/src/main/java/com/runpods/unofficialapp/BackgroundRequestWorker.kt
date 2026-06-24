package com.runpods.unofficialapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class BackgroundRequestWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_ENDPOINT_URL = "endpoint_url"
        const val KEY_API_TOKEN = "api_token"
        const val KEY_PROMPT = "prompt"
        const val KEY_IMAGE_URI = "image_uri"
        const val KEY_OUTPUT_FORMAT = "output_format"
        const val KEY_SIZE = "size"
        const val KEY_RESOLUTION = "resolution"
        const val KEY_ENABLE_SAFETY_CHECKER = "enable_safety_checker"
        const val KEY_ASPECT_RATIO = "aspect_ratio"
        const val KEY_DURATION = "duration"
        const val KEY_GENERATE_AUDIO = "generate_audio"
        const val KEY_IMAGE_URL = "image_url"
        const val KEY_RESULT_PATH = "result_path"
        const val KEY_ERROR_MESSAGE = "error_message"

        private const val CHANNEL_ID = "runpods_background_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private val contentResolver = applicationContext.contentResolver
    private val runpodsClient = RunpodsClient(contentResolver)
    private val httpClient = OkHttpClient()

    override suspend fun doWork(): Result {
        val endpointUrl = inputData.getString(KEY_ENDPOINT_URL).orEmpty()
        val apiToken = inputData.getString(KEY_API_TOKEN).orEmpty()
        val prompt = inputData.getString(KEY_PROMPT).orEmpty()
        val outputFormat = inputData.getString(KEY_OUTPUT_FORMAT).orEmpty()
        val size = inputData.getString(KEY_SIZE).orEmpty()
        val resolution = inputData.getString(KEY_RESOLUTION).orEmpty()
        val aspectRatio = inputData.getString(KEY_ASPECT_RATIO).orEmpty()
        val duration = inputData.getString(KEY_DURATION)?.toIntOrNull()
        val generateAudio = inputData.getBoolean(KEY_GENERATE_AUDIO, false)
        val imageUrl = inputData.getString(KEY_IMAGE_URL).orEmpty()
        val imageUriString = inputData.getString(KEY_IMAGE_URI)
        val imageUri = imageUriString?.let { android.net.Uri.parse(it) }

        if (endpointUrl.isBlank() || apiToken.isBlank()) {
            return Result.failure()
        }

        return when (val result = runpodsClient.invokeEndpoint(
            endpointUrl = endpointUrl,
            apiKey = apiToken,
            prompt = prompt,
            outputFormat = outputFormat.ifBlank { "jpeg" },
            size = size.ifBlank { "1024*1024" },
            resolution = resolution,
            enableSafetyChecker = enableSafetyChecker,
            aspectRatio = aspectRatio,
            duration = duration,
            generateAudio = generateAudio,
            imageUrl = imageUrl,
            selectedUri = imageUri
        )) {
            is RunpodsClient.RunpodsResponse.Success -> {
                saveResult(result.data, result.mimeType)
            }
            is RunpodsClient.RunpodsResponse.DownloadUrl -> {
                fetchAndSaveRemote(result.url, result.mimeType)
            }
            is RunpodsClient.RunpodsResponse.Error -> {
                showNotification("Background request failed", result.errorMessage)
                Result.failure()
            }
            is RunpodsClient.RunpodsResponse.JobQueued -> {
                showNotification("Background request queued", "Job ${result.jobId} is in progress")
                Result.success()
            }
        }
    }

    private suspend fun saveResult(data: ByteArray, mimeType: String?): Result {
        val fileName = "runpods-background-${System.currentTimeMillis()}"
        val result = runpodsClient.saveBytesToDownloads(
            contentResolver,
            data,
            fileName,
            mimeType ?: "application/octet-stream"
        )

        return when (result) {
            is SaveResult.Success -> {
                showNotification("Background request complete", "Saved to ${result.path}")
                setOutputData(workDataOf(KEY_RESULT_PATH to result.path))
                Result.success()
            }
            is SaveResult.Error -> {
                showNotification("Background request failed", result.message)
                Result.failure()
            }
        }
    }

    private suspend fun fetchAndSaveRemote(url: String, mimeType: String?): Result {
        return try {
            val request = Request.Builder().url(url).get().build()
            val response = httpClient.newCall(request).execute()
            val bytes = response.body?.bytes()
            if (!response.isSuccessful || bytes == null) {
                return Result.failure()
            }
            saveResult(bytes, mimeType ?: response.body?.contentType()?.toString())
        } catch (exception: IOException) {
            showNotification("Background request failed", exception.message ?: "Network error")
            Result.failure()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Runpods Background Requests",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for background Runpods request results"
            }
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(title: String, message: String) {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext)
            .notify(NOTIFICATION_ID, notification)
    }
}
