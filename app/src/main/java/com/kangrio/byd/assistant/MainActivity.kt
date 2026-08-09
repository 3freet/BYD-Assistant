package com.kangrio.byd.assistant

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import com.kangrio.byd.assistant.ui.theme.AssistantTheme
import com.kangrio.byd.assistant.util.PermissionUtil
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

    var processing by remember { mutableStateOf(Processing(false, "")) }

    var isGranted by remember {
        mutableStateOf(
            PermissionUtil.isGranted(
                context,
                Manifest.permission.WRITE_SECURE_SETTINGS
            )
        )
    }

    var isEnabledVoiceAssistant by remember {
        mutableStateOf(
            if (isGranted) {
                PermissionUtil.isEnableVoiceAssistant(context)
            } else {
                false
            }
        )
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
        Box {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(
                        state = rememberScrollState()
                    )
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // title
                Text(
                    text = "Assistant",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier
                        .padding(top = 16.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedRow(
                    modifier = Modifier
                        .fillMaxWidth(),
                    enabled = !isGranted
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Permission",
                        )
                        TextButton(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(16.dp)
                                ),
                            onClick = {
                                scope.launch {
                                    processing = Processing(
                                        true,
                                        "Granting permission...\n${Manifest.permission.WRITE_SECURE_SETTINGS}"
                                    )
                                    PermissionUtil.adbRequestPermission(
                                        context,
                                        Manifest.permission.WRITE_SECURE_SETTINGS
                                    )
                                    delay(1000)
                                    processing = Processing(false, "")
                                    isGranted = PermissionUtil.isGranted(
                                        context,
                                        Manifest.permission.WRITE_SECURE_SETTINGS
                                    )
                                }
                            }
                        ) {
                            Text(
                                text = "Next",
                            )
                        }
                    }
                }

                OutlinedRow(
                    modifier = Modifier
                        .fillMaxWidth(),
                    enabled = !isEnabledVoiceAssistant && isGranted
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Enable voice assistant",
                        )
                        TextButton(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(16.dp)
                                ),
                            onClick = {
                                scope.launch {
                                    processing = Processing(true, "Enabling voice assistant...")
                                    PermissionUtil.enableVoiceAssistant(context)
                                    isEnabledVoiceAssistant = true
                                    delay(1000)
                                    processing = Processing(false, "")
                                }
                            }
                        ) {
                            Text(
                                text = "Next",
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .alpha(if (isEnabledVoiceAssistant) 1f else 0f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(16.dp)
                            ),
                        onClick = {
                            val intent = Intent(Intent.ACTION_VOICE_COMMAND)
                            context.startActivity(intent)
                        }
                    ) {
                        Text(
                            text = "Test",
                        )
                    }
                }
            }
        }
        if (processing.processing) {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth(0.8f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = processing.message,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            CircularProgressIndicator()
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun OutlinedRow(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline
        )
    ) {
        if (enabled) {
            content()
        }
    }
}