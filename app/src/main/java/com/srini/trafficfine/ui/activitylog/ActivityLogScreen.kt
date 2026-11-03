package com.srini.trafficfine.ui.activitylog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srini.trafficfine.domain.model.LogAction
import com.srini.trafficfine.domain.model.OfficerLog
import com.srini.trafficfine.ui.components.BackTopBar
import com.srini.trafficfine.ui.components.EmptyState
import com.srini.trafficfine.ui.components.SectionTitle
import com.srini.trafficfine.ui.components.SyncBadge
import com.srini.trafficfine.util.Formatters
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormat = DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy", Locale.ENGLISH)

@Composable
fun ActivityLogScreen(onBack: () -> Unit, viewModel: ActivityLogViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { BackTopBar("Activity log", onBack) }) { padding ->
        if (state.groups.isEmpty() && !state.isLoading) {
            EmptyState(
                Icons.AutoMirrored.Filled.EventNote,
                "No activity yet",
                "Sign-ins, challans and payments you record appear here.",
                Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            state.groups.forEach { (day, logs) ->
                item(key = day.toString()) { SectionTitle(dayFormat.format(day)) }
                items(logs, key = { it.id }) { log -> LogRow(log) }
            }
        }
    }
}

@Composable
private fun LogRow(log: OfficerLog) {
    ListItem(
        leadingContent = { Icon(log.action.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(log.action.label()) },
        supportingContent = { Text(log.details, style = MaterialTheme.typography.bodySmall) },
        trailingContent = {
            Column {
                Text(Formatters.time(log.timestamp), style = MaterialTheme.typography.labelSmall)
                SyncBadge(log.syncState)
            }
        },
    )
}

private fun LogAction.label(): String = when (this) {
    LogAction.SIGN_IN -> "Signed in"
    LogAction.SIGN_OUT -> "Signed out"
    LogAction.VIOLATION_ISSUED -> "Challan issued"
    LogAction.FINE_PAID -> "Fine collected"
    LogAction.CHALLAN_SHARED -> "Challan shared"
    LogAction.SYNC_COMPLETED -> "Synced"
}

private fun LogAction.icon(): ImageVector = when (this) {
    LogAction.SIGN_IN -> Icons.AutoMirrored.Filled.Login
    LogAction.SIGN_OUT -> Icons.AutoMirrored.Filled.Logout
    LogAction.VIOLATION_ISSUED -> Icons.AutoMirrored.Filled.ReceiptLong
    LogAction.FINE_PAID -> Icons.Filled.Payments
    LogAction.CHALLAN_SHARED -> Icons.Filled.Share
    LogAction.SYNC_COMPLETED -> Icons.Filled.CloudDone
}
