package com.srini.trafficfine.ui.history

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srini.trafficfine.domain.model.ViolationStatus
import com.srini.trafficfine.ui.components.BackTopBar
import com.srini.trafficfine.ui.components.EmptyState
import com.srini.trafficfine.ui.dashboard.ViolationRow
import com.srini.trafficfine.util.Formatters

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenViolation: (String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filters by viewModel.filterState.collectAsStateWithLifecycle()

    Scaffold(topBar = { BackTopBar("Violation history", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = filters.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("Search plate or challan number") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (filters.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) { Icon(Icons.Filled.Clear, "Clear search") }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = filters.status == null, onClick = { viewModel.onStatusSelected(null) }, label = { Text("All") })
                ViolationStatus.entries.forEach { status ->
                    FilterChip(
                        selected = filters.status == status,
                        onClick = { viewModel.onStatusSelected(status) },
                        label = { Text(status.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DateRange.entries.forEach { range ->
                    FilterChip(
                        selected = filters.range == range,
                        onClick = { viewModel.onRangeSelected(range) },
                        label = { Text(range.label) },
                    )
                }
            }
            Text(
                "${state.violations.size} challan(s) · ${Formatters.rupees(state.totalAmount)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (state.violations.isEmpty() && !state.isLoading) {
                EmptyState(Icons.Filled.SearchOff, "No challans found", "Try a different plate number, status or date range.")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.violations, key = { it.id }) { violation ->
                        ViolationRow(violation, onClick = { onOpenViolation(violation.id) })
                    }
                }
            }
        }
    }
}
