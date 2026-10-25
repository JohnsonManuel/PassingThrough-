package com.passingthrough.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PassingThroughTheme {
                PassingThroughApp(viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.setVisible(true)
    }

    override fun onStop() {
        viewModel.setVisible(false)
        super.onStop()
    }
}

// ---------------------------------------------------------------- App

/** The map is always there; an info page (city, state or country) slides over it. */
@Composable
fun PassingThroughApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val trainModeOn by TrainModeService.running.collectAsStateWithLifecycle()

    var hasLocation by remember { mutableStateOf(LocationHelper.hasPermission(context)) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    var level by rememberSaveable { mutableStateOf(Level.CITY) }
    var openPage by rememberSaveable { mutableStateOf<Level?>(null) }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        hasLocation = result.values.any { it } || LocationHelper.hasPermission(context)
        if (hasLocation) viewModel.onLocationPermissionGranted()
    }
    // Train mode starts either way; without this permission it just can't alert.
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { TrainModeService.start(context) }

    LaunchedEffect(Unit) {
        if (hasLocation) {
            viewModel.loadIfNeeded()
        } else if (!askedOnce) {
            askedOnce = true
            locationLauncher.launch(LocationHelper.PERMISSIONS)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val now = LocationHelper.hasPermission(context)
        if (now && !hasLocation) viewModel.onLocationPermissionGranted()
        hasLocation = now
    }

    fun setTrainMode(on: Boolean) {
        if (!on) {
            TrainModeService.stop(context)
            return
        }
        if (!hasLocation) {
            locationLauncher.launch(LocationHelper.PERMISSIONS)
            return
        }
        val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsNotificationPermission) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            TrainModeService.start(context)
        }
    }

    fun open(page: Level) {
        viewModel.openRegion(page)
        openPage = page
    }

    Box(Modifier.fillMaxSize()) {
        MapScreen(
            state = state,
            level = level,
            onLevelChange = { level = it },
            trainModeOn = trainModeOn,
            onTrainModeChange = { setTrainMode(it) },
            hasLocation = hasLocation,
            onAllowLocation = { locationLauncher.launch(LocationHelper.PERMISSIONS) },
            onRefresh = {
                if (hasLocation) viewModel.refresh() else locationLauncher.launch(LocationHelper.PERMISSIONS)
            },
            onOpenPage = { open(it) },
        )
        openPage?.let { page ->
            BackHandler { openPage = null }
            AreaScreen(
                level = page,
                state = state,
                onBack = { openPage = null },
                onRetry = {
                    if (page == Level.CITY) viewModel.refresh() else viewModel.openRegion(page, retry = true)
                },
            )
        }
    }
}

/**
 * Shows a place in the phone's map app (Google Maps, OsmAnd, Organic Maps, …),
 * or in Google Maps on the web when no map app is installed.
 */
fun openInMaps(context: Context, name: String, location: LatLon) {
    val lat = String.format(Locale.US, "%.6f", location.lat)
    val lon = String.format(Locale.US, "%.6f", location.lon)
    val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(name)})"))
    try {
        context.startActivity(geo)
    } catch (e: ActivityNotFoundException) {
        val web = Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lon")
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, web))
        } catch (e: ActivityNotFoundException) {
            // Neither a map app nor a browser: nothing sensible to open.
        }
    }
}
