package space.tihsmile.trivogasim.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import space.tihsmile.trivogasim.core.AlertKind
import space.tihsmile.trivogasim.data.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

/** Static polygons are cached once and transformed as paths, not projected vertex by vertex per frame. */
private data class Projection(val size: Size, val minX: Double, val maxX: Double,
                              val minY: Double, val maxY: Double) {
    private val centerX = (minX + maxX) / 2
    private val centerY = (minY + maxY) / 2
    private val cosLat = 0.69
    private val scale = min((size.width - 16f) / ((maxX - minX) * cosLat),
        (size.height - 16f) / (maxY - minY)).toFloat().coerceAtLeast(.01f)
    fun point(lon: Double, lat: Double) = Offset(
        size.width / 2f + ((lon - centerX) * cosLat * scale).toFloat(),
        size.height / 2f - ((lat - centerY) * scale).toFloat())
    fun geo(pos: Offset): Pair<Double, Double> =
        (centerX + (pos.x - size.width / 2f) / (cosLat * scale)) to
        (centerY - (pos.y - size.height / 2f) / scale)
}

private data class DrawableTerritory(val id: String?, val shape: MapShape, val path: Path, val oblastId: String?)

private fun regionId(name: String, type: String, oblast: String? = null): String? =
    RegionCatalog.findByName(type, name, oblast)?.id

private fun makePath(shape: MapShape, projection: Projection): Path = Path().apply {
    shape.polygons.forEach { ring ->
        ring.forEachIndexed { i, (lon, lat) ->
            val at = projection.point(lon, lat)
            if (i == 0) moveTo(at.x, at.y) else lineTo(at.x, at.y)
        }
        close()
    }
}

private fun shade(level: String, ageMillis: Long, permanent: Boolean) = when {
    permanent -> Color(0xFF792026)
    level == "YELLOW" && ageMillis >= 1_800_000L -> Color(0xFF746024)
    level == "YELLOW" -> Color(0xFFD1A52A)
    ageMillis >= 1_800_000L -> Color(0xFF842832)
    else -> Color(0xFFD74A53)
}

private fun prettyTime(time: Long?): String = time?.let {
    SimpleDateFormat("HH:mm", Locale("uk", "UA")).format(Date(it))
} ?: "—"

@Composable
fun MapScreen(events: List<AlertEvent>) {
    val context = LocalContext.current
    var offline by remember { mutableStateOf<OfflineGeometry?>(null) }
    var failed by remember { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(Size.Zero) }
    var selected by remember { mutableStateOf<String?>(null) }
    val version by RegionCatalog.generation.collectAsState()
    LaunchedEffect(Unit) {
        try { offline = GeoRepository.offline(context) }
        catch (_: Exception) { failed = "Не вдалося підготувати геометрію мапи. Перевірте інтернет або додайте офлайн-контури під час збірки." }
    }
    LaunchedEffect(Unit) { while (true) { delay(15_000); now = System.currentTimeMillis() } }

    val geometry = offline
    val bounds = remember(geometry) {
        val all = geometry?.provinces.orEmpty()
        listOf(all.minOfOrNull { it.west } ?: 21.5, all.maxOfOrNull { it.east } ?: 41.4,
            all.minOfOrNull { it.south } ?: 43.3, all.maxOfOrNull { it.north } ?: 52.5)
    }
    val projection = remember(size, bounds) {
        Projection(size, bounds[0], bounds[1], bounds[2], bounds[3])
    }
    // Build immutable vector Paths off the UI thread; only transforms happen during gestures.
    var provinces by remember { mutableStateOf(emptyList<DrawableTerritory>()) }
    var districts by remember { mutableStateOf(emptyList<DrawableTerritory>()) }
    LaunchedEffect(geometry, size, version) {
        if (geometry == null || size.width < 1f || size.height < 1f) return@LaunchedEffect
        val prepared = withContext(Dispatchers.Default) {
            val provincePaths = geometry.provinces.map { shape ->
                DrawableTerritory(regionId(shape.name, "OBLAST"), shape, makePath(shape, projection), null)
            }
            val districtPaths = geometry.raions.map { (oblastName, shape) ->
                val id = regionId(shape.name, "RAYON", oblastName)
                DrawableTerritory(id, shape, makePath(shape, projection), id?.let(RegionCatalog::oblastFor))
            }
            provincePaths to districtPaths
        }
        provinces = prepared.first
        districts = prepared.second
    }
    val active = remember(events) { events.filter { it.type == "AIR" && it.status == "ACTIVE" } }
    val activeById = remember(active, version) { active.groupBy { RegionCatalog.signatureOf(it.regionId) } }
    val levelByProvince = remember(active, version) {
        active.filter { RegionCatalog.type(it.regionId) == "OBLAST" }
            .groupBy { RegionCatalog.signatureOf(it.regionId) }
    }
    val threats = events.filter { e ->
        e.status == "DELIVERED" && AlertKind.from(e.type).isOneShot &&
            (now - (e.startedAt ?: 0L)) in 0..1_800_000 &&
            events.none { cleared -> cleared.type == "AIR" && cleared.status == "ENDED" &&
                cleared.endedAt != null && cleared.endedAt >= (e.startedAt ?: 0L) &&
                RegionCatalog.matches(e.regionId, cleared.regionId) }
    }
    val markers = remember(threats, version) {
        threats.mapNotNull { e -> RegionCatalog.pointFor(e.regionId)?.let { e to it } }
    }
    val currentZoom by rememberUpdatedState(zoom)
    val currentPan by rememberUpdatedState(pan)
    val currentSize by rememberUpdatedState(size)
    val currentProjection by rememberUpdatedState(projection)
    val currentDistricts by rememberUpdatedState(districts)
    val currentProvinces by rememberUpdatedState(provinces)

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Header("Мапа України", trailing = { SimBadge() })
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Локально намальована мапа · без онлайн-підкладки", color = Muted, fontSize = 12.sp)
            TextButton(onClick = { zoom = 1f; pan = Offset.Zero }) { Text("Вся Україна", color = Accent) }
        }
        if (failed.isNotBlank()) Text(failed, color = Color(0xFFFFB76E),
            fontSize = 13.sp, modifier = Modifier.padding(16.dp))
        if (offline == null && failed.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent)
        Canvas(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 7.dp)
            .background(Color(0xFF0E1520), RoundedCornerShape(13.dp))
            .onSizeChanged { size = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(Unit) {
                detectTransformGestures { _, drag, scale, _ ->
                    zoom = (zoom * scale).coerceIn(1f, 14f)
                    val maxPan = size.width.coerceAtLeast(size.height) * zoom
                    pan = Offset((pan.x + drag.x).coerceIn(-maxPan, maxPan),
                        (pan.y + drag.y).coerceIn(-maxPan, maxPan))
                }
            }
            .pointerInput(geometry) {
                detectTapGestures { at ->
                    val sz = currentSize
                    if (sz.width < 1f) return@detectTapGestures
                    val px = (at - currentPan - Offset(sz.width / 2, sz.height / 2)) / currentZoom +
                        Offset(sz.width / 2, sz.height / 2)
                    val (lon, lat) = currentProjection.geo(px)
                    val target = if (currentZoom > 1.65f) currentDistricts.lastOrNull {
                        it.shape.contains(lon, lat)
                    } ?: currentProvinces.lastOrNull { it.shape.contains(lon, lat) }
                    else currentProvinces.lastOrNull { it.shape.contains(lon, lat) }
                    selected = target?.id
                }
            }) {
            val center = Offset(size.width / 2, size.height / 2)
            withTransform({ translate(pan.x, pan.y); scale(zoom, zoom, center) }) {
                provinces.forEach { entry ->
                    val allEvents = entry.id?.let { levelByProvince[RegionCatalog.signatureOf(it)] }.orEmpty()
                    val state = allEvents.maxByOrNull { if (it.dangerLevel == "RED") 1 else 0 }
                    drawPath(entry.path, state?.let {
                        shade(it.dangerLevel, now - (it.startedAt ?: now), it.permanent)
                    } ?: Color(0xFF1E354A))
                    drawPath(entry.path, if (entry.id == selected) Accent else Color(0xFF7597B5),
                        style = Stroke(1.0f / zoom))
                }
                districts.forEach { entry ->
                    val direct = entry.id?.let { activeById[RegionCatalog.signatureOf(it)] }.orEmpty()
                    val inherited = entry.oblastId?.let { levelByProvince[RegionCatalog.signatureOf(it)] }.orEmpty()
                    val state = (direct + inherited).maxByOrNull { if (it.dangerLevel == "RED") 1 else 0 }
                    if (state != null) drawPath(entry.path,
                        shade(state.dangerLevel, now - (state.startedAt ?: now), state.permanent))
                    if (zoom > 1.35f) drawPath(entry.path,
                        if (entry.id == selected) Accent else Color(0xFF637F99), style = Stroke(.65f / zoom))
                }
                markers.forEach { (_, geo) ->
                    val pt = projection.point(geo.first, geo.second)
                    drawCircle(Color(0xFFFFD600), radius = 7f / zoom, center = pt)
                    drawCircle(Color.Black, radius = 2.8f / zoom, center = pt)
                }
            }
        }
        Text("Два пальці — масштаб і рух. Жовті точки — повідомлення про загрози (до 30 хв).",
            fontSize = 10.sp, color = Muted, modifier = Modifier.padding(horizontal = 13.dp, vertical = 5.dp))
        if (selected != null) {
            val selection = selected!!
            val journal = events.filter { e ->
                !AlertKind.from(e.type).isOneShot && e.status in setOf("ACTIVE", "ENDED") &&
                    RegionCatalog.matches(e.regionId, selection)
            }.sortedByDescending { it.startedAt ?: 0L }.take(12)
            Column(Modifier.heightIn(max = 190.dp).padding(horizontal = 12.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(RegionCatalog.getName(selection), fontWeight = FontWeight.Bold, color = Color.White)
                    TextButton(onClick = { selected = null }) { Text("Закрити", color = Accent) }
                }
                if (journal.isEmpty()) Text("Подій немає", color = Muted, fontSize = 12.sp)
                LazyColumn {
                    items(journal, key = { it.id }) { e ->
                        val status = if (e.status == "ACTIVE") "триває" else prettyTime(e.endedAt)
                        val age = (e.endedAt ?: now) - (e.startedAt ?: now)
                        Text("${e.regionName}: ${prettyTime(e.startedAt)} — $status · ${age.coerceAtLeast(0) / 60_000} хв",
                            color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        }
        Text("Географічні межі: © OpenStreetMap contributors (ODbL), локально перероблені.",
            fontSize = 9.sp, color = Muted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
    }
}
