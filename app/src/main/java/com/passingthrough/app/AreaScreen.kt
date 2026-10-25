package com.passingthrough.app

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to map")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val layoutDirection = LocalLayoutDirection.current
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(layoutDirection) + 16.dp,
                end = padding.calculateEndPadding(layoutDirection) + 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (failed && info == null) {
                item(key = "error") { ErrorCard(MainViewModel.ERROR_LOAD, onRetry) }
            }
            if (place == null && loading) {
                item(key = "spinner") { Spinner("Looking up this place…") }
            }
            if (place != null) {
                item(key = "header") { HeaderCard(level, place, info, image) }
            }
            if (loading && place != null) {
                item(key = "progress") {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp))
                }
            }
            info?.summary?.let { summary ->
                item(key = "about") { TextCard("About", summary) }
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
                items(others, key = { "nearby:${it.lang}:${it.title}" }) { NearbyCard(it) }
            }
            if (place != null) {
                item(key = "footer") { AttributionText() }
            }
        }
    }
}

@Composable
fun cardColors() = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)

@Composable
fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            Button(onClick = onRetry) { Text("Try again") }
        }
    }
}

@Composable
fun Spinner(text: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
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
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
    )
}

@Composable
private fun HeaderCard(level: Level, place: PlaceName, info: TownInfo?, image: String?) {
    Card(colors = cardColors()) {
        Column {
            PlacePhoto(
                url = image,
                contentDescription = place.name,
                modifier = Modifier.fillMaxWidth().height(190.dp),
            )
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        when (level) {
                            Level.CITY -> "You are in"
                            Level.STATE -> "You are in the state of"
                            Level.COUNTRY -> "You are in the country"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    place.name,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (place.subtitle.isNotBlank()) {
                    Text(
                        place.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                info?.description?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
                }
            }
        }
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
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun TextCard(title: String, text: String) {
    Card(colors = cardColors()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionTitle(title)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun KnownForCard(sentences: List<String>) {
    Card(colors = cardColors()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Known for")
            sentences.forEach { sentence ->
                Row {
                    Text("•", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(sentence, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(history: String) {
    var expanded by rememberSaveable(history) { mutableStateOf(false) }
    var overflows by remember(history) { mutableStateOf(false) }
    Card(colors = cardColors()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionTitle("History")
            Text(
                history,
                style = MaterialTheme.typography.bodyMedium,
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
}

/** A highlighted attraction: big photo, what it is, and a few sentences about it. Tap opens the map. */
@Composable
private fun AttractionCard(place: NearbyPlace) {
    val context = LocalContext.current
    Card(onClick = { openInMaps(context, place.title, place.location) }, colors = cardColors()) {
        Column {
            Box {
                PlacePhoto(
                    url = place.thumbnailUrl,
                    contentDescription = place.title,
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                )
                DistanceBadge(place, Modifier.align(Alignment.TopEnd).padding(10.dp))
            }
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(place.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                place.description?.let {
                    Text(
                        it.replaceFirstChar { c -> c.titlecase(Locale.getDefault()) },
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                place.summary?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 6, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    "Show on map",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
fun DistanceBadge(place: NearbyPlace, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        modifier = modifier,
    ) {
        Text(
            formatDistance(place.distanceKm),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun NearbyCard(place: NearbyPlace) {
    val context = LocalContext.current
    Card(onClick = { openInMaps(context, place.title, place.location) }, colors = cardColors()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlacePhoto(
                url = place.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(place.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                (place.summary ?: place.description)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    formatDistance(place.distanceKm),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (place.lang != "en") {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            place.lang.uppercase(Locale.US),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}
