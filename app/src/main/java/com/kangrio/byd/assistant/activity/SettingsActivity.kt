package com.kangrio.byd.assistant.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import com.kangrio.byd.assistant.service.VoiceWakeService
import com.kangrio.byd.assistant.ui.composable.AppIcon
import com.kangrio.byd.assistant.ui.theme.AssistantTheme
import com.kangrio.byd.assistant.util.Preferences
import com.kangrio.byd.assistant.util.Utils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.time.Duration.Companion.milliseconds


class SettingsActivity : ComponentActivity() {

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                val otaRelease by initialOtaRelease

                Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
                    SettingsScreen(
                        modifier = Modifier.padding(paddingValues),
                        initialOtaRelease = otaRelease,
                        onStateToggle = { state -> onStateToggle(state) },
                        onPlayDingToggle = { state -> Preferences.playDingOnStart = state },
                        onSensitivityChange = { VoiceWakeService.setSensitivity(this, it) },
                        onModelChanged = { model -> VoiceWakeService.setModel(this, model) },
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
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
    onModelChanged: (String) -> Unit = { _-> },
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

    var expandedSensitivity by remember { mutableStateOf(false) }
    var selectedSensitivityLevel by remember { mutableStateOf(SENSITIVITY_OPTIONS.firstOrNull { (_, s) -> s == Preferences.hotwordSensitivity }?.first ?: "" ) }

    var isStateOn by remember { mutableStateOf(Preferences.startHotword) }
    var isPlayDing by remember { mutableStateOf(Preferences.playDingOnStart) }

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<ReleaseInfo?>(initialOtaRelease) }
    var showUpdateDialog by remember { mutableStateOf(initialOtaRelease != null) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }

    val models = remember(expandedModels) {
        File(context.filesDir, "openwakeword/models")
            .listFiles()
            ?.filter { file -> file.isFile && file.name.endsWith(".onnx") }
            ?.map { file -> file.name.removeSuffix(".onnx") }
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
                    label = "Voice Detection (Experimental)",
                    description = "Enable wake-word detection. False triggers may occur."
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
                    label = "Wake Word",
                    description = "Select the model used to detect your wake word."
                ) {
                    Box {
                        FilledTonalButton(
                            onClick = { expandedModels = !expandedModels }
                        ) {
                            Text(selectedModel)
                        }

                        DropdownMenu(
                            expanded = expandedModels,
                            onDismissRequest = { expandedModels = false }
                        ) {
                            models.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model) },
                                    onClick = {
                                        onModelChanged(model)
                                        selectedModel = model
                                        expandedModels = false
                                    }
                                )
                            }
                        }
                    }
                }

                SettingRow(
                    label = "Detection Sensitivity",
                    description = "Adjust how easily the wake word is detected."
                ) {
                    Box {
                        FilledTonalButton(
                            onClick = { expandedSensitivity = !expandedSensitivity }
                        ) {
                            Text(selectedSensitivityLevel)
                        }

                        DropdownMenu(
                            expanded = expandedSensitivity,
                            onDismissRequest = { expandedSensitivity = false }
                        ) {
                            SENSITIVITY_OPTIONS.forEach { (level, sensitivity) ->
                                DropdownMenuItem(
                                    text = { Text(level) },
                                    onClick = {
                                        onSensitivityChange(sensitivity)
                                        selectedSensitivityLevel = level
                                        expandedSensitivity = false
                                    }
                                )
                            }
                        }
                    }
                }

                SettingRow(
                    label = "Debug Logcat",
                    description = "View live system log output."
                ) {
                    FilledTonalButton(
                        onClick = {
                            context.startActivity(Intent(context, LogcatActivity::class.java))
                        }
                    ) {
                        Text("Open")
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

private val SENSITIVITY_OPTIONS: List<Pair<String, Float>> = listOf(
    "Easiest"   to 0.05f,
    "Easy"      to 0.2f,
    "Balanced"  to 0.5f,
    "Strict"    to 0.7f
)

@Preview(showBackground = true, widthDp = 480, heightDp = 480)
@Composable
private fun SettingsScreenPreview() {
    var stateOn by remember { mutableStateOf(true) }

    MaterialTheme {
        SettingsScreen(
            onStateToggle = { stateOn = it }
        )
    }
}