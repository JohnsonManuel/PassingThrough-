package com.passingthrough.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Info page data for the state or the country around the user. */
data class AreaDetails(
    val name: String?,
    val place: PlaceName? = null,
    val info: TownInfo? = null,
    val loading: Boolean = true,
    val failed: Boolean = false,
)

data class UiState(
    /** True until the town name is known for the first time. */
    val loading: Boolean = false,
    /** True while the town article and nearby places are still on their way. */
    val detailsLoading: Boolean = false,
    val error: String? = null,
    val location: LatLon? = null,
    val place: PlaceName? = null,
    val town: TownInfo? = null,
    val nearby: List<NearbyPlace> = emptyList(),
    val regions: Map<Level, AreaDetails> = emptyMap(),
) {
    val attractions: List<NearbyPlace> get() = PlaceRepository.attractions(nearby)

    /** Every town gets a picture: its own, else the photo of its best-known nearby place. */
    val townImage: String?
        get() = town?.imageUrl
            ?: attractions.firstOrNull()?.thumbnailUrl?.let { PlaceRepository.resizeThumbnail(it, 800) }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        const val ERROR_NO_LOCATION =
            "Couldn't get your location. Check that location is turned on and try again."
        const val ERROR_LOAD =
            "Couldn't load information for this place. Check your connection and try again."
        /** Look the town up again after moving this far. */
        private const val LOOKUP_DISTANCE_KM = 1.0
        /** Within the same town, reload attractions only after moving this far. */
        private const val DETAILS_DISTANCE_KM = 5.0
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var job: Job? = null
    private var liveJob: Job? = null
    private val regionJobs = mutableMapOf<Level, Job>()
    private var visible = false
    private var lastLookup: LatLon? = null
    private var detailsOrigin: LatLon? = null

    /** Called from the activity's onStart / onStop: the live dot only runs while the app is open. */
    fun setVisible(isVisible: Boolean) {
        visible = isVisible
        if (isVisible) {
            startLiveUpdates()
        } else {
            liveJob?.cancel()
            liveJob = null
        }
    }

    fun onLocationPermissionGranted() {
        refresh()
        if (visible) startLiveUpdates()
    }

    fun loadIfNeeded() {
        if (_state.value.place == null && job?.isActive != true) refresh()
    }

    fun refresh() {
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(loading = it.place == null, error = null) }
            val here = LocationHelper.getCurrentLocation(getApplication<Application>())
            if (here == null) {
                _state.update { it.copy(loading = false, detailsLoading = false, error = ERROR_NO_LOCATION) }
                return@launch
            }
            load(here, force = true)
        }
    }

    private fun startLiveUpdates() {
        if (liveJob?.isActive == true || !LocationHelper.hasPermission(getApplication<Application>())) return
        liveJob = viewModelScope.launch {
            LocationHelper.updates(getApplication<Application>()).collect { onLiveLocation(it) }
        }
    }

    private fun onLiveLocation(here: LatLon) {
        _state.update { it.copy(location = here) }
        if (job?.isActive == true) return
        val previous = lastLookup
        if (previous != null && haversineKm(previous, here) < LOOKUP_DISTANCE_KM) return
        job = viewModelScope.launch { load(here, force = false) }
    }

    private suspend fun load(here: LatLon, force: Boolean) {
        lastLookup = here
        _state.update { it.copy(location = here, error = null) }
        val place = try {
            PlaceRepository.reverseGeocode(here)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (force) {
                _state.update { it.copy(loading = false, detailsLoading = false, error = ERROR_LOAD) }
            } else {
                lastLookup = null // Try again on the next live fix.
            }
            return
        }

        val sameTown = _state.value.place?.name == place.name
        val origin = detailsOrigin
        if (!force && sameTown && origin != null && haversineKm(origin, here) < DETAILS_DISTANCE_KM) {
            _state.update { it.copy(place = place, loading = false) }
            return
        }
        detailsOrigin = here

        _state.update {
            if (sameTown) {
                // Same town: keep the old content visible while it reloads.
                it.copy(place = place, loading = false, detailsLoading = true)
            } else {
                it.copy(place = place, town = null, nearby = emptyList(), loading = false, detailsLoading = true)
            }
        }

        var townFailed = false
        var nearbyFailed = false
        coroutineScope {
            launch {
                try {
                    val town = PlaceRepository.townInfo(place)
                    _state.update { it.copy(town = town, nearby = withoutTown(it.nearby, town)) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    townFailed = true
                }
            }
            launch {
                try {
                    val nearby = PlaceRepository.nearby(here, place)
                    _state.update { it.copy(nearby = withoutTown(nearby, it.town)) }
                    val described = PlaceRepository.withSummaries(nearby)
                    _state.update { it.copy(nearby = withoutTown(described, it.town)) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    nearbyFailed = true
                }
            }
        }
        _state.update {
            it.copy(
                detailsLoading = false,
                error = if (townFailed && nearbyFailed && place.found) ERROR_LOAD else null,
            )
        }
    }

    private fun withoutTown(list: List<NearbyPlace>, town: TownInfo?): List<NearbyPlace> =
        if (town == null) list else PlaceRepository.withoutTown(list, town.wikidataId, listOf(town.title))

    /** Loads the info page for the state or country around the user, unless it is already there. */
    fun openRegion(level: Level, retry: Boolean = false) {
        if (level == Level.CITY) return
        val current = _state.value
        val here = current.location ?: return
        val name = if (level == Level.STATE) current.place?.stateName else current.place?.countryName
        val existing = current.regions[level]
        if (!retry && existing != null && existing.name == name && !existing.failed) return

        regionJobs[level]?.cancel()
        _state.update { it.copy(regions = it.regions + (level to AreaDetails(name = name))) }
        regionJobs[level] = viewModelScope.launch {
            val details = try {
                val place = PlaceRepository.reverseGeocode(here, level)
                AreaDetails(name = name, place = place, info = PlaceRepository.townInfo(place), loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AreaDetails(name = name, loading = false, failed = true)
            }
            _state.update { it.copy(regions = it.regions + (level to details)) }
        }
    }
}
