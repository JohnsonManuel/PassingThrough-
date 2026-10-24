# Passing Through

A native Android app that tells you where you are and what is interesting about it: the town,
what it is famous for, some of its history and notable places nearby. Built mainly for train
journeys, but it works anywhere.

* No AI, no API keys. All data comes from free, open APIs:
  [OpenStreetMap Nominatim](https://nominatim.org/) (town name),
  [Wikidata](https://www.wikidata.org/) (finding the right article) and
  [Wikipedia](https://www.wikipedia.org/) (summary, history, photos, nearby places).
* No Google Play Services: location uses Android's built-in `LocationManager`, so it also runs
  on de-Googled phones.
* **Train mode** runs a foreground service and posts a "Now passing …" notification for every
  new town, even with the screen off.
* Every API response is cached on disk, so places you have already seen still load when the
  train loses signal.

## Requirements

* Android Studio (latest stable), which bundles a suitable JDK (17+)
* Android SDK Platform 35 (Android Studio installs it on first sync if missing)
* A phone or emulator running Android 8.0 (API 26) or newer

## Build and install

### With Android Studio

1. **File → Open** and choose this folder.
2. Wait for the Gradle sync to finish.
3. Connect your phone with USB debugging enabled (or start an emulator) and press **Run ▶**.

### From the command line

```bash
# Linux / macOS
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Windows
gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

If Gradle cannot find the SDK, create `local.properties` in the project root containing
`sdk.dir=/path/to/Android/Sdk` (Android Studio does this for you).

## Using it

1. Allow location access when asked. The app loads the town you are in straight away.
2. Scroll through the cards: **About**, **Known for**, **History** and **Notable places nearby**.
   Tap a place to open its Wikipedia article.
3. Turn on **Train mode** to get a notification for each new town. On Android 13+ the app asks
   for notification permission at that moment. Use the **Stop** button in the notification or
   the switch to end it.

On an emulator, set a location under *Extended controls → Location* (for example Cottbus,
51.7563, 14.3329).

## Project layout

```
app/src/main/java/com/passingthrough/app/
  App.kt              Application: HTTP cache, notification channels, Coil image loader
  MainActivity.kt     Compose UI and theme
  MainViewModel.kt    UiState and loading logic
  PlaceRepository.kt  API calls, "known for" / history extraction, filtering and ranking
  LocationHelper.kt   Permissions and one-shot location
  TrainModeService.kt Foreground service for train mode
  Notifications.kt    Channels, ongoing notification, town alerts
  Http.kt             HTTP GET with disk cache fallback
  Models.kt           Data classes and haversine distance
```

## Being a good API citizen

Every request sends the User-Agent defined in `Http.USER_AGENT`. If you publish your own build,
change the contact URL there to your own. Nominatim is called at most once per second and results
are cached per ~100 m.

## Attribution

Text and images from Wikipedia (CC BY-SA). Place names © OpenStreetMap contributors (ODbL).
