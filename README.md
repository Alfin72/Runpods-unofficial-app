# Runpods Unofficial App

A small Android app that connects to Runpods public endpoints.

The app is built with Jetpack Compose, Kotlin, and OkHttp. It supports:

- authenticating with a Runpods API token
- switching between available Runpods endpoints
- uploading an image from device storage
- sending a prompt to the selected endpoint
- downloading the returned image or video to the device

## App features

### Authentication
The app stores an API token using Jetpack DataStore. Enter your Runpods bearer token in the API token field, and it is saved automatically.

### Endpoint selection
There is a default list of built-in Runpods endpoints in `DefaultEndpoints.kt`:

- `https://api.runpods.io/v2/stable-diffusion`
- `https://api.runpods.io/v2/image-to-image`
- `https://api.runpods.io/v2/video-generation`
- a custom endpoint placeholder

You can select one from the built-in list or enter any endpoint URL directly.

### Prompt and image upload
The screen allows:

- typing a prompt for text-to-image or prompt-driven generation
- selecting a local image to upload if the endpoint accepts image input

The selected image is shown in the UI before sending the request.

### Sending requests
When the Send request button is tapped, the app:

1. validates that an API token and endpoint are present
2. sends an HTTP POST request to the selected endpoint
3. includes the prompt as JSON or multipart form data when an image is attached
4. handles JSON responses with downloadable URLs, or binary image/video responses directly

The text request body now matches Runpod-style JSON, wrapped under `input`, for example:

```json
{
  "input": {
    "enable_base64_output": false,
    "enable_sync_mode": false,
    "output_format": "jpeg",
    "prompt": "A futuristic city with a slightly dark neon atmosphere and glowing street lights...",
    "seed": -1,
    "size": "1024*1024"
  }
}
```

The app also supports additional Runpod request options:

- `resolution` (for models that accept it, such as `1k`)
- `output_format` (for example `png` or `jpeg`)
- `enable_safety_checker` (toggle safety review on or off)

This matches endpoints such as:

```bash
curl -X POST https://api.runpod.ai/v2/google-nano-banana-2-edit/run \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer YOUR_API_KEY' \
  -d '{"input":{"prompt":"Change the man to a woman, and hold a digital-style banana.","resolution":"1k","output_format":"png","enable_safety_checker":true}}'
```

For queued jobs, the app can check job status with the endpoint:

```bash
curl -X GET https://api.runpod.ai/v2/google-nano-banana-2-edit/status/{job_id} \
  -H 'Authorization: Bearer YOUR_API_KEY'
```

### Saving results
After a successful call, tap Save result to store the returned file under `Download/Runpods` on the device.

### Background execution
A new `Run in background` button allows the app to schedule endpoint calls via WorkManager. Background requests continue even when the UI is closed, and successful responses are saved automatically to the device downloads folder.

> On Android 13+ the app may require the `POST_NOTIFICATIONS` permission to show completion notifications.

## Project structure

- `app/src/main/java/com/runpods/unofficialapp/MainActivity.kt`
  - hosts the Compose UI and image picker
- `app/src/main/java/com/runpods/unofficialapp/RunpodsScreen.kt`
  - defines the Compose UI, including token entry, endpoint controls, prompt input, image preview, and buttons
- `app/src/main/java/com/runpods/unofficialapp/RunpodsViewModel.kt`
  - holds UI state and orchestrates user actions
  - persists API token and endpoint selections using `PreferencesRepository`
  - invokes endpoint requests via `RunpodsClient`
  - saves returned bytes to Downloads
- `app/src/main/java/com/runpods/unofficialapp/RunpodsClient.kt`
  - makes HTTP requests with OkHttp
  - constructs JSON or multipart payloads
  - parses JSON responses for downloadable URLs
  - writes binary output to the Android Downloads provider
- `app/src/main/java/com/runpods/unofficialapp/PreferencesRepository.kt`
  - stores token and endpoint settings using DataStore preferences
- `app/src/main/java/com/runpods/unofficialapp/DefaultEndpoints.kt`
  - defines the default endpoint list shown in the UI

## How the code works

### UI flow

1. `MainActivity` creates the `RunpodsViewModel` and registers an image picker.
2. `RunpodsScreen` subscribes to `uiState` from the view model and renders the form.
3. Changes to token, endpoint, prompt, or image selection update state immediately.
4. When the user sends a request, the view model performs validation and calls the endpoint.
5. The UI displays loading, success, or error messages.
6. When the response is ready, the user can save the returned bytes or download URL result.

### Network and response handling

- If an image is selected, `RunpodsClient` builds a multipart form request with `prompt` and `file`.
- Otherwise, it sends the prompt as JSON.
- The HTTP response is inspected for content type:
  - binary image or video responses are saved directly
  - JSON responses are parsed for fields such as `output_url`, `url`, `image_url`, or `video_url`
- If a URL is returned, the app treats it as a downloadable result.

## Build and run

### Requirements

- Android Studio or Android SDK with Gradle
- minimum SDK 24
- compile SDK 34
- Kotlin 1.9 and Jetpack Compose enabled

### Run from Android Studio

1. Open the project in Android Studio.
2. Sync Gradle.
3. Run the `app` module on an emulator or device.

### Run from command line

```bash
cd /workspaces/Runpods-unofficial-app
./gradlew assembleDebug
```

## Usage walkthrough

1. Launch the app on a device or emulator.
2. Paste your Runpods bearer token into the `API token` field.
3. Choose an endpoint from the dropdown or enter a custom endpoint URL.
4. Type a prompt into the `Prompt` field.
5. (Optional) Tap `Upload image` to select an image from your device.
6. Tap `Send request` to invoke the selected endpoint.
7. Wait for the success message and then tap `Save result` to store the file locally.
8. Open the device `Downloads` folder and find saved output in `Download/Runpods`.

## Notes

- The app currently supports running on devices with Android 24+.
- The default endpoint list is configurable in `DefaultEndpoints.kt`.
- Authentication is handled by entering the Runpods bearer token in the UI and using it in the `Authorization` header.
- Response saving uses the Android `MediaStore.Downloads` provider to store files in the device downloads folder.
