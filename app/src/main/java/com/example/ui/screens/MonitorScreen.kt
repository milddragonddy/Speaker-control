package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.NoiseLog
import com.example.data.model.QuietZonePreset
import com.example.data.model.UiState
import com.example.data.preferences.QuietPreferences
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.CalmGreen
import com.example.ui.theme.CautionAmber
import com.example.ui.viewmodel.LiveMonitorState
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun MonitorScreen(
    monitorState: LiveMonitorState,
    preferences: QuietPreferences,
    noiseLogsState: UiState<List<NoiseLog>>,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
    onSelectPreset: (QuietZonePreset) -> Unit,
    onThresholdChange: (Int) -> Unit,
    onToggleAutoGuard: (Boolean) -> Unit,
    onCaptureSnapshot: () -> Unit,
    onDeleteLog: (String) -> Unit,
    onReportNoiseClick: (measuredDb: Int, peakDb: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDeniedExplicitly by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            permissionDeniedExplicitly = false
            onStartMonitoring()
        } else {
            permissionDeniedExplicitly = true
        }
    }

    val statusColor by animateColorAsState(
        targetValue = when {
            !monitorState.isMonitoring -> MaterialTheme.colorScheme.primary
            monitorState.currentDb >= 70 -> AlertCrimson
            monitorState.currentDb >= preferences.thresholdDb -> CautionAmber
            else -> CalmGreen
        },
        label = "gauge_status_color"
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 720.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = preferences.zoneName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "သတ်မှတ်ကန့်သတ်ချက်: ${preferences.thresholdDb} dB",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            color = statusColor.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = when {
                                    !monitorState.isMonitoring -> "အသင့်"
                                    monitorState.currentDb >= 70 -> "အန္တရာယ်အဆင့်"
                                    monitorState.isViolationActive -> "ကန့်သတ်ချက်ကျော်"
                                    else -> "အေးချမ်း"
                                },
                                color = statusColor,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    DecibelArcGauge(
                        currentDb = monitorState.currentDb,
                        thresholdDb = preferences.thresholdDb,
                        accentColor = statusColor,
                        isMonitoring = monitorState.isMonitoring
                    )

                    WaveformVisualizer(
                        bars = monitorState.bars,
                        accentColor = statusColor
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MetricPill(label = "Avg dB", value = "${monitorState.avgDb}")
                        MetricPill(label = "Peak dB", value = "${monitorState.peakDb}")
                        MetricPill(label = "Auto-Guard", value = "${monitorState.autoGuardTriggeredCount}x")
                    }

                    if (permissionDeniedExplicitly && !hasMicPermission) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "မိုက်ခရိုဖုန်းခွင့်ပြုချက် (RECORD_AUDIO) လိုအပ်ပါသည်။ ဆူညံသံတိုင်းတာရန် ခွင့်ပြုပေးပါ။",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    if (monitorState.errorMessage != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = monitorState.errorMessage,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (monitorState.isMonitoring) {
                                    onStopMonitoring()
                                } else if (hasMicPermission) {
                                    onStartMonitoring()
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("monitor_toggle_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (monitorState.isMonitoring) AlertCrimson else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = if (monitorState.isMonitoring) Icons.Filled.MicOff else Icons.Filled.Mic,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (monitorState.isMonitoring) "ရပ်တန့်မည်" else "တိုင်းတာမှုစတင်မည်"
                            )
                        }

                        OutlinedButton(
                            onClick = onCaptureSnapshot,
                            modifier = Modifier
                                .height(50.dp)
                                .testTag("save_noise_log_button")
                        ) {
                            Icon(imageVector = Icons.Filled.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("သိမ်းမည်")
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val avg = if (monitorState.avgDb > 0) monitorState.avgDb else monitorState.currentDb
                            val peak = if (monitorState.peakDb > 0) monitorState.peakDb else monitorState.currentDb
                            onReportNoiseClick(avg, peak)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("report_from_monitor_button")
                    ) {
                        Icon(imageVector = Icons.Filled.Report, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ဤတိုင်းတာချက်ဖြင့် တိုင်ကြားစာတင်မည်")
                    }
                }
            }
        }

        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "ဆေးရုံဇုန် ကန့်သတ်ချက်နှင့် Auto-Guard",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuietZonePreset.entries.forEach { preset ->
                            FilterChip(
                                selected = preferences.thresholdDb == preset.thresholdDb,
                                onClick = { onSelectPreset(preset) },
                                label = { Text("${preset.labelMm} (${preset.thresholdDb} dB)") },
                                modifier = Modifier.testTag("preset_${preset.name.lowercase()}")
                            )
                        }
                    }

                    Text(
                        text = "အသံကန့်သတ်ချက် ချိန်ညှိရန်: ${preferences.thresholdDb} dB",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = preferences.thresholdDb.toFloat(),
                        onValueChange = { onThresholdChange(it.toInt()) },
                        valueRange = 30f..85f,
                        modifier = Modifier.testTag("threshold_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Auto-Guard အလိုအလျောက်အသံထိန်းစနစ်",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "သတ်မှတ် dB ကျော်ပါက ချိတ်ဆက်ထားသော စပီကာ/မီဒီယာအသံကို အလိုအလျောက်လျှော့ချပေးမည်။",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = preferences.autoGuardEnabled,
                            onCheckedChange = onToggleAutoGuard,
                            modifier = Modifier.testTag("auto_guard_switch")
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "ဆူညံသံ ကန့်သတ်ချက်ကျော်လွန်မှု မှတ်တမ်းများ",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        when (noiseLogsState) {
            is UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
            is UiState.Error -> {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = noiseLogsState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
            is UiState.Success -> {
                if (noiseLogsState.data.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.GraphicEq,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "မှတ်တမ်းများ မရှိသေးပါ။ တိုင်းတာမှုစတင်ပြီး သက်သေမှတ်တမ်းများ သိမ်းဆည်းနိုင်ပါသည်။",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(noiseLogsState.data, key = { it.id }) { log ->
                        NoiseLogCard(
                            log = log,
                            onDelete = { onDeleteLog(log.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DecibelArcGauge(
    currentDb: Int,
    thresholdDb: Int,
    accentColor: Color,
    isMonitoring: Boolean
) {
    val progress by animateFloatAsState(
        targetValue = if (isMonitoring) ((currentDb - 20f) / 90f).coerceIn(0.02f, 1f) else 0f,
        animationSpec = spring(stiffness = 300f),
        label = "db_arc_progress"
    )
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier.size(196.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 18.dp.toPx()
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

            drawArc(
                color = trackColor,
                startAngle = 140f,
                sweepAngle = 260f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = accentColor,
                startAngle = 140f,
                sweepAngle = 260f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isMonitoring) "$currentDb" else "--",
                style = MaterialTheme.typography.displayLarge,
                color = accentColor
            )
            Text(
                text = "dB SPL (Limit $thresholdDb)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WaveformVisualizer(
    bars: List<Float>,
    accentColor: Color
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(track.copy(alpha = 0.45f))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        val count = bars.size.coerceAtLeast(1)
        val gap = 4.dp.toPx()
        val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(2f)
        bars.forEachIndexed { index, amplitude ->
            val h = (size.height * amplitude.coerceIn(0.08f, 1f))
            val x = index * (barWidth + gap)
            val y = (size.height - h) / 2f
            drawRoundRect(
                color = accentColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
private fun MetricPill(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NoiseLogCard(
    log: NoiseLog,
    onDelete: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    val timeText = log.createdAt?.toDate()?.let { formatter.format(it) } ?: "Just now"
    val isHigh = log.peakDb >= log.thresholdDb

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isHigh) CautionAmber.copy(alpha = 0.16f) else CalmGreen.copy(alpha = 0.16f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isHigh) Icons.Filled.Warning else Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = if (isHigh) CautionAmber else CalmGreen
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${log.zoneName} • Peak ${log.peakDb} dB",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Avg ${log.avgDb} dB (Limit ${log.thresholdDb} dB) • $timeText" +
                        if (log.autoGuarded) " • Auto-Guard ချိန်ညှိပြီး" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_log_${log.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete log",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
