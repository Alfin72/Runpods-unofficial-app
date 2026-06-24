package com.runpods.unofficialapp

object DefaultEndpoints {
    val list = listOf(
        EndpointItem("RunPods Stable Diffusion", "https://api.runpods.io/v2/stable-diffusion"),
        EndpointItem("RunPods Image-to-Image", "https://api.runpods.io/v2/image-to-image"),
        EndpointItem("RunPods Video Generation", "https://api.runpods.io/v2/video-generation"),
        EndpointItem("Custom endpoint", "https://api.runpods.io/v2/<your-endpoint-id>")
    )
}
