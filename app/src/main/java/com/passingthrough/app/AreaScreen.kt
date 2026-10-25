package com.passingthrough.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.util.Locale

/** The info page for the city, the state or the country, opened from the map. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AreaScreen(level: Level, state: UiState, onBack: () -> Unit, onRetry: () -> Unit) {
    val region = state.regions[level]
    val place: PlaceName?
    val info: TownInfo?
    val image: String?
    val loading: Boolean
    val failed: Boolean
    if (level == Level.CITY) {
        place = state.place
        info = state.town
        image = state.townImage
        loading = state.loading || state.detailsLoading
        failed = state.error != null
    } else {
        place = region?.place
        info = region?.info
        image = info?.imageUrl
        loading = region?.loading ?: true
        failed = region?.failed ?: false
    }
    val title = place?.name ?: region?.name ?: level.label
    val attractions = if (level == Level.CITY) state.attractions else emptyList()
    val others = if (level == Level.CITY) state.nearby.filterNot { it in attractions } else emptyList()

    // Large title that collapses into a small app bar while scrolling (Material 3 large top app bar).
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to map")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val layoutDirection = LocalLayoutDirection.current
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().widthIn(max = MaxContentWidth),
                contentPadding = PaddingValues(
                    start = padding.calculateStartPadding(layoutDirection) + Spacing.m,
                    end = padding.calculateEndPadding(layoutDirection) + Spacing.m,
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + Spacing.l,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                if (failed && info == null) {
                    item(key = "error") { ErrorCard(MainViewModel.ERROR_LOAD, onRetry) }
                }
                if (place == null && loading) {
                    item(key = "spinner") { Spinner("Looking up this place…") }
                }
                if (place != null) {
                    item(key = "hero") { Hero(level, place, info, image, loading) }
                }
                info?.summary?.let { summary ->
                    item(key = "about") {
                        SectionCard("About") { Text(summary, style = MaterialTheme.typography.bodyLarge) }
                    }
                }
                if (info != null && info.knownFor.isNotEmpty()) {
                    item(key = "known") { KnownForCard(info.knownFor) }
                }
                info?.history?.let { history ->
                    item(key = "history") { HistoryCard(history) }
                }
                if (attractions.isNotEmpty()) {
                    item(key = "attractionsHeading") { SectionHeading("Main attractions") }
                    items(attractions, key = { "attraction:${it.lang}:${it.title}" }) { AttractionCard(it) }
                }
                if (others.isNotEmpty()) {
                    item(key = "nearbyHeading") { SectionHeading("More places nearby") }
                    item(key = "nearbyList") { NearbyList(others) }
                }
                if (place != null) {
                    item(key = "footer") { AttributionText() }
                }
            }
        }
    }
}

@Composable
fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text("Try again") }
        }
    }
}

@Composable
fun Spinner(text: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        CircularProgressIndicator()
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun AttributionText(modifier: Modifier = Modifier) {
    Text(
        "Text and images from Wikipedia (CC BY-SA). Map and place names © OpenStreetMap contributors.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.s),
    )
}

/** Icon for each level, used on the map and in the "Explore" sheet. */
fun Level.iconRes(): Int = when (this) {
    Level.CITY -> R.drawable.ic_location_city
    Level.STATE -> R.drawable.ic_terrain
    Level.COUNTRY -> R.drawable.ic_public
}

/** Full-width photo with the place's region and short description under it. */
@Composable
private fun Hero(level: Level, place: PlaceName, info: TownInfo?, image: String?, loading: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        PlacePhoto(
            url = image,
            contentDescription = place.name,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(MaterialTheme.shapes.extraLarge),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = Spacing.xxs)) {
            Icon(
                painterResource(level.iconRes()),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(Spacing.xs))
            Text(
                when (level) {
                    Level.CITY -> "You are in"
                    Level.STATE -> "State or region"
                    Level.COUNTRY -> "Country"
                } + if (place.subtitle.isNotBlank()) " · ${place.subtitle}" else "",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        info?.description?.let {
            Text(
                it.replaceFirstChar { c -> c.titlecase(Locale.getDefault()) },
                style = MaterialTheme.typography.titleMedium,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(horizontal = Spacing.xxs),
            )
        }
        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

/** A photo, or a calm placeholder when a place has none anywhere. */
@Composable
fun PlacePhoto(url: String?, contentDescription: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Place,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f),
            modifier = Modifier.size(40.dp),
        )
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(top = Spacing.m, start = Spacing.xxs)
            .semantics { heading() },
    )
}

/** Filled card (Material 3: surface container highest, no shadow) with a title. */
@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            content()
        }
    }
}

@Composable
private fun KnownForCard(sentences: List<String>) {
    SectionCard("Known for") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            sentences.forEach { sentence ->
                Row {
                    Box(
                        Modifier
                            .padding(top = 7.dp)
                            .size(8.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(MaterialTheme.colorScheme.secondary),
                    )
                    Spacer(Modifier.width(Spacing.s))
                    Text(sentence, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(history: String) {
    var expanded by rememberSaveable(history) { mutableStateOf(false) }
    var overflows by remember(history) { mutableStateOf(false) }
    SectionCard("History") {
        Text(
            history,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = if (expanded) Int.MAX_VALUE else 8,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        )
        if (overflows || expanded) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Show less" else "Show more")
            }
        }
    }
}

/** A highlighted attraction: big photo, what it is, a few sentences, and an "Open in Maps" action. */
@Composable
private fun AttractionCard(place: NearbyPlace) {
    val context = LocalContext.current
    val open = { openInMaps(context, place.title, place.location) }
    ElevatedCard(onClick = open, modifier = Modifier.fillMaxWidth()) {
        Box {
            PlacePhoto(
                url = place.thumbnailUrl,
                contentDescription = place.title,
                modifier = Modifier.fillMaxWidth().height(180.dp),
            )
            DistanceBadge(place, Modifier.align(Alignment.TopEnd).padding(Spacing.s))
        }
        Column(modifier = Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(place.title, style = MaterialTheme.typography.titleMedium)
            place.description?.let {
                Text(
                    it.replaceFirstChar { c -> c.titlecase(Locale.getDefault()) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            place.summary?.let {
                Spacer(Modifier.height(Spacing.xxs))
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 6, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Spacing.m, end = Spacing.m, bottom = Spacing.m),
            horizontalArrangement = Arrangement.End,
        ) {
            FilledTonalButton(onClick = open, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                Icon(
                    painterResource(R.drawable.ic_map),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Open in Maps")
            }
        }
    }
}

@Composable
fun DistanceBadge(place: NearbyPlace, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    ) {
        Text(
            formatDistance(place.distanceKm),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
        )
    }
}

/** The remaining places as a Material 3 list inside one filled card, divided by outline-variant lines. */
@Composable
private fun NearbyList(places: List<NearbyPlace>) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        places.forEachIndexed { index, place ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ListItem(
                headlineContent = { Text(place.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                supportingContent = (place.summary ?: place.description)?.let { text ->
                    { Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                },
                leadingContent = {
                    PlacePhoto(
                        url = place.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.medium),
                    )
                },
                trailingContent = {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        Text(formatDistance(place.distanceKm), style = MaterialTheme.typography.labelMedium)
                        if (place.lang != "en") LanguageTag(place.lang)
                    }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                modifier = Modifier.clickable { openInMaps(context, place.title, place.location) },
            )
        }
    }
}

@Composable
private fun LanguageTag(lang: String) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Text(
            lang.uppercase(Locale.US),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
