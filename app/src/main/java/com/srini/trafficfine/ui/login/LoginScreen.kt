package com.srini.trafficfine.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srini.trafficfine.domain.model.Role

@Composable
fun LoginScreen(viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showPassword by rememberSaveable { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Filled.LocalPolice,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text("e-Challan", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Traffic enforcement · Officer sign-in",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                if (!state.backendConfigured) BackendMissingCard(state.demoAvailable)
                OutlinedTextField(
                    value = state.email,
                    onValueChange = viewModel::onEmailChange,
                    label = { Text("Official email") },
                    leadingIcon = { Icon(Icons.Filled.Mail, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showPassword) "Hide password" else "Show password",
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                state.info?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = viewModel::submit,
                    enabled = state.canSubmit,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Sign in")
                    }
                }
                if (state.backendConfigured) {
                    TextButton(onClick = viewModel::sendPasswordReset) { Text("Forgot password?") }
                    Text(
                        "Accounts are provisioned by the station administrator.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.demoAvailable) {
                    DemoSection(
                        startingDemo = state.startingDemo,
                        enabled = !state.isBusy,
                        onTryDemo = viewModel::tryDemo,
                    )
                }
            }
        }
    }
}

@Composable
private fun BackendMissingCard(demoAvailable: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(12.dp))
            Text(
                if (demoAvailable) {
                    "This build has no Firebase project configured, so officer sign-in is off. " +
                        "Try the demo to explore the app with sample data."
                } else {
                    "This build has no Firebase project configured. Add app/google-services.json and rebuild."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun DemoSection(startingDemo: Role?, enabled: Boolean, onTryDemo: (Role) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        HorizontalDivider(Modifier.weight(1f))
        Text(
            "  or  ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(Modifier.weight(1f))
    }
    OutlinedButton(
        onClick = { onTryDemo(Role.ADMIN) },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag(LoginTestTags.DEMO_ADMIN),
    ) {
        if (startingDemo == Role.ADMIN) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Try the demo")
        }
    }
    TextButton(
        onClick = { onTryDemo(Role.OFFICER) },
        enabled = enabled,
        modifier = Modifier.testTag(LoginTestTags.DEMO_OFFICER),
    ) {
        Text(if (startingDemo == Role.OFFICER) "Preparing demo…" else "Demo as a field officer")
    }
    Text(
        "Sample challans stay on this device. Nothing is sent to a server.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

object LoginTestTags {
    const val DEMO_ADMIN = "login_demo_admin"
    const val DEMO_OFFICER = "login_demo_officer"
}
