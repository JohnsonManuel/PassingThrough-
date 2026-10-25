package com.passingthrough.app

import android.view.MotionEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.TilesOverlay

private val DEFAULT_CENTER = LatLon(50.0, 10.0)
private const val DEFAULT_ZOOM = 4.0
private const val MIN_ZOOM = 3.0
private const val ANIMATION_MS = 900L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    state: UiState,
    level: Level,
    onLevelChange: (Level) -> Unit,
    trainModeOn: Boolean,
    onTrainModeChange: (Boolean) -> Unit,
    hasLocation: Boolean,
    onAllowLocation: () -> Unit,
    onRefresh: () -> Unit,
    onOpenPage: (Level) -> Unit,
) {
    var follow by rememberSaveable { mutableStateOf(true) }
    var showLabel by rememberSaveable { mutableStateOf(false) }
    var showChooser by rememberSaveable { mutableStateOf(false) }
    var selectedTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var dotPosition by remember { mutableStateOf<IntOffset?>(null) }

    val attractions = state.attractions
    val showAttractions = level == Level.CITY

    Box(Modifier.fillMaxSize()) {
        OsmMap(
            location = state.location,
            level = level,
            follow = follow,
            attractions = if (showAttractions) attractions else emptyList(),
            selectedTitle = selectedTitle,
            onUserMovedMap = { follow = false },
            onMyLocationClick = { showLabel = !showLabel },
            onAttractionClick = { selectedTitle = it.title },
            onMapTap = {
                showLabel = false
                selectedTitle = null
            },
            onDotPositionChange = { dotPosition = it },
            modifier = Modifier.fillMaxSize(),
        )

        // The name of where you are, popping up above your dot.
        val dot = dotPosition
        val place = state.place
        if (showLabel && dot != null && place != null) {
            LocationLabel(
                name = place.name,
                imageUrl = state.townImage,
                dot = dot,
                onClick = { showChooser = true },
            )
        }

        TopPanel(
            level = level,
            onLevelChange = {
                follow = true
                onLevelChange(it)
            },
            trainModeOn = trainModeOn,
            onTrainModeChange = onTrainModeChange,
            onRefresh = onRefresh,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(12.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!follow && state.location != null) {
                SmallFloatingActionButton(
                    onClick = { follow = true },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.End).padding(end = 16.dp),
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = "Back to my location")
                }
            }
            when {
                !hasLocation -> BottomCard {
                    Text("Location needed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Passing Through uses your location to show where you are and what is interesting " +
                            "around you. It is only sent to OpenStreetMap and Wikipedia to look these up.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onAllowLocation) { Text("Allow location") }
                }
                state.error != null && place == null -> BottomCard {
                    Text(state.error, style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onRefresh) { Text("Try again") }
                }
                place == null -> BottomCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Finding where you are…", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                level == Level.CITY -> CityBottom(
                    place = place,
                    attractions = attractions,
                    loading = state.detailsLoading,
                    selectedTitle = selectedTitle,
                    onOpenCity = { onOpenPage(Level.CITY) },
                )
                else -> RegionBottom(
                    level = level,
                    name = if (level == Level.STATE) place.stateName else place.countryName,
                    onOpen = { onOpenPage(level) },
                )
            }
            Text(
                "© OpenStreetMap contributors · Wikipedia",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }

    val place = state.place
    if (showChooser && place != null) {
        LevelChooser(
            place = place,
            onChoose = {
                showChooser = false
                showLabel = false
                onOpenPage(it)
            },
            onDismiss = { showChooser = false },
        )
    }
}

// ---------------------------------------------------------------- Map

@Composable
private fun OsmMap(
    location: LatLon?,
    level: Level,
    follow: Boolean,
    attractions: List<NearbyPlace>,
    selectedTitle: String?,
    onUserMovedMap: () -> Unit,
    onMyLocationClick: () -> Unit,
    onAttractionClick: (NearbyPlace) -> Unit,
    onMapTap: () -> Unit,
    onDotPositionChange: (IntOffset?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val currentOnUserMoved by rememberUpdatedState(onUserMovedMap)
    val currentOnMyLocationClick by rememberUpdatedState(onMyLocationClick)
    val currentOnAttractionClick by rememberUpdatedState(onAttractionClick)
    val currentOnMapTap by rememberUpdatedState(onMapTap)
    val currentOnDotPosition by rememberUpdatedState(onDotPositionChange)
    val currentLocation by rememberUpdatedState(location)

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = MIN_ZOOM
            // "Train view" is the deepest the map goes: towns, never streets.
            maxZoomLevel = Level.CITY.zoom
            isVerticalMapRepetitionEnabled = false
            setScrollableAreaLimitLatitude(MapView.getTileSystem().maxLatitude, MapView.getTileSystem().minLatitude, 0)
            controller.setZoom(DEFAULT_ZOOM)
            controller.setCenter(GeoPoint(DEFAULT_CENTER.lat, DEFAULT_CENTER.lon))
        }
    }
    val myMarker = remember {
        Marker(mapView).apply {
            icon = ContextCompat.getDrawable(context, R.drawable.ic_my_location_dot)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            setInfoWindow(null)
            setOnMarkerClickListener { _, _ ->
                currentOnMyLocationClick()
                true
            }
        }
    }
    val attractionMarkers = remember { mutableListOf<Pair<NearbyPlace, Marker>>() }
    val pinIcon = remember { ContextCompat.getDrawable(context, R.drawable.ic_attraction_pin) }
    val selectedPinIcon = remember { ContextCompat.getDrawable(context, R.drawable.ic_attraction_pin_selected) }

    fun reportDot() {
        if (currentLocation == null || mapView.width == 0) {
            currentOnDotPosition(null)
            return
        }
        val p = mapView.projection.toPixels(myMarker.position, null)
        currentOnDotPosition(IntOffset(p.x, p.y))
    }

    // One-time setup of the overlays and listeners.
    DisposableEffect(mapView) {
        // Taps on empty map close the label. Added first, so markers get their taps before it.
        mapView.overlays.add(
            MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                    currentOnMapTap()
                    return false
                }

                override fun longPressHelper(p: GeoPoint?): Boolean = false
            }),
        )
        // Dragging the map stops following the user until they tap "back to my location".
        mapView.overlays.add(object : Overlay() {
            override fun onTouchEvent(event: MotionEvent, mapView: MapView): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_MOVE && event.historySize > 0) currentOnUserMoved()
                return false
            }
        })
        mapView.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                reportDot()
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                reportDot()
                return false
            }
        })
        mapView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> reportDot() }
        onDispose { mapView.onDetach() }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(dark) {
        mapView.overlayManager.tilesOverlay.setColorFilter(if (dark) TilesOverlay.INVERT_COLORS else null)
        mapView.invalidate()
    }

    // The live dot.
    LaunchedEffect(location) {
        if (location == null) return@LaunchedEffect
        val point = GeoPoint(location.lat, location.lon)
        val firstFix = myMarker !in mapView.overlays
        myMarker.position = point
        if (firstFix) {
            mapView.overlays.add(myMarker)
            mapView.controller.animateTo(point, level.zoom, ANIMATION_MS)
        } else if (follow) {
            mapView.controller.animateTo(point)
        }
        mapView.invalidate()
        reportDot()
    }

    // Country / State / Train view, and "back to my location".
    LaunchedEffect(level, follow) {
        val here = location
        if (here == null) {
            mapView.controller.setZoom(if (level == Level.CITY) DEFAULT_ZOOM else level.zoom)
        } else if (follow) {
            mapView.controller.animateTo(GeoPoint(here.lat, here.lon), level.zoom, ANIMATION_MS)
        }
    }

    // Attraction pins, with the selected one in amber.
    LaunchedEffect(attractions, selectedTitle) {
        if (attractionMarkers.map { it.first } != attractions) {
            attractionMarkers.forEach { mapView.overlays.remove(it.second) }
            attractionMarkers.clear()
            for (place in attractions) {
                val marker = Marker(mapView).apply {
                    position = GeoPoint(place.location.lat, place.location.lon)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    setInfoWindow(null)
                    setOnMarkerClickListener { _, _ ->
                        currentOnAttractionClick(place)
                        true
                    }
                }
                attractionMarkers += place to marker
                // Keep the user's dot drawn (and tappable) above the pins.
                val dotIndex = mapView.overlays.indexOf(myMarker)
                if (dotIndex >= 0) mapView.overlays.add(dotIndex, marker) else mapView.overlays.add(marker)
            }
        }
        for ((place, marker) in attractionMarkers) {
            marker.icon = if (place.title == selectedTitle) selectedPinIcon else pinIcon
        }
        mapView.invalidate()
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

// ---------------------------------------------------------------- Overlays on the map

@Composable
private fun LocationLabel(name: String, imageUrl: String?, dot: IntOffset, onClick: () -> Unit) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val gap = with(LocalDensity.current) { 22.dp.roundToPx() }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 6.dp,
        modifier = Modifier
            .offset { IntOffset(dot.x - size.width / 2, dot.y - size.height - gap) }
            .onSizeChanged { size = it },
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlacePhoto(url = imageUrl, contentDescription = null, modifier = Modifier.size(30.dp).clip(CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Explore $name")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopPanel(
    level: Level,
    onLevelChange: (Level) -> Unit,
    trainModeOn: Boolean,
    onTrainModeChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Passing Through",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
                Text("Train mode", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(8.dp))
                Switch(checked = trainModeOn, onCheckedChange = onTrainModeChange)
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(end = 8.dp)) {
                Level.entries.forEachIndexed { index, entry ->
                    SegmentedButton(
                        selected = entry == level,
                        onClick = { onLevelChange(entry) },
                        shape = SegmentedButtonDefaults.itemShape(index, Level.entries.size),
                        icon = {},
                    ) {
                        Text(entry.label, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomCard(content: @Composable () -> Unit) {
    Card(
        colors = cardColors(),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

/** Train view: where you are, and the main attractions as photo cards. */
@Composable
private fun CityBottom(
    place: PlaceName,
    attractions: List<NearbyPlace>,
    loading: Boolean,
    selectedTitle: String?,
    onOpenCity: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedTitle, attractions) {
        val index = attractions.indexOfFirst { it.title == selectedTitle }
        if (index >= 0) listState.animateScrollToItem(index)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            onClick = onOpenCity,
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shadowElevation = 4.dp,
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Passing through ", style = MaterialTheme.typography.bodyMedium)
                Text(
                    place.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (loading) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "About ${place.name}")
            }
        }
        if (attractions.isNotEmpty()) {
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(attractions, key = { _, p -> "${p.lang}:${p.title}" }) { _, attraction ->
                    AttractionTile(attraction, selected = attraction.title == selectedTitle)
                }
            }
        }
    }
}

@Composable
private fun AttractionTile(place: NearbyPlace, selected: Boolean) {
    val context = LocalContext.current
    Card(
        onClick = { openInMaps(context, place.title, place.location) },
        colors = cardColors(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.secondary) else null,
        modifier = Modifier.width(250.dp),
    ) {
        Box {
            PlacePhoto(
                url = place.thumbnailUrl,
                contentDescription = place.title,
                modifier = Modifier.fillMaxWidth().height(120.dp),
            )
            DistanceBadge(place, Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                place.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                place.summary ?: place.description ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                minLines = 3,
            )
        }
    }
}

/** State or country level: a short card that opens its info page. */
@Composable
private fun RegionBottom(level: Level, name: String?, onOpen: () -> Unit) {
    BottomCard {
        Text(
            if (level == Level.STATE) "You are in the state of" else "You are in",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            name ?: level.label,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Button(onClick = onOpen) { Text("Explore ${name ?: level.label.lowercase()}") }
    }
}

@Composable
private fun LevelChooser(place: PlaceName, onChoose: (Level) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Explore") },
        text = {
            Column {
                ChooserRow("City", place.name) { onChoose(Level.CITY) }
                place.stateName?.let { ChooserRow("State", it) { onChoose(Level.STATE) } }
                place.countryName?.let { ChooserRow("Country", it) { onChoose(Level.COUNTRY) } }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ChooserRow(kind: String, name: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(kind, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}
