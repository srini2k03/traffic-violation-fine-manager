package com.srini.trafficfine.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srini.trafficfine.BuildConfig
import com.srini.trafficfine.domain.model.Officer
import com.srini.trafficfine.ui.components.BackTopBar
import com.srini.trafficfine.ui.components.SectionTitle

@Composable
fun ProfileScreen(
    officer: Officer,
    onBack: () -> Unit,
    onOpenActivityLog: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    Scaffold(topBar = { BackTopBar("Profile & settings", onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.LocalPolice,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(officer.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(officer.email, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (officer.isAdmin) "Administrator" else "Traffic officer",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            ListItem(
                leadingContent = { Icon(Icons.Filled.Badge, contentDescription = null) },
                headlineContent = { Text(officer.badgeNumber) },
                supportingContent = { Text(officer.station.ifBlank { "Station not set" }) },
            )

            SectionTitle("Sync")
            ListItem(
                leadingContent = {
                    Icon(
                        if (state.unsyncedCount == 0) Icons.Filled.CloudDone else Icons.Filled.CloudUpload,
                        contentDescription = null,
                    )
                },
                headlineContent = {
                    Text(if (state.unsyncedCount == 0) "Everything is synced" else "${state.unsyncedCount} change(s) waiting to sync")
                },
                supportingContent = { Text("Records are saved on this device first and uploaded automatically.") },
                trailingContent = {
                    if (state.isSyncing) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(onClick = viewModel::syncNow) {
                            Icon(Icons.Filled.Sync, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Sync now")
                        }
                    }
                },
            )

            SectionTitle("Account")
            ListItem(
                leadingContent = { Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = null) },
                headlineContent = { Text("My activity log") },
                trailingContent = { TextButton(onClick = onOpenActivityLog) { Text("Open") } },
            )
            ListItem(
                leadingContent = { Icon(Icons.Filled.Password, contentDescription = null) },
                headlineContent = { Text("Change password") },
                supportingContent = { Text("We'll email a secure reset link") },
                trailingContent = { TextButton(onClick = viewModel::sendPasswordReset) { Text("Send") } },
            )
            HorizontalDivider()
            ListItem(
                leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
                headlineContent = { Text("Version ${BuildConfig.VERSION_NAME}") },
                supportingContent = { Text("Fine amounts are indicative (Motor Vehicles (Amendment) Act, 2019)") },
            )
            OutlinedButton(onClick = { confirmSignOut = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Sign out")
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = {
                Text(
                    if (state.unsyncedCount > 0) {
                        "${state.unsyncedCount} change(s) have not synced yet. They stay on this device and upload after you sign in again."
                    } else {
                        "You will need your password to sign in again."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    onSignOut()
                }) { Text("Sign out") }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
        )
    }
}
