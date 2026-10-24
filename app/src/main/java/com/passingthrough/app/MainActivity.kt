package com.passingthrough.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PassingThroughTheme {
                MainScreen(viewModel)
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

// ---------------------------------------------------------------- Theme

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F5C4A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC9E6DA),
    onPrimaryContainer = Color(0xFF0B2E24),
    secondary = Color(0xFFC8862B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6DDB5),
    onSecondaryContainer = Color(0xFF3A2500),
    tertiary = Color(0xFF4F6354),
    onTertiary = Color.White,
    background = Color(0xFFFAF6EE),
    onBackground = Color(0xFF1E1B16),
    surface = Color(0xFFFAF6EE),
    onSurface = Color(0xFF1E1B16),
    surfaceVariant = Color(0xFFEDE6D8),
    onSurfaceVariant = Color(0xFF4D473C),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFDF8),
    surfaceContainer = Color(0xFFF4EEE3),
    surfaceContainerHigh = Color(0xFFEEE8DC),
    surfaceContainerHighest = Color(0xFFE8E1D4),
    outline = Color(0xFF7F776A),
    outlineVariant = Color(0xFFD6CEBF),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD3B8),
    onPrimary = Color(0xFF003828),
    primaryContainer = Color(0xFF0F4A3A),
    onPrimaryContainer = Color(0xFFC9E6DA),
    secondary = Color(0xFFF0B866),
    onSecondary = Color(0xFF452B00),
    secondaryContainer = Color(0xFF63410C),
    onSecondaryContainer = Color(0xFFFFDDB0),
    tertiary = Color(0xFFB5CCBA),
    onTertiary = Color(0xFF213527),
    background = Color(0xFF121512),
    onBackground = Color(0xFFE4E2DC),
    surface = Color(0xFF121512),
    onSurface = Color(0xFFE4E2DC),
    surfaceVariant = Color(0xFF3F4943),
    onSurfaceVariant = Color(0xFFBFC9C2),
    surfaceContainerLowest = Color(0xFF0C0F0D),
    surfaceContainerLow = Color(0xFF1A1E1B),
    surfaceContainer = Color(0xFF1E2320),
    surfaceContainerHigh = Color(0xFF282D2A),
    surfaceContainerHighest = Color(0xFF333835),
    outline = Color(0xFF89938C),
    outlineVariant = Color(0xFF3F4943),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

@Composable
fun PassingThroughTheme(content: @Composable () -> Unit) {
    val colors: ColorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}

// ---------------------------------------------------------------- Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val trainModeOn by TrainModeService.running.collectAsStateWithLifecycle()

    var hasLocation by remember { mutableStateOf(LocationHelper.hasPermission(context)) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        hasLocation = result.values.any { it } || LocationHelper.hasPermission(context)
        if (hasLocation) viewModel.refresh()
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
        if (now && !hasLocation) viewModel.refresh()
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
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (needsNotificationPermission) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            TrainModeService.start(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Passing Through", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { if (hasLocation) viewModel.refresh() else locationLauncher.launch(LocationHelper.PERMISSIONS) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
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
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(layoutDirection) + 16.dp,
                end = padding.calculateEndPadding(layoutDirection) + 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "train") { TrainModeCard(trainModeOn) { setTrainMode(it) } }

            if (!hasLocation) {
                item(key = "permission") {
                    PermissionCard { locationLauncher.launch(LocationHelper.PERMISSIONS) }
                }
            }

            state.error?.let { error ->
                item(key = "error") { ErrorCard(error) { viewModel.refresh() } }
            }

            if (state.loading && state.place == null) {
                item(key = "spinner") { FindingYou() }
            }

            state.place?.let { place ->
                item(key = "header") { HeaderCard(place, state.town) }
            }

            if (state.detailsLoading) {
                item(key = "progress") {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp))
                }
            }

            state.town?.let { town ->
                town.summary?.let { summary ->
                    item(key = "about") { TextCard("About", summary) }
                }
                if (town.knownFor.isNotEmpty()) {
                    item(key = "known") { KnownForCard(town.knownFor) }
                }
                town.history?.let { history ->
                    item(key = "history") { HistoryCard(history, historyUrl(town)) }
                }
            }

            if (state.nearby.isNotEmpty()) {
                item(key = "nearbyHeading") {
                    Text(
                        "Notable places nearby",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                    )
                }
                items(state.nearby, key = { "nearby:${it.lang}:${it.title}" }) { NearbyCard(it) }
            }

            if (state.place != null) {
                item(key = "footer") {
                    Text(
                        "Text and images from Wikipedia (CC BY-SA). Place names © OpenStreetMap contributors.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

private fun historyUrl(town: TownInfo): String? {
    val base = town.articleUrl ?: return null
    val heading = town.historyHeading ?: return base
    return base.substringBefore('#') + "#" + heading.replace(' ', '_')
}

@Composable
private fun cardColors() = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
)

@Composable
private fun TrainModeCard(on: Boolean, onToggle: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Train mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "Get a notification for each new town you pass, even with the screen off.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = on, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun PermissionCard(onAllow: () -> Unit) {
    Card(colors = cardColors()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Location needed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Passing Through uses your location to find the town you are in and the interesting " +
                    "places around it. Your location is only sent to OpenStreetMap and Wikipedia to look these up.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onAllow) { Text("Allow location") }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            Button(onClick = onRetry) { Text("Try again") }
        }
    }
}

@Composable
private fun FindingYou() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator()
        Text("Finding where you are…", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun HeaderCard(place: PlaceName, town: TownInfo?) {
    Card(colors = cardColors()) {
        Column {
            town?.imageUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = place.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
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
                        "You are in",
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
                town?.description?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
                }
            }
        }
    }
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
private fun HistoryCard(history: String, url: String?) {
    val uriHandler = LocalUriHandler.current
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
            Row {
                if (overflows || expanded) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "Show less" else "Show more")
                    }
                }
                if (url != null) {
                    TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) {
                        Text("Read on Wikipedia")
                    }
                }
            }
        }
    }
}

@Composable
private fun NearbyCard(place: NearbyPlace) {
    val uriHandler = LocalUriHandler.current
    Card(
        onClick = { runCatching { uriHandler.openUri(place.url) } },
        colors = cardColors(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val thumbShape = RoundedCornerShape(12.dp)
            if (place.thumbnailUrl != null) {
                AsyncImage(
                    model = place.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(thumbShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(thumbShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(place.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                place.description?.let {
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
