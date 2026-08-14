package com.kangrio.byd.assistant.service

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import ai.kitt.snowboy.SnowboyDetect
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("MissingPermission")
class SnowboyDetector(
    private val context: Context,
    private val modelFile: String,
    private val sensitivity: Float = 0.5f,
    private val audioGain: Float = 1.0f,
    private val onDetected: () -> Unit
) {

    private var detector: SnowboyDetect? = null
    private var audioRecord: AudioRecord? = null

    private val running = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)

    private var thread: Thread? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return

        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            channelConfig,
            audioFormat
        )

        require(bufferSize > 0) {
            "Unable to determine AudioRecord buffer size"
        }

        val recordBufferSize = maxOf(
            bufferSize,
            sampleRate / 2
        )

        detector = SnowboyDetect(
            assetFile("snowboy/common.res"),
            assetFile(modelFile)
        ).apply {
            SetSensitivity(sensitivity.toString())
            SetAudioGain(audioGain)
            ApplyFrontend(true)
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            recordBufferSize
        )

        audioRecord?.startRecording()

        thread = Thread {
            detectionLoop(recordBufferSize / 2)
        }.apply {
            name = "SnowboyDetector"
            start()
        }
    }

    fun pause() {
        paused.set(true)
    }

    fun resume() {
        if (!running.get()) return

        paused.set(false)
    }

    fun isPaused(): Boolean {
        return paused.get()
    }

    private fun detectionLoop(bufferSize: Int) {
        val buffer = ShortArray(bufferSize)

        try {
            while (running.get()) {

                val count = audioRecord?.read(
                    buffer,
                    0,
                    buffer.size
                ) ?: break

                if (count <= 0 || paused.get()) {
                    continue
                }

                val result = detector?.RunDetection(
                    buffer,
                    count
                ) ?: break

                if (result > 0) {
                    onDetected()
                }
            }
        } finally {
            stopInternal()
        }
    }

    fun stop() {
        if (!running.getAndSet(false)) return

        thread?.interrupt()

        stopInternal()
    }

    private fun stopInternal() {
        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }

        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }

        audioRecord = null

        detector?.delete()
        detector = null

        thread = null
    }

    private fun assetFile(path: String): String {
        val file = File(
            context.cacheDir,
            path.substringAfterLast('/')
        )

        if (!file.exists()) {
            context.assets.open(path).use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
        }

        return file.absolutePath
    }
}