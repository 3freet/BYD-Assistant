package com.kangrio.byd.assistant.service

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import ai.kitt.snowboy.SnowboyDetect
import android.util.Log
import com.audx.android.Audx
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("MissingPermission")
class SnowboyDetector(
    private val context: Context,
    private val modelName: String = "hey_rio",
    private val sensitivity: Float = 0.5f,
    private val audioGain: Float = 1.0f,
    private val audioSource: Int = MediaRecorder.AudioSource.VOICE_RECOGNITION,
    private val onDetected: () -> Unit
) {

    private val modelsPath = "snowboy/models"
    private var detector: SnowboyDetect? = null
    private var audioRecord: AudioRecord? = null
    private var audx: Audx? = null
    private val running = AtomicBoolean(false)

    private var thread: Thread? = null

    init {
        context.assets.list(modelsPath)?.forEach { name ->
            val file = File(
                context.filesDir,
                "$modelsPath/$name"
            ).apply {
                parentFile?.mkdirs()
            }
            if (!file.exists()) {
                context.assets.open("$modelsPath/$name").use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANEL_CONFIG,
            AUDIO_FORMAT
        )

        require(bufferSize > 0) {
            "Unable to determine AudioRecord buffer size"
        }

        val recordBufferSize = maxOf(
            bufferSize,
            SAMPLE_RATE / 2
        )

        val modelPath = getModelPath(modelName)
        val modelFile = File(modelPath)
        if (!modelFile.exists() || modelFile.length() == 0L) {
            Log.e("SnowboyDetector", "Model file not found or empty: $modelPath — detection aborted")
            running.set(false)
            return
        }

        detector = SnowboyDetect(
            assetFile("snowboy/common.res"),
            modelPath
        ).apply {
            SetSensitivity("%.1f".format(sensitivity))
            SetAudioGain(audioGain)
            ApplyFrontend(true)
        }

        audx = Audx.Builder()
            .inputRate(SAMPLE_RATE)
            .resampleQuality(Audx.AUDX_RESAMPLER_QUALITY_VOIP)
            .build()

        audioRecord = AudioRecord(
            audioSource,
            SAMPLE_RATE,
            CHANEL_CONFIG,
            AUDIO_FORMAT,
            recordBufferSize
        )

        audioRecord?.startRecording()

        thread = Thread {
            detectionLoop(CHUNK_SIZE)
        }.apply {
            name = "SnowboyDetector"
            start()
        }
    }

    private fun detectionLoop(bufferSize: Int) {
        val buffer = ShortArray(bufferSize)
        val output = ShortArray(bufferSize)

        try {
            while (running.get()) {

                if(isPaused()) {
                    Thread.sleep(100)
                    continue
                }

                val count = audioRecord?.read(
                    buffer,
                    0,
                    buffer.size
                ) ?: break

                if (count <= 0) {
                    continue
                }

                processAudioChunk(buffer, output)
            }
        } catch (_: InterruptedException) {
        } finally {
            stopInternal()
        }
    }

    /**
     * Reduces the gain of the input buffer by half (50%).
     */
    fun reduceGain(buffer: ShortArray, length: Int) {
        for (i in 0 until length) {
            buffer[i] = (buffer[i].toInt() shr 1).toShort()
        }
    }

    private fun processAudioChunk(
        audioData: ShortArray,
        output: ShortArray
    ) {
        reduceGain(audioData, audioData.size)
        audx?.process(audioData, output) { vadProbability ->
            if (vadProbability > 0.5) {
                Log.d("SnowboyDetector", "VAD=$vadProbability")
                val result = detector?.RunDetection(output, output.size) ?: 0
                if (result > 0) {
                    Log.d("SnowboyDetector", "Wake word detected")
                    onDetected()
                }
            }
        }
    }

    fun pause() {
        audioRecord?.stop()
    }

    fun resume() {
        if (!running.get()) return

        audioRecord?.startRecording()
    }

    fun isPaused(): Boolean {
        return audioRecord?.recordingState == AudioRecord.RECORDSTATE_STOPPED
    }

    fun stop() {
        if (!running.getAndSet(false)) return

        thread?.interrupt()
    }

    private fun stopInternal() {
        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        try {
            audx?.close()
        } catch (_: Exception) {
        }
        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }
        try {
            detector?.delete()
        } catch (_: Exception) {
        }

        audx = null
        audioRecord = null
        detector = null
        thread = null
    }

    private fun getModelPath(modelName: String): String {
        return File(
            context.filesDir,
            "snowboy/models/$modelName.pmdl"
        ).absolutePath
    }

    private fun assetFile(path: String): String {
        val file = File(
            context.filesDir,
            path
        ).apply {
            parentFile?.mkdirs()
        }

        if (!file.exists()) {
            context.assets.open(path).use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
        }

        return file.absolutePath
    }

    companion object {
        const val SAMPLE_RATE = 16000
        private const val CHUNK_SIZE = Audx.FRAME_SIZE
        const val CHANEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }
}