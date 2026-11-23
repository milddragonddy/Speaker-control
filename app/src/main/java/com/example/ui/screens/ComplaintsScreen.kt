package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ComplaintStatus
import com.example.data.model.NoiseComplaint
import com.example.data.model.NoiseSeverity
import com.example.data.model.NoiseSourceType
import com.example.data.model.UiState
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.CalmGreen
import com.example.ui.theme.CautionAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DraftComplaintPrefill(
    val measuredDb: Int = 65,
    val peakDb: Int = 78
)

@Composable
fun ComplaintsScreen(
    complaintsState: UiState<List<NoiseComplaint>>,
    defaultReporterName: String,
    defaultFacilityName: String,
    prefill: DraftComplaintPrefill?,
    onClearPrefill: () -> Unit,
    onCreateComplaint: (
        reporterName: String,
        facilityName: String,
        sourceType: NoiseSourceType,
        measuredDb: Int,
        peakDb: Int,
        description: String,
        onDone: () -> Unit
    ) -> Unit,
    onUpdateStatus: (complaintId: String, status: ComplaintStatus, responseNote: String?) -> Unit,
    onDeleteComplaint: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf<ComplaintStatus?>(null) }
    var showCreateDialog by remember(prefill) { mutableStateOf(prefill != null) }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "ဆေးရုံနှင့် လူထု ဆူညံသံတိုင်ကြားရေးစင်တာ",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "သတ်မှတ် dB ကျော်လွန်နေသော ဆောင်းဘောက်နှင့် ဆူညံသံများကို သက်သေမှတ်တမ်းဖြင့် တင်သွင်းသိမ်းဆည်းပြီး သက်ဆိုင်ရာသို့ Share ပေးပို့နိုင်ပါသည်။",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { showCreateDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("new_complaint_button")
                        ) {
                            Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("တိုင်ကြားစာအသစ် တင်သွင်းမည်")
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == null,
                        onClick = { selectedFilter = null },
                        label = { Text("အားလုံး") },
                        modifier = Modifier.testTag("filter_all")
                    )
                    ComplaintStatus.entries.forEach { status ->
                        FilterChip(
                            selected = selectedFilter == status,
                            onClick = { selectedFilter = status },
                            label = { Text(status.labelMm) },
                            modifier = Modifier.testTag("filter_${status.name.lowercase()}")
                        )
                    }
                }
            }

            when (complaintsState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
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
                                text = complaintsState.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
                is UiState.Success -> {
                    val filtered = complaintsState.data.filter {
                        selectedFilter == null || it.statusEnum == selectedFilter
                    }
                    if (filtered.isEmpty()) {
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
                                        imageVector = Icons.Filled.Report,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "တိုင်ကြားချက်မှတ်တမ်း မရှိသေးပါ။ အထက်ပါခလုတ်မှ တိုင်ကြားစာအသစ် ဖန်တီးနိုင်ပါသည်။",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(filtered, key = { it.id }) { complaint ->
                            ComplaintCard(
                                complaint = complaint,
                                onShare = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_SUBJECT,
                                            "Hospital Quiet Zone Noise Complaint - ${complaint.facilityName}"
                                        )
                                        putExtra(Intent.EXTRA_TEXT, buildShareReportText(complaint))
                                    }
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, "တိုင်ကြားစာ ပေးပို့မည်")
                                    )
                                },
                                onEscalate = {
                                    onUpdateStatus(
                                        complaint.id,
                                        ComplaintStatus.ESCALATED,
                                        "သက်ဆိုင်ရာ ကျန်းမာရေးနှင့် အုပ်ချုပ်ရေးဌာနသို့ ထပ်ဆင့်ပေးပို့ပြီး"
                                    )
                                },
                                onResolve = {
                                    onUpdateStatus(
                                        complaint.id,
                                        ComplaintStatus.RESOLVED,
                                        "ဆူညံသံပြဿနာ ဖြေရှင်းဆောင်ရွက်ပြီး"
                                    )
                                },
                                onDelete = { onDeleteComplaint(complaint.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateComplaintDialog(
            initialReporter = defaultReporterName,
            initialFacility = defaultFacilityName,
            initialMeasuredDb = prefill?.measuredDb ?: 65,
            initialPeakDb = prefill?.peakDb ?: 78,
            onDismiss = {
                showCreateDialog = false
                onClearPrefill()
            },
            onSubmit = { reporter, facility, source, measured, peak, desc ->
                onCreateComplaint(reporter, facility, source, measured, peak, desc) {
                    showCreateDialog = false
                    onClearPrefill()
                }
            }
        )
    }
}

@Composable
private fun ComplaintCard(
    complaint: NoiseComplaint,
    onShare: () -> Unit,
    onEscalate: () -> Unit,
    onResolve: () -> Unit,
    onDelete: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()) }
    val displayDate = (complaint.updatedAt ?: complaint.createdAt)?.toDate()?.let {
        formatter.format(it)
    } ?: "Just now"

    val severityColor = when (complaint.severityEnum) {
        NoiseSeverity.CRITICAL -> AlertCrimson
        NoiseSeverity.HIGH -> CautionAmber
        NoiseSeverity.MODERATE -> CalmGreen
    }
    val statusColor = when (complaint.statusEnum) {
        ComplaintStatus.RESOLVED -> CalmGreen
        ComplaintStatus.ESCALATED -> CautionAmber
        ComplaintStatus.SUBMITTED -> MaterialTheme.colorScheme.primary
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = complaint.facilityName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${complaint.sourceEnum.labelMm} • $displayDate",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = severityColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${complaint.peakDb} dB",
                        color = severityColor,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = statusColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = complaint.statusEnum.labelMm,
                        color = statusColor,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = "Avg ${complaint.measuredDb} dB • ${complaint.reporterName}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Text(
                text = complaint.description,
                style = MaterialTheme.typography.bodyLarge
            )

            if (!complaint.authorityResponse.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "မှတ်ချက်: ${complaint.authorityResponse}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onShare,
                        modifier = Modifier.testTag("share_complaint_${complaint.id}")
                    ) {
                        Icon(imageVector = Icons.Filled.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ပေးပို့မည်")
                    }

                    if (complaint.statusEnum == ComplaintStatus.SUBMITTED) {
                        OutlinedButton(
                            onClick = onEscalate,
                            modifier = Modifier.testTag("escalate_complaint_${complaint.id}")
                        ) {
                            Text("ဌာနသို့တင်")
                        }
                    }

                    if (complaint.statusEnum != ComplaintStatus.RESOLVED) {
                        OutlinedButton(
                            onClick = onResolve,
                            modifier = Modifier.testTag("resolve_complaint_${complaint.id}")
                        ) {
                            Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ပြီးစီး")
                        }
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_complaint_${complaint.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete complaint",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateComplaintDialog(
    initialReporter: String,
    initialFacility: String,
    initialMeasuredDb: Int,
    initialPeakDb: Int,
    onDismiss: () -> Unit,
    onSubmit: (
        reporterName: String,
        facilityName: String,
        sourceType: NoiseSourceType,
        measuredDb: Int,
        peakDb: Int,
        description: String
    ) -> Unit
) {
    var reporter by remember { mutableStateOf(initialReporter) }
    var facility by remember { mutableStateOf(initialFacility) }
    var sourceType by remember { mutableStateOf(NoiseSourceType.LOUDSPEAKER) }
    var measuredDb by remember { mutableIntStateOf(initialMeasuredDb.coerceIn(20, 140)) }
    var peakDb by remember { mutableIntStateOf(initialPeakDb.coerceIn(20, 140)) }
    var description by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "ဆူညံသံတိုင်ကြားစာ တင်သွင်းရန်",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = facility,
                    onValueChange = { facility = it.take(120) },
                    label = { Text("ဆေးရုံ / ကျန်းမာရေးဌာန / ရပ်ကွက်အမည် *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("complaint_facility_input")
                )

                OutlinedTextField(
                    value = reporter,
                    onValueChange = { reporter = it.take(80) },
                    label = { Text("တိုင်ကြားသူအမည် / ဌာန") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("complaint_reporter_input")
                )

                Text(
                    text = "ဆူညံသံအရင်းအမြစ်",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NoiseSourceType.entries.forEach { src ->
                        FilterChip(
                            selected = sourceType == src,
                            onClick = { sourceType = src },
                            label = { Text(src.labelMm) }
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "တိုင်းတာချက် — Avg: $measuredDb dB | Peak: $peakDb dB",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(1000) },
                    label = { Text("လူနာများအပေါ် ထိခိုက်မှုနှင့် အခြေအနေအသေးစိတ် *") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("complaint_description_input")
                )

                if (validationError != null) {
                    Text(
                        text = validationError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanFacility = facility.trim()
                    val cleanDesc = description.trim()
                    if (cleanFacility.isEmpty() || cleanDesc.isEmpty()) {
                        validationError = "ဆေးရုံ/ဌာနအမည်နှင့် အကြောင်းအရာကို ဖြည့်သွင်းပေးပါ။"
                    } else {
                        onSubmit(
                            reporter.trim().ifBlank { "Healthcare Staff / Citizen" },
                            cleanFacility,
                            sourceType,
                            measuredDb,
                            peakDb,
                            cleanDesc
                        )
                    }
                },
                modifier = Modifier.testTag("submit_complaint_confirm_button")
            ) {
                Text("တင်သွင်းမည်")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ပယ်ဖျက်")
            }
        }
    )
}

private fun buildShareReportText(complaint: NoiseComplaint): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val dateStr = formatter.format((complaint.createdAt?.toDate()) ?: Date())
    return buildString {
        appendLine("📢 ဆေးရုံနှင့် ကျန်းမာရေးဌာန ဆူညံသံတိုင်ကြားစာ (Hospital Quiet-Zone Report)")
        appendLine("━━━━━━━━━━━━━━━━━━━━")
        appendLine("🏥 နေရာ/ဆေးရုံ: ${complaint.facilityName}")
        appendLine("👤 တိုင်ကြားသူ: ${complaint.reporterName}")
        appendLine("🔊 အရင်းအမြစ်: ${complaint.sourceEnum.labelMm} (${complaint.sourceEnum.labelEn})")
        appendLine("📊 တိုင်းတာချက်: Avg ${complaint.measuredDb} dB | Peak ${complaint.peakDb} dB")
        appendLine("⚠️ ပြင်းထန်မှု: ${complaint.severityEnum.labelMm}")
        appendLine("🕒 အချိန်: $dateStr")
        appendLine("📝 အကြောင်းအရာ:")
        appendLine(complaint.description)
        appendLine("━━━━━━━━━━━━━━━━━━━━")
        appendLine("WHO စံနှုန်းအရ ဆေးရုံလူနာဆောင်များအတွက် အများဆုံး ၃၀-၄၅ dB သာ ရှိသင့်ပါသည်။")
    }
}
