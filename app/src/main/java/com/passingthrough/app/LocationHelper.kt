package com.passingthrough.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * Location without Google Play Services: plain [LocationManager] with the fused provider
 * (API 31+) and GPS / network as fallbacks.
 */
object LocationHelper {

    val PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    /** On a train a one-minute-old fix is already kilometres away. */
    private const val MAX_REUSE_AGE_MS = 30_000L
    private const val TIMEOUT_MS = 20_000L
    /** Used only after a timeout, so the user still gets something rather than an error. */
    private const val MAX_FALLBACK_AGE_MS = 10 * 60_000L

    fun hasPermission(context: Context): Boolean =
        PERMISSIONS.any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    fun ageMillis(location: Location): Long =
        (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000

    fun Location.toLatLon() = LatLon(latitude, longitude)

    /** Must be called from the main thread. Returns null if no fix could be obtained. */
    suspend fun getCurrentLocation(context: Context): LatLon? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val last = freshestLastKnown(lm)
        if (last != null && ageMillis(last) < MAX_REUSE_AGE_MS) return last.toLatLon()

        val providers = enabledProviders(lm)
        val fix = if (providers.isEmpty()) null else withTimeoutOrNull(TIMEOUT_MS) {
            firstFix(context, lm, providers)
        }
        return fix?.toLatLon()
            ?: last?.takeIf { ageMillis(it) < MAX_FALLBACK_AGE_MS }?.toLatLon()
    }

    fun enabledProviders(lm: LocationManager): List<String> {
        val enabled = try {
            lm.getProviders(true)
        } catch (e: SecurityException) {
            emptyList()
        }
        val order = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }
        return order.filter { it in enabled }
    }

    @SuppressLint("MissingPermission")
    fun freshestLastKnown(lm: LocationManager): Location? =
        enabledProviders(lm)
            .mapNotNull {
                try {
                    lm.getLastKnownLocation(it)
                } catch (e: Exception) {
                    null
                }
            }
            .maxByOrNull { it.elapsedRealtimeNanos }

    /** Asks every provider at once and returns the first fix that arrives. */
    @SuppressLint("MissingPermission")
    private suspend fun firstFix(
        context: Context,
        lm: LocationManager,
        providers: List<String>,
    ): Location? = suspendCancellableCoroutine { cont ->
        val done = AtomicBoolean(false)
        val remaining = AtomicInteger(providers.size)
        val signals = mutableListOf<CancellationSignal>()
        val listeners = mutableListOf<LocationListener>()

        fun cleanup() {
            signals.forEach { it.cancel() }
            listeners.forEach { runCatching { lm.removeUpdates(it) } }
        }

        fun onResult(location: Location?) {
            if (location != null) {
                if (done.compareAndSet(false, true)) {
                    cleanup()
                    cont.resume(location)
                }
            } else if (remaining.decrementAndGet() == 0 && done.compareAndSet(false, true)) {
                cleanup()
                cont.resume(null)
            }
        }

        for (provider in providers) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val signal = CancellationSignal()
                    signals += signal
                    lm.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) {
                        onResult(it)
                    }
                } else {
                    val listener = SimpleLocationListener(
                        onLocation = { onResult(it) },
                        onDisabled = { onResult(null) },
                    )
                    listeners += listener
                    @Suppress("DEPRECATION")
                    lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                }
            } catch (e: Exception) {
                onResult(null)
            }
        }
        cont.invokeOnCancellation { cleanup() }
    }
}

/**
 * Implements every [LocationListener] method explicitly: before API 30 they have no default
 * implementations and the framework would crash calling a missing one.
 */
class SimpleLocationListener(
    private val onLocation: (Location) -> Unit,
    private val onDisabled: () -> Unit = {},
) : LocationListener {
    override fun onLocationChanged(location: Location) = onLocation(location)
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) = onDisabled()

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
}
