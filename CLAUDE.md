# Passing Through

Native Android app that shows where you are and what's interesting about it: the town, state and
country, what each is known for, its history, and the main attractions nearby. It's built mainly
for train journeys (a "train mode" alerts you for every new town), but it works anywhere.

## Hard rules (from the owner)

- **No AI/LLM, no API keys.** All data comes from free, open APIs: OpenStreetMap Nominatim,
  Wikidata, Wikipedia, and OpenStreetMap map tiles.
- **No Google Play Services.** Location uses the framework `LocationManager`, so the app runs on
  de-Googled phones.
- **Never link to Wikipedia pages.** Wikipedia text and photos are fetched and shown in the app.
  Tapping a place opens it in the phone's map app (`geo:` intent, falling back to Google Maps on
  the web); see `openInMaps()` in `MainActivity.kt`.
- Every HTTP request sends a descriptive User-Agent (`Http.USER_AGENT`). Coil and osmdroid use it
  too. Nominatim is called at most once per second.
- Format coordinates in URLs with `Locale.US` (a German phone would otherwise write commas).

## Tech stack

Kotlin 2.1.0, Jetpack Compose (BOM 2024.12.01) with Material 3, AGP 8.7.3, Gradle 8.11.1 (wrapper),
Java 17, minSdk 26 / target and compile SDK 35, package `com.passingthrough.app`.
Libraries: Coil 2.7 (images), OkHttp 4.12 (only for Coil's User-Agent), osmdroid 6.1.20 (map).
Networking is plain `HttpURLConnection` + `org.json`. There's one Activity, one `AndroidViewModel`
exposing `StateFlow<UiState>`, and an object repository. There's no navigation library: the info
page is drawn over the map, and `BackHandler` closes it.

## Code map (`app/src/main/java/com/passingthrough/app/`)

| File | What it does |
|---|---|
| `App.kt` | Application: HTTP disk cache init, notification channels, Coil `ImageLoader` with User-Agent, osmdroid config (User-Agent, tile cache in `cacheDir`). |
| `MainActivity.kt` | Activity, permission flow, train-mode toggle, root composable `PassingThroughApp` (map + info page overlay), `openInMaps()`. |
| `Theme.kt` | Material 3 light/dark color schemes (generated, see below), `Shapes`, `Spacing` tokens, `MaxContentWidth`. |
| `MapScreen.kt` | Home screen: `BottomSheetScaffold` with the osmdroid map, floating top bar (title, refresh, train-mode switch, Country/State/Train view segmented buttons), animated name label over the location dot, attraction pins, the sheet (place header + attraction carousel, more places when dragged up), and the "Explore" modal sheet (City/State/Country). `OsmMap` wraps `MapView` in `AndroidView`. |
| `AreaScreen.kt` | Info page for city, state or country: large collapsing app bar, hero photo, About, Known for, History, Main attractions (with "Open in Maps"), More places (Material `ListItem`s). Also shared bits: `PlacePhoto`, `DistanceBadge`, `ErrorCard`, `Spinner`, `AttributionText`, `Level.iconRes()`. |
| `MainViewModel.kt` | `UiState` (city data + `regions` map for state/country), live location while the app is visible, a new town lookup after every 1 km, attraction reload after 5 km in the same town, `openRegion(level)` for the state/country pages. |
| `PlaceRepository.kt` | All API calls plus the rules: Nominatim reverse geocoding per `Level`, Wikidata sitelinks and P18 image, Wikipedia summary, full extract, "known for" sentences, history section, GeoSearch (en + local language), filtering and ranking, attraction intros. |
| `LocationHelper.kt` | Permissions, one-shot `getCurrentLocation()` (fused/GPS/network race, 30 s fix reuse, 20 s timeout), live `updates()` flow, `SimpleLocationListener` (implements every method; needed before API 30). |
| `TrainModeService.kt` | Foreground service (`location` type): GPS + network updates every 30 s / 300 m, town lookup after every 1 km, "Now passing {Town}" notification (one shared ID). Publishes `running` and `location` StateFlows. |
| `Notifications.kt` | Channels ("Train mode" low, "New town alerts" default), the ongoing notification with a Stop action, town alerts. |
| `Http.kt` | GET with a disk cache: every success is saved to `cacheDir/http/<sha1(url)>`; on any network error the saved copy is returned (offline on trains). 10 s connect, 15 s read. |
| `Models.kt` | `LatLon`, `Level` (COUNTRY 5.5 / STATE 7.5 / CITY 12.0 map zoom; Nominatim zoom 3/5/10), `PlaceName`, `TownInfo`, `NearbyPlace`, haversine, `formatDistance`. |

Resources: adaptive launcher icon (train window, hills, amber pin, rail) with a monochrome layer;
`ic_my_location_dot`, `ic_attraction_pin(_selected)`; Material icons copied as vectors
(`ic_my_location`, `ic_train`, `ic_map`, `ic_public`, `ic_location_city`, `ic_terrain`), because
the material-icons-extended artifact is too large.

## Key behaviour and decisions

- **Levels:** the map has Country, State and "Train view" (city). Train view (zoom 12) is the
  map's `maxZoomLevel`, so you never get street level. The top bar's segmented buttons switch
  levels. Dragging the map stops following you; the my-location FAB resumes it.
- **Tap your dot:** a label with the town's name and photo appears. Tapping it opens the Explore
  sheet, then the City/State/Country info page.
- **Attractions:** GeoSearch within 10 km, run in English and the country's language in parallel.
  Results are merged by Wikidata ID; boring ones (streets, schools, constituencies, companies) are
  dropped; ranked by `articleLength / (1 + km/4)`; top 30 kept. "Main attractions" are the top 10
  that have a photo. Their first 4 sentences come from one `prop=extracts&exintro` call per
  language.
- **Every town gets a photo:** Wikipedia summary image → Wikidata P18 image (via Commons
  `Special:FilePath`) → photo of the best nearby attraction → placeholder.
- **State/country pages** use their own Nominatim reverse lookup (zoom 5 / 3) for the Wikidata ID,
  then the same article pipeline as towns.
- **Theme:** generated with `@material/material-color-utilities` (TonalSpot) from the brand green
  `#1F5C4A`, amber `#C8862B`, a harmonized sky blue, and the cream `#FAF6EE` hue. Light-mode
  primary is pinned to the exact brand green. There's no dynamic (wallpaper) color, on purpose:
  the app keeps its identity. In dark mode the map tiles are color-inverted.
- **Design guidance:** the Material 3 skill is installed in `.agents/skills/material-3`
  (symlinked into `.claude/skills/`). Use it for UI work: tokens over raw values
  (`MaterialTheme.colorScheme/shapes`, `Spacing`), correct component variants, tonal elevation,
  and shadows only over the busy map.

## Local development loop (do this yourself, don't ask the user to)

Prerequisites: Android Studio (SDK, JDK 17+, `adb`), a device from `adb devices` (emulator or a
phone with wireless debugging). If Gradle can't find the SDK, create `local.properties` with
`sdk.dir=...`.

```bash
./gradlew installDebug                                    # build + install (fast after the first build)
adb shell am start -n com.passingthrough.app/.MainActivity
adb shell pm grant com.passingthrough.app android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.passingthrough.app android.permission.POST_NOTIFICATIONS   # Android 13+
adb exec-out screencap -p > /tmp/shot.png                 # then Read /tmp/shot.png to see the screen
adb logcat -d -t 300 --pid=$(adb shell pidof com.passingthrough.app)   # app log
adb logcat -d -b crash                                    # crashes
adb shell pm clear com.passingthrough.app                 # fresh start (also clears the caches)
adb shell input tap X Y                                   # tap (coordinates from a screenshot)
adb shell input swipe X1 Y1 X2 Y2 300                     # drag, e.g. pull up the bottom sheet
```

After a UI change: install, launch, wait a few seconds for data, screenshot, look at it, iterate.
Check both themes with `adb shell cmd uimode night yes|no`.

**Fake location (emulator only).** Note the order is **longitude latitude**:

```bash
adb emu geo fix 14.3329 51.7563          # Cottbus
```

Simulate a train ride Cottbus → Senftenberg (about 40 km) to test the moving dot, new-town
lookups and train-mode alerts:

```bash
for p in "14.3329 51.7563" "14.2900 51.7100" "14.2266 51.6517" "14.1661 51.5906" "14.0900 51.5500" "14.0016 51.5252"; do
  adb emu geo fix $p; sleep 20
done
```

You can also use the emulator's *Extended controls → Location → Routes* for smooth playback.
On a real phone the location is real; test there only for final checks.

## Status (as of the last cloud session)

- Branch `ccr-e932f991-f17blt` holds all the work. `.github/workflows/build.yml` builds the debug
  APK on every push and uploads it as the `passing-through-debug-apk` artifact. All builds through
  commit `6062ef1` (Material 3 redesign) are green.
- **Compiled, but never run on a device yet.** The cloud environment had no Android SDK and no
  network access to the APIs. First local job: install, run, and fix whatever is broken.
  Specifically check:
  - Map tiles load; the dot appears and follows you; Country/State/Train view zoom correctly; pinch
    stops at Train view.
  - The tap-dot label appears in the right place (it's positioned from `MapView.projection`).
  - Bottom-sheet peek height: measured from the sheet's first block (`onPeekMeasured`) plus drag
    handle (48 dp) plus navigation bar. Verify it neither clips the carousel nor covers half the map.
  - Attraction pins and carousel; tapping a pin highlights and scrolls to its card; cards open the
    maps app.
  - Info pages for city, state and country load (state/country do extra Nominatim calls).
  - Train mode: foreground notification, Stop action, "Now passing" alerts with the screen off.
  - Offline: places already viewed load with data off (`Http` disk cache, osmdroid tile cache).
  - Dark mode, and German system language (decimal commas must not reach URLs).
- The data logic (`PlaceRepository` parsing, extraction, ranking; `Http` cache) was unit-tested on
  the JVM in the cloud, but those tests are **not in the repo**. Adding them under
  `app/src/test` (with `testImplementation("org.json:json:...")`, because Android's `org.json` is
  stubbed in unit tests) would be a good early task.
- `README.md`'s "Project layout" section predates the map redesign (it doesn't list `Theme.kt`,
  `MapScreen.kt` or `AreaScreen.kt`).

## Ideas the owner has raised or that are pending

- The owner has more feature steps planned; ask before large changes in direction.
- Optional: wallpaper-based dynamic color as a setting (deliberately off now).
- Optional: an emulator GPX route file for the Cottbus → Senftenberg ride.

## Git

Develop on `ccr-e932f991-f17blt` unless the owner says otherwise. Commit messages: imperative
subject, short body explaining why.
