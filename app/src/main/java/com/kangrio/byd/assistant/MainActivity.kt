package com.kangrio.byd.assistant

import android.Manifest
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kangrio.byd.assistant.service.VoiceWakeService
import com.kangrio.byd.assistant.ui.composable.AppIcon
import com.kangrio.byd.assistant.ui.theme.AssistantTheme
import com.kangrio.byd.assistant.util.Preferences
import com.kangrio.byd.assistant.util.Utils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AssistantTheme {
                AssistantApp()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (Utils.setupCompleted(this)) {
            finishAffinity()
        }
    }
}

@PreviewScreenSizes
@Composable
fun AssistantApp() {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Home(modifier = Modifier.padding(innerPadding))
    }
}

data class Processing(
    val processing: Boolean,
    val message: String
)

@Composable
fun Home(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var processing by remember { mutableStateOf(Processing(false, "")) }

    var showAssistantDialog by remember { mutableStateOf(false) }

    var assistantApps by remember {
        mutableStateOf(Utils.listAssistantPackages(context))
    }

    var isGrantedWriteSecureSettings by remember {
        mutableStateOf(
            Utils.isGranted(
                context,
                Manifest.permission.WRITE_SECURE_SETTINGS
            )
        )
    }

    var isEnabledVoiceAssistant by remember {
        mutableStateOf(
            Utils.isEnabledVoiceAssistant(context)
        )
    }

    var isAutoStart by remember {
        mutableStateOf(
            Utils.isGrantedAutoStart(context)
        )
    }

    // check every 1 second
    LaunchedEffect(lifecycleOwner) {
        while (true) {
            isAutoStart = Utils.isGrantedAutoStart(context)
            assistantApps = Utils.listAssistantPackages(context)
            isGrantedWriteSecureSettings = Utils.isGranted(context, Manifest.permission.WRITE_SECURE_SETTINGS)
            isEnabledVoiceAssistant = Utils.isEnabledVoiceAssistant(context)
            if (listOf(isAutoStart, assistantApps.isNotEmpty(), isGrantedWriteSecureSettings, isEnabledVoiceAssistant).all { it }) {
                VoiceWakeService.startService(context)
            }
            delay(1_000L.milliseconds)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 2.dp,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title & Description Header
                Text(
                    text = "Assistant Setup",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Text(
                    text = "Follow each detailed step below to configure and test Assistant as your primary voice assistant.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                )

                // Step 1: Auto Start Check
                StepCard(
                    stepNumber = 1,
                    icon = Icons.Default.RestartAlt,
                    title = "Auto Start",
                    statusText = "Check is allowed auto start",
                    isCompleted = isAutoStart,
                    isActive = !isAutoStart,
                    description = "Allow auto start when device is rebooted.",
                    detailText = "Assistant needs to be re-enabled after every reboot.",
                    buttonText = "Allow",
                    onButtonClick = {
                        Utils.markAutoStartTime(context)
                        Utils.openAutoStartSettings(context)
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Step 2: Assistant App Check
                StepCard(
                    stepNumber = 2,
                    icon = Icons.Default.Android,
                    title = "Select Assistant App",
                    statusText = if (assistantApps.isNotEmpty()) Utils.getCurrentAssistantApp(context).name else "App Missing",
                    isCompleted = Preferences.assistantPackageComponent.isNotEmpty(),
                    isActive = Preferences.assistantPackageComponent.isEmpty(),
                    description = "Choose the AI assistant you want to use",
                    detailText = "Select an installed assistant that supports VoiceInteractionService.\nYour selected assistant will be used when triggered.",
                    buttonText = if (assistantApps.isEmpty()) "Install Assistant" else "Select Assistant",
                    onButtonClick = {
                        if (assistantApps.isNotEmpty()) {
                            showAssistantDialog = true
                        } else {
                            Utils.openStore(context)
                        }
                    }
                )

                if (showAssistantDialog) {
                    AlertDialog(
                        onDismissRequest = { showAssistantDialog = false },
                        title = {
                            Text("Select Assistant App")
                        },
                        text = {
                            Column {
                                assistantApps.forEach { assistant ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                Preferences.assistantPackageComponent =
                                                    "${assistant.packageName}/${assistant.className}"

                                                showAssistantDialog = false
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

                Spacer(modifier = Modifier.height(16.dp))

                // Step 3: System Permission
                StepCard(
                    stepNumber = 3,
                    icon = Icons.Default.Key,
                    title = "Grant Write Secure Settings Permission",
                    statusText = if (isGrantedWriteSecureSettings) "Permission Granted" else if (assistantApps.isNotEmpty()) "Action Required" else "Blocked by Step 1",
                    isCompleted = isGrantedWriteSecureSettings,
                    isActive = assistantApps.isNotEmpty() && !isGrantedWriteSecureSettings,
                    description = "Required to write system-level assistant configuration keys into Android Secure Settings.",
                    detailText = "Permission Required:\nandroid.permission.WRITE_SECURE_SETTINGS\n\nAutomatic Method:\nUses dadb to connect locally on device over ADB socket and execute pm grant.",
                    manualAdbCommand = "adb shell pm grant ${context.packageName} ${Manifest.permission.WRITE_SECURE_SETTINGS}",
                    buttonText = if (isGrantedWriteSecureSettings) "Granted" else "Grant via ADB",
                    onButtonClick = {
                        scope.launch {
                            processing = Processing(
                                true,
                                "Connecting via ADB to grant permission:\n${Manifest.permission.WRITE_SECURE_SETTINGS}"
                            )
                            Utils.adbRequestPermission(
                                context,
                                Manifest.permission.WRITE_SECURE_SETTINGS
                            )
                            delay(1200)
                            processing = Processing(false, "")
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Step 4: Enable Voice Assistant
                StepCard(
                    stepNumber = 4,
                    icon = Icons.Default.Settings,
                    title = "Configure Voice Assistant Service",
                    statusText = if (isEnabledVoiceAssistant) "Service Enabled" else if (isGrantedWriteSecureSettings) "Ready to Enable" else "Blocked by Step 2",
                    isCompleted = isEnabledVoiceAssistant,
                    isActive = isGrantedWriteSecureSettings && !isEnabledVoiceAssistant,
                    description = "Configures Assistant (GsaVoiceInteractionService) as your active default voice interaction service.",
                    detailText = "Target Component:\n- ${Preferences.assistantPackageComponent}\n\nSettings Updated:\n- Settings.Secure.assistant\n- Settings.Secure.voice_interaction_service",
                    buttonText = if (isEnabledVoiceAssistant) "Enabled" else "Enable Assistant",
                    onButtonClick = {
                        scope.launch {
                            processing = Processing(
                                true,
                                "Writing voice interaction settings for Assistant..."
                            )
                            Utils.enableVoiceAssistant(context, Preferences.assistantPackageComponent)
                            delay(1000)
                            processing = Processing(false, "")
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Step 5: Test Shortcut
                StepCard(
                    stepNumber = 5,
                    icon = Icons.Default.PlayArrow,
                    title = "Test Voice Assistant Launcher",
                    statusText = if (isEnabledVoiceAssistant) "Ready to Test" else "Blocked by Step 4",
                    isCompleted = false,
                    isActive = isEnabledVoiceAssistant,
                    description = "Launches the voice command intent to verify Assistant opens properly.",
                    detailText = "Intent Action:\nandroid.intent.action.ASSIST\n\nBehavior:\nLaunching this application in the future will automatically trigger Assistant.",
                    buttonText = "Launch Test",
                    onButtonClick = {
                        Utils.startVoiceAssistant(context)
                    }
                )
            }
        }

        if (processing.processing) {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                text = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = processing.message,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun StepCard(
    stepNumber: Int,
    icon: ImageVector,
    title: String,
    statusText: String,
    isCompleted: Boolean,
    isActive: Boolean,
    description: String,
    detailText: String? = null,
    manualAdbCommand: String? = null,
    buttonText: String,
    onButtonClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val borderColor = when {
        isCompleted -> MaterialTheme.colorScheme.primary
        isActive -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    }

    val containerColor = when {
        isCompleted -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
        isActive -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Step Header: Badge + Title + Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (isCompleted) MaterialTheme.colorScheme.primary
                                else if (isActive) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.outlineVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCompleted) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Completed",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Text(
                                text = "$stepNumber",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isActive) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isActive || isCompleted) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        isCompleted -> MaterialTheme.colorScheme.primaryContainer
                        isActive -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = when {
                            isCompleted -> MaterialTheme.colorScheme.onPrimaryContainer
                            isActive -> MaterialTheme.colorScheme.onSecondaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Step Description
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isActive || isCompleted) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )

            // Technical Details Block
            if (detailText != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Technical Details",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Technical Details",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = detailText,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Manual ADB Command Box
            if (!isCompleted && manualAdbCommand != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = "Manual ADB",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Manual ADB Command Alternative",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = manualAdbCommand,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText(
                                    "ADB Command",
                                    manualAdbCommand
                                )
                                clipboard.setPrimaryClip(clip)
                            },
                            enabled = true,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Copy Command")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (isCompleted) {
                    OutlinedButton(
                        onClick = {},
                        enabled = false,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = buttonText)
                    }
                } else {
                    Button(
                        onClick = onButtonClick,
                        enabled = isActive,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = buttonText)
                    }
                }
            }
        }
    }
}