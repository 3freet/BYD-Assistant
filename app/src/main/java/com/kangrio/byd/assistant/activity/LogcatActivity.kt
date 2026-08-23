package com.kangrio.byd.assistant.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kangrio.byd.assistant.ui.theme.AssistantTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Maximum number of log lines kept in memory. */
private const val MAX_LINES = 100

/** Logcat priority levels used for filtering. */
enum class LogLevel(val tag: String, val shortTag: Char) {
    VERBOSE("V", 'V'),
    DEBUG("D", 'D'),
    INFO("I", 'I'),
    WARNING("W", 'W'),
    ERROR("E", 'E'),
}

/** A single parsed logcat line. */
data class LogLine(val raw: String, val level: LogLevel?)

private fun parseLevel(line: String): LogLevel? {
    return try {
        val parts = line.trim().split(Regex("\\s+"))
        val levelChar = parts.getOrNull(4)?.firstOrNull()
        LogLevel.entries.firstOrNull { it.shortTag == levelChar }
    } catch (_: Exception) {
        null
    }
}

class LogcatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AssistantTheme {
                Scaffold(
                    topBar = { LogcatTopBar(onBack = { finish() }) }
                ) { paddingValues ->
                    LogcatScreen(modifier = Modifier.padding(paddingValues))
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogcatTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = { Text("Logcat") },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
    )
}

@Composable
fun LogcatScreen(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val lines = remember { mutableStateListOf<LogLine>() }
    val listState = rememberLazyListState()
    var autoScroll by remember { mutableStateOf(true) }
    var selectedLevel by remember { mutableStateOf<LogLevel?>(null) }
    var process: Process? by remember { mutableStateOf(null) }

    DisposableEffect(Unit) {
        val job = scope.launch(Dispatchers.IO) {
            try {
                val proc = Runtime.getRuntime().exec(
                    arrayOf("logcat", "-T", "1", "-v", "threadtime")
                )
                process = proc

                val batch = ArrayList<LogLine>(14)
                var lastFlush = System.currentTimeMillis()

                suspend fun flush() {
                    if (batch.isEmpty()) return

                    withContext(Dispatchers.Main) {
                        lines.addAll(batch)
                        batch.clear()
                        if (lines.size > MAX_LINES) {
                            lines.removeRange(0, lines.size - MAX_LINES)
                        }
                    }
                }

                proc.inputStream.bufferedReader().use { reader ->
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        val parsed = LogLine(raw = line, level = parseLevel(line))
                        batch.add(parsed)
                        val now = System.currentTimeMillis()
                        if (batch.size > 10 || now - lastFlush > 100) {
                            flush()
                            lastFlush = now
                        }
                    }
                    flush()
                }
            } catch (_: Exception) { /* process killed on dispose */ }
        }

        onDispose {
            job.cancel()
            process?.destroy()
        }
    }

    LaunchedEffect(lines.size, autoScroll) {
        if (autoScroll && lines.isNotEmpty()) {
            listState.scrollToItem(lines.size - 1)
        }
    }

    val filtered = if (selectedLevel == null) lines
    else lines.filter { it.level == selectedLevel || it.level == null }

    Column(modifier = modifier.fillMaxSize()) {
        // Filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = selectedLevel == null,
                onClick = { selectedLevel = null },
                label = { Text("All") }
            )
            LogLevel.entries.forEach { level ->
                FilterChip(
                    selected = selectedLevel == level,
                    onClick = { selectedLevel = if (selectedLevel == level) null else level },
                    label = { Text(level.name) }
                )
            }
        }

        // Log list
        Box(modifier = Modifier.weight(1f)) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    items(filtered) { logLine ->
                        Text(
                            text = logLine.raw,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = levelColor(logLine.level),
                        )
                    }
                }
            }

            // Scroll-to-bottom button
            if (!autoScroll) {
                IconButton(
                    onClick = {
                        autoScroll = true
                        scope.launch { if (lines.isNotEmpty()) listState.scrollToItem(lines.size - 1) }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(50)
                        )
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Scroll to bottom",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Bottom bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${filtered.size} lines",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { lines.clear() }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear logs")
                }
                FilterChip(
                    selected = autoScroll,
                    onClick = { autoScroll = !autoScroll },
                    label = { Text("Auto-scroll") }
                )
            }
        }
    }
}

@Composable
private fun levelColor(level: LogLevel?) = when (level) {
    LogLevel.ERROR   -> MaterialTheme.colorScheme.error
    LogLevel.WARNING -> MaterialTheme.colorScheme.tertiary
    LogLevel.INFO    -> MaterialTheme.colorScheme.primary
    LogLevel.DEBUG   -> MaterialTheme.colorScheme.onSurfaceVariant
    LogLevel.VERBOSE -> MaterialTheme.colorScheme.outline
    null             -> MaterialTheme.colorScheme.onSurface
}
