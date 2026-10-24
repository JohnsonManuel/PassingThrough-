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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        const val ERROR_NO_LOCATION =
            "Couldn't get your location. Check that location is turned on and try again."
        const val ERROR_LOAD =
            "Couldn't load information for this place. Check your connection and try again."
        private const val TRAIN_RELOAD_DISTANCE_KM = 2.0
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var job: Job? = null
    private var visible = false

    init {
        viewModelScope.launch {
            TrainModeService.location.filterNotNull().collect { reloadIfTravelled(it) }
        }
    }

    /** Called from the activity's onStart / onStop. */
    fun setVisible(isVisible: Boolean) {
        visible = isVisible
        if (isVisible) TrainModeService.location.value?.let { reloadIfTravelled(it) }
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
            load(here)
        }
    }

    private fun reloadIfTravelled(here: LatLon) {
        if (!visible || !TrainModeService.running.value) return
        if (job?.isActive == true) return
        val shown = _state.value.location
        if (shown != null && haversineKm(shown, here) <= TRAIN_RELOAD_DISTANCE_KM) return
        job = viewModelScope.launch { load(here) }
    }

    private suspend fun load(here: LatLon) {
        _state.update { it.copy(location = here, error = null) }
        val place = try {
            PlaceRepository.reverseGeocode(here)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, detailsLoading = false, error = ERROR_LOAD) }
            return
        }

        _state.update {
            if (it.place?.name == place.name) {
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
                    _state.update {
                        it.copy(
                            town = town,
                            nearby = if (town == null) it.nearby else
                                PlaceRepository.withoutTown(it.nearby, town.wikidataId, listOf(town.title)),
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    townFailed = true
                }
            }
            launch {
                try {
                    val nearby = PlaceRepository.nearby(here, place)
                    _state.update {
                        val town = it.town
                        it.copy(
                            nearby = if (town == null) nearby else
                                PlaceRepository.withoutTown(nearby, town.wikidataId, listOf(town.title)),
                        )
                    }
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
}
