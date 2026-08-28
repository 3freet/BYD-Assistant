package com.kangrio.byd.assistant.util

import android.content.Context
import android.util.Log
import com.kangrio.byd.assistant.data.OnlineSttModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * Downloads and unpacks offline Vosk speech-recognition models, mirroring
 * [WakeWordModelManager]'s already-proven download shape (temp file, progress callback,
 * rename-on-success) — the difference is a Vosk model is a directory tree, not a single file,
 * so a download here is followed by a zip-extract step.
 */
object VoskModelManager {
    private const val TAG = "VoskModelManager"
    private const val MODELS_ROOT = "vosk/models"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun getModelDir(context: Context, languageTag: String): File =
        File(context.filesDir, "$MODELS_ROOT/$languageTag")

    /** A provisioned model directory always contains a "conf" subdirectory — cheap, good-enough
     * sanity check that a prior download+unzip actually completed rather than leaving a partial. */
    fun isProvisioned(context: Context, languageTag: String): Boolean =
        File(getModelDir(context, languageTag), "conf").isDirectory

    fun installedModelName(context: Context, languageTag: String): String? {
        if (!isProvisioned(context, languageTag)) return null
        return File(getModelDir(context, languageTag), MODEL_NAME_MARKER).takeIf { it.exists() }?.readText()?.trim()
    }

    suspend fun downloadAndUnpack(
        context: Context,
        model: OnlineSttModel,
        onProgress: (Float) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        val modelDir = getModelDir(context, model.languageTag)
        val tempZip = File(modelDir.parentFile, "${model.languageTag}.zip.part")
        modelDir.parentFile?.mkdirs()

        val request = Request.Builder()
            .url(model.downloadUrl)
            .header("User-Agent", "Assistant-Vosk-Downloader")
            .get()
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Download failed with HTTP ${response.code}")
                    return@withContext false
                }
                val body = response.body ?: return@withContext false
                val totalBytes = body.contentLength().takeIf { it > 0 } ?: model.sizeBytes
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tempZip).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                // Extraction happens after the download, but report progress up to
                                // 90% during download so the UI doesn't sit at 100% while unzipping.
                                onProgress((downloadedBytes.toFloat() / totalBytes.toFloat()) * 0.9f)
                            }
                        }
                    }
                }
            }

            // Fresh extract: never merge into a directory that might hold a previous model's files.
            if (modelDir.exists()) modelDir.deleteRecursively()
            modelDir.mkdirs()
            unzipFlattened(tempZip, modelDir)
            File(modelDir, MODEL_NAME_MARKER).writeText(model.name)
            onProgress(1.0f)
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Error downloading/unpacking model ${model.name}", e)
            modelDir.deleteRecursively()
            false
        } finally {
            tempZip.delete()
        }
    }

    fun deleteModel(context: Context, languageTag: String): Boolean =
        getModelDir(context, languageTag).deleteRecursively()

    /** Vosk's official archives contain exactly one top-level directory; this strips it so
     * [Model][org.vosk.Model] can be pointed directly at `targetDir` (which must already exist). */
    private fun unzipFlattened(zipFile: File, targetDir: File) {
        val targetCanonical = targetDir.canonicalFile
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val relativePath = entry.name.substringAfter('/', missingDelimiterValue = "")
                if (relativePath.isNotEmpty()) {
                    val outFile = File(targetDir, relativePath)
                    // Zip-slip guard: refuse any entry that would resolve outside targetDir.
                    if (outFile.canonicalFile.path.startsWith(targetCanonical.path + File.separator)) {
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { output -> zis.copyTo(output) }
                        }
                    } else {
                        Log.w(TAG, "Skipping suspicious zip entry: ${entry.name}")
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private const val MODEL_NAME_MARKER = ".model_name"
}
