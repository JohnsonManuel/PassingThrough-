package com.passingthrough.app

import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

/** The map's zoom steps. [CITY] ("Train view") is the deepest the map ever goes. */
enum class Level(val label: String, val zoom: Double, val nominatimZoom: Int) {
    COUNTRY("Country", 5.5, 3),
    STATE("State", 7.5, 5),
    CITY("Train view", 12.0, 10),
}

/** Result of the Nominatim reverse lookup. */
data class PlaceName(
    val name: String,
    val subtitle: String,
    val countryCode: String?,
    val wikidataId: String?,
    val wikipediaTag: String?,
    /** False when Nominatim could not name the place (we show the fallback text). */
    val found: Boolean,
    val level: Level = Level.CITY,
    val stateName: String? = null,
    val countryName: String? = null,
)

data class TownInfo(
    val title: String,
    val lang: String,
    val description: String?,
    val summary: String?,
    val imageUrl: String?,
    val articleUrl: String?,
    val wikidataId: String?,
    val knownFor: List<String>,
    val history: String?,
    val historyHeading: String?,
)

data class NearbyPlace(
    val title: String,
    val description: String?,
    val location: LatLon,
    val distanceKm: Double,
    val thumbnailUrl: String?,
    val lang: String,
    val wikidataId: String?,
    val articleLength: Long,
    /** First sentences of the article, fetched after the list itself. */
    val summary: String? = null,
)

private const val EARTH_RADIUS_KM = 6371.0088

fun haversineKm(a: LatLon, b: LatLon): Double {
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val h = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
    return 2 * EARTH_RADIUS_KM * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

/** "850 m" below one kilometre, "3.2 km" above (decimal separator follows the device language). */
fun formatDistance(km: Double): String =
    if (km < 1.0) {
        val metres = ((km * 1000) / 10).roundToInt() * 10
        "$metres m"
    } else {
        String.format(Locale.getDefault(), "%.1f km", km)
    }
