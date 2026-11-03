package com.srini.trafficfine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.srini.trafficfine.ui.MainViewModel
import com.srini.trafficfine.ui.SessionState
import com.srini.trafficfine.ui.navigation.TrafficFineRoot
import com.srini.trafficfine.ui.theme.TrafficFineTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { viewModel.session.value is SessionState.Loading }
        enableEdgeToEdge()
        setContent {
            TrafficFineTheme {
                TrafficFineRoot(mainViewModel = viewModel)
            }
        }
    }
}
