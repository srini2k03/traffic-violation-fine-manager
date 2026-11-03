package com.srini.trafficfine.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srini.trafficfine.domain.demo.DemoData
import com.srini.trafficfine.domain.model.Officer
import com.srini.trafficfine.domain.model.Violation
import com.srini.trafficfine.domain.model.ViolationCatalogue
import com.srini.trafficfine.ui.components.SectionTitle
import com.srini.trafficfine.ui.components.StatCard
import com.srini.trafficfine.ui.components.StatusChip
import com.srini.trafficfine.ui.components.SyncBadge
import com.srini.trafficfine.ui.theme.StatusColors
import com.srini.trafficfine.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    officer: Officer,
    onIssueViolation: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenActivityLog: () -> Unit,
    onOpenViolation: (String) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenAdmin: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(officer.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${officer.badgeNumber} · ${officer.station}" + if (officer.isAdmin) " · Admin" else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (state.isSyncing) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = viewModel::syncNow) { Icon(Icons.Filled.Sync, "Sync now") }
                    }
                    IconButton(onClick = onOpenProfile) { Icon(Icons.Filled.AccountCircle, "Profile and settings") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onIssueViolation,
                icon = { Icon(Icons.Filled.AddCircle, contentDescription = null) },
                text = { Text("Issue challan") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag(DASHBOARD_LIST_TAG),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (DemoData.isDemoUid(officer.uid)) {
                item { DemoBanner() }
            }
            if (officer.isAdmin) {
                item {
                    Button(onClick = onOpenAdmin, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.AdminPanelSettings, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Admin console · analytics & officers")
                    }
                }
            }
            item { SectionTitle("Today") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        "Challans issued",
                        state.stats.issuedToday.toString(),
                        Icons.AutoMirrored.Filled.ReceiptLong,
                        Modifier.weight(1f),
                    )
                    StatCard(
                        "Fines issued",
                        Formatters.rupees(state.stats.amountIssuedToday),
                        Icons.Filled.CurrencyRupee,
                        Modifier.weight(1f),
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        "Collected (${state.stats.paidToday})",
                        Formatters.rupees(state.stats.amountCollectedToday),
                        Icons.Filled.AccountBalanceWallet,
                        Modifier.weight(1f),
                        accent = StatusColors.Paid,
                    )
                    StatCard(
                        "Pending (${state.stats.pendingCount})",
                        Formatters.rupees(state.stats.pendingAmount),
                        Icons.Filled.HourglassTop,
                        Modifier.weight(1f),
                        accent = StatusColors.Pending,
                    )
                }
            }
            if (state.stats.unsyncedCount > 0) {
                item {
                    Text(
                        "${state.stats.unsyncedCount} record(s) waiting to sync — they are safe on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.stats.topViolations.isNotEmpty()) {
                item { SectionTitle("Top offences today") }
                items(state.stats.topViolations, key = { it.first }) { (code, count) ->
                    Text(
                        "${ViolationCatalogue.find(code)?.title ?: code}  ·  $count",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onOpenHistory, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.History, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("History")
                    }
                    OutlinedButton(onClick = onOpenActivityLog, modifier = Modifier.weight(1f)) {
                        Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Activity log")
                    }
                }
            }
            item { SectionTitle("Recent challans") }
            if (state.recent.isEmpty() && !state.isLoading) {
                item {
                    Text(
                        "No challans yet. Tap \"Issue challan\" to record a violation.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.recent, key = { it.id }) { violation ->
                ViolationRow(violation, onClick = { onOpenViolation(violation.id) })
            }
            item { Spacer(Modifier.size(72.dp)) }
        }
    }
}

@Composable
fun ViolationRow(violation: Violation, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
        ListItem(
            headlineContent = { Text(Formatters.plate(violation.vehicleNumber), style = MaterialTheme.typography.titleMedium) },
            supportingContent = {
                Column {
                    Text(
                        violation.fine.lines.joinToString { it.title },
                        maxLines = 1,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(Formatters.dateTime(violation.issuedAt), style = MaterialTheme.typography.labelSmall)
                        SyncBadge(violation.syncState)
                    }
                }
            },
            trailingContent = {
                Column(horizontalAlignment = Alignment.End) {
                    Text(Formatters.rupees(violation.totalFine), style = MaterialTheme.typography.titleSmall)
                    StatusChip(violation.status)
                }
            },
        )
    }
}

@Composable
private fun DemoBanner() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Science, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(
                "Demo mode · sample data stored on this device only. Sign out to end the demo.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Test tag on the dashboard list (used by the screenshot tour). */
const val DASHBOARD_LIST_TAG = "dashboard_list"
