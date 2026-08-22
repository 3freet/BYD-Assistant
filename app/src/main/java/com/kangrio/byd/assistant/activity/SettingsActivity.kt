package com.kangrio.byd.assistant.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.kangrio.byd.assistant.ota.OtaUpdater
import com.kangrio.byd.assistant.data.ReleaseInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.kangrio.byd.assistant.service.VoiceWakeService
import com.kangrio.byd.assistant.ui.composable.AppIcon
import com.kangrio.byd.assistant.ui.theme.AssistantTheme
import com.kangrio.byd.assistant.util.Preferences
import com.kangrio.byd.assistant.util.SnowboyTrainClient
import com.kangrio.byd.assistant.util.Utils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val WAKE_WORD_MODEL_NAME = "user_custom"
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
            setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
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
    private var initialOtaRelease = mutableStateOf<ReleaseInfo?>(null)

    private val requestMicPermissionWakeWord = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            VoiceWakeService.setState(this, true)
        } else {
            Toast.makeText(this, "Microphone permission is required for wake word detection", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestMicPermissionTrain = registerForActivityResult(
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

        val otaVersion = intent.getStringExtra("EXTRA_OTA_VERSION")
        val otaUrl = intent.getStringExtra("EXTRA_OTA_URL")
        val otaName = intent.getStringExtra("EXTRA_OTA_NAME")
        val otaBody = intent.getStringExtra("EXTRA_OTA_BODY")
        if (!otaVersion.isNullOrEmpty() && !otaUrl.isNullOrEmpty()) {
            initialOtaRelease.value = ReleaseInfo(
                tagName = otaVersion,
                versionName = otaVersion.removePrefix("v").removePrefix("V"),
                title = otaVersion,
                body = otaBody ?: "",
                htmlUrl = "",
                downloadUrl = otaUrl,
                apkName = otaName ?: "Assistant.apk"
            )
        }

        setContent {
            AssistantTheme {
                val panelOpen by showTrainPanel
                val phase by recordPhase
                val currentSamples by samples
                val otaRelease by initialOtaRelease

                Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
                    SettingsScreen(
                        modifier = Modifier.padding(paddingValues),
                        initialOtaRelease = otaRelease,
                        onStateToggle = { state -> onStateToggle(state) },
                        onPlayDingToggle = { state -> Preferences.playDingOnStart = state },
                        onSensitivityChange = { VoiceWakeService.setSensitivity(this, it) },
                        onGainChange = { VoiceWakeService.setGain(this, it) },
                        recordPhase = phase,
                        sampleCount = currentSamples.size,
                        onModelChanged = { model -> VoiceWakeService.setModel(this, model) },
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
        finish()
    }

    private fun onStateToggle(state: Boolean) {
        if (!state) {
            VoiceWakeService.setState(this, false)
            return
        }

        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            VoiceWakeService.setState(this, true)
        } else {
            requestMicPermissionWakeWord.launch(Manifest.permission.RECORD_AUDIO)
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

        if (hasPermission) beginRecording() else requestMicPermissionTrain.launch(Manifest.permission.RECORD_AUDIO)
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
                outputDir = filesDir.resolve("snowboy/models")
            )) {
                is SnowboyTrainClient.TrainResult.Success -> {
                    VoiceWakeService.setModel(this@SettingsActivity, result.pmdlFile.nameWithoutExtension)
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
    initialOtaRelease: ReleaseInfo? = null,
    onStateToggle: (Boolean) -> Unit,
    onPlayDingToggle: (Boolean) -> Unit = {},
    onSensitivityChange: (Float) -> Unit = {},
    onGainChange: (Float) -> Unit = {},
    recordPhase: RecordPhase = RecordPhase.Idle,
    sampleCount: Int = 0,
    onModelChanged: (String) -> Unit = { _-> },
    onStartRecordClick: () -> Unit = {},
    onSubmitClick: () -> Unit = {},
    onNewClick: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val outline = MaterialTheme.colorScheme.outlineVariant

    var showAssistantDialog by remember { mutableStateOf(false) }
    var showAssistantPermissionsDialog by remember { mutableStateOf(false) }
    var assistantApps by remember {
        mutableStateOf(Utils.listAssistantPackages(context))
    }
    var selectedAssistantApp by remember { mutableStateOf(Utils.getCurrentAssistantApp(context)) }

    var isWriteSecureSettingsGranted by remember { mutableStateOf(Utils.isGranted(context, Manifest.permission.WRITE_SECURE_SETTINGS)) }

    var expandedModels by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf(Preferences.hotwordModelName) }

    var isStateOn by remember { mutableStateOf(Preferences.startHotword) }
    var isPlayDing by remember { mutableStateOf(Preferences.playDingOnStart) }
    var sensitivity by remember { mutableFloatStateOf(Preferences.hotwordSensitivity) }
    var gain by remember { mutableFloatStateOf(Preferences.hotwordGain) }

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<ReleaseInfo?>(initialOtaRelease) }
    var showUpdateDialog by remember { mutableStateOf(initialOtaRelease != null) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }

    val models = remember(selectedModel) {
        context.filesDir.resolve("snowboy/models")
            .listFiles()
            ?.filter { it.isFile && it.extension.equals("pmdl", ignoreCase = true) }
            ?: emptyList()
    }

    LaunchedEffect(lifecycleOwner) {
        while (true) {
            isWriteSecureSettingsGranted = Utils.isGranted(context, Manifest.permission.WRITE_SECURE_SETTINGS)
            isStateOn = VoiceWakeService.isWakeWordStarted
            delay(1_000L.milliseconds)
        }
    }

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
                    .verticalScroll(rememberScrollState()),
            ) {
                SettingRow(
                    label = "Permission Onboarding",
                    description = "Review and manage all required app permissions."
                ) {
                    FilledTonalButton(
                        onClick = {
                            context.startActivity(Intent(context, PermissionOnboardingActivity::class.java))
                        }
                    ) {
                        Text("Open Wizard")
                    }
                }

                SettingRow(
                    label = "Assistant app",
                    description = "Select the assistant app to use."
                ) {
                        FilledTonalButton(
                            onClick = {
                                if (!isWriteSecureSettingsGranted) {
                                    Toast.makeText(context, "Write Secure Settings permission is required", Toast.LENGTH_SHORT).show()
                                    return@FilledTonalButton
                                }
                                showAssistantDialog = true
                            }
                        ) {
                            Text(selectedAssistantApp.name)
                        }
                }
                if (Utils.getNotificationListenerComponentName(context, selectedAssistantApp.packageName) != null) {
                    SettingRow(
                        label = "Assistant Permissions",
                        description = "Give the assistant app required permissions."
                    ) {
                        FilledTonalButton(
                            onClick = {
                                if (!isWriteSecureSettingsGranted) {
                                    Toast.makeText(context, "Write Secure Settings permission is required", Toast.LENGTH_SHORT).show()
                                    return@FilledTonalButton
                                }
                                showAssistantPermissionsDialog = true
                            }
                        ) {
                            Text("Grant")
                        }
                    }
                }

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
                    description = "Play a sound when the voice assistant launches."
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

                SettingRow(
                    label = "Check for Updates",
                    description = "Current version: v${OtaUpdater.getCurrentVersionName(context)}"
                ) {
                    FilledTonalButton(
                        onClick = {
                            if (isCheckingUpdate || isDownloadingUpdate) return@FilledTonalButton
                            isCheckingUpdate = true
                            scope.launch {
                                val release = OtaUpdater.checkForUpdate(context, force = true)
                                isCheckingUpdate = false
                                if (release != null) {
                                    availableUpdate = release
                                    showUpdateDialog = true
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Assistant is up-to-date (v${OtaUpdater.getCurrentVersionName(context)})",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        enabled = !isCheckingUpdate && !isDownloadingUpdate
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Check Now")
                        }
                    }
                }

                HorizontalDivider(color = outline)

                SettingRow(
                    label = "Record Your Custom Wake Word",
                    description = "Record at least 3 samples of your custom wake word to train your voice model."
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Box {
                            FilledTonalButton(
                                onClick = { expandedModels = true }
                            ) {
                                Text(selectedModel)
                            }

                            DropdownMenu(
                                expanded = expandedModels,
                                onDismissRequest = { expandedModels = false }
                            ) {
                                models.forEach { model ->
                                    DropdownMenuItem(
                                        text = { Text(model.nameWithoutExtension) },
                                        onClick = {
                                            onModelChanged(model.nameWithoutExtension)
                                            selectedModel = model.nameWithoutExtension
                                            expandedModels = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                RecordPanel(
                    phase = recordPhase,
                    sampleCount = sampleCount,
                    onStartRecordClick = onStartRecordClick,
                    onSubmitClick = {
                        onSubmitClick()
                        selectedModel = WAKE_WORD_MODEL_NAME
                    },
                    onNewClick = onNewClick,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            TitlePill(text = "Created by @KangRio")
        }
    }

    if (showAssistantDialog) {
        AlertDialog(
            onDismissRequest = { showAssistantDialog = false },
            title = {
                Text("Select Assistant App")
            },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                ) {
                    assistantApps.forEach { assistant ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showAssistantDialog = false
                                    Utils.enableVoiceAssistant(context, assistant.componentName)
                                    Preferences.assistantPackageComponent = assistant.componentName
                                    selectedAssistantApp = assistant
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcon(
                                packageName = assistant.packageName,
                                modifier = Modifier.size(40.dp)
                            )

                            Spacer(Modifier.width(12.dp))

                            Text(assistant.name)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showAssistantPermissionsDialog) {
        var componentName by remember { mutableStateOf(Utils.getNotificationListenerComponentName(context, selectedAssistantApp.packageName)) }
        if (componentName != null) {
            AlertDialog(
                onDismissRequest = { showAssistantPermissionsDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(
                            packageName = selectedAssistantApp.packageName,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Grant Assistant Permissions")
                    }
                },
                text = {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showAssistantPermissionsDialog = false
                                }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            var granted by remember { mutableStateOf(Utils.isNotificationListenerEnabled(context, selectedAssistantApp.packageName)) }
                            Text("Notification Listener")
                            Switch(
                                checked = granted,
                                onCheckedChange = {
                                    scope.launch {
                                        if (it) {
                                            Utils.grantNotificationListener(context, selectedAssistantApp.packageName)
                                            delay(1000.milliseconds)
                                            granted = Utils.isNotificationListenerEnabled(context, selectedAssistantApp.packageName)
                                        }
                                    }
                                }
                            )
                        }
                    }
                },
                confirmButton = {}
            )
        } else {
            showAssistantPermissionsDialog = false
            Toast.makeText(context, "No need to grant permissions", Toast.LENGTH_SHORT).show()
        }
    }

    if (showUpdateDialog && availableUpdate != null) {
        val update = availableUpdate!!
        AlertDialog(
            onDismissRequest = {
                if (!isDownloadingUpdate) {
                    showUpdateDialog = false
                }
            },
            title = {
                Text("Update Available: ${update.tagName}")
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = if (update.body.isNotBlank()) update.body else "A new version of Assistant is available.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (isDownloadingUpdate) {
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Downloading... ${(downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isDownloadingUpdate) return@Button
                        isDownloadingUpdate = true
                        downloadProgress = 0f
                        scope.launch {
                            val file = OtaUpdater.downloadApk(
                                context = context,
                                downloadUrl = update.downloadUrl,
                                fileName = update.apkName.ifEmpty { "Assistant_${update.tagName}.apk" },
                                onProgress = { progress ->
                                    downloadProgress = progress
                                }
                            )
                            isDownloadingUpdate = false
                            if (file != null && file.exists()) {
                                showUpdateDialog = false
                                OtaUpdater.installApk(context, file)
                            } else {
                                Toast.makeText(context, "Failed to download update", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !isDownloadingUpdate
                ) {
                    Text(if (isDownloadingUpdate) "Downloading..." else "Download & Install")
                }
            },
            dismissButton = {
                if (!isDownloadingUpdate) {
                    OutlinedButton(onClick = { showUpdateDialog = false }) {
                        Text("Later")
                    }
                }
            }
        )
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
    val scope = rememberCoroutineScope()
    val isRecording = phase is RecordPhase.Recording
    val isUploading = phase is RecordPhase.Uploading
    val isTrained = phase is RecordPhase.Trained
    val isBusy = isRecording || isUploading

    var recordText by remember { mutableStateOf("Start") }
    var isWaiting by remember { mutableStateOf(false) }

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
                onClick = {
                    scope.launch {
                        isWaiting = true
                        var timer = 2
                        repeat(timer) {
                            recordText = "Wait ${timer - it}..."
                            delay(1.seconds)
                        }

                        onStartRecordClick()

                        timer = 3
                        repeat(timer) {
                            recordText = "Recording ${timer - it}..."
                            delay(1.seconds)
                        }

                        onStartRecordClick()
                        recordText = "Start"
                        isWaiting = false
                    }
                },
                enabled = !isWaiting && !isUploading && !isTrained && !isRecording
            ) {
                Text(recordText)
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
    RecordPhase.Recording -> "Recording... speak your word"
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

    MaterialTheme {
        SettingsScreen(
            onStateToggle = { stateOn = it },
            sampleCount = 2,
        )
    }
}