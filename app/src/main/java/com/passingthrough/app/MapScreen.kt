package com.passingthrough.app

import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
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
/** Height of the Material 3 sheet drag handle (4 dp bar plus 22 dp padding above and below). */
private val DragHandleHeight = 48.dp
/** "More places" shown when the sheet is dragged up; the full list is on the city page. */
private const val SHEET_MORE_PLACES = 12

/**
 * The home screen: a full-screen map with a floating top bar, and a Material 3 standard bottom
 * sheet that peeks with the current place and its attractions and can be dragged up for more.
 */
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
    var peekContentPx by remember { mutableIntStateOf(0) }

    val density = LocalDensity.current
    val navigationBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val peekHeight = if (peekContentPx == 0) {
        160.dp
    } else {
        with(density) { peekContentPx.toDp() } + DragHandleHeight + navigationBarHeight
    }
    val attractions = state.attractions
    val place = state.place

    BottomSheetScaffold(
        scaffoldState = rememberBottomSheetScaffoldState(),
        sheetPeekHeight = peekHeight,
        sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        sheetContent = {
            SheetContent(
                state = state,
                level = level,
                hasLocation = hasLocation,
                selectedTitle = selectedTitle,
                onAllowLocation = onAllowLocation,
                onRefresh = onRefresh,
                onOpenPage = onOpenPage,
                onPeekMeasured = { peekContentPx = it },
                bottomPadding = navigationBarHeight,
            )
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            OsmMap(
                location = state.location,
                level = level,
                follow = follow,
                attractions = if (level == Level.CITY) attractions else emptyList(),
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
            LocationLabel(
                visible = showLabel && dotPosition != null && place != null,
                name = place?.name.orEmpty(),
                imageUrl = state.townImage,
                dot = dotPosition ?: IntOffset.Zero,
                onClick = { showChooser = true },
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                TopBar(
                    level = level,
                    onLevelChange = {
                        follow = true
                        onLevelChange(it)
                    },
                    trainModeOn = trainModeOn,
                    onTrainModeChange = onTrainModeChange,
                    onRefresh = onRefresh,
                )
                Row(verticalAlignment = Alignment.Top) {
                    MapAttribution(Modifier.weight(1f))
                    if (!follow && state.location != null) {
                        SmallFloatingActionButton(
                            onClick = { follow = true },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Icon(painterResource(R.drawable.ic_my_location), contentDescription = "Back to my location")
                        }
                    }
                }
            }
        }
    }

    if (showChooser && place != null) {
        ExploreSheet(
            place = place,
            townImage = state.townImage,
            onChoose = {
                showChooser = false
                showLabel = false
                onOpenPage(it)
            },
            onDismiss = { showChooser = false },
        )
    }
}

// ---------------------------------------------------------------- Bottom sheet

@Composable
private fun SheetContent(
    state: UiState,
    level: Level,
    hasLocation: Boolean,
    selectedTitle: String?,
    onAllowLocation: () -> Unit,
    onRefresh: () -> Unit,
    onOpenPage: (Level) -> Unit,
    onPeekMeasured: (Int) -> Unit,
    bottomPadding: Dp,
) {
    val place = state.place
    val attractions = state.attractions
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = bottomPadding + Spacing.m),
    ) {
        // Everything in this first block is visible while the sheet peeks.
        Column(
            modifier = Modifier.onSizeChanged { onPeekMeasured(it.height) }.padding(bottom = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            when {
                !hasLocation -> SheetMessage(
                    title = "Location needed",
                    text = "Passing Through uses your location to show where you are and what is interesting " +
                        "around you. It is only sent to OpenStreetMap and Wikipedia to look these up.",
                    action = "Allow location",
                    onAction = onAllowLocation,
                )
                state.error != null && place == null -> SheetMessage(
                    title = "Something went wrong",
                    text = state.error,
                    action = "Try again",
                    onAction = onRefresh,
                )
                place == null -> Row(
                    modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(Spacing.m))
                    Text("Finding where you are…", style = MaterialTheme.typography.bodyLarge)
                }
                else -> {
                    val name = when (level) {
                        Level.CITY -> place.name
                        Level.STATE -> place.stateName
                        Level.COUNTRY -> place.countryName
                    } ?: level.label
                    PlaceHeader(
                        level = level,
                        name = name,
                        subtitle = when (level) {
                            Level.CITY -> place.subtitle
                            Level.STATE -> place.countryName.orEmpty()
                            Level.COUNTRY -> ""
                        },
                        onExplore = { onOpenPage(level) },
                    )
                    if (level == Level.CITY) {
                        if (state.detailsLoading) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l))
                        }
                        if (attractions.isNotEmpty()) {
                            AttractionCarousel(attractions, selectedTitle)
                        } else if (!state.detailsLoading) {
                            Text(
                                "No highlighted attractions around here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = Spacing.l),
                            )
                        }
                    }
                }
            }
        }

        // Revealed by dragging the sheet up.
        val more = if (level == Level.CITY) state.nearby.filterNot { it in attractions } else emptyList()
        if (more.isNotEmpty()) {
            Text(
                "More places nearby",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.xs),
            )
            val context = LocalContext.current
            more.take(SHEET_MORE_PLACES).forEachIndexed { index, nearby ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(start = 88.dp, end = Spacing.l),
                    )
                }
                ListItem(
                    headlineContent = { Text(nearby.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = (nearby.summary ?: nearby.description)?.let { text ->
                        { Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    },
                    leadingContent = {
                        PlacePhoto(
                            url = nearby.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.medium),
                        )
                    },
                    trailingContent = { Text(formatDistance(nearby.distanceKm), style = MaterialTheme.typography.labelMedium) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier
                        .padding(horizontal = Spacing.xs)
                        .clickable { openInMaps(context, nearby.title, nearby.location) },
                )
            }
        }
    }
}

@Composable
private fun SheetMessage(title: String, text: String, action: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xxs))
        Button(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun PlaceHeader(level: Level, name: String, subtitle: String, onExplore: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                when (level) {
                    Level.CITY -> "Passing through"
                    Level.STATE -> "State or region"
                    Level.COUNTRY -> "Country"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(Spacing.s))
        FilledTonalButton(onClick = onExplore, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
            Icon(
                painterResource(level.iconRes()),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Explore")
        }
    }
}

/** Material 3 "uncontained" carousel: photo-first cards that snap as you swipe. */
@Composable
private fun AttractionCarousel(attractions: List<NearbyPlace>, selectedTitle: String?) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedTitle, attractions) {
        val index = attractions.indexOfFirst { it.title == selectedTitle }
        if (index >= 0) listState.animateScrollToItem(index)
    }
    LazyRow(
        state = listState,
        flingBehavior = rememberSnapFlingBehavior(listState),
        contentPadding = PaddingValues(horizontal = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        items(attractions, key = { "${it.lang}:${it.title}" }) { attraction ->
            AttractionTile(attraction, selected = attraction.title == selectedTitle)
        }
    }
}

@Composable
private fun AttractionTile(place: NearbyPlace, selected: Boolean) {
    val context = LocalContext.current
    Card(
        onClick = { openInMaps(context, place.title, place.location) },
        shape = MaterialTheme.shapes.extraLarge,
        border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.secondary) else null,
        modifier = Modifier.width(240.dp),
    ) {
        Box {
            PlacePhoto(
                url = place.thumbnailUrl,
                contentDescription = place.title,
                modifier = Modifier.fillMaxWidth().height(132.dp).clip(MaterialTheme.shapes.extraLarge),
            )
            DistanceBadge(place, Modifier.align(Alignment.TopEnd).padding(Spacing.s))
        }
        Column(
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Text(place.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                place.summary ?: place.description ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** City / State / Country choice after tapping your name on the map (modal bottom sheet with a list). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExploreSheet(place: PlaceName, townImage: String?, onChoose: (Level) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Text(
            "Explore",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.xs),
        )
        ExploreRow(Level.CITY, "City", place.name, townImage) { onChoose(Level.CITY) }
        place.stateName?.let { ExploreRow(Level.STATE, "State or region", it, null) { onChoose(Level.STATE) } }
        place.countryName?.let { ExploreRow(Level.COUNTRY, "Country", it, null) { onChoose(Level.COUNTRY) } }
        Spacer(Modifier.height(Spacing.l))
    }
}

@Composable
private fun ExploreRow(level: Level, kind: String, name: String, imageUrl: String?, onClick: () -> Unit) {
    ListItem(
        overlineContent = { Text(kind) },
        headlineContent = { Text(name, style = MaterialTheme.typography.titleMedium) },
        leadingContent = {
            if (imageUrl != null) {
                PlacePhoto(imageUrl, contentDescription = null, modifier = Modifier.size(48.dp).clip(CircleShape))
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(level.iconRes()),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.padding(horizontal = Spacing.xs).clip(MaterialTheme.shapes.large).clickable(onClick = onClick),
    )
}

// ---------------------------------------------------------------- Floating controls on the map

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    level: Level,
    onLevelChange: (Level) -> Unit,
    trainModeOn: Boolean,
    onTrainModeChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
) {
    // Floats over a busy map, so it keeps a level-2 shadow on top of its tonal container color.
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(start = Spacing.m, end = Spacing.m, top = Spacing.xxs, bottom = Spacing.s),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Passing Through",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
                Switch(
                    checked = trainModeOn,
                    onCheckedChange = onTrainModeChange,
                    thumbContent = {
                        Icon(
                            painterResource(R.drawable.ic_train),
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    },
                    modifier = Modifier.padding(start = Spacing.xxs),
                )
            }
            Text(
                if (trainModeOn) "Train mode on: alerts for each new town" else "Train mode off",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.xs),
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
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
private fun MapAttribution(modifier: Modifier = Modifier) {
    Box(modifier) {
        Text(
            "© OpenStreetMap contributors",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f))
                .padding(horizontal = Spacing.xs, vertical = 2.dp),
        )
    }
}

@Composable
private fun LocationLabel(visible: Boolean, name: String, imageUrl: String?, dot: IntOffset, onClick: () -> Unit) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val gap = with(LocalDensity.current) { 22.dp.roundToPx() }
    val origin = TransformOrigin(0.5f, 1f)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(transformOrigin = origin),
        exit = fadeOut() + scaleOut(transformOrigin = origin),
        modifier = Modifier
            .offset { IntOffset(dot.x - size.width / 2, dot.y - size.height - gap) }
            .onSizeChanged { size = it },
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.padding(start = 6.dp, end = Spacing.s, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlacePhoto(url = imageUrl, contentDescription = null, modifier = Modifier.size(32.dp).clip(CircleShape))
                Spacer(Modifier.width(Spacing.xs))
                Text(name, style = MaterialTheme.typography.titleSmall)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Explore $name")
            }
        }
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

