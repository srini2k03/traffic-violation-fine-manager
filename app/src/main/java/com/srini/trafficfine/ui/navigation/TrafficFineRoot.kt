package com.srini.trafficfine.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.srini.trafficfine.domain.model.Officer
import com.srini.trafficfine.ui.MainViewModel
import com.srini.trafficfine.ui.SessionState
import com.srini.trafficfine.ui.activitylog.ActivityLogScreen
import com.srini.trafficfine.ui.admin.AdminScreen
import com.srini.trafficfine.ui.dashboard.DashboardScreen
import com.srini.trafficfine.ui.detail.ViolationDetailScreen
import com.srini.trafficfine.ui.history.HistoryScreen
import com.srini.trafficfine.ui.issue.IssueViolationScreen
import com.srini.trafficfine.ui.login.LoginScreen
import com.srini.trafficfine.ui.profile.ProfileScreen

/** Auth gate: unauthenticated users only ever see the login screen. */
@Composable
fun TrafficFineRoot(mainViewModel: MainViewModel) {
    val session by mainViewModel.session.collectAsStateWithLifecycle()
    when (val state = session) {
        SessionState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        SessionState.SignedOut -> LoginScreen()
        is SessionState.SignedIn -> AppNavHost(officer = state.officer, onSignOut = mainViewModel::onSignOut)
    }
}

@Composable
private fun AppNavHost(officer: Officer, onSignOut: () -> Unit) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Route.Dashboard) {
        composable<Route.Dashboard> {
            DashboardScreen(
                officer = officer,
                onIssueViolation = { navController.navigate(Route.IssueViolation) },
                onOpenHistory = { navController.navigate(Route.History) },
                onOpenActivityLog = { navController.navigate(Route.ActivityLog) },
                onOpenViolation = { navController.navigate(Route.ViolationDetail(it)) },
                onOpenProfile = { navController.navigate(Route.Profile) },
                onOpenAdmin = { navController.navigate(Route.Admin) },
            )
        }
        composable<Route.IssueViolation> {
            IssueViolationScreen(
                onBack = { navController.popBackStack() },
                onIssued = { id ->
                    navController.navigate(Route.ViolationDetail(id)) {
                        popUpTo<Route.IssueViolation> { inclusive = true }
                    }
                },
            )
        }
        composable<Route.History> {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenViolation = { navController.navigate(Route.ViolationDetail(it)) },
            )
        }
        composable<Route.ViolationDetail> {
            ViolationDetailScreen(onBack = { navController.popBackStack() })
        }
        composable<Route.ActivityLog> {
            ActivityLogScreen(onBack = { navController.popBackStack() })
        }
        composable<Route.Profile> {
            ProfileScreen(
                officer = officer,
                onBack = { navController.popBackStack() },
                onOpenActivityLog = { navController.navigate(Route.ActivityLog) },
                onSignOut = onSignOut,
            )
        }
        composable<Route.Admin> {
            AdminScreen(
                admin = officer,
                onBack = { navController.popBackStack() },
                onOpenHistory = { navController.navigate(Route.History) },
            )
        }
    }
}
