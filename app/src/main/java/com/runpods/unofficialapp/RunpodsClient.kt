package com.runpods.unofficialapp

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class RunpodsClient(private val contentResolver: ContentResolver) {
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient()

    sealed class RunpodsResponse {
        data class Success(val data: ByteArray, val mimeType: String?) : RunpodsResponse()
        data class DownloadUrl(val url: String, val mimeType: String?) : RunpodsResponse()
        data class JobQueued(val jobId: String, val status: String? = null) : RunpodsResponse()
        data class Error(val errorMessage: String) : RunpodsResponse()
    }

    suspend fun invokeEndpoint(
        endpointUrl: String,
        apiKey: String,
        prompt: String,
        outputFormat: String,
        size: String,
        resolution: String,
        enableSafetyChecker: Boolean,
        aspectRatio: String,
        duration: Int?,
        generateAudio: Boolean,
        layout: String,
        imageUrl: String,
        selectedUri: Uri?
    ): RunpodsResponse {
        return try {
            val requestBody = when {
                selectedUri != null -> buildMultipartRequest(
                    prompt,
                    selectedUri,
                    outputFormat,
                    size,
                    resolution,
                    enableSafetyChecker,
                    aspectRatio,
                    duration,
                    generateAudio,
                    layout,
                    imageUrl
                )
                else -> buildTextRequest(
                    prompt,
                    outputFormat,
                    size,
                    resolution,
                    enableSafetyChecker,
                    aspectRatio,
                    duration,
                    generateAudio,
                    layout,
                    imageUrl
                )
            }

            val request = Request.Builder()
                .url(endpointUrl)
                .header("Authorization", "Bearer $apiKey")
                .header("Accept", "application/json, image/*, video/*, application/octet-stream")
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return RunpodsResponse.Error("Request failed: ${response.code} ${response.message}")
                }
                val contentType = response.body?.contentType()?.toString()
                val bodyBytes = response.body?.bytes()
                if (bodyBytes == null) {
                    return RunpodsResponse.Error("Empty response body")
                }
                if (contentType != null && contentType.contains("application/json")) {
                    return parseJsonResponse(bodyBytes, contentType)
                }
                return RunpodsResponse.Success(bodyBytes, contentType)
            }
        } catch (exception: IOException) {
            RunpodsResponse.Error(exception.message ?: "Network error")
        }
    }

    private fun buildTextRequest(
        prompt: String,
        outputFormat: String,
        size: String,
        resolution: String,
        enableSafetyChecker: Boolean,
        aspectRatio: String,
        duration: Int?,
        generateAudio: Boolean,
        layout: String,
        imageUrl: String
    ) =
        json.encodeToString(
            PromptPayload.serializer(),
            PromptPayload(
                input = PromptInput(
                    prompt = prompt,
                    enable_base64_output = false,
                    enable_sync_mode = false,
                    output_format = outputFormat,
                    seed = -1,
                    size = size,
                    resolution = resolution.ifBlank { null },
                    enable_safety_checker = enableSafetyChecker,
                    aspect_ratio = aspectRatio.ifBlank { null },
                    duration = duration,
                    generate_audio = generateAudio,
                    layout = layout.ifBlank { null },
                    image_url = imageUrl.ifBlank { null }
                )
            )
        ).toRequestBody("application/json".toMediaTypeOrNull())

    private fun buildMultipartRequest(
        prompt: String,
        selectedUri: Uri,
        outputFormat: String,
        size: String,
        resolution: String,
        enableSafetyChecker: Boolean,
        aspectRatio: String,
        duration: Int?,
        generateAudio: Boolean,
        layout: String,
        imageUrl: String
    ): MultipartBody {
        val mimeType = contentResolver.getType(selectedUri) ?: "image/jpeg"
        val inputStream = contentResolver.openInputStream(selectedUri) ?: throw IOException("Unable to open selected image")
        val tempFile = kotlin.io.path.createTempFile(suffix = "${System.currentTimeMillis()}").toFile()
        inputStream.use { source ->
            tempFile.outputStream().use { target ->
                source.copyTo(target)
            }
        }

        val imageBody = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
        return MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("prompt", prompt)
            .addFormDataPart("file", tempFile.name, imageBody)
            .addFormDataPart("output_format", outputFormat)
            .addFormDataPart("size", size)
            .addFormDataPart("resolution", resolution)
            .addFormDataPart("enable_safety_checker", enableSafetyChecker.toString())
            .apply {
                if (aspectRatio.isNotBlank()) addFormDataPart("aspect_ratio", aspectRatio)
                if (duration != null) addFormDataPart("duration", duration.toString())
                addFormDataPart("generate_audio", generateAudio.toString())
                if (layout.isNotBlank()) addFormDataPart("layout", layout)
            }
            .build()
    }

    private fun parseJsonResponse(bodyBytes: ByteArray, contentType: String?): RunpodsResponse {
        return try {
            val element = json.parseToJsonElement(bodyBytes.decodeToString())
            val candidate = element.jsonObject["output_url"]
                ?: element.jsonObject["url"]
                ?: element.jsonObject["image_url"]
                ?: element.jsonObject["video_url"]
            if (candidate != null) {
                return RunpodsResponse.DownloadUrl(candidate.jsonPrimitive.content, contentType)
            }
            val jobId = element.jsonObject["job_id"]?.jsonPrimitive?.content
            val status = element.jsonObject["status"]?.jsonPrimitive?.content
            if (!jobId.isNullOrBlank()) {
                return RunpodsResponse.JobQueued(jobId, status)
            }
            val stringText = element.jsonObject["result"]?.jsonPrimitive?.content
            if (stringText != null && stringText.startsWith("http")) {
                return RunpodsResponse.DownloadUrl(stringText, contentType)
            }
            RunpodsResponse.Error("JSON response did not contain a downloadable URL or job ID.")
        } catch (exception: Exception) {
            RunpodsResponse.Error("Failed to parse JSON response: ${exception.message}")
        }
    }

    suspend fun checkJobStatus(endpointUrl: String, apiKey: String, jobId: String): RunpodsResponse {
        return try {
            val statusUrl = buildStatusUrl(endpointUrl, jobId)
            val request = Request.Builder()
                .url(statusUrl)
                .header("Authorization", "Bearer $apiKey")
                .header("Accept", "application/json")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return RunpodsResponse.Error("Status check failed: ${response.code} ${response.message}")
                }
                val bodyBytes = response.body?.bytes()
                if (bodyBytes == null) {
                    return RunpodsResponse.Error("Empty status response body")
                }
                val element = json.parseToJsonElement(bodyBytes.decodeToString()).jsonObject
                val status = element["status"]?.jsonPrimitive?.content
                val candidate = element["output_url"]
                    ?: element["url"]
                    ?: element["image_url"]
                    ?: element["video_url"]
                if (candidate != null) {
                    return RunpodsResponse.DownloadUrl(candidate.jsonPrimitive.content, response.body?.contentType()?.toString())
                }
                if (!status.isNullOrBlank()) {
                    return RunpodsResponse.JobQueued(jobId, status)
                }
                return RunpodsResponse.Error("Status response did not contain a URL or status.")
            }
        } catch (exception: Exception) {
            RunpodsResponse.Error(exception.message ?: "Status check failed")
        }
    }

    private fun buildStatusUrl(endpointUrl: String, jobId: String): String {
        return if (endpointUrl.endsWith("/run")) {
            endpointUrl.removeSuffix("/run") + "/status/$jobId"
        } else {
            endpointUrl.trimEnd('/') + "/status/$jobId"
        }
    }

    suspend fun saveBytesToDownloads(
        resolver: ContentResolver,
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): SaveResult {
        val relativePath = "Download/Runpods"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, if (fileName.contains('.')) fileName else "$fileName${inferExtension(mimeType)}")
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            }
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return SaveResult.Error("Failed to create download file")

        return try {
            resolver.openOutputStream(uri)?.use { output ->
                output.write(bytes)
            } ?: return SaveResult.Error("Unable to open output stream")
            SaveResult.Success(uri.toString())
        } catch (exception: Exception) {
            SaveResult.Error(exception.message ?: "Unable to save file")
        }
    }

    private fun inferExtension(mimeType: String): String = when {
        mimeType.contains("png") -> ".png"
        mimeType.contains("jpeg") || mimeType.contains("jpg") -> ".jpg"
        mimeType.contains("gif") -> ".gif"
        mimeType.contains("mp4") -> ".mp4"
        mimeType.contains("webm") -> ".webm"
        else -> ".bin"
    }
}

@kotlinx.serialization.Serializable
private data class PromptPayload(val input: PromptInput)

@kotlinx.serialization.Serializable
private data class PromptInput(
    val prompt: String,
    val enable_base64_output: Boolean = false,
    val enable_sync_mode: Boolean = false,
    val output_format: String = "jpeg",
    val seed: Int = -1,
    val size: String = "1024*1024",
    val resolution: String? = null,
    val enable_safety_checker: Boolean = true,
    val aspect_ratio: String? = null,
    val duration: Int? = null,
    val generate_audio: Boolean = false,
    val layout: String? = null,
    val image: String? = null
)
