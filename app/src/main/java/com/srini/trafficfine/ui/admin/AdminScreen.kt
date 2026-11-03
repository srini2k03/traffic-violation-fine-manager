package com.srini.trafficfine.ui.admin

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srini.trafficfine.domain.model.AnalyticsSummary
import com.srini.trafficfine.domain.model.Officer
import com.srini.trafficfine.domain.model.OfficerPerformance
import com.srini.trafficfine.domain.model.ViolationCatalogue
import com.srini.trafficfine.domain.model.ViolationStatus
import com.srini.trafficfine.ui.components.BackTopBar
import com.srini.trafficfine.ui.components.EmptyState
import com.srini.trafficfine.ui.components.SectionTitle
import com.srini.trafficfine.ui.components.StatCard
import com.srini.trafficfine.ui.theme.StatusColors
import com.srini.trafficfine.util.Formatters
import kotlin.math.roundToInt

private val categoryColors = listOf(
    Color(0xFF0B3D91), Color(0xFF00897B), Color(0xFFEF6C00), Color(0xFF6A1B9A), Color(0xFFC62828), Color(0xFF546E7A),
)

@Composable
fun AdminScreen(
    admin: Officer,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: AdminViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    Scaffold(
        topBar = {
            BackTopBar("Admin console", onBack) {
                TextButton(onClick = onOpenHistory) { Text("All challans") }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = state.tab.ordinal) {
                Tab(
                    selected = state.tab == AdminTab.ANALYTICS,
                    onClick = { viewModel.selectTab(AdminTab.ANALYTICS) },
                    text = { Text("Analytics") },
                )
                Tab(
                    selected = state.tab == AdminTab.OFFICERS,
                    onClick = { viewModel.selectTab(AdminTab.OFFICERS) },
                    text = { Text("Officers") },
                )
            }
            when (state.tab) {
                AdminTab.ANALYTICS -> AnalyticsTab(state.analytics, state.range, viewModel::selectRange)
                AdminTab.OFFICERS -> OfficersTab(
                    admin = admin,
                    officers = state.officers,
                    performance = state.analytics.officers,
                    rangeLabel = state.range.label,
                    pendingToggle = state.pendingToggle,
                    onToggle = viewModel::setActive,
                )
            }
        }
    }
}

@Composable
private fun AnalyticsTab(summary: AnalyticsSummary, range: AnalyticsRange, onRange: (AnalyticsRange) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnalyticsRange.entries.forEach { r ->
                    FilterChip(selected = r == range, onClick = { onRange(r) }, label = { Text(r.label) })
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Challans", summary.totalIssued.toString(), Icons.AutoMirrored.Filled.ReceiptLong, Modifier.weight(1f))
                StatCard("Fines issued", Formatters.rupees(summary.totalAmount), Icons.Filled.CurrencyRupee, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    "Collected · ${(summary.collectionRate * 100).roundToInt()}%",
                    Formatters.rupees(summary.totalCollected),
                    Icons.Filled.AccountBalanceWallet,
                    Modifier.weight(1f),
                    accent = StatusColors.Paid,
                )
                StatCard(
                    "Repeat offenders",
                    "${(summary.repeatOffenderShare * 100).roundToInt()}%",
                    Icons.Filled.Repeat,
                    Modifier.weight(1f),
                    accent = StatusColors.Pending,
                )
            }
        }
        item {
            ChartCard("Fines per day", "Light bar: issued · dark bar: collected") {
                DailyBarChart(summary.days)
            }
        }
        item {
            val slices = listOf(
                ChartSlice("Pending", (summary.byStatus[ViolationStatus.PENDING] ?: 0).toFloat(), StatusColors.Pending),
                ChartSlice("Paid", (summary.byStatus[ViolationStatus.PAID] ?: 0).toFloat(), StatusColors.Paid),
                ChartSlice("Cancelled", (summary.byStatus[ViolationStatus.CANCELLED] ?: 0).toFloat(), StatusColors.Cancelled),
            )
            ChartCard("Challans by status") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DonutChart(slices, centerLabel = slices.sumOf { it.value.toInt() }.toString())
                    Spacer(Modifier.width(24.dp))
                    Legend(slices)
                }
            }
        }
        if (summary.byCategory.isNotEmpty()) {
            item {
                ChartCard("Offences by category") {
                    HorizontalBars(
                        summary.byCategory.mapIndexed { i, (category, count) ->
                            ChartSlice(
                                category.name.lowercase().replaceFirstChar { it.uppercase() },
                                count.toFloat(),
                                categoryColors[i % categoryColors.size],
                            )
                        },
                    )
                }
            }
        }
        if (summary.topOffences.isNotEmpty()) {
            item {
                ChartCard("Top offences") {
                    HorizontalBars(
                        summary.topOffences.map { (code, count) ->
                            ChartSlice(ViolationCatalogue.find(code)?.title ?: code, count.toFloat(), MaterialTheme.colorScheme.tertiary)
                        },
                    )
                }
            }
        }
        if (summary.officers.isNotEmpty()) {
            item {
                ChartCard("Officer leaderboard", "Challans issued in the selected period") {
                    HorizontalBars(
                        summary.officers.take(8).map { ChartSlice(it.officerName, it.issued.toFloat(), MaterialTheme.colorScheme.primary) },
                    )
                }
            }
        }
    }
}

@Composable
private fun OfficersTab(
    admin: Officer,
    officers: OfficersState,
    performance: List<OfficerPerformance>,
    rangeLabel: String,
    pendingToggle: String?,
    onToggle: (Officer, Boolean) -> Unit,
) {
    when {
        officers.isLoading -> Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
        }
        officers.error != null -> EmptyState(Icons.Filled.Groups, "Can't load officers", officers.error)
        officers.officers.isEmpty() -> EmptyState(
            Icons.Filled.Groups,
            "No officers yet",
            "Provision accounts with scripts/set-role.mjs; they will appear here.",
        )
        else -> {
            val stats = performance.associateBy { it.officerId }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    SectionTitle("${officers.officers.size} officer(s) · ${officers.officers.count { it.active }} active")
                }
                items(officers.officers, key = { it.uid }) { officer ->
                    val perf = stats[officer.uid]
                    Card(Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    officer.name + if (officer.isAdmin) " · admin" else "",
                                    fontWeight = FontWeight.SemiBold,
                                )
                            },
                            supportingContent = {
                                Column {
                                    Text("${officer.badgeNumber} · ${officer.station}", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        if (perf == null) {
                                            "No challans in $rangeLabel"
                                        } else {
                                            "$rangeLabel: ${perf.issued} challans · ${Formatters.rupees(perf.amount)} · collected ${Formatters.rupees(perf.collected)}"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            trailingContent = {
                                if (pendingToggle == officer.uid) {
                                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                } else {
                                    Switch(
                                        checked = officer.active,
                                        onCheckedChange = { onToggle(officer, it) },
                                        enabled = officer.uid != admin.uid && pendingToggle == null,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartCard(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
