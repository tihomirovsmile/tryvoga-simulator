package space.tihsmile.trivogasim.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPOutputStream
import org.json.JSONArray
import org.json.JSONObject
import java.util.zip.GZIPInputStream

/** Preprocessed, app-bundled vector polygons; no map API, no network access at runtime.
 * Source attribution: © OpenStreetMap contributors, ODbL (derived offline geometry). */
data class MapShape(val name: String, val polygons: List<List<Pair<Double, Double>>>) {
    val points: List<Pair<Double, Double>> by lazy { polygons.flatten() }
    val west by lazy { points.minOfOrNull { it.first } ?: 22.0 }
    val east by lazy { points.maxOfOrNull { it.first } ?: 40.0 }
    val south by lazy { points.minOfOrNull { it.second } ?: 44.0 }
    val north by lazy { points.maxOfOrNull { it.second } ?: 52.0 }
    fun contains(x: Double, y: Double): Boolean {
        if (x < west || x > east || y < south || y > north) return false
        return polygons.any { ring ->
            var hit = false
            if (ring.size < 3) return@any false
            var previous = ring.last()
            for (current in ring) {
                if ((current.second > y) != (previous.second > y) &&
                    x < (previous.first - current.first) * (y - current.second) /
                        (previous.second - current.second) + current.first) hit = !hit
                previous = current
            }
            hit
        }
    }
}

data class OfflineGeometry(val provinces: List<MapShape>, val raions: List<Pair<String, MapShape>>)

object GeoRepository {
    @Volatile private var snapshot: OfflineGeometry? = null

    suspend fun offline(context: Context): OfflineGeometry = withContext(Dispatchers.IO) {
        snapshot?.let { return@withContext it }
        snapshot ?: run {
                val localFile = File(context.filesDir, "ukraine_offline.json.gz")
                val stream = try { context.assets.open("geo/ukraine_offline.json.gz") }
                catch (_: java.io.IOException) {
                    if (!localFile.exists()) {
                        // One-time fallback for APKs built directly in AndroidIDE, without the build pipeline.
                        // Download static administrative geometries, compile and cache locally.
                        downloadOfflineMap(context, localFile)
                    }
                    localFile.inputStream()
                }
                val json = GZIPInputStream(stream).bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
                val result = OfflineGeometry(readShapes(json.getJSONArray("oblasts")).map { it.second },
                    readShapes(json.getJSONArray("raions")))
                require(result.provinces.size >= 23 && result.raions.size >= 100) {
                    "Недостатньо контурів для офлайн-мапи"
                }
                snapshot = result
                result
        }
    }

    private val translit = mapOf('а' to "a", 'б' to "b", 'в' to "v", 'г' to "h", 'ґ' to "g",
        'д' to "d", 'е' to "e", 'є' to "ye", 'ж' to "zh", 'з' to "z", 'и' to "y",
        'і' to "i", 'ї' to "yi", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m",
        'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
        'у' to "u", 'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch",
        'ш' to "sh", 'щ' to "shch", 'ь' to "", 'ю' to "yu", 'я' to "ya")
    private fun slug(s: String): String = s.lowercase().map { translit[it] ?: it.toString() }
        .joinToString("").replace(Regex("[^a-z0-9]+"), "_").trim('_')

    /** Static GitHub dataset, not a live OSM map service. Runs only if the APK has no packed geometry. */
    private suspend fun downloadOfflineMap(context: Context, target: File) = coroutineScope {
        val cache = File(context.filesDir, "geo_sources").apply { mkdirs() }
        val base = "https://cdn.jsdelivr.net/gh/darmat1/ukraine-geo-data@main/geodata/"
        suspend fun file(name: String): JSONObject = withContext(Dispatchers.IO) {
            val path = File(cache, "$name.geojson")
            if (!path.exists() || path.length() < 100L) {
                val conn = URL("$base$name.geojson").openConnection() as HttpURLConnection
                conn.connectTimeout = 12_000; conn.readTimeout = 28_000
                try {
                    check(conn.responseCode == 200) { "HTTP ${conn.responseCode}: $name" }
                    val tmp = File(cache, "$name.download")
                    conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                    require(tmp.length() < 15_000_000L)
                    tmp.renameTo(path)
                } finally { conn.disconnect() }
            }
            JSONObject(path.readText(Charsets.UTF_8))
        }
        fun parseDocument(document: JSONObject, parent: String): List<JSONObject> {
            val result = ArrayList<JSONObject>()
            val features = document.getJSONArray("features")
            for (i in 0 until features.length()) {
                val feature = features.getJSONObject(i)
                val props = feature.optJSONObject("properties") ?: continue
                val translated = props.optString("name:uk")
                val name = translated.takeIf { it.isNotBlank() && it != "null" }
                    ?: props.optString("name")
                val geom = feature.optJSONObject("geometry") ?: continue
                val polys = when (geom.optString("type")) {
                    "Polygon" -> listOf(geom.getJSONArray("coordinates"))
                    "MultiPolygon" -> {
                        val mp = geom.getJSONArray("coordinates")
                        (0 until mp.length()).map { mp.getJSONArray(it) }
                    }
                    else -> emptyList()
                }
                val ringList = JSONArray()
                for (poly in polys) {
                    if (poly.length() == 0) continue
                    val rawRing = poly.getJSONArray(0)
                    val simplified = JSONArray()
                    var lastX = Double.NaN; var lastY = Double.NaN
                    for (j in 0 until rawRing.length()) {
                        val pt = rawRing.getJSONArray(j)
                        val x = pt.getDouble(0); val y = pt.getDouble(1)
                        if (j == 0 || j == rawRing.length() - 1 ||
                            kotlin.math.abs(x-lastX) + kotlin.math.abs(y-lastY) > 0.0015) {
                            simplified.put(JSONArray().put(x).put(y)); lastX=x; lastY=y
                        }
                    }
                    if (simplified.length() >= 3) ringList.put(simplified)
                }
                if (name.isNotBlank() && ringList.length() > 0)
                    result += JSONObject().put("n", name).put("o", parent).put("p", ringList)
            }
            return result
        }
        val country = parseDocument(file("Ukraine"), "")
        val semaphore = Semaphore(4)
        val districts = country.map { oblast ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    try { parseDocument(file(slug(oblast.getString("n"))), oblast.getString("n")) }
                    catch (_: Exception) { emptyList<JSONObject>() }
                }
            }
        }.awaitAll().flatten()
        require(country.size >= 23 && districts.size >= 100) { "Неповні геодані: ${districts.size} районів" }
        val packed = JSONObject().put("oblasts", JSONArray(country)).put("raions", JSONArray(districts))
        val tmp = File(context.filesDir, "ukraine_offline.partial")
        GZIPOutputStream(tmp.outputStream().buffered(), 64 * 1024).bufferedWriter(Charsets.UTF_8).use {
            it.write(packed.toString())
        }
        if (!tmp.renameTo(target)) { tmp.copyTo(target, overwrite = true); tmp.delete() }
    }

    private fun readShapes(array: JSONArray): List<Pair<String, MapShape>> {
        val output = ArrayList<Pair<String, MapShape>>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val rings = obj.getJSONArray("p")
            val polygons = ArrayList<List<Pair<Double, Double>>>(rings.length())
            for (j in 0 until rings.length()) {
                val jsonRing = rings.getJSONArray(j)
                val points = ArrayList<Pair<Double, Double>>(jsonRing.length())
                for (p in 0 until jsonRing.length()) {
                    val c = jsonRing.getJSONArray(p)
                    points += c.getDouble(0) to c.getDouble(1)
                }
                if (points.size >= 3) polygons += points
            }
            if (polygons.isNotEmpty()) output += obj.optString("o", "") to
                MapShape(obj.getString("n"), polygons)
        }
        return output
    }
}
