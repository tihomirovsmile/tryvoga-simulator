package space.tihsmile.trivogasim.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** KATOTTG register, with a small built-in fallback. No live alarm API. */
object RegionCatalog {
    @Volatile var regions: List<Region> = emptyList()
        private set
    @Volatile var byId: Map<String, Region> = emptyMap()
        private set
    @Volatile var coordinates: Map<String, Pair<Double, Double>> = emptyMap()
        private set
    val generation = MutableStateFlow(0)
    @Volatile private var legacy: List<Region> = emptyList()

    private fun parseJson(data: String): Pair<List<Region>, Map<String, Pair<Double, Double>>> {
        val array = JSONArray(data)
        val result = ArrayList<Region>(array.length())
        val locations = HashMap<String, Pair<Double, Double>>()
        for (index in 0 until array.length()) {
            val obj = array.getJSONObject(index)
            val id = obj.getString("id")
            result += Region(id, obj.getString("name"), obj.getString("type"),
                obj.optString("parentId").takeIf { it.isNotBlank() && it != "null" })
            val lat = obj.optDouble("lat", Double.NaN)
            val lon = obj.optDouble("lon", Double.NaN)
            if (lat.isFinite() && lon.isFinite() && lat in 43.0..53.0 && lon in 21.0..42.0)
                locations[id] = lon to lat
        }
        return result to locations
    }

    @Synchronized fun initialize(context: Context) {
        if (regions.isNotEmpty()) return
        val fallback = context.assets.open("regions_uk.json").bufferedReader().use { it.readText() }
        legacy = parseJson(fallback).first
        val downloaded = File(context.filesDir, "katottg_regions.json")
        val loaded = try {
            if (downloaded.exists()) parseJson(downloaded.readText(Charsets.UTF_8))
            else context.assets.open("regions_full.json").bufferedReader().use { parseJson(it.readText()) }
        } catch (_: Exception) { legacy to emptyMap() }
        install(loaded.first, loaded.second)
    }

    @Synchronized private fun install(data: List<Region>, points: Map<String, Pair<Double, Double>>) {
        val newList = if (data.size > legacy.size * 2) data else legacy
        val all = (newList + legacy).distinctBy { it.id }
        regions = newList
        byId = all.associateBy { it.id }
        coordinates = points
        generation.value += 1
    }

    /** Downloads the full ~31,744-entry register once, caches it and broadcasts a UI refresh.
     *  If unavailable, keep the local fallback and allow retry later.
     */
    suspend fun updateFull(context: Context): Boolean = withContext(Dispatchers.IO) {
        initialize(context)
        if (regions.size > 25_000) return@withContext true
        try {
            val remote = URL("https://raw.githubusercontent.com/alexbabintsev/ua-geo/main/data/out/katottg.csv")
            val conn = remote.openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 50_000
            val lines = try {
                if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    val header = splitCsv(reader.readLine() ?: error("CSV header missing"))
                    val idx = header.withIndex().associate { it.value.removePrefix("\uFEFF") to it.index }
                    fun field(row: List<String>, name: String): String = idx[name]?.let { row.getOrNull(it) }.orEmpty()
                    val all = mutableListOf<JSONObject>()
                    reader.forEachLine { line ->
                        val row = splitCsv(line)
                        val id = field(row, "code")
                        val name = field(row, "full_name").ifBlank { field(row, "name") }
                        val lvl = field(row, "level").toIntOrNull() ?: 0
                        val type = when (lvl) {
                            1 -> "OBLAST"
                            2 -> "RAYON"
                            3 -> "HROMADA"
                            4 -> "SETTLEMENT"
                            else -> ""
                        }
                        if (id.isBlank() || name.isBlank() || type.isBlank()) return@forEachLine
                        val obj = JSONObject().put("id", id).put("name", name).put("type", type)
                            .put("parentId", field(row, "parent_code"))
                        field(row, "lat").toDoubleOrNull()?.let { obj.put("lat", it) }
                        field(row, "lon").toDoubleOrNull()?.let { obj.put("lon", it) }
                        all += obj
                    }
                    JSONArray(all).toString()
                }
            } finally { conn.disconnect() }
            val parsed = parseJson(lines)
            if (parsed.first.size < 25_000) error("Incomplete KATOTTG response")
            val output = File(context.filesDir, "katottg_regions.json")
            val temp = File(context.filesDir, "katottg_regions.tmp")
            temp.writeText(lines, Charsets.UTF_8)
            if (!temp.renameTo(output)) { temp.copyTo(output, overwrite = true); temp.delete() }
            install(parsed.first, parsed.second)
            true
        } catch (_: Exception) { false }
    }

    private fun splitCsv(s: String): List<String> {
        val result = ArrayList<String>()
        val builder = StringBuilder()
        var quotes = false
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '"' && quotes && i + 1 < s.length && s[i + 1] == '"' -> { builder.append('"'); i++ }
                c == '"' -> quotes = !quotes
                c == ',' && !quotes -> { result += builder.toString(); builder.setLength(0) }
                else -> builder.append(c)
            }
            i++
        }
        result += builder.toString()
        return result
    }

    @Synchronized internal fun installForUnitTests(list: List<Region>) {
        legacy = emptyList(); install(list, emptyMap())
    }

    fun getName(id: String): String = byId[id]?.name ?: id
    fun type(id: String): String = byId[id]?.type ?: ""
    fun lineage(id: String): List<String> {
        val result = ArrayList<String>()
        val visited = HashSet<String>()
        var node: String? = id
        while (node != null && visited.add(node)) {
            result += node
            node = byId[node]?.parentId
        }
        return result
    }

    fun oblastId(id: String): String? = lineage(id).firstOrNull { type(it) == "OBLAST" }
    private fun normalizedName(region: Region): String = region.name.lowercase(Locale("uk", "UA"))
        .removePrefix("м. ").replace(Regex("\\s+"), " ").trim()
    private fun signature(id: String): String {
        val r = byId[id] ?: return id
        val oblast = if (r.type == "OBLAST") r else oblastId(id)?.let { byId[it] }
        return "${r.type}|${normalizedName(r)}|${oblast?.let { normalizedName(it) } ?: ""}"
    }
    fun matches(eventRegion: String, subscription: String): Boolean {
        val first = lineage(eventRegion).map(::signature).toSet()
        val second = lineage(subscription).map(::signature).toSet()
        return signature(eventRegion) in second || signature(subscription) in first
    }

    fun signatureOf(id: String): String = signature(id)
    fun pointFor(id: String): Pair<Double, Double>? = coordinates[id] ?: run {
        val wanted = signature(id)
        regions.firstOrNull { signature(it.id) == wanted && coordinates.containsKey(it.id) }
            ?.let { coordinates[it.id] }
    }
    fun isSettlement(id: String): Boolean = type(id) == "SETTLEMENT"
    fun isPermanentTerritory(id: String): Boolean {
        val oblast = oblastId(id)?.let(::getName) ?: getName(id)
        return oblast == "Луганська область" || oblast == "Автономна Республіка Крим"
    }

    fun deliverySubscription(regionId: String, follows: List<Subscription>, isThreat: Boolean): String? =
        follows.map { it.regionId }
            .filter { matches(regionId, it) && (!isThreat || type(it) != "OBLAST") }
            .maxByOrNull { lineage(it).size }

    fun locationCase(id: String): String {
        val region = byId[id] ?: return "у $id"
        val s = region.name
        if (region.type == "OBLAST" && s.endsWith(" область")) {
            val base = s.removeSuffix(" область")
            val form = when {
                base.endsWith("ська") -> base.dropLast(4) + "ській"
                base.endsWith("цька") -> base.dropLast(4) + "цькій"
                else -> base
            }
            return "у $form області"
        }
        if (region.type == "RAYON" && s.endsWith(" район")) {
            val root = s.removeSuffix(" район")
            return "у " + (if (root.endsWith("ий")) root.dropLast(2) + "ому" else root) + " районі"
        }
        val cases = mapOf("Кременчук" to "Кременчуці", "Світловодськ" to "Світловодську",
            "Полтава" to "Полтаві", "Київ" to "Києві", "Харків" to "Харкові", "Одеса" to "Одесі",
            "Дніпро" to "Дніпрі", "Львів" to "Львові", "Кропивницький" to "Кропивницькому")
        return if (region.type == "SETTLEMENT") "у " + (cases[s.removePrefix("м. ")] ?: s)
        else "у $s"
    }
}
