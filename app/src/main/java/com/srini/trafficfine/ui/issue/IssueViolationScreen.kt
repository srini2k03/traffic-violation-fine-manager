package com.srini.trafficfine.ui.issue

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.srini.trafficfine.domain.model.FineBreakdown
import com.srini.trafficfine.domain.validation.FormError
import com.srini.trafficfine.domain.validation.ViolationFormValidator
import com.srini.trafficfine.ui.components.BackTopBar
import com.srini.trafficfine.ui.components.SectionTitle
import com.srini.trafficfine.util.FileSharing
import com.srini.trafficfine.util.Formatters
import java.io.File
import java.util.Locale

@Composable
fun IssueViolationScreen(
    onBack: () -> Unit,
    onIssued: (String) -> Unit,
    viewModel: IssueViolationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.issuedViolationId) {
        state.issuedViolationId?.let {
            viewModel.onNavigatedToIssued()
            onIssued(it)
        }
    }

    // --- Location permission -------------------------------------------------------------
    val locationPermissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) viewModel.refreshLocation() else viewModel.onLocationPermissionDenied()
    }
    val requestLocation = {
        val granted = locationPermissions.any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (granted) viewModel.refreshLocation() else locationLauncher.launch(locationPermissions)
    }
    LaunchedEffect(Unit) { if (state.location == null) requestLocation() }

    // --- Evidence photo (Activity Result API; no CAMERA permission needed) ---------------
    var pendingPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val path = pendingPhotoPath
        if (saved && path != null) viewModel.onEvidenceCaptured(path) else path?.let { File(it).delete() }
        pendingPhotoPath = null
    }
    val takePhoto = {
        val file = FileSharing.newEvidenceFile(context)
        pendingPhotoPath = file.absolutePath
        cameraLauncher.launch(FileSharing.uriFor(context, file))
    }

    Scaffold(
        topBar = { BackTopBar("Issue e-challan", onBack) },
        bottomBar = {
            SubmitBar(
                fine = state.fine,
                isSubmitting = state.isSubmitting,
                onSubmit = viewModel::submit,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.submitError?.let { ErrorBanner(it) }

            SectionTitle("Vehicle")
            OutlinedTextField(
                value = state.vehicleNumber,
                onValueChange = viewModel::onVehicleNumberChange,
                label = { Text("Registration number") },
                placeholder = { Text("TN 09 AB 1234") },
                singleLine = true,
                isError = FormError.VEHICLE_NUMBER_INVALID in state.errors,
                supportingText = {
                    when {
                        FormError.VEHICLE_NUMBER_INVALID in state.errors ->
                            Text("Enter a valid Indian plate, e.g. TN 09 AB 1234 or 22 BH 1234 AB")
                        state.plateHint != null -> Text("✓ ${state.plateHint}")
                        else -> Text("State code · RTO · series · number")
                    }
                },
                trailingIcon = {
                    if (state.plateValid) Icon(Icons.Filled.CheckCircle, "Valid", tint = MaterialTheme.colorScheme.primary)
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth().testTag(PLATE_FIELD_TAG),
            )

            SectionTitle("Offences")
            if (FormError.NO_VIOLATION_SELECTED in state.errors) {
                Text("Select at least one offence", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            OffenceChips(
                selected = state.selectedCodes,
                onToggle = viewModel::onToggleViolation,
                state = state,
            )

            SectionTitle("Offender (optional)")
            OutlinedTextField(
                value = state.offenderName,
                onValueChange = viewModel::onOffenderNameChange,
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.offenderPhone,
                onValueChange = viewModel::onOffenderPhoneChange,
                label = { Text("Mobile number") },
                prefix = { Text("+91 ") },
                singleLine = true,
                isError = FormError.PHONE_INVALID in state.errors,
                supportingText = if (FormError.PHONE_INVALID in state.errors) {
                    { Text("Enter a valid 10-digit Indian mobile number") }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("Location")
            LocationCard(state = state, onRefresh = requestLocation)

            SectionTitle("Photo evidence")
            EvidenceCard(
                path = state.evidencePath,
                required = state.selectedCodes.any { it in ViolationFormValidator.EVIDENCE_MANDATORY },
                missing = FormError.EVIDENCE_REQUIRED in state.errors,
                onCapture = takePhoto,
                onRemove = viewModel::onEvidenceRemoved,
            )

            SectionTitle("Notes")
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text("Officer remarks") },
                minLines = 3,
                isError = FormError.NOTES_TOO_LONG in state.errors,
                supportingText = { Text("${state.notes.length}/${ViolationFormValidator.MAX_NOTES_LENGTH}") },
                modifier = Modifier.fillMaxWidth(),
            )

            FineBreakdownCard(state.fine)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OffenceChips(selected: Set<String>, onToggle: (String) -> Unit, state: IssueViolationUiState) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.catalogue.forEach { type ->
            FilterChip(
                selected = type.code in selected,
                onClick = { onToggle(type.code) },
                label = { Text("${type.title} · ${Formatters.rupees(type.baseFine)}") },
            )
        }
    }
}

@Composable
private fun LocationCard(state: IssueViolationUiState, onRefresh: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val location = state.location
                when {
                    state.isLocating -> Text("Getting current location…")
                    location != null -> {
                        Text(location.address ?: "Location captured", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            String.format(Locale.US, "%.5f, %.5f", location.latitude, location.longitude) +
                                (location.accuracyMeters?.let { " · ±${it.toInt()} m" } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> Text(
                        if (state.locationUnavailable) "Location unavailable — grant permission and retry" else "No location yet",
                        color = if (FormError.LOCATION_MISSING in state.errors) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            if (state.isLocating) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                FilledTonalIconButton(onClick = onRefresh) { Icon(Icons.Filled.MyLocation, "Refresh location") }
            }
        }
    }
}

@Composable
private fun EvidenceCard(path: String?, required: Boolean, missing: Boolean, onCapture: () -> Unit, onRemove: () -> Unit) {
    if (path == null) {
        OutlinedButton(onClick = onCapture, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.CameraAlt, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (required) "Capture photo (required)" else "Capture photo")
        }
        if (missing) {
            Text(
                "Photo evidence is mandatory for the selected offence",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    } else {
        Box {
            AsyncImage(
                model = File(path),
                contentDescription = "Evidence photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)),
            )
            FilledTonalIconButton(onClick = onRemove, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Icon(Icons.Filled.Close, "Remove photo")
            }
        }
        TextButton(onClick = onCapture) { Text("Retake") }
    }
}

@Composable
private fun FineBreakdownCard(fine: FineBreakdown) {
    if (fine.lines.isEmpty()) return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Fine calculation", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            fine.lines.forEach { line ->
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("${line.title} (Sec. ${line.section})", style = MaterialTheme.typography.bodyMedium)
                        if (line.priorOffences > 0) {
                            Text(
                                "Repeat offender: ${line.priorOffences} prior · ×${line.multiplierPercent / 100.0}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Text(Formatters.rupees(line.amount), style = MaterialTheme.typography.bodyMedium)
                }
            }
            HorizontalDivider()
            Row {
                Text("Total", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text(Formatters.rupees(fine.total), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SubmitBar(fine: FineBreakdown, isSubmitting: Boolean, onSubmit: () -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Total fine", style = MaterialTheme.typography.labelMedium)
                Text(Formatters.rupees(fine.total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Button(onClick = onSubmit, enabled = !isSubmitting) {
                if (isSubmitting) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Issue challan")
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(8.dp))
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

/** Test tag on the registration-number field (used by the screenshot tour). */
const val PLATE_FIELD_TAG = "issue_plate"
