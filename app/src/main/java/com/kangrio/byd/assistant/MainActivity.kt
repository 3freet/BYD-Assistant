package com.kangrio.byd.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kangrio.byd.assistant.ui.theme.AssistantTheme
import com.kangrio.byd.assistant.util.Utils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
        finishAffinity()
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

    var isGoogleAppInstalled by remember {
        mutableStateOf(Utils.isGoogleAppInstalled(context))
    }

    var isGranted by remember {
        mutableStateOf(
            Utils.isGranted(
                context,
                Manifest.permission.WRITE_SECURE_SETTINGS
            )
        )
    }

    var isEnabledVoiceAssistant by remember {
        mutableStateOf(
            Utils.isEnableVoiceAssistant(context)
        )
    }

    // Refresh states automatically when coming back to the screen
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isGoogleAppInstalled = Utils.isGoogleAppInstalled(context)
                isGranted = Utils.isGranted(
                    context,
                    Manifest.permission.WRITE_SECURE_SETTINGS
                )
                if (isGranted) {
                    isEnabledVoiceAssistant = Utils.isEnableVoiceAssistant(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
                    text = "Follow each detailed step below to configure and test Google Assistant as your primary voice assistant.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                )

                // Step 1: Google App Check
                StepCard(
                    stepNumber = 1,
                    icon = Icons.Default.Android,
                    title = "Google App Installation",
                    statusText = if (isGoogleAppInstalled) "App Installed" else "App Missing",
                    isCompleted = isGoogleAppInstalled,
                    isActive = !isGoogleAppInstalled,
                    description = "Verifies if the Google App is installed on this device to handle voice command intents.",
                    detailText = "Package Name:\ncom.google.android.googlequicksearchbox\n\nStatus:\n${if (isGoogleAppInstalled) "Installed and ready" else "Not installed. Install via Store below."}",
                    buttonText = if (isGoogleAppInstalled) "Installed" else "Install Google App",
                    onButtonClick = {
                        Utils.openGoogleAppInStore(context)
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Step 2: System Permission
                StepCard(
                    stepNumber = 2,
                    icon = Icons.Default.Key,
                    title = "Grant Write Secure Settings Permission",
                    statusText = if (isGranted) "Permission Granted" else if (isGoogleAppInstalled) "Action Required" else "Blocked by Step 1",
                    isCompleted = isGranted,
                    isActive = isGoogleAppInstalled && !isGranted,
                    description = "Required to write system-level assistant configuration keys into Android Secure Settings.",
                    detailText = "Permission Required:\nandroid.permission.WRITE_SECURE_SETTINGS\n\nAutomatic Method:\nUses dadb to connect locally on device over ADB socket and execute pm grant.",
                    manualAdbCommand = "adb shell pm grant ${context.packageName} ${Manifest.permission.WRITE_SECURE_SETTINGS}",
                    buttonText = if (isGranted) "Granted" else "Grant via ADB",
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
                            isGranted = Utils.isGranted(
                                context,
                                Manifest.permission.WRITE_SECURE_SETTINGS
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Step 3: Enable Voice Assistant
                StepCard(
                    stepNumber = 3,
                    icon = Icons.Default.Settings,
                    title = "Configure Voice Assistant Service",
                    statusText = if (isEnabledVoiceAssistant) "Service Enabled" else if (isGranted) "Ready to Enable" else "Blocked by Step 2",
                    isCompleted = isEnabledVoiceAssistant,
                    isActive = isGranted && !isEnabledVoiceAssistant,
                    description = "Configures Google Assistant (GsaVoiceInteractionService) as your active default voice interaction service.",
                    detailText = "Target Component:\ncom.google.android.googlequicksearchbox/\ncom.google.android.voiceinteraction.GsaVoiceInteractionService\n\nSettings Updated:\n- Settings.Secure.assistant\n- Settings.Secure.voice_interaction_service",
                    buttonText = if (isEnabledVoiceAssistant) "Enabled" else "Enable Assistant",
                    onButtonClick = {
                        scope.launch {
                            processing = Processing(
                                true,
                                "Writing voice interaction settings for Google Assistant..."
                            )
                            Utils.enableVoiceAssistant(context)
                            delay(1000)
                            isEnabledVoiceAssistant = Utils.isEnableVoiceAssistant(context)
                            processing = Processing(false, "")
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Step 4: Test Shortcut
                StepCard(
                    stepNumber = 4,
                    icon = Icons.Default.PlayArrow,
                    title = "Test Voice Assistant Launcher",
                    statusText = if (isEnabledVoiceAssistant) "Ready to Test" else "Blocked by Step 3",
                    isCompleted = false,
                    isActive = isEnabledVoiceAssistant,
                    description = "Launches the voice command intent to verify Google Assistant opens properly.",
                    detailText = "Intent Action:\nandroid.intent.action.VOICE_COMMAND\n\nBehavior:\nLaunching this application in the future will automatically trigger Google Assistant.",
                    buttonText = "Launch Test",
                    onButtonClick = {
                        val intent = Intent(Intent.ACTION_VOICE_COMMAND)
                        context.startActivity(intent)
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