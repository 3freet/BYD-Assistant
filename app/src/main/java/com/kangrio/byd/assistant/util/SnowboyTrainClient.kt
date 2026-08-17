package com.kangrio.byd.assistant.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Client for the snowboy-seasalt HTTP API (https://github.com/rhasspy/snowboy-seasalt).
 *
 * POSTs 3+ recorded wake-word .wav samples to a self-hosted `snowboy-seasalt`
 * Docker instance's `/generate` endpoint and saves the returned `.pmdl` model.
 *
 * Note: the seasalt server has no auth/TLS of its own, so `baseUrl` should point
 * at a host on your local network / VPN, not a publicly exposed address.
 */
class SnowboyTrainClient(
    private val baseUrl: String, // e.g. "http://192.168.1.50:8000"
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS) // ffmpeg trim + model generation isn't instant
        .build()
) {
    sealed class TrainResult {
        data class Success(val pmdlFile: File) : TrainResult()
        data class Failure(val message: String) : TrainResult()
    }

    /**
     * @param modelName name for the resulting wake word (also used as output filename)
     * @param samples at least 3 recorded .wav examples of the wake word
     * @param outputDir directory to save the returned .pmdl into
     * @param noTrim disable the server's automatic leading/trailing silence trim
     */
    suspend fun train(
        modelName: String,
        samples: List<File>,
        outputDir: File,
        noTrim: Boolean = false
    ): TrainResult = withContext(Dispatchers.IO) {
        require(samples.size >= 3) { "Snowboy requires at least 3 audio examples" }
        try {
            val bodyBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("modelName", modelName)

            samples.forEachIndexed { index, file ->
                bodyBuilder.addFormDataPart(
                    "example${index + 1}",
                    file.name,
                    file.asRequestBody("audio/wav".toMediaType())
                )
            }

            val url = buildString {
                append(baseUrl.trimEnd('/'))
                append("/generate")
                if (noTrim) append("?noTrim=true")
            }

            val request = Request.Builder()
                .url(url)
                .post(bodyBuilder.build())
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext TrainResult.Failure(
                        "Server returned ${response.code}: ${response.message}"
                    )
                }

                val body = response.body
                    ?: return@withContext TrainResult.Failure("Empty response body")

                if (!outputDir.exists()) outputDir.mkdirs()
                val pmdlFile = File(outputDir, "$modelName.pmdl")

                pmdlFile.outputStream().use { out ->
                    body.byteStream().copyTo(out)
                }

                TrainResult.Success(pmdlFile)
            }
        } catch (e: IOException) {
            TrainResult.Failure(e.message ?: "Network error while training")
        }
    }
}
 