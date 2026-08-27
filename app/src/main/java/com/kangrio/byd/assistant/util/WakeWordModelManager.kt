package com.kangrio.byd.assistant.util

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.kangrio.byd.assistant.data.OnlineWakeWordModel
import com.kangrio.byd.assistant.ota.OtaUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.jvm.Throws

object WakeWordModelManager {
    private const val TAG = "WakeWordModelManager"
    const val MODELS_PATH = "openwakeword/models"
    const val TEMPLATE_EXTENSION = ".wwtpl"
    private const val TEMPLATE_MAGIC = "WWT1"
    private const val TEMPLATE_EMBEDDING_DIM = 96

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun getModelsDir(context: Context): File {
        return File(context.filesDir, MODELS_PATH).apply { mkdirs() }
    }

    fun ensureDefaultModels(context: Context) {
        try {
            val assetList = context.assets.list(MODELS_PATH) ?: return
            val modelsDir = getModelsDir(context)
            for (name in assetList) {
                if (!name.endsWith(".onnx", ignoreCase = true)) continue
                val target = File(modelsDir, name)
                if (!target.exists()) {
                    context.assets.open("$MODELS_PATH/$name").use { input ->
                        target.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to copy default models from assets", e)
        }
    }

    fun getInstalledModels(context: Context): List<String> {
        ensureDefaultModels(context)
        val files = getModelsDir(context).listFiles() ?: return emptyList()
        return files
            .filter {
                it.isFile && (it.name.endsWith(".onnx", ignoreCase = true) ||
                        it.name.endsWith(TEMPLATE_EXTENSION, ignoreCase = true))
            }
            .map { it.name.removeSuffix(".onnx").removeSuffix(TEMPLATE_EXTENSION) }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    fun isCustomTemplate(context: Context, modelName: String): Boolean {
        return File(getModelsDir(context), "$modelName$TEMPLATE_EXTENSION").exists()
    }

    fun isInstalled(context: Context, fileName: String): Boolean {
        return File(getModelsDir(context), fileName).exists()
    }

    fun isBuiltInModel(context: Context, modelName: String): Boolean {
        val assetList = try {
            context.assets.list(MODELS_PATH) ?: emptyArray()
        } catch (_: Exception) {
            emptyArray()
        }
        return assetList.any { it.equals("$modelName.onnx", ignoreCase = true) }
    }

    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        name = cursor.getString(nameIndex)
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Could not query display name for URI: $uri", e)
            }
        }
        if (name == null) {
            name = uri.lastPathSegment?.substringAfterLast('/')
        }
        return name
    }

    @Throws(Throwable::class)
    fun validate(filePath: String): Boolean {
        OrtEnvironment.getEnvironment().createSession(
            filePath,
            OrtSession.SessionOptions()
        ).use {
            // Valid
        }
        return true
    }

    suspend fun importModelFromUri(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val fileName = getFileNameFromUri(context, uri)
                ?: return@withContext Result.failure(IllegalArgumentException("Could not determine file name."))

            if (!fileName.endsWith(".onnx", ignoreCase = true)) {
                return@withContext Result.failure(IllegalArgumentException("Please select a file with .onnx extension."))
            }

            val targetFile = File(getModelsDir(context), fileName)
            val tempFile = File(getModelsDir(context), "$fileName.importing")

            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IllegalStateException("Failed to open file stream."))

            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            try {
                validate(tempFile.absolutePath)
            } catch (e: Throwable) {
                tempFile.delete()
                throw e
            }

            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            val modelName = fileName.removeSuffix(".onnx")
            Result.success(modelName)
        } catch (e: Throwable) {
            Log.e(TAG, "Error importing model from URI: $uri", e)
            Result.failure(e)
        }
    }

    suspend fun fetchOnlineModels(context: Context): List<OnlineWakeWordModel> = withContext(Dispatchers.IO) {
        val treeResponse = OtaUpdater.gitHubApi.getTree(
            owner = "fwartner",
            repo = "home-assistant-wakewords-collection",
            treeSha = "main",
            recursive = 1
        )

        val modelsDir = getModelsDir(context)
        treeResponse.tree
            .filter { item ->
                item.type == "blob" &&
                        item.path.startsWith("en/") &&
                        item.path.endsWith(".onnx", ignoreCase = true)
            }
            .map { item ->
                val fileName = item.path.substringAfterLast('/')
                val name = fileName.removeSuffix(".onnx")
                val isInstalled = File(modelsDir, fileName).exists()
                val downloadUrl = "https://raw.githubusercontent.com/fwartner/home-assistant-wakewords-collection/main/${item.path}"
                OnlineWakeWordModel(
                    name = name,
                    fileName = fileName,
                    path = item.path,
                    downloadUrl = downloadUrl,
                    size = item.size,
                    isInstalled = isInstalled
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    suspend fun downloadModel(
        context: Context,
        model: OnlineWakeWordModel,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        val targetFile = File(getModelsDir(context), model.fileName)
        val tempFile = File(getModelsDir(context), "${model.fileName}.tmp")

        val request = Request.Builder()
            .url(model.downloadUrl)
            .header("User-Agent", "Assistant-WakeWord-Downloader")
            .get()
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Download model failed with HTTP ${response.code}")
                    return@withContext false
                }

                val body = response.body ?: return@withContext false
                val totalBytes = body.contentLength().takeIf { it > 0 } ?: model.size
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                onProgress(downloadedBytes.toFloat() / totalBytes.toFloat())
                            }
                        }
                        output.flush()
                    }
                }

                if (targetFile.exists()) {
                    targetFile.delete()
                }
                val renamed = tempFile.renameTo(targetFile)
                if (!renamed) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
                onProgress(1.0f)
                return@withContext true
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error downloading model ${model.fileName}", e)
            if (tempFile.exists()) tempFile.delete()
            return@withContext false
        }
    }

    fun deleteModel(context: Context, modelName: String): Boolean {
        if (isBuiltInModel(context, modelName)) return false

        val classifierFile = File(getModelsDir(context), "$modelName.onnx")
        val templateFile = File(getModelsDir(context), "$modelName$TEMPLATE_EXTENSION")
        return when {
            classifierFile.exists() -> classifierFile.delete()
            templateFile.exists() -> templateFile.delete()
            else -> false
        }
    }

    /** Saves a recorded wake word as a pooled embedding "template" (see [TEMPLATE_MAGIC] format). */
    fun saveCustomTemplate(context: Context, name: String, embedding: FloatArray): Result<String> {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.contains('/') || trimmed.contains('.')) {
            return Result.failure(IllegalArgumentException("Enter a valid name (no \".\" or \"/\")."))
        }
        if (embedding.size != TEMPLATE_EMBEDDING_DIM) {
            return Result.failure(IllegalStateException("Unexpected embedding size: ${embedding.size}"))
        }
        if (getInstalledModels(context).any { it.equals(trimmed, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("\"$trimmed\" is already in use."))
        }

        return try {
            val buffer = ByteBuffer
                .allocate(4 + 4 + 8 + TEMPLATE_EMBEDDING_DIM * 4)
                .order(ByteOrder.nativeOrder())
            buffer.put(TEMPLATE_MAGIC.toByteArray(Charsets.US_ASCII))
            buffer.putInt(TEMPLATE_EMBEDDING_DIM)
            buffer.putLong(System.currentTimeMillis())
            for (value in embedding) buffer.putFloat(value)

            File(getModelsDir(context), "$trimmed$TEMPLATE_EXTENSION").writeBytes(buffer.array())
            Result.success(trimmed)
        } catch (e: Throwable) {
            Log.e(TAG, "Error saving custom wake word template: $trimmed", e)
            Result.failure(e)
        }
    }

    /** Loads a template saved by [saveCustomTemplate], or null if missing/corrupt. */
    fun loadCustomTemplate(context: Context, name: String): FloatArray? {
        val file = File(getModelsDir(context), "$name$TEMPLATE_EXTENSION")
        if (!file.exists()) return null

        return try {
            val buffer = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.nativeOrder())
            val magic = ByteArray(4).also { buffer.get(it) }
            if (String(magic, Charsets.US_ASCII) != TEMPLATE_MAGIC) return null

            val dim = buffer.getInt()
            if (dim != TEMPLATE_EMBEDDING_DIM) return null
            buffer.getLong() // createdAtEpochMillis, currently unused

            FloatArray(dim).also { buffer.asFloatBuffer().get(it) }
        } catch (e: Throwable) {
            Log.e(TAG, "Error loading custom wake word template: $name", e)
            null
        }
    }
}
