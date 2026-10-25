package com.passingthrough.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Collections
import java.util.Locale

/** All network lookups plus the rule-based text extraction and ranking. */
object PlaceRepository {

    const val FALLBACK_TOWN_NAME = "Somewhere along the way"

    private const val NOMINATIM_MIN_INTERVAL_MS = 1_100L
    private const val NEARBY_RADIUS_M = 10_000
    private const val MAX_NEARBY = 30
    private const val MAX_ATTRACTIONS = 10
    private const val MAX_SUMMARIES = 20
    private const val MAX_HISTORY_CHARS = 3_000

    private val nominatimMutex = Mutex()
    private var lastNominatimNanos = 0L

    private val placeCache = lruCache<PlaceName>(200)
    private val townCache = lruCache<TownInfo>(50)

    private val countryLanguages = mapOf(
        "de" to "de", "at" to "de", "ch" to "de", "li" to "de",
        "fr" to "fr", "it" to "it", "es" to "es", "nl" to "nl", "pl" to "pl",
        "cz" to "cs", "dk" to "da", "se" to "sv", "no" to "no", "pt" to "pt",
        "hu" to "hu", "fi" to "fi", "jp" to "ja",
    )

    private val knownForPattern = Regex(
        "known for|famous for|noted for|renowned for|best known|famous as|well known|" +
            "known as the|bekannt für|bekannt durch|berühmt",
        RegexOption.IGNORE_CASE,
    )
    private val sentenceSplit = Regex("(?<=[.!?])\\s+")
    private val level2Heading = Regex("^==\\s*([^=].*?)\\s*==$")
    private val boringWords = Regex(
        "\\b(street|road|avenue|school|constituency|company|electoral)\\b|" +
            "straße|schule|gymnasium|wahlkreis|unternehmen",
        RegexOption.IGNORE_CASE,
    )
    private val thumbSize = Regex("/\\d+px-")

    // ---------------------------------------------------------------- Nominatim

    suspend fun reverseGeocode(location: LatLon, level: Level = Level.CITY): PlaceName {
        val lat = String.format(Locale.US, "%.3f", location.lat)
        val lon = String.format(Locale.US, "%.3f", location.lon)
        val key = "$level:$lat,$lon"
        placeCache[key]?.let { return it }

        val url = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=${level.nominatimZoom}" +
            "&addressdetails=1&extratags=1&accept-language=en&lat=$lat&lon=$lon"
        val body = nominatimMutex.withLock {
            placeCache[key]?.let { return it }
            val waitMs = NOMINATIM_MIN_INTERVAL_MS - (System.nanoTime() - lastNominatimNanos) / 1_000_000
            if (lastNominatimNanos != 0L && waitMs > 0) delay(waitMs)
            try {
                withContext(Dispatchers.IO) { Http.get(url) }
            } finally {
                lastNominatimNanos = System.nanoTime()
            }
        }
        val place = parseNominatim(body, level)
        placeCache[key] = place
        return place
    }

    fun parseNominatim(body: String, level: Level = Level.CITY): PlaceName {
        val root = JSONObject(body)
        val address = root.optJSONObject("address")
        if (root.has("error") || address == null) {
            return PlaceName(FALLBACK_TOWN_NAME, "", null, null, null, found = false, level = level)
        }
        val nameKeys = when (level) {
            Level.CITY -> listOf("city", "town", "village", "municipality", "hamlet", "suburb")
            Level.STATE -> listOf("state", "province", "region", "state_district", "county")
            Level.COUNTRY -> listOf("country")
        }
        val subtitleKeys = when (level) {
            Level.CITY -> listOf("county", "state", "country")
            Level.STATE -> listOf("country")
            Level.COUNTRY -> emptyList()
        }
        val town = nameKeys.firstNotNullOfOrNull { address.str(it) }
        val name = town ?: FALLBACK_TOWN_NAME
        val subtitle = subtitleKeys
            .mapNotNull { address.str(it) }
            .filter { !it.equals(name, ignoreCase = true) }
            .distinct()
            .joinToString(", ")

        var wikidata: String? = null
        var wikipedia: String? = null
        val objectName = root.str("name")
        if (town != null && objectName != null && objectName.equals(town, ignoreCase = true)) {
            val extra = root.optJSONObject("extratags")
            wikidata = extra?.str("wikidata")?.takeIf { it.matches(Regex("Q\\d+")) }
            wikipedia = extra?.str("wikipedia")
        }
        return PlaceName(
            name = name,
            subtitle = subtitle,
            countryCode = address.str("country_code")?.lowercase(Locale.US),
            wikidataId = wikidata,
            wikipediaTag = wikipedia,
            found = town != null,
            level = level,
            stateName = listOf("state", "province", "region").firstNotNullOfOrNull { address.str(it) },
            countryName = address.str("country"),
        )
    }

    // ---------------------------------------------------------------- Town article

    /** Returns null when no usable article exists; throws on network failure. */
    suspend fun townInfo(place: PlaceName): TownInfo? {
        if (!place.found) return null
        val cacheKey = place.wikidataId ?: "${place.countryCode}:${place.name}"
        townCache[cacheKey]?.let { return it }

        return withContext(Dispatchers.IO) {
            for ((lang, title) in articleCandidates(place)) {
                val summary = fetchSummary(lang, title) ?: continue
                val fullText = try {
                    fetchExtract(lang, summary.title)
                } catch (e: HttpStatusException) {
                    null
                }
                val history = fullText?.let { extractHistory(it) }
                val qid = summary.wikidataId ?: place.wikidataId
                val image = summary.imageUrl ?: qid?.let {
                    try {
                        commonsImage(it)
                    } catch (e: java.io.IOException) {
                        null
                    }
                }
                val info = summary.copy(
                    imageUrl = image,
                    wikidataId = qid,
                    knownFor = fullText?.let { extractKnownFor(it) } ?: emptyList(),
                    history = history?.second,
                    historyHeading = history?.first,
                )
                townCache[cacheKey] = info
                return@withContext info
            }
            null
        }
    }

    private fun articleCandidates(place: PlaceName): List<Pair<String, String>> {
        val candidates = mutableListOf<Pair<String, String>>()
        val qid = place.wikidataId
        if (qid != null) {
            val url = "https://www.wikidata.org/w/api.php?action=wbgetentities&format=json" +
                "&props=sitelinks&sitefilter=enwiki%7Cdewiki&ids=$qid"
            val links = try {
                JSONObject(Http.get(url)).optJSONObject("entities")
                    ?.optJSONObject(qid)?.optJSONObject("sitelinks")
            } catch (e: HttpStatusException) {
                null
            }
            links?.optJSONObject("enwiki")?.str("title")?.let { candidates += "en" to it }
            links?.optJSONObject("dewiki")?.str("title")?.let { candidates += "de" to it }
        }
        if (candidates.isEmpty()) {
            val tag = place.wikipediaTag
            val colon = tag?.indexOf(':') ?: -1
            if (tag != null && colon in 1 until tag.length - 1) {
                candidates += tag.substring(0, colon).trim() to tag.substring(colon + 1).trim()
            }
            candidates += "en" to place.name
        }
        return candidates.distinct()
    }

    /** The Wikidata "image" (P18) of an item, as an 800 px Wikimedia Commons URL. */
    private fun commonsImage(qid: String): String? {
        val url = "https://www.wikidata.org/w/api.php?action=wbgetclaims&format=json&property=P18&entity=$qid"
        return parseCommonsImage(Http.get(url))
    }

    fun parseCommonsImage(body: String): String? {
        val file = JSONObject(body).optJSONObject("claims")?.optJSONArray("P18")
            ?.optJSONObject(0)?.optJSONObject("mainsnak")?.optJSONObject("datavalue")
            ?.str("value") ?: return null
        val encoded = URLEncoder.encode(file.replace(' ', '_'), "UTF-8").replace("+", "%20")
        return "https://commons.wikimedia.org/wiki/Special:FilePath/$encoded?width=800"
    }

    /** Wikipedia thumbnail URLs can be resized by rewriting their "/NNNpx-" part. */
    fun resizeThumbnail(url: String, px: Int): String = url.replace(thumbSize, "/${px}px-")

    private fun fetchSummary(lang: String, title: String): TownInfo? {
        val path = URLEncoder.encode(title.replace(' ', '_'), "UTF-8").replace("+", "%20")
        val body = try {
            Http.get("https://$lang.wikipedia.org/api/rest_v1/page/summary/$path")
        } catch (e: HttpStatusException) {
            if (e.code == 404) return null
            throw e
        }
        return parseSummary(lang, body)
    }

    fun parseSummary(lang: String, body: String): TownInfo? {
        val root = JSONObject(body)
        if (root.str("type") == "disambiguation") return null
        val title = root.optJSONObject("titles")?.str("normalized") ?: root.str("title") ?: return null

        val original = root.optJSONObject("originalimage")
        val thumb = root.optJSONObject("thumbnail")?.str("source")
        val image = when {
            original != null && original.optInt("width", Int.MAX_VALUE) <= 1600 -> original.str("source")
            thumb != null -> resizeThumbnail(thumb, 800)
            else -> null
        }
        return TownInfo(
            title = title,
            lang = lang,
            description = root.str("description"),
            summary = root.str("extract"),
            imageUrl = image,
            articleUrl = root.optJSONObject("content_urls")?.optJSONObject("mobile")?.str("page")
                ?: "https://$lang.m.wikipedia.org/wiki/" + URLEncoder.encode(title.replace(' ', '_'), "UTF-8"),
            wikidataId = root.str("wikibase_item"),
            knownFor = emptyList(),
            history = null,
            historyHeading = null,
        )
    }

    private fun fetchExtract(lang: String, title: String): String? {
        val url = "https://$lang.wikipedia.org/w/api.php?action=query&format=json&formatversion=2" +
            "&prop=extracts&explaintext=1&exsectionformat=wiki&redirects=1&titles=" +
            URLEncoder.encode(title, "UTF-8")
        val pages = JSONObject(Http.get(url)).optJSONObject("query")?.optJSONArray("pages") ?: return null
        return pages.optJSONObject(0)?.str("extract")
    }

    fun extractKnownFor(text: String): List<String> {
        val body = text.lines()
            .filterNot { it.trim().startsWith("==") }
            .joinToString(" ")
        return body.split(sentenceSplit)
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.length in 30..320 && knownForPattern.containsMatchIn(it) }
            .distinct()
            .take(4)
    }

    /** Returns (heading, text) of the first level-2 "History"/"Geschichte" section. */
    fun extractHistory(text: String): Pair<String, String>? {
        val lines = text.lines()
        var start = -1
        var heading = ""
        for (i in lines.indices) {
            val match = level2Heading.matchEntire(lines[i].trim()) ?: continue
            val h = match.groupValues[1]
            if (h.startsWith("History", ignoreCase = true) || h.startsWith("Geschichte", ignoreCase = true)) {
                start = i
                heading = h
                break
            }
        }
        if (start < 0) return null

        val paragraphs = mutableListOf<String>()
        for (i in start + 1 until lines.size) {
            val line = lines[i].trim()
            if (level2Heading.matches(line)) break
            if (line.startsWith("=")) continue
            if (line.isNotEmpty()) paragraphs += line
        }
        var history = paragraphs.joinToString("\n\n")
        if (history.isBlank()) return null
        if (history.length > MAX_HISTORY_CHARS) {
            val cut = history.substring(0, MAX_HISTORY_CHARS)
            val end = cut.lastIndexOfAny(charArrayOf('.', '!', '?'))
            history = if (end > MAX_HISTORY_CHARS / 3) cut.substring(0, end + 1) else cut.trimEnd() + "…"
        }
        return heading to history
    }

    fun firstSentence(text: String?): String? =
        text?.trim()?.split(sentenceSplit)?.firstOrNull()?.takeIf { it.isNotBlank() }

    /** Body text for the "Now passing …" notification. */
    fun alertText(place: PlaceName, town: TownInfo?): String =
        town?.knownFor?.firstOrNull()
            ?: firstSentence(town?.summary)
            ?: town?.description
            ?: place.subtitle

    // ---------------------------------------------------------------- Nearby places

    /** Throws only when every language failed. */
    suspend fun nearby(location: LatLon, place: PlaceName): List<NearbyPlace> = coroutineScope {
        val localLang = place.countryCode?.let { countryLanguages[it] }?.takeIf { it != "en" }
        val en = async(Dispatchers.IO) { runCatching { geosearch("en", location) } }
        val local = localLang?.let { lang -> async(Dispatchers.IO) { runCatching { geosearch(lang, location) } } }

        val enResult = en.await()
        val localResult = local?.await()
        if (enResult.isFailure && (localResult == null || localResult.isFailure)) {
            throw enResult.exceptionOrNull()!!
        }
        val merged = mergeAndRank(
            enResult.getOrDefault(emptyList()),
            localResult?.getOrDefault(emptyList()) ?: emptyList(),
        )
        withoutTown(merged, place.wikidataId, listOf(place.name))
    }

    private fun geosearch(lang: String, location: LatLon): List<NearbyPlace> {
        val coord = String.format(Locale.US, "%.5f%%7C%.5f", location.lat, location.lon)
        val url = "https://$lang.wikipedia.org/w/api.php?action=query&format=json&formatversion=2" +
            "&redirects=1&generator=geosearch&ggscoord=$coord&ggsradius=$NEARBY_RADIUS_M&ggslimit=50" +
            "&prop=coordinates%7Cpageimages%7Cdescription%7Cinfo%7Cpageprops&colimit=max" +
            "&piprop=thumbnail&pithumbsize=480&pilimit=max&ppprop=wikibase_item"
        return parseGeosearch(lang, location, Http.get(url))
    }

    fun parseGeosearch(lang: String, origin: LatLon, body: String): List<NearbyPlace> {
        val pages = JSONObject(body).optJSONObject("query")?.optJSONArray("pages") ?: return emptyList()
        val result = mutableListOf<NearbyPlace>()
        for (i in 0 until pages.length()) {
            val page = pages.optJSONObject(i) ?: continue
            val title = page.str("title") ?: continue
            val coords = page.optJSONArray("coordinates")?.optJSONObject(0) ?: continue
            if (!coords.has("lat") || !coords.has("lon")) continue
            val loc = LatLon(coords.getDouble("lat"), coords.getDouble("lon"))
            result += NearbyPlace(
                title = title,
                description = page.str("description"),
                location = loc,
                distanceKm = haversineKm(origin, loc),
                thumbnailUrl = page.optJSONObject("thumbnail")?.str("source"),
                lang = lang,
                wikidataId = page.optJSONObject("pageprops")?.str("wikibase_item"),
                articleLength = page.optLong("length", 0L),
            )
        }
        return result
    }

    fun mergeAndRank(english: List<NearbyPlace>, local: List<NearbyPlace>): List<NearbyPlace> {
        val seen = HashSet<String>()
        val merged = mutableListOf<NearbyPlace>()
        for (p in english) {
            val qid = p.wikidataId
            if (qid == null || seen.add(qid)) merged += p
        }
        for (p in local) {
            val qid = p.wikidataId ?: continue
            if (seen.add(qid)) merged += p
        }
        return merged
            .filterNot { isBoring(it) }
            .sortedByDescending { score(it) }
            .take(MAX_NEARBY)
    }

    private fun score(p: NearbyPlace): Double = p.articleLength / (1.0 + p.distanceKm / 4.0)

    fun isBoring(p: NearbyPlace): Boolean {
        if (p.title.endsWith("straße", ignoreCase = true) || p.title.endsWith(" Street")) return true
        return boringWords.containsMatchIn(p.title) ||
            (p.description != null && boringWords.containsMatchIn(p.description))
    }

    /** Places with a photo come first: they are the "main attractions" shown on the map. */
    fun attractions(places: List<NearbyPlace>): List<NearbyPlace> =
        places.filter { it.thumbnailUrl != null }.take(MAX_ATTRACTIONS)

    /** Adds the first sentences of each article to the attractions and the next few places. */
    suspend fun withSummaries(places: List<NearbyPlace>): List<NearbyPlace> = coroutineScope {
        val top = attractions(places)
        val wanted = (top + places.filterNot { it in top }).take(MAX_SUMMARIES).filter { it.summary == null }
        val fetched = wanted.groupBy { it.lang }.map { (lang, group) ->
            async(Dispatchers.IO) {
                lang to try {
                    fetchIntros(lang, group.map { it.title })
                } catch (e: java.io.IOException) {
                    emptyMap()
                } catch (e: org.json.JSONException) {
                    emptyMap()
                }
            }
        }.map { it.await() }.toMap()
        places.map { p -> fetched[p.lang]?.get(p.title)?.let { p.copy(summary = it) } ?: p }
    }

    private fun fetchIntros(lang: String, titles: List<String>): Map<String, String> {
        if (titles.isEmpty()) return emptyMap()
        val joined = titles.joinToString("%7C") { URLEncoder.encode(it, "UTF-8") }
        val url = "https://$lang.wikipedia.org/w/api.php?action=query&format=json&formatversion=2" +
            "&prop=extracts&exintro=1&explaintext=1&exsentences=4&exlimit=max&redirects=1&titles=$joined"
        return parseIntros(Http.get(url))
    }

    /** Title -> intro text; requested titles that were normalized or redirected map to the same text. */
    fun parseIntros(body: String): Map<String, String> {
        val query = JSONObject(body).optJSONObject("query") ?: return emptyMap()
        val result = HashMap<String, String>()
        val pages = query.optJSONArray("pages")
        for (i in 0 until (pages?.length() ?: 0)) {
            val page = pages!!.optJSONObject(i) ?: continue
            val title = page.str("title") ?: continue
            val extract = page.str("extract")?.trim()?.replace(Regex("\\s+"), " ") ?: continue
            result[title] = extract
        }
        // Redirects first, so a title that was normalized and then redirected resolves fully.
        for (key in listOf("redirects", "normalized")) {
            val arr = query.optJSONArray(key) ?: continue
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val from = o.str("from") ?: continue
                val to = o.str("to") ?: continue
                result[to]?.let { result[from] = it }
            }
        }
        return result
    }

    fun withoutTown(list: List<NearbyPlace>, townQid: String?, townTitles: Collection<String>): List<NearbyPlace> =
        list.filterNot { p ->
            (townQid != null && p.wikidataId == townQid) ||
                townTitles.any { it.equals(p.title, ignoreCase = true) }
        }

    // ---------------------------------------------------------------- helpers

    private fun JSONObject.str(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun <T> lruCache(maxSize: Int): MutableMap<String, T> =
        Collections.synchronizedMap(object : LinkedHashMap<String, T>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, T>?) = size > maxSize
        })
}
