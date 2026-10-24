package com.passingthrough.app

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.passingthrough.app.LocationHelper.toLatLon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Follows the journey in the foreground and alerts for each new town. */
class TrainModeService : Service() {

    companion object {
        const val ACTION_STOP = "com.passingthrough.app.action.STOP_TRAIN_MODE"

        private const val UPDATE_INTERVAL_MS = 30_000L
        private const val UPDATE_DISTANCE_M = 300f
        private const val LOOKUP_DISTANCE_KM = 1.0

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        private val _location = MutableStateFlow<LatLon?>(null)
        val location: StateFlow<LatLon?> = _location.asStateFlow()

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, TrainModeService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TrainModeService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val listener = SimpleLocationListener(onLocation = { onLocation(it.toLatLon()) })
    private var started = false
    private var lastLookup: LatLon? = null
    private var lookupJob: Job? = null
    private var currentTown: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (started) return START_STICKY

        if (!LocationHelper.hasPermission(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(
                this,
                Notifications.ONGOING_ID,
                Notifications.ongoing(this, null),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
        } catch (e: Exception) {
            // E.g. permission revoked, or Android refuses a foreground start from the background.
            stopSelf()
            return START_NOT_STICKY
        }
        started = true
        _running.value = true
        startLocationUpdates()
        scope.launch {
            LocationHelper.getCurrentLocation(this@TrainModeService)?.let { onLocation(it) }
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val available = lm.allProviders
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (provider !in available) continue
            try {
                lm.requestLocationUpdates(provider, UPDATE_INTERVAL_MS, UPDATE_DISTANCE_M, listener, Looper.getMainLooper())
            } catch (e: Exception) {
                // Missing fine permission for GPS, or provider unavailable: the other one may still work.
            }
        }
    }

    private fun onLocation(here: LatLon) {
        _location.value = here
        if (lookupJob?.isActive == true) return
        val previous = lastLookup
        if (previous != null && haversineKm(previous, here) < LOOKUP_DISTANCE_KM) return
        lookupJob = scope.launch { lookup(here) }
    }

    private suspend fun lookup(here: LatLon) {
        val place = try {
            PlaceRepository.reverseGeocode(here)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return // Try again on the next location update.
        }
        lastLookup = here
        if (!place.found) return
        Notifications.updateOngoing(this, place.name)

        val previousTown = currentTown
        if (place.name == previousTown) return
        currentTown = place.name
        if (previousTown == null) return // The town we started in is not "new".

        val town = try {
            PlaceRepository.townInfo(place)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        Notifications.showTown(this, place.name, PlaceRepository.alertText(place, town))
    }

    override fun onDestroy() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        runCatching { lm.removeUpdates(listener) }
        scope.cancel()
        _running.value = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
