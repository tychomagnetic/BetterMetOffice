package com.example

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.WeatherScreen
import com.example.ui.WeatherViewModel
import com.example.ui.theme.MetOfficeWeatherTheme
import com.example.widget.WidgetLocationHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val weatherViewModel: WeatherViewModel by viewModels()
    private var visibleClockJob: Job? = null
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (!WidgetLocationHelper.hasLocationPermission(this)) {
            Toast.makeText(
                this,
                "Location permission is required when the widget is set to GPS location.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MetOfficeWeatherTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WeatherScreen(viewModel = weatherViewModel)
                }
            }
        }

        if (!WidgetLocationHelper.hasLocationPermission(this)) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    override fun onStart() {
        super.onStart()
        weatherViewModel.onVisibleTimeCheck()
        visibleClockJob?.cancel()
        visibleClockJob = lifecycleScope.launch {
            while (isActive) {
                val millisToNextMinute = 60_000L - (System.currentTimeMillis() % 60_000L)
                delay(millisToNextMinute)
                weatherViewModel.onVisibleTimeCheck()
            }
        }
    }

    override fun onStop() {
        visibleClockJob?.cancel()
        visibleClockJob = null
        super.onStop()
    }
}
