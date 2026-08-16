package com.kangrio.byd.assistant.ui.onboarding

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kangrio.byd.assistant.service.VoiceWakeService
import com.kangrio.byd.assistant.ui.composable.AppIcon
import com.kangrio.byd.assistant.util.Preferences
import com.kangrio.byd.assistant.util.Utils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/** Data model representing a permission item in onboarding */
data class OnboardingPermissionItem(
    val id: String,
    val title: String,
    val category: String,
    val description: String,
    val detailedReason: String,
    val icon: ImageVector,
    val isGranted: Boolean,
    val isRequired: Boolean = true,
    val manualAdbCommand: String? = null,
    val actionText: String,
    val onAction: () -> Unit
)

enum class OnboardingTab {
    WIZARD,
    CHECKLIST
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionOnboardingScreen(
    onOnboardingFinished: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var selectedTab by remember { mutableStateOf(OnboardingTab.WIZARD) }
    var currentStepIndex by remember { mutableIntStateOf(0) }
    var isAdbProcessing by remember { mutableStateOf(false) }
    var adbMessage by remember { mutableStateOf("") }
    var showAssistantDialog by remember { mutableStateOf(false) }

    // Live permission states
    var isMicGranted by remember { mutableStateOf(Utils.isGranted(context, Manifest.permission.RECORD_AUDIO)) }
    var isOverlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isAutoStartGranted by remember { mutableStateOf(Utils.isGrantedAutoStart(context)) }
    var isWriteSecureSettingsGranted by remember { mutableStateOf(Utils.isGranted(context, Manifest.permission.WRITE_SECURE_SETTINGS)) }
    var isNotificationGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Utils.isGranted(context, Manifest.permission.POST_NOTIFICATIONS)
            } else true
        )
    }
    var isAssistantConfigured by remember {
        mutableStateOf(Utils.isEnabledVoiceAssistant(context) && Preferences.assistantPackageComponent.isNotEmpty())
    }
    var assistantApps by remember { mutableStateOf(Utils.listAssistantPackages(context)) }
    var selectedAssistant by remember { mutableStateOf(Utils.getCurrentAssistantApp(context)) }

    // Launchers for permissions
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isMicGranted = granted
        if (!granted) {
            Toast.makeText(context, "Microphone permission is required for voice commands", Toast.LENGTH_SHORT).show()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isNotificationGranted = granted
    }

    // Auto-update permission states every 1 sec or on resume
    LaunchedEffect(lifecycleOwner) {
        while (true) {
            isMicGranted = Utils.isGranted(context, Manifest.permission.RECORD_AUDIO)
            isOverlayGranted = Settings.canDrawOverlays(context)
            isAutoStartGranted = Utils.isGrantedAutoStart(context)
            isWriteSecureSettingsGranted = Utils.isGranted(context, Manifest.permission.WRITE_SECURE_SETTINGS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                isNotificationGranted = Utils.isGranted(context, Manifest.permission.POST_NOTIFICATIONS)
            }
            isAssistantConfigured = Utils.isEnabledVoiceAssistant(context) && Preferences.assistantPackageComponent.isNotEmpty()
            assistantApps = Utils.listAssistantPackages(context)
            selectedAssistant = Utils.getCurrentAssistantApp(context)
            
            if (Utils.setupCompleted(context)) {
                VoiceWakeService.startService(context)
            }
            delay(1_000L.milliseconds)
        }
    }

    val permissionItems = remember(
        isMicGranted,
        isOverlayGranted,
        isAutoStartGranted,
        isWriteSecureSettingsGranted,
        isNotificationGranted,
        isAssistantConfigured,
        selectedAssistant
    ) {
        val list = mutableListOf(
            OnboardingPermissionItem(
                id = "mic",
                title = "Microphone Access",
                category = "Core Voice Detection",
                description = "Allows Assistant to capture wake word detection and process speech commands.",
                detailedReason = "Without microphone access, hotword detection ('Hey Rio') and voice command recognition cannot operate.",
                icon = Icons.Default.Mic,
                isGranted = isMicGranted,
                isRequired = true,
                actionText = if (isMicGranted) "Granted" else "Grant Microphone Access",
                onAction = {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            ),
            OnboardingPermissionItem(
                id = "overlay",
                title = "Display Over Other Apps",
                category = "System UI Floating Overlay",
                description = "Allows the Assistant to open the Voice Assistant over other apps.",
                detailedReason = "This permission is required for the Assistant to launch the Voice Assistant while you are using other apps.",
                icon = Icons.Default.Layers,
                isGranted = isOverlayGranted,
                isRequired = true,
                actionText = if (isOverlayGranted) "Granted" else "Open Overlay Settings",
                onAction = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            "package:${context.packageName}".toUri()
                        ).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    )
                }
            ),
            OnboardingPermissionItem(
                id = "autostart",
                title = "Auto-Start & Background Execution",
                category = "Background Service",
                description = "Keeps the background listener active on startup without OS battery optimization kills.",
                detailedReason = "Ensures voice detection service automatically starts when device reboots or remains in standby.",
                icon = Icons.Default.BatteryChargingFull,
                isGranted = isAutoStartGranted,
                isRequired = true,
                actionText = if (isAutoStartGranted) "Configured" else "Allow Auto-Start",
                onAction = {
                    scope.launch {
                        Utils.openAutoStartSettings(context)
                        delay(2000.milliseconds)
                        Utils.markAutoStartTime(context)
                    }
                }
            ),
            OnboardingPermissionItem(
                id = "securesettings",
                title = "Write Secure Settings",
                category = "System Voice Assistant Key",
                description = "Registers Assistant as your system-level default voice interaction service.",
                detailedReason = "Android requires WRITE_SECURE_SETTINGS permission to set system voice keys. Grantable automatically via local ADB socket or command.",
                icon = Icons.Default.Security,
                isGranted = isWriteSecureSettingsGranted,
                isRequired = true,
                manualAdbCommand = "adb shell pm grant ${context.packageName} ${Manifest.permission.WRITE_SECURE_SETTINGS}",
                actionText = if (isWriteSecureSettingsGranted) "Permission Granted" else "Grant via Local ADB",
                onAction = {
                    scope.launch {
                        isAdbProcessing = true
                        adbMessage = "Connecting to device via local ADB socket to grant permission..."
                        Utils.adbRequestPermission(context, Manifest.permission.WRITE_SECURE_SETTINGS)
                        delay(1200)
                        isAdbProcessing = false
                        isWriteSecureSettingsGranted = Utils.isGranted(context, Manifest.permission.WRITE_SECURE_SETTINGS)
                    }
                }
            ),
            OnboardingPermissionItem(
                id = "assistant_app",
                title = "Target AI Assistant App",
                category = "Voice Engine Selection",
                description = "Select which AI voice application to trigger when Assistant is invoked.",
                detailedReason = "Choose between Google, ChatGPT, Perplexity, Claude or other installed voice engines.",
                icon = Icons.Default.SmartToy,
                isGranted = isAssistantConfigured,
                isRequired = true,
                actionText = if (selectedAssistant.name.isNotEmpty()) "Selected: ${selectedAssistant.name}" else "Select Assistant App",
                onAction = {
                    if (assistantApps.isNotEmpty()) {
                        if (!isWriteSecureSettingsGranted) {
                            Toast.makeText(context, "Write Secure Settings permission is required", Toast.LENGTH_SHORT).show()
                            return@OnboardingPermissionItem
                        }
                        showAssistantDialog = true
                    } else {
                        Utils.openStore(context)
                    }
                }
            )
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(
                0,
                OnboardingPermissionItem(
                    id = "notification",
                    title = "Post Notifications",
                    category = "Service Status Notification",
                    description = "Displays the background voice detection active service icon.",
                    detailedReason = "Android 13+ requires notification permission for foreground services with microphone access.",
                    icon = Icons.Default.Notifications,
                    isGranted = isNotificationGranted,
                    isRequired = false,
                    actionText = if (isNotificationGranted) "Granted" else "Allow Notifications",
                    onAction = {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                )
            )
        }

        list
    }

    val totalPermissions = permissionItems.size
    val grantedCount = permissionItems.count { it.isGranted }
    val progress = if (totalPermissions > 0) grantedCount.toFloat() / totalPermissions.toFloat() else 0f
    val isAllGranted = grantedCount == totalPermissions

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Permission Onboarding",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$grantedCount of $totalPermissions Granted",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isMicGranted = Utils.isGranted(context, Manifest.permission.RECORD_AUDIO)
                        isOverlayGranted = Settings.canDrawOverlays(context)
                        isAutoStartGranted = Utils.isGrantedAutoStart(context)
                        isWriteSecureSettingsGranted = Utils.isGranted(context, Manifest.permission.WRITE_SECURE_SETTINGS)
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh status")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Overall Progress Bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = if (isAllGranted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )

            // Tab Selector (Step Wizard vs Full Checklist)
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == OnboardingTab.WIZARD,
                    onClick = { selectedTab = OnboardingTab.WIZARD },
                    text = { Text("Step-by-Step") }
                )
                Tab(
                    selected = selectedTab == OnboardingTab.CHECKLIST,
                    onClick = { selectedTab = OnboardingTab.CHECKLIST },
                    text = { Text("All Permissions") }
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Crossfade(targetState = selectedTab, label = "tabTransition") { tab ->
                    when (tab) {
                        OnboardingTab.WIZARD -> {
                            WizardView(
                                items = permissionItems,
                                currentStepIndex = currentStepIndex,
                                onStepChange = { currentStepIndex = it },
                                isAllGranted = isAllGranted,
                                onFinish = {
                                    if (Utils.setupCompleted(context)) {
                                        VoiceWakeService.startService(context)
                                    }
                                    onOnboardingFinished()
                                }
                            )
                        }
                        OnboardingTab.CHECKLIST -> {
                            ChecklistView(
                                items = permissionItems,
                                isAllGranted = isAllGranted,
                                onFinish = {
                                    if (Utils.setupCompleted(context)) {
                                        VoiceWakeService.startService(context)
                                    }
                                    onOnboardingFinished()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // ADB Processing Modal
    if (isAdbProcessing) {
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
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = adbMessage,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        )
    }

    // Assistant App Select Dialog
    if (showAssistantDialog) {
        AlertDialog(
            onDismissRequest = { showAssistantDialog = false },
            title = { Text("Select Target Assistant") },
            text = {
                Column {
                    assistantApps.forEach { assistant ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    Preferences.assistantPackageComponent = assistant.componentName
                                    Utils.enableVoiceAssistant(context, assistant.componentName)
                                    isAssistantConfigured = Utils.isEnabledVoiceAssistant(context)
                                    showAssistantDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcon(
                                packageName = assistant.packageName,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = assistant.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
fun WizardView(
    items: List<OnboardingPermissionItem>,
    currentStepIndex: Int,
    onStepChange: (Int) -> Unit,
    isAllGranted: Boolean,
    onFinish: () -> Unit
) {
    if (items.isEmpty()) return

    val validIndex = currentStepIndex.coerceIn(0, items.size - 1)
    val item = items[validIndex]
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Step Badge
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text(
                    text = "Step ${validIndex + 1} of ${items.size}",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }

            // Big Icon Banner
            Surface(
                color = if (item.isGranted) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = CircleShape,
                modifier = Modifier
                    .size(96.dp)
                    .padding(8.dp),
                border = BorderStroke(
                    2.dp,
                    if (item.isGranted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (item.isGranted) Icons.Default.CheckCircle else item.icon,
                        contentDescription = item.title,
                        tint = if (item.isGranted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Category & Title
            Text(
                text = item.category.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = item.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )

            // Status Chip
            StatusChip(isGranted = item.isGranted, isRequired = item.isRequired)

            Spacer(modifier = Modifier.height(20.dp))

            // Description Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = item.detailedReason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (item.manualAdbCommand != null && !item.isGranted) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Manual ADB Command:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = item.manualAdbCommand,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("ADB Command", item.manualAdbCommand)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied ADB command to clipboard", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy command",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Button
            Button(
                onClick = item.onAction,
                enabled = !item.isGranted || item.id == "assistant_app",
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (item.isGranted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (item.isGranted) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = item.actionText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Navigation Footer
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { onStepChange(validIndex - 1) },
                enabled = validIndex > 0
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Back")
            }

            if (validIndex < items.size - 1) {
                Button(
                    onClick = { onStepChange(validIndex + 1) }
                ) {
                    Text("Next")
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next")
                }
            } else {
                Button(
                    enabled = isAllGranted,
                    onClick = onFinish,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAllGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("Launch")
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.PlayArrow, contentDescription = "Launch")
                }
            }
        }
    }
}

@Composable
fun ChecklistView(
    items: List<OnboardingPermissionItem>,
    isAllGranted: Boolean,
    onFinish: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            items.forEach { item ->
                PermissionCard(item = item)
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            enabled = isAllGranted,
            onClick = onFinish,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isAllGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (isAllGranted) Icons.Default.CheckCircle else Icons.Default.PlayArrow,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Launch",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun PermissionCard(item: OnboardingPermissionItem) {
    val context = LocalContext.current
    val borderColor = if (item.isGranted) Color(0xFF81C784) else MaterialTheme.colorScheme.outlineVariant

    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = if (item.isGranted) Color(0xFFC8E6C9) else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = if (item.isGranted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = item.category,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                StatusChip(isGranted = item.isGranted, isRequired = item.isRequired)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = item.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (item.manualAdbCommand != null && !item.isGranted) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = item.manualAdbCommand,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("ADB Command", item.manualAdbCommand)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied ADB command", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy command",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            FilledTonalButton(
                onClick = item.onAction,
                enabled = !item.isGranted || item.id == "assistant_app",
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = item.actionText)
            }
        }
    }
}

@Composable
fun StatusChip(isGranted: Boolean, isRequired: Boolean) {
    val backgroundColor = when {
        isGranted -> Color(0xFFE8F5E9)
        isRequired -> Color(0xFFFFEBEE)
        else -> Color(0xFFFFF8E1)
    }
    val textColor = when {
        isGranted -> Color(0xFF2E7D32)
        isRequired -> Color(0xFFC62828)
        else -> Color(0xFFF57F17)
    }
    val text = when {
        isGranted -> "Granted"
        isRequired -> "Required"
        else -> "Optional"
    }

    Surface(
        color = backgroundColor,
        shape = CircleShape
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Warning,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
