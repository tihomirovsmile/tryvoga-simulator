package space.tihsmile.trivogasim.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max

/** Geometry is from OpenStreetMap via darmat1/ukraine-geo-data (ODbL).
 *  The application never downloads live alarm status or draws other countries.
 *  Documents are cached after successful first download. */
data class MapShape(val name: String, val polygons: List<List<Pair<Double, Double>>>) {
    val points: List<Pair<Double, Double>> by lazy { polygons.flatten() }
    val west: Double get() = points.minOfOrNull { it.first } ?: 22.0
    val east: Double get() = points.maxOfOrNull { it.first } ?: 40.0
    val south: Double get() = points.minOfOrNull { it.second } ?: 44.0
    val north: Double get() = points.maxOfOrNull { it.second } ?: 52.0
    val center: Pair<Double, Double> get() = ((west + east) / 2) to ((north + south) / 2)
    fun contains(x: Double, y: Double): Boolean {
        return polygons.any { ring ->
            if (ring.size < 3) return@any false
            var hit = false
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

object GeoRepository {
    private const val BASE = "https://cdn.jsdelivr.net/gh/darmat1/ukraine-geo-data@main/geodata/"
    private val translit = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "h", 'ґ' to "g", 'д' to "d", 'е' to "e",
        'є' to "ye", 'ж' to "zh", 'з' to "z", 'и' to "y", 'і' to "i", 'ї' to "yi",
        'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o",
        'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f",
        'х' to "kh", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "shch",
        'ь' to "", 'ю' to "yu", 'я' to "ya", 'ы' to "y", 'э' to "e", 'ё' to "yo"
    )
    fun slug(name: String): String = name.lowercase().map { translit[it] ?: it.toString() }
        .joinToString("").replace(Regex("[^a-z0-9 ]"), "_")
        .replace(' ', '_').replace(Regex("_+"), "_").trim('_')

    suspend fun load(context: Context, path: List<String>): List<MapShape> = withContext(Dispatchers.IO) {
        require(path.size <= 3)
        val filename = if (path.isEmpty()) "Ukraine" else path.joinToString(".")
        val dir = File(context.filesDir, "geo_cache").apply { mkdirs() }
        val file = File(dir, "$filename.geojson")
        if (!file.exists() || file.length() < 20L) {
            // Build-time offline cache (country and districts) is optional.
            try {
                context.assets.open("geo/$filename.geojson").use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (_: java.io.IOException) { }
        }
        if (!file.exists() || file.length() < 20L) {
            val connection = URL("$BASE$filename.geojson").openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Accept", "application/geo+json,application/json")
            try {
                if (connection.responseCode !in 200..299) error("Геодані не знайдено: HTTP ${connection.responseCode}")
                val temporary = File(dir, "$filename.tmp")
                connection.inputStream.use { stream -> temporary.outputStream().use { stream.copyTo(it) } }
                if (temporary.length() > 16 * 1024 * 1024) { temporary.delete(); error("Завеликий файл карти") }
                if (!temporary.renameTo(file)) { temporary.copyTo(file, overwrite = true); temporary.delete() }
            } finally { connection.disconnect() }
        }
        parse(file.readText(Charsets.UTF_8))
    }

    fun parse(raw: String): List<MapShape> {
        val root = JSONObject(raw)
        val features = root.getJSONArray("features")
        val result = ArrayList<MapShape>(features.length())
        for (i in 0 until features.length()) {
            val obj = features.getJSONObject(i)
            val props = obj.optJSONObject("properties") ?: continue
            val translated = props.optString("name:uk", "")
            val name = if (translated.isNotBlank() && translated != "null") translated
                else props.optString("name")
            val geom = obj.optJSONObject("geometry") ?: continue
            val rings = mutableListOf<List<Pair<Double, Double>>>()
            when (geom.optString("type")) {
                "Polygon" -> addPolygon(geom.getJSONArray("coordinates"), rings)
                "MultiPolygon" -> {
                    val polygons = geom.getJSONArray("coordinates")
                    for (p in 0 until polygons.length()) addPolygon(polygons.getJSONArray(p), rings)
                }
            }
            if (name.isNotBlank() && rings.isNotEmpty()) result += MapShape(name, rings)
        }
        return result
    }

    private fun addPolygon(array: JSONArray, result: MutableList<List<Pair<Double, Double>>>) {
        // Only the outer boundary of each polygon. Interior holes aren't administrative units.
        if (array.length() == 0) return
        val ring = array.getJSONArray(0)
        val points = ArrayList<Pair<Double, Double>>(ring.length())
        for (i in 0 until ring.length()) {
            val coordinate = ring.getJSONArray(i)
            points += coordinate.getDouble(0) to coordinate.getDouble(1)
        }
        if (points.size >= 3) result += points
    }
}
