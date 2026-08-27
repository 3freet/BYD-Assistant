package com.suxsem.androidwakeword

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections

/**
 * One-shot mel-spectrogram + embedding extraction for recording a custom wake word.
 * Loads only the two shared, wake-word-agnostic feature models (no VAD, no classifier,
 * no continuous-listening state) so a single short clip can be turned into one pooled
 * embedding vector for template matching in [WakeWordDetector].
 */
class EmbeddingExtractor(private val context: Context) {

    private val sampleRate = 16000
    private val frameSamples = 1280       // 80 ms step, matches WakeWordDetector
    private val contextSamples = 480
    private val embeddingDim = 96
    private val melCoeffs = 32
    private val melWindow = 76
    private val melStep = 8
    private val coldStartStepsToDiscard = 10 // first ~800ms of a fresh mel window is partially zero-padded

    private val ortEnv = OrtEnvironment.getEnvironment()
    private val melSession: OrtSession
    private val embeddingSession: OrtSession
    private val melInputName: String
    private val embeddingInputName: String

    init {
        val opts = OrtSession.SessionOptions()
        fun loadAsset(name: String) = ortEnv.createSession(context.assets.open(name).readBytes(), opts)
        melSession = loadAsset("melspectrogram.onnx")
        embeddingSession = loadAsset("embedding_model.onnx")
        melInputName = melSession.inputNames.first()
        embeddingInputName = embeddingSession.inputNames.first()
    }

    /** Records [recordMs] of audio and returns the mean-pooled embedding of the clip. */
    suspend fun recordAndEmbed(audioSource: Int, recordMs: Long = 3000L): FloatArray =
        withContext(Dispatchers.Default) {
            embed(recordPcm(audioSource, recordMs))
        }

    fun release() {
        melSession.close()
        embeddingSession.close()
        ortEnv.close()
    }

    @SuppressLint("MissingPermission")
    private fun recordPcm(audioSource: Int, recordMs: Long): ShortArray {
        val totalSamples = (sampleRate * recordMs / 1000L).toInt()
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(totalSamples)

        val audioRecord = AudioRecord(
            audioSource, sampleRate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, minBuffer
        )
        val pcm = ShortArray(totalSamples)
        try {
            audioRecord.startRecording()
            var offset = 0
            while (offset < totalSamples) {
                val read = audioRecord.read(pcm, offset, totalSamples - offset, AudioRecord.READ_BLOCKING)
                if (read <= 0) break
                offset += read
            }
        } finally {
            audioRecord.stop()
            audioRecord.release()
        }
        return pcm
    }

    /**
     * Converts a raw 16kHz mono PCM clip into a single mean-pooled [embeddingDim]-dim vector,
     * replicating [WakeWordDetector]'s mel→embedding windowing as one non-streaming batch call.
     */
    fun embed(pcm: ShortArray): FloatArray {
        val numSteps = (pcm.size - contextSamples) / frameSamples
        require(numSteps > coldStartStepsToDiscard) { "Clip too short to embed: $numSteps steps" }

        val melRing = WakeWordDetector.MelRingBuffer(melWindow + numSteps * melStep, melCoeffs)

        val melInput = ByteBuffer.allocateDirect(numSteps * 1760 * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        for (i in 0 until numSteps) {
            val srcOffset = i * frameSamples
            for (j in 0 until 1760) {
                melInput.put(pcm[srcOffset + j].toFloat())
            }
        }
        melInput.rewind()

        val melOutput = ByteBuffer.allocateDirect(numSteps * melWindow * melCoeffs * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()

        OnnxTensor.createTensor(ortEnv, melInput, longArrayOf(numSteps.toLong(), 1760L)).use { tensor ->
            melSession.run(Collections.singletonMap(melInputName, tensor)).use { out ->
                val flatBuffer = (out[0] as OnnxTensor).floatBuffer
                flatBuffer.rewind()
                repeat(numSteps * 8) {
                    val frame = melRing.advance()
                    for (f in 0 until melCoeffs) {
                        frame[f] = (flatBuffer.get() / 10.0f) + 2.0f
                    }
                }
            }
        }

        val ringSize = melRing.size
        for (b in 0 until numSteps) {
            val batchOffset = b * melWindow * melCoeffs
            val stepsBack = (numSteps - 1 - b) * melStep
            val startIdx = (ringSize - melWindow) - stepsBack
            for (f in 0 until melWindow) {
                melOutput.position(batchOffset + (f * melCoeffs))
                melOutput.put(melRing[startIdx + f], 0, melCoeffs)
            }
        }
        melOutput.rewind()

        val pooled = FloatArray(embeddingDim)
        var pooledCount = 0

        OnnxTensor.createTensor(
            ortEnv, melOutput, longArrayOf(numSteps.toLong(), melWindow.toLong(), melCoeffs.toLong(), 1L)
        ).use { tensor ->
            embeddingSession.run(Collections.singletonMap(embeddingInputName, tensor)).use { out ->
                val outBuffer = (out[0] as OnnxTensor).floatBuffer
                val emb = FloatArray(embeddingDim)
                for (step in 0 until numSteps) {
                    outBuffer.get(emb)
                    if (step >= coldStartStepsToDiscard) {
                        for (i in 0 until embeddingDim) pooled[i] += emb[i]
                        pooledCount++
                    }
                }
            }
        }
        for (i in 0 until embeddingDim) pooled[i] /= pooledCount
        return pooled
    }
}
