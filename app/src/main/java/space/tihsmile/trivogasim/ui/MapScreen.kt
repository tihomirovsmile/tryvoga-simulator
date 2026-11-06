package space.tihsmile.trivogasim.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import space.tihsmile.trivogasim.core.AlertKind
import space.tihsmile.trivogasim.data.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

private data class ViewProjection(val size: Size, val zoom: Float, val pan: Offset,
                                  val west: Double, val east: Double, val south: Double, val north: Double) {
    private val cosLatitude = .69
    private val base = min((size.width - 22f) / ((east - west) * cosLatitude),
        (size.height - 22f) / (north - south)).toFloat().coerceAtLeast(0.01f)
    private val midLon = (west + east)/2.0
    private val midLat = (south + north)/2.0
    fun project(lon: Double, lat: Double): Offset = Offset(
        size.width/2f + (((lon-midLon)*cosLatitude)*base*zoom).toFloat() + pan.x,
        size.height/2f - ((lat-midLat)*base*zoom).toFloat() + pan.y)
    fun unproject(point: Offset): Pair<Double,Double> =
        midLon + (point.x - size.width/2f - pan.x)/base/zoom/cosLatitude to
            midLat - (point.y - size.height/2f - pan.y)/base/zoom
}

private fun activeShade(level: String, age: Long, permanent: Boolean): Color = when {
    permanent -> Color(0xFF792026)
    level == "YELLOW" && age >= 1_800_000 -> Color(0xFF786327)
    level == "YELLOW" -> Color(0xFFDDAC29)
    age >= 1_800_000 -> Color(0xFF842832)
    else -> Color(0xFFD74A53)
}
private fun time(t: Long?): String = t?.let { SimpleDateFormat("HH:mm", Locale("uk","UA")).format(Date(it)) } ?: "—"
private fun duration(millis: Long): String { val m=(millis/60000L).coerceAtLeast(0);return "${m/60} год ${m%60} хв" }
private fun eventTitle(e: AlertEvent): String = e.title.ifBlank { AlertKind.from(e.type).label }

/** One canvas, no drill-down Activity. Events are rendered at country scale and reprojected on pinch-zoom. */
@Composable
fun MapScreen(events: List<AlertEvent>, onBack: (() -> Unit)? = null,
              pickType: String? = null, picked: Set<String> = emptySet(), onPick: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    var country by remember { mutableStateOf(emptyList<MapShape>()) }
    val districts = remember { mutableStateMapOf<String, List<MapShape>>() }
    val communities = remember { mutableStateMapOf<String, List<MapShape>>() }
    val settlements = remember { mutableStateMapOf<String, List<MapShape>>() }
    var canvasPixels by remember { mutableStateOf(Size.Zero) }
    var loading by remember { mutableStateOf(true) }
    var loadedCount by remember { mutableIntStateOf(0) }
    var failure by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var retry by remember { mutableIntStateOf(0) }
    val gen by RegionCatalog.generation.collectAsState()
    LaunchedEffect(Unit) { while (true) { delay(15_000); now=System.currentTimeMillis() } }
    LaunchedEffect(retry) {
        loading = true; failure = ""; loadedCount = 0; districts.clear()
        try {
            country = GeoRepository.load(context, emptyList())
            loading = false
            // Region files download concurrently and the map updates with each completed district.
            // This fixes the old UI which only drew district alerts after opening an oblast.
            val semaphore = Semaphore(4)
            val requests = country.map { shape ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        try {
                            val found = GeoRepository.load(context, listOf(GeoRepository.slug(shape.name)))
                            val name = shape.name
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                districts[name] = found; loadedCount++
                            }
                        } catch (_: Exception) {
                            kotlinx.coroutines.withContext(Dispatchers.Main) { loadedCount++ }
                        }
                    }
                }
            }
            requests.awaitAll()
        } catch (e: Exception) {
            failure = e.message ?: "Геометрія недоступна"
            loading = false
        }
    }
    val active = events.filter { it.status == "ACTIVE" && it.type == "AIR" }
    val threats = events.filter { e ->
        e.status == "DELIVERED" && AlertKind.from(e.type).isOneShot &&
            now-(e.startedAt ?: 0L) in 0..1_800_000L &&
            events.none { cleared -> cleared.type == "AIR" && cleared.status == "ENDED" &&
                RegionCatalog.matches(e.regionId, cleared.regionId) &&
                (cleared.endedAt ?: 0L) >= (e.startedAt ?: 0L) }
    }
    val countryBounds = remember(country) {
        val pts = country.flatMap { it.points }
        listOf(pts.minOfOrNull { it.first } ?: 21.8, pts.maxOfOrNull { it.first } ?: 41.3,
            pts.minOfOrNull { it.second } ?: 43.9, pts.maxOfOrNull { it.second } ?: 52.4)
    }
    LaunchedEffect(zoom, pan, canvasPixels, country, districts.size) {
        if (canvasPixels.width < 10f || country.isEmpty() || zoom < 2.5f) return@LaunchedEffect
        val screen = ViewProjection(canvasPixels,zoom,pan,countryBounds[0],countryBounds[1],countryBounds[2],countryBounds[3])
        val center = screen.unproject(Offset(canvasPixels.width/2,canvasPixels.height/2))
        val province = country.firstOrNull { it.contains(center.first,center.second) } ?: return@LaunchedEffect
        val oblSlug = GeoRepository.slug(province.name)
        val rayon = districts[province.name]?.firstOrNull { it.contains(center.first,center.second) } ?: return@LaunchedEffect
        val rayonSlug = GeoRepository.slug(rayon.name)
        val rayonKey = "$oblSlug.$rayonSlug"
        if (rayonKey !in communities) {
            try { communities[rayonKey] = GeoRepository.load(context,listOf(oblSlug,rayonSlug)) }
            catch (_: Exception) { }
        }
        if (zoom >= 7f) {
            val hromada = communities[rayonKey]?.firstOrNull { it.contains(center.first,center.second) }
                ?: return@LaunchedEffect
            val hromadaSlug = GeoRepository.slug(hromada.name)
            val key = "$rayonKey.$hromadaSlug"
            if (key !in settlements) {
                try { settlements[key] = GeoRepository.load(context,listOf(oblSlug,rayonSlug,hromadaSlug)) }
                catch (_: Exception) { }
            }
        }
    }
    val countryMap = remember(country, gen) { country.mapNotNull { s ->
        regionFor(s.name, "OBLAST")?.let { id -> id to s }
    } }
    val districtMap = remember(districts.toMap(), gen) {
        districts.toMap().flatMap { (oblast, shapes) ->
            shapes.mapNotNull { s -> regionFor(s.name, "RAYON", oblast)?.let { id -> id to s } }
        }
    }
    val marks = remember(threats, gen) { threats.mapNotNull { e ->
        RegionCatalog.pointFor(e.regionId)?.let { e to it }
    } }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Header(if (pickType != null) "Оголосити по мапі" else "Мапа", onBack, trailing = { SimBadge() })
        Row(Modifier.fillMaxWidth().padding(horizontal = 15.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Україна · масштаб ${"%.1f".format(Locale.US,zoom)}×", color = Color.White,
                fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            TextButton(onClick = { zoom=1f; pan=Offset.Zero }) { Text("На всю країну", color=Accent) }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent)
        else if (loadedCount < country.size) Text("Завантажено меж районів: $loadedCount/${country.size}", color = Muted,
            fontSize = 11.sp, modifier=Modifier.padding(start=16.dp))
        if (failure.isNotBlank()) Row(Modifier.padding(12.dp)) {
            Text("Карта: $failure", color=Muted, modifier=Modifier.weight(1f))
            TextButton(onClick={retry++}) { Text("Повторити",color=Accent) }
        }
        Canvas(Modifier.fillMaxWidth().weight(1f).padding(6.dp)
            .onSizeChanged { canvasPixels = Size(it.width.toFloat(),it.height.toFloat()) }
            .background(Color(0xFF0B111A), RoundedCornerShape(13.dp))
            .pointerInput(country, districtMap, pickType, zoom, pan, gen) {
                detectTapGestures { at ->
                    val projection = ViewProjection(Size(size.width.toFloat(),size.height.toFloat()),zoom,pan,
                        countryBounds[0],countryBounds[1],countryBounds[2],countryBounds[3])
                    val lonLat = projection.unproject(at)
                    val candidate = when(pickType) {
                        "SETTLEMENT" -> {
                            // Settlement boundaries are not available for every village. Select nearest centre.
                            val radius = 30f
                            RegionCatalog.regions.asSequence().filter { it.type == "SETTLEMENT" }
                                .mapNotNull { r -> RegionCatalog.pointFor(r.id)?.let { r.id to projection.project(it.first,it.second) } }
                                .filter { (_,pt) -> (pt-at).getDistance() < radius }.minByOrNull { (_,pt) -> (pt-at).getDistance() }?.first
                        }
                        "RAYON" -> districtMap.lastOrNull { (_,shape)->shape.contains(lonLat.first,lonLat.second) }?.first
                        "OBLAST" -> countryMap.lastOrNull { (_,shape)->shape.contains(lonLat.first,lonLat.second) }?.first
                        else -> if (zoom < 1.8f)
                            countryMap.lastOrNull { (_,shape)->shape.contains(lonLat.first,lonLat.second) }?.first
                        else districtMap.lastOrNull { (_,shape)->shape.contains(lonLat.first,lonLat.second) }?.first
                            ?: countryMap.lastOrNull { (_,shape)->shape.contains(lonLat.first,lonLat.second) }?.first
                    }
                    if (candidate != null) {
                        selected = candidate
                        onPick?.invoke(candidate)
                    }
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, drag, zoomChange, _ ->
                    zoom = (zoom * zoomChange).coerceIn(1f,24f)
                    pan = pan + drag
                    val limit = 1200f * zoom
                    pan = Offset(pan.x.coerceIn(-limit,limit),pan.y.coerceIn(-limit,limit))
                }
            }) {
            if (country.isEmpty()) return@Canvas
            val projection = ViewProjection(size,zoom,pan,countryBounds[0],countryBounds[1],countryBounds[2],countryBounds[3])
            // Province backgrounds are always drawn, including uninterrupted historical alerts.
            countryMap.forEach { (id,shape) ->
                val byProvince = active.filter { it.regionId == id || RegionCatalog.matches(it.regionId,id) }
                val whole = byProvince.filter { RegionCatalog.signatureOf(it.regionId) == RegionCatalog.signatureOf(id) }
                val main = whole.firstOrNull()
                val fill = if (main == null) Color(0xFF1B3248) else
                    activeShade(main.dangerLevel,now-(main.startedAt ?: now),main.permanent)
                drawRegion(shape,projection,fill, if (selected == id || id in picked) Accent else Color(0xFF6983A3), 1.5f)
            }
            // All districts are shaded on the whole-country map, not only after entering an oblast.
            districtMap.forEach { (id,shape) ->
                val eventsHere = active.filter { e ->
                    RegionCatalog.signatureOf(e.regionId) == RegionCatalog.signatureOf(id) ||
                        (RegionCatalog.type(e.regionId) == "OBLAST" && RegionCatalog.matches(id,e.regionId))
                }
                val top = eventsHere.maxByOrNull { if (it.dangerLevel == "RED") 1 else 0 }
                if (top != null) drawRegion(shape,projection,
                    activeShade(top.dangerLevel,now-(top.startedAt ?: now),top.permanent),
                    if (zoom>1.55f) Color(0xFF6F819A) else Color.Transparent,if (zoom>1.55f) .6f else .0f)
                else if (zoom>1.55f) drawRegion(shape,projection,Color.Transparent,Color(0xFF4D647D),.55f)
                if (pickType == "RAYON" && id in picked) drawRegion(shape,projection,Color.Transparent,Accent,2.8f)
            }
            // On pinch zoom, load the visible raion's hromada boundaries, then actual settlement
            // polygons where OSM provides them. Many small villages have only a point, not a polygon.
            if (zoom >= 2.5f) communities.values.flatten().forEach { shape ->
                drawRegion(shape,projection,Color.Transparent,Color(0xFF5B7890),.48f)
            }
            if (zoom >= 7f) settlements.values.flatten().forEach { shape ->
                val placeId=regionFor(shape.name,"SETTLEMENT")
                val activeHere=active.firstOrNull { placeId!=null &&
                    RegionCatalog.signatureOf(it.regionId)==RegionCatalog.signatureOf(placeId) }
                drawRegion(shape,projection,activeHere?.let {
                    activeShade(it.dangerLevel,now-(it.startedAt ?: now),it.permanent)
                } ?: Color.Transparent,Color(0xFF7994A0),.44f)
            }
            // Air alerts without a loaded polygon (or at individual settlements) still show
            // a geographical status point at country scale. Never replace missing borders with guesses.
            active.filter { e -> RegionCatalog.type(e.regionId)=="SETTLEMENT" ||
                (RegionCatalog.type(e.regionId)=="RAYON" && districtMap.none { (id,_) ->
                    RegionCatalog.signatureOf(id)==RegionCatalog.signatureOf(e.regionId) })
            }.forEach { event ->
                RegionCatalog.pointFor(event.regionId)?.let { (lon,lat) ->
                    val pt=projection.project(lon,lat)
                    if (pt.x in 0f..size.width && pt.y in 0f..size.height) {
                        val shade=activeShade(event.dangerLevel,now-(event.startedAt ?: now),event.permanent)
                        drawCircle(Color.Black,radius=9f,center=pt)
                        drawCircle(shade,radius=6f,center=pt)
                    }
                }
            }
            // Marker positions for one-shot threats (visible at every scale for up to 30min).
            marks.forEach { (_,location) ->
                val pt=projection.project(location.first,location.second)
                if (pt.x in -10f..size.width+10f && pt.y in -10f..size.height+10f) {
                    drawCircle(Color(0xFFFFD600),radius=9f,center=pt)
                    drawCircle(Color(0xFF181818),radius=4f,center=pt)
                }
            }
            if (pickType == "SETTLEMENT" && zoom>=3f) {
                RegionCatalog.regions.asSequence().filter { it.type == "SETTLEMENT" }.mapNotNull { region ->
                    RegionCatalog.pointFor(region.id)?.let { region.id to projection.project(it.first,it.second) }
                }.filter { (_,pt)->pt.x in 0f..size.width && pt.y in 0f..size.height }.take(3000).forEach { (id,pt) ->
                    drawCircle(if (id in picked) Accent else Color(0xFFBFCBDC),radius=if (id in picked) 5f else 1.8f,center=pt)
                }
            }
        }
        Text("Двома пальцями — масштаб і переміщення · межі: OpenStreetMap · без даних реальних тривог",
            color=Muted, fontSize=10.sp, modifier=Modifier.padding(horizontal=12.dp,vertical=5.dp))
        val selection = selected
        if (selection != null) {
            val journal=events.filter { e ->
                !AlertKind.from(e.type).isOneShot &&
                    (RegionCatalog.matches(e.regionId, selection) || RegionCatalog.matches(selection, e.regionId))
                    && e.status in setOf("ACTIVE","ENDED")
            }.sortedByDescending { it.startedAt ?: 0L }.take(8)
            Column(Modifier.heightIn(max=205.dp).verticalScroll(rememberScrollState()).padding(horizontal=12.dp)) {
                Text(RegionCatalog.getName(selection),fontWeight=FontWeight.Bold,color=Color.White)
                if (pickType==null) {
                    if (journal.isEmpty()) Text("Немає подій у журналі",color=Muted,fontSize=12.sp)
                    journal.forEach { e ->
                        Text("${eventTitle(e)} · ${e.regionName} · ${time(e.startedAt)} — ${if(e.status=="ACTIVE") "триває" else time(e.endedAt)}",
                            color=Color.White,fontSize=12.sp)
                        e.startedAt?.let { Text(duration((e.endedAt ?: now)-it), color=Muted,fontSize=11.sp) }
                    }
                } else Text("Натисніть на територію, щоб додати або зняти вибір.", color=Muted,fontSize=11.sp)
            }
        } else if (pickType == "SETTLEMENT" && zoom<3f) Text("Наблизьте мапу для вибору міст і сіл.",color=Muted,modifier=Modifier.padding(12.dp))
        else Text("Натисніть на область або район для журналу.",color=Muted,fontSize=12.sp,modifier=Modifier.padding(12.dp))
    }
}

private fun DrawScope.drawRegion(shape: MapShape, project: ViewProjection,
                                 fill: Color, outline: Color, stroke: Float) {
    val path=Path()
    shape.polygons.forEach { ring ->
        ring.forEachIndexed { idx, (lon,lat) ->
            val pt=project.project(lon,lat)
            if(idx==0) path.moveTo(pt.x,pt.y) else path.lineTo(pt.x,pt.y)
        }
        path.close()
    }
    if (fill != Color.Transparent) drawPath(path,fill)
    if (stroke>0f && outline!=Color.Transparent) drawPath(path,outline,style=Stroke(stroke))
}

private fun regionFor(name: String, type: String, oblastName: String?=null): String? {
    val all=RegionCatalog.regions.filter { it.type==type && it.name.removePrefix("м. ").equals(name.removePrefix("м. "),true) }
    return if (oblastName!=null) all.firstOrNull { r ->
        RegionCatalog.oblastId(r.id)?.let(RegionCatalog::getName)?.equals(oblastName,true) == true
    }?.id ?: all.firstOrNull()?.id else all.firstOrNull()?.id
}
