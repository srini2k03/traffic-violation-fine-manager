package com.srini.trafficfine.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.srini.trafficfine.domain.model.PaymentMode
import com.srini.trafficfine.domain.model.Violation
import com.srini.trafficfine.domain.model.ViolationStatus
import com.srini.trafficfine.domain.usecase.CancelViolationUseCase
import com.srini.trafficfine.domain.usecase.MarkFinePaidUseCase
import com.srini.trafficfine.ui.components.BackTopBar
import com.srini.trafficfine.ui.components.SectionTitle
import com.srini.trafficfine.ui.components.StatusChip
import com.srini.trafficfine.ui.components.SyncBadge
import com.srini.trafficfine.util.FileSharing
import com.srini.trafficfine.util.Formatters
import java.io.File
import java.util.Locale

@Composable
fun ViolationDetailScreen(onBack: () -> Unit, viewModel: ViolationDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showPayDialog by rememberSaveable { mutableStateOf(false) }
    var showCancelDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.share) {
        state.share?.let {
            FileSharing.shareChallan(context, it.path, it.challanNumber, it.phone)
            viewModel.onShareHandled()
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    Scaffold(
        topBar = {
            BackTopBar("Challan details", onBack) {
                if (state.isGeneratingPdf) {
                    CircularProgressIndicator(Modifier.size(20.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = viewModel::shareChallan, enabled = state.violation != null) {
                        Icon(Icons.Filled.Share, "Share e-challan PDF")
                    }
                }
                if (state.isAdmin && state.violation?.status == ViolationStatus.PENDING) {
                    IconButton(onClick = { showCancelDialog = true }, enabled = !state.isMarkingPaid) {
                        Icon(Icons.Filled.Block, "Cancel challan")
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            val v = state.violation
            if (v != null && v.status == ViolationStatus.PENDING) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = viewModel::shareChallan, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Share PDF")
                    }
                    Button(onClick = { showPayDialog = true }, enabled = !state.isMarkingPaid, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Payments, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Mark paid")
                    }
                }
            }
        },
    ) { padding ->
        val violation = state.violation
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            violation == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Challan not found")
            }
            else -> DetailContent(
                violation = violation,
                onOpenMap = {
                    violation.location?.let { loc ->
                        val uri = Uri.parse("geo:${loc.latitude},${loc.longitude}?q=${loc.latitude},${loc.longitude}")
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    }
                },
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (showCancelDialog) {
        CancelDialog(
            onDismiss = { showCancelDialog = false },
            onConfirm = { reason ->
                showCancelDialog = false
                viewModel.cancel(reason)
            },
        )
    }

    if (showPayDialog) {
        MarkPaidDialog(
            onDismiss = { showPayDialog = false },
            onConfirm = { mode, reference ->
                showPayDialog = false
                viewModel.markPaid(mode, reference)
            },
        )
    }
}

@Composable
private fun DetailContent(violation: Violation, onOpenMap: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        Formatters.plate(violation.vehicleNumber),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    StatusChip(violation.status)
                }
                Text(violation.challanNumber, style = MaterialTheme.typography.labelLarge)
                Text(Formatters.dateTime(violation.issuedAt), style = MaterialTheme.typography.bodySmall)
                SyncBadge(violation.syncState)
            }
        }

        SectionTitle("Fine")
        violation.fine.lines.forEach { line ->
            Row {
                Column(Modifier.weight(1f)) {
                    Text(line.title, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Section ${line.section}" +
                            if (line.priorOffences > 0) " · repeat ×${line.multiplierPercent / 100.0}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(Formatters.rupees(line.amount))
            }
        }
        HorizontalDivider()
        Row {
            Text("Total", Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Text(Formatters.rupees(violation.totalFine), fontWeight = FontWeight.Bold)
        }

        violation.cancelReason?.let { reason ->
            SectionTitle("Cancellation")
            Text(reason, color = MaterialTheme.colorScheme.error)
        }

        violation.payment?.let { payment ->
            SectionTitle("Payment")
            DetailRow("Paid on", Formatters.dateTime(payment.paidAt))
            DetailRow("Mode", payment.mode.name)
            DetailRow("Reference", payment.reference)
        }

        SectionTitle("Offender")
        DetailRow("Name", violation.offenderName ?: "—")
        DetailRow("Mobile", violation.offenderPhone?.let { "+91 $it" } ?: "—")

        SectionTitle("Location")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                violation.location?.let {
                    it.address ?: String.format(Locale.US, "%.5f, %.5f", it.latitude, it.longitude)
                } ?: "Not recorded",
                modifier = Modifier.weight(1f),
            )
            if (violation.location != null) {
                IconButton(onClick = onOpenMap) { Icon(Icons.Filled.Map, "Open in maps") }
            }
        }

        val photo: Any? = violation.localEvidencePath?.let { File(it) }?.takeIf { it.exists() } ?: violation.evidencePhotoUrl
        if (photo != null) {
            SectionTitle("Evidence")
            AsyncImage(
                model = photo,
                contentDescription = "Evidence photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)),
            )
        }

        if (violation.notes.isNotBlank()) {
            SectionTitle("Notes")
            Text(violation.notes)
        }

        SectionTitle("Issued by")
        DetailRow("Officer", violation.officerName)
        DetailRow("Badge", violation.officerBadge)
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.width(110.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}

@Composable
private fun MarkPaidDialog(onDismiss: () -> Unit, onConfirm: (PaymentMode, String) -> Unit) {
    var mode by rememberSaveable { mutableStateOf(PaymentMode.CASH) }
    var reference by rememberSaveable { mutableStateOf("") }
    val referenceOk = mode == PaymentMode.CASH || reference.trim().length >= MarkFinePaidUseCase.MIN_REFERENCE_LENGTH

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record payment") },
        text = {
            Column {
                PaymentMode.entries.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = mode == option, onClick = { mode = option }, role = Role.RadioButton)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == option, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(option.name.lowercase().replaceFirstChar { it.uppercase() })
                    }
                }
                if (mode != PaymentMode.CASH) {
                    OutlinedTextField(
                        value = reference,
                        onValueChange = { reference = it.take(40) },
                        label = { Text("Transaction reference") },
                        singleLine = true,
                        isError = reference.isNotEmpty() && !referenceOk,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(mode, reference) }, enabled = referenceOk) { Text("Confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CancelDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by rememberSaveable { mutableStateOf("") }
    val valid = reason.trim().length >= CancelViolationUseCase.MIN_REASON_LENGTH
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cancel challan") },
        text = {
            Column {
                Text("Cancelled challans stay on record and cannot be paid.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(CancelViolationUseCase.MAX_REASON_LENGTH) },
                    label = { Text("Reason") },
                    minLines = 2,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(reason) }, enabled = valid) { Text("Cancel challan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep") } },
    )
}
