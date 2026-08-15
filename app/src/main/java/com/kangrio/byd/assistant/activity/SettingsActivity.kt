package com.kangrio.byd.assistant.activity

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.kangrio.byd.assistant.service.VoiceWakeService
import com.kangrio.byd.assistant.ui.theme.AssistantTheme
import com.kangrio.byd.assistant.util.Preferences
import com.kangrio.byd.assistant.util.SnowboyTrainClient
import kotlinx.coroutines.launch
import java.io.File

private const val WAKE_WORD_MODEL_NAME = "hey_rio"
private const val SEASALT_BASE_URL = "https://snowboy.jolanrensen.nl" // snowboy-seasalt host
private const val MIN_SAMPLES = 3

/** Drives the inline record panel: which controls are enabled and what's shown. */
sealed class RecordPhase {
    data object Idle : RecordPhase()
    data object Recording : RecordPhase()
    data object Uploading : RecordPhase()
    data class Trained(val pmdlFile: File) : RecordPhase()
    data class Failure(val message: String) : RecordPhase()
}

/** Thin wrapper around MediaRecorder for one take at a time. */
private class SampleRecorder(
    private val context: android.content.Context,
    private val outputDir: File
) {
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null

    fun start(): File {
        if (!outputDir.exists()) outputDir.mkdirs()
        val file = File(outputDir, "sample_${System.currentTimeMillis()}.m4a")
        currentFile = file

        @Suppress("DEPRECATION")
        val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }

        newRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = newRecorder
        return file
    }

    /** Stops the current take and returns the recorded file, or null if nothing was captured. */
    fun stop(): File? {
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            currentFile
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            currentFile?.delete()
            null
        }
    }

    /** Aborts the current take, discarding any partial recording. */
    fun cancel() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
            // recorder never produced valid output; nothing to clean up beyond release
        }
        recorder?.release()
        recorder = null
        currentFile?.delete()
        currentFile = null
    }
}

class SettingsActivity : ComponentActivity() {

    private val trainClient = SnowboyTrainClient(baseUrl = SEASALT_BASE_URL)
    private lateinit var sampleRecorder: SampleRecorder

    private var showTrainPanel = mutableStateOf(false)
    private var recordPhase = mutableStateOf<RecordPhase>(RecordPhase.Idle)
    private var samples = mutableStateOf<List<File>>(emptyList())

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) beginRecording() else {
            Toast.makeText(this, "Microphone permission is required to record", Toast.LENGTH_SHORT).show()
        }
    }

    private val savePmdlLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        val pmdlFile = (recordPhase.value as? RecordPhase.Trained)?.pmdlFile
        if (uri != null && pmdlFile != null) {
            contentResolver.openOutputStream(uri)?.use { out ->
                pmdlFile.inputStream().use { it.copyTo(out) }
            }
            Toast.makeText(this, "Saved $WAKE_WORD_MODEL_NAME.pmdl", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sampleRecorder = SampleRecorder(
            context = applicationContext,
            outputDir = cacheDir.resolve("wake_samples")
        )

        setContent {
            AssistantTheme {
                val panelOpen by showTrainPanel
                val phase by recordPhase
                val currentSamples by samples

                Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
                    SettingsScreen(
                        modifier = Modifier.padding(paddingValues),
                        onStateToggle = { state -> VoiceWakeService.setState(this, state) },
                        onPlayDingToggle = { state -> Preferences.playDingOnStart = state },
                        onSensitivityChange = { VoiceWakeService.setSensitivity(this, it) },
                        onGainChange = { VoiceWakeService.setGain(this, it) },
                        showTrainPanel = panelOpen,
                        recordPhase = phase,
                        sampleCount = currentSamples.size,
                        onTrainClick = { onTrainClick() },
                        onStartRecordClick = { onStartOrStopRecord(phase) },
                        onSubmitClick = { submitSamples(currentSamples) },
                        onNewClick = { resetPanel() },
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (recordPhase.value is RecordPhase.Recording) {
            sampleRecorder.cancel()
            recordPhase.value = RecordPhase.Idle
        }
    }

    private fun onTrainClick() {
        showTrainPanel.value = !showTrainPanel.value
        if (showTrainPanel.value && Preferences.startHotword) {
            Toast.makeText(this, "Recomment to disable voice detection before training", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onStartOrStopRecord(phase: RecordPhase) {
        if (phase is RecordPhase.Recording) {
            val file = sampleRecorder.stop()
            recordPhase.value = RecordPhase.Idle
            if (file != null) {
                samples.value += file
            } else {
                Toast.makeText(this, "Recording failed, try again", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val hasPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) beginRecording() else requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun beginRecording() {
        try {
            sampleRecorder.start()
            recordPhase.value = RecordPhase.Recording
        } catch (e: Exception) {
            recordPhase.value = RecordPhase.Failure(e.message ?: "Couldn't start recording")
        }
    }

    private fun submitSamples(currentSamples: List<File>) {
        if (currentSamples.size < MIN_SAMPLES) {
            Toast.makeText(this, "Record at least $MIN_SAMPLES samples first", Toast.LENGTH_SHORT).show()
            return
        }

        recordPhase.value = RecordPhase.Uploading
        lifecycleScope.launch {
            when (val result = trainClient.train(
                modelName = WAKE_WORD_MODEL_NAME,
                samples = currentSamples,
                outputDir = filesDir.resolve("snowboy")
            )) {
                is SnowboyTrainClient.TrainResult.Success -> {
                    VoiceWakeService.setModel(this@SettingsActivity, result.pmdlFile)
                    recordPhase.value = RecordPhase.Trained(result.pmdlFile)
                    Toast.makeText(this@SettingsActivity, "Wake word trained", Toast.LENGTH_SHORT).show()
                }
                is SnowboyTrainClient.TrainResult.Failure -> {
                    recordPhase.value = RecordPhase.Failure(result.message)
                    Toast.makeText(this@SettingsActivity, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /** Clears recorded takes and any trained/failed result so a fresh session can start. */
    private fun resetPanel() {
        if (recordPhase.value is RecordPhase.Recording) sampleRecorder.cancel()
        samples.value.forEach { it.delete() }
        samples.value = emptyList()
        recordPhase.value = RecordPhase.Idle
    }
}

/**
 * Settings screen, matching the app wireframe:
 * outer rounded frame -> "Settings" title pill -> inner rounded panel with
 * setting rows ("State" toggle, "Train your voice" action, expandable record
 * panel) -> footer pill.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onStateToggle: (Boolean) -> Unit,
    onPlayDingToggle: (Boolean) -> Unit = {},
    onSensitivityChange: (Float) -> Unit = {},
    onGainChange: (Float) -> Unit = {},
    showTrainPanel: Boolean = false,
    recordPhase: RecordPhase = RecordPhase.Idle,
    sampleCount: Int = 0,
    onTrainClick: () -> Unit,
    onStartRecordClick: () -> Unit = {},
    onSubmitClick: () -> Unit = {},
    onNewClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val outline = MaterialTheme.colorScheme.outlineVariant

    var isStateOn by remember { mutableStateOf(Preferences.startHotword) }
    var isPlayDing by remember { mutableStateOf(Preferences.playDingOnStart) }
    var sensitivity by remember { mutableFloatStateOf(Preferences.hotwordSensitivity) }
    var gain by remember { mutableFloatStateOf(Preferences.hotwordGain) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .border(1.dp, outline, RoundedCornerShape(28.dp))
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TitlePill(text = "Settings")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
                    .weight(1f)
                    .border(1.dp, outline, RoundedCornerShape(20.dp)),
            ) {
                SettingRow(
                    label = "Voice Detection",
                    description = "Enable or disable wake-word detection."
                ) {
                    Switch(
                        checked = isStateOn,
                        onCheckedChange = {
                            isStateOn = it
                            onStateToggle(it)
                        },
                        modifier = Modifier.semantics {
                            contentDescription = "State toggle, ${if (isStateOn) "on" else "off"}"
                        }
                    )
                }

                SettingRow(
                    label = "Play Sound",
                    description = "Play a sound when voice detection starts."
                ) {
                    Switch(
                        checked = isPlayDing,
                        onCheckedChange = {
                            isPlayDing = it
                            onPlayDingToggle(it)
                        },
                        modifier = Modifier.semantics {
                            contentDescription =
                                "Play ding toggle, ${if (isPlayDing) "on" else "off"}"
                        }
                    )
                }

                SettingRow(
                    label = "Detection Sensitivity",
                    description = "Adjust how easily the wake word is detected."
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Slider(
                            modifier = Modifier
                                .width(240.dp)
                                .padding(end = 12.dp),
                            value = sensitivity,
                            onValueChange = {
                                sensitivity = it
                            },
                            onValueChangeFinished = {
                                onSensitivityChange(sensitivity)
                            },
                            valueRange = 0.1f..1f,
                            steps = 8,
                        )
                        Text(
                            text = "%.1f".format(sensitivity),
                        )
                    }
                }

                SettingRow(
                    label = "Microphone Gain",
                    description = "Adjust the microphone input volume."
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Slider(
                            modifier = Modifier
                                .width(240.dp)
                                .padding(end = 12.dp),
                            value = gain,
                            onValueChange = {
                                gain = it
                            },
                            onValueChangeFinished = {
                                onGainChange(gain)
                            },
                            valueRange = 0.1f..2.0f,
                            steps = 19,
                        )
                        Text(
                            text = "%.1f".format(gain),
                        )
                    }
                }

                HorizontalDivider(color = outline)

                SettingRow(
                    label = "Train Wake Word Model",
                    description = "Record your voice samples to improve recognition of \"Hey Rio\" by recording at least 3 samples."
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Button(
                            modifier = Modifier.padding(end = 12.dp),
                            onClick = onTrainClick
                        ) {
                            Text("Train")
                        }
                        FilledTonalButton(
                            onClick = {
                                context.filesDir.resolve("snowboy").deleteRecursively()
                                VoiceWakeService.setState(context, false)
                                VoiceWakeService.setState(context, true)
                            }
                        ) {
                            Text("Default")
                        }
                    }
                }

                AnimatedVisibility(
                    visible = showTrainPanel,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    RecordPanel(
                        phase = recordPhase,
                        sampleCount = sampleCount,
                        onStartRecordClick = onStartRecordClick,
                        onSubmitClick = onSubmitClick,
                        onNewClick = onNewClick,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TitlePill(text = "Created by @KangRio")
        }
    }
}

@Composable
private fun RecordPanel(
    phase: RecordPhase,
    sampleCount: Int,
    onStartRecordClick: () -> Unit,
    onSubmitClick: () -> Unit,
    onNewClick: () -> Unit,
) {
    val isRecording = phase is RecordPhase.Recording
    val isUploading = phase is RecordPhase.Uploading
    val isTrained = phase is RecordPhase.Trained
    val isBusy = isRecording || isUploading

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Text(
            text = statusText(phase, sampleCount),
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onStartRecordClick,
                enabled = !isUploading && !isTrained
            ) {
                if (isRecording) {
                    Text("Stop")
                } else {
                    Text("Start record")
                }
            }

            FilledTonalButton(
                onClick = onSubmitClick,
                enabled = !isBusy && !isTrained && sampleCount >= MIN_SAMPLES
            ) {
                if (isUploading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Submit")
                }
            }

            OutlinedButton(
                onClick = onNewClick,
                enabled = !isBusy && sampleCount > 0
            ) {
                Text("New")
            }
        }
    }
}

private fun statusText(phase: RecordPhase, sampleCount: Int): String = when (phase) {
    RecordPhase.Idle -> "$sampleCount / $MIN_SAMPLES samples recorded"
    RecordPhase.Recording -> "Recording... tap Stop when done"
    RecordPhase.Uploading -> "Uploading and training..."
    is RecordPhase.Trained -> "Model trained — tap New to record again"
    is RecordPhase.Failure -> "Failed: ${phase.message}"
}

@Composable
private fun TitlePill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Normal
        )
    }
}

@Composable
private fun SettingRow(
    label: String,
    description: String = "",
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (description.isNotEmpty()) 64.dp else 56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.padding(end = 12.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        content()
    }
}

@Preview(showBackground = true, widthDp = 480, heightDp = 480)
@Composable
private fun SettingsScreenPreview() {
    var stateOn by remember { mutableStateOf(true) }
    var panelOpen by remember { mutableStateOf(true) }

    MaterialTheme {
        SettingsScreen(
            onStateToggle = { stateOn = it },
            showTrainPanel = panelOpen,
            sampleCount = 2,
            onTrainClick = { panelOpen = !panelOpen }
        )
    }
}