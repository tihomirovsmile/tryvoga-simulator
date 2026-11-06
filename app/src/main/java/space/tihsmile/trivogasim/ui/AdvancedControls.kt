package space.tihsmile.trivogasim.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.tihsmile.trivogasim.core.*
import space.tihsmile.trivogasim.data.*

@Composable
fun MapComposerScreen(events: List<AlertEvent>, onBack: () -> Unit,
                      onCreate: (Set<AlertKind>, Set<String>, String, Long, String, String, Long) -> Unit) {
    var territory by remember { mutableStateOf("OBLAST") }
    var type by remember { mutableStateOf(AlertKind.AIR) }
    var level by remember { mutableStateOf("RED") }
    val chosen = remember { mutableStateListOf<String>() }
    var minutes by remember { mutableStateOf("0") }
    var seconds by remember { mutableStateOf("0") }
    var autoEnd by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf(false) }
    val delay = (minutes.toLongOrNull() ?: 0L).coerceIn(0,10080)*60L + (seconds.toLongOrNull() ?: 0L).coerceIn(0,59)
    val options = if (territory == "SETTLEMENT")
        listOf(AlertKind.AIR, AlertKind.ARTILLERY, AlertKind.FIGHTS, AlertKind.CHEMICAL,
            AlertKind.RADIATION, AlertKind.INCREASED, AlertKind.DRONE, AlertKind.MISSILE,
            AlertKind.BALLISTIC, AlertKind.BOMB, AlertKind.SPECIAL)
        else listOf(AlertKind.AIR)
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=5.dp), horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("OBLAST" to "Області", "RAYON" to "Райони", "SETTLEMENT" to "Н.п.").forEach { (mode,label) ->
                ChoiceChip(label,territory==mode) { territory=mode;chosen.clear();type=AlertKind.AIR;preview=false }
            }
        }
        Box(Modifier.weight(1f)) {
            MapScreen(events,onBack=onBack,pickType=territory,picked=chosen.toSet(),onPick={ id ->
                if ((type != AlertKind.AIR || !RegionCatalog.isPermanentTerritory(id)) && (id in chosen || chosen.size<1000)) {
                    if (id in chosen) chosen.remove(id) else chosen.add(id)
                    preview=false
                }
            })
        }
        Column(Modifier.fillMaxWidth().background(Tile,RoundedCornerShape(topStart=16.dp,topEnd=16.dp))
            .padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
            Text("Обрано: ${chosen.size}",color=Color.White,fontWeight=FontWeight.Bold)
            if (chosen.isNotEmpty()) Text(chosen.take(3).joinToString(", ") { RegionCatalog.getName(it) } +
                if (chosen.size>3) " та ще ${chosen.size-3}" else "",color=Muted,fontSize=11.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                options.forEach { k -> ChoiceChip(k.label,type==k) { type=k } }
            }
            if (type==AlertKind.SPECIAL) {
                OutlinedTextField(title,{title=it},label={Text("Назва повідомлення")},modifier=Modifier.fillMaxWidth())
                OutlinedTextField(body,{body=it},label={Text("Текст повідомлення")},modifier=Modifier.fillMaxWidth())
            }
            if (type==AlertKind.AIR) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                ChoiceChip("🟡 Жовтий",level=="YELLOW") { level="YELLOW" }
                ChoiceChip("🔴 Червоний",level=="RED") { level="RED" }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
                OutlinedTextField(minutes,{minutes=it.filter(Char::isDigit).take(5)},label={Text("Затримка, хв")},
                    singleLine=true,modifier=Modifier.weight(1f))
                OutlinedTextField(seconds,{seconds=it.filter(Char::isDigit).take(2)},label={Text("Сек.")},
                    singleLine=true,modifier=Modifier.weight(.65f))
                if(type.hasAllClear) OutlinedTextField(autoEnd,{autoEnd=it.filter(Char::isDigit).take(5)},
                    label={Text("Автовідбій, хв")},singleLine=true,modifier=Modifier.weight(1.1f))
            }
            if (preview) {
                Text("Підтвердження: ${type.label} · ${chosen.size} територій · ${if (type==AlertKind.AIR) level else ""}",
                    color=Accent,fontSize=12.sp)
                BigButton("Оголосити (${chosen.size})", {
                    onCreate(setOf(type),chosen.toSet(),level,delay,title,body,(autoEnd.toLongOrNull() ?: 0L)*60L)
                    preview=false
                },enabled=chosen.isNotEmpty())
            } else BigButton("Перегляд перед оголошенням",{preview=true},enabled=chosen.isNotEmpty())
        }
    }
}

@Composable
fun LevelChangeScreen(events: List<AlertEvent>, onBack: () -> Unit,
                      onChange: (Set<String>, String, Long) -> Unit) {
    val active = events.filter { it.status=="ACTIVE" && it.type=="AIR" && !it.permanent }
    val selection=remember { mutableStateListOf<String>() }
    var level by remember { mutableStateOf("RED") }
    var seconds by remember { mutableStateOf("0") }
    Column(Modifier.fillMaxSize()) {
        Header("Зміна рівня тривоги",onBack,trailing={SimBadge()})
        Text("Оберіть одну або кілька активних повітряних тривог.",
            color=Muted,modifier=Modifier.padding(horizontal=18.dp))
        Row(Modifier.padding(18.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            ChoiceChip("Жовтий рівень",level=="YELLOW") { level="YELLOW" }
            ChoiceChip("Червоний рівень",level=="RED") { level="RED" }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=16.dp)) {
            active.forEach { e ->
                DarkCard(Modifier.padding(bottom=8.dp),onClick={
                    if(e.id in selection) selection.remove(e.id) else selection.add(e.id)
                }) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(e.id in selection,onCheckedChange={checked ->
                            if(checked){if(e.id !in selection)selection.add(e.id)}else selection.remove(e.id)
                        })
                        Column(Modifier.weight(1f)) {
                            Text(e.regionName,color=Color.White)
                            Text(if(e.dangerLevel=="YELLOW") "Жовтий рівень" else "Червоний рівень",color=Muted,fontSize=12.sp)
                        }
                    }
                }
            }
            if(active.isEmpty()) Text("Немає активних тривог для зміни рівня.",color=Muted)
        }
        OutlinedTextField(seconds,{seconds=it.filter(Char::isDigit).take(7)},
            label={Text("Затримка, секунд (0 = зараз)")},modifier=Modifier.fillMaxWidth().padding(horizontal=15.dp),singleLine=true)
        Box(Modifier.padding(15.dp)) {
            BigButton("Змінити рівень",{onChange(selection.toSet(),level,seconds.toLongOrNull() ?: 0L)},
                enabled=selection.isNotEmpty())
        }
    }
}

@Composable
fun RoflComposerScreen(onBack: () -> Unit,
                       onCreate: (String, Set<String>, Long, Long) -> Unit) {
    val context = LocalContext.current
    var current by remember { mutableStateOf(RoflCatalog.entries.first().id) }
    var query by remember { mutableStateOf("") }
    var delay by remember { mutableStateOf("0") }
    var endAfter by remember { mutableStateOf("") }
    val selected=remember { mutableStateListOf<String>() }
    val version by RegionCatalog.generation.collectAsState()
    val settlements=remember(version,query) {
        if (query.trim().length < 2) emptyList()
        else RegionCatalog.regions.filter { it.type=="SETTLEMENT" && it.name.contains(query.trim(),true) }.take(80)
    }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Header("Рофл тривоги",onBack,trailing={SimBadge()})
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Text("10 мемних подій · окремі від звичайних тривог",color=Muted,fontSize=12.sp)
            RoflCatalog.entries.forEach { entry ->
                DarkCard(onClick={current=entry.id}) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        val bitmap=remember(entry.imageFile) {
                            try { context.assets.open("alerts/rofl/images/${entry.imageFile}").use { stream ->
                                BitmapFactory.Options().let { opt ->
                                    opt.inSampleSize=8
                                    BitmapFactory.decodeStream(stream,null,opt)?.asImageBitmap()
                                }
                            } } catch(_:Exception) { null }
                        }
                        if(bitmap!=null) androidx.compose.foundation.Image(bitmap,null,Modifier.size(66.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.title,color=Color.White,fontSize=13.sp,fontWeight=FontWeight.Bold)
                            Text(entry.body,color=Muted,fontSize=11.sp,maxLines=3)
                        }
                        RadioButton(current==entry.id,onClick={current=entry.id})
                    }
                }
            }
            Text("Населені пункти",color=Accent,fontWeight=FontWeight.Bold)
            OutlinedTextField(query,{query=it},label={Text("Пошук міста або села")},modifier=Modifier.fillMaxWidth())
            if(selected.isNotEmpty()) Text("Обрано: "+selected.take(6).joinToString(", ") {RegionCatalog.getName(it)},color=Accent,fontSize=12.sp)
            settlements.forEach { r ->
                Row(Modifier.fillMaxWidth().clickable {
                    if(r.id in selected) selected.remove(r.id) else selected.add(r.id)
                },verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(r.id in selected,onCheckedChange={ if(it) {if(r.id !in selected)selected.add(r.id)} else selected.remove(r.id) })
                    Text(r.name+" · "+(RegionCatalog.oblastId(r.id)?.let(RegionCatalog::getName) ?: ""),color=Color.White,fontSize=12.sp)
                }
            }
            OutlinedTextField(delay,{delay=it.filter(Char::isDigit).take(7)},label={Text("Затримка в секундах")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(endAfter,{endAfter=it.filter(Char::isDigit).take(6)},label={Text("Автоматичний відбій, хвилин (необов'язково)")},modifier=Modifier.fillMaxWidth())
        }
        Box(Modifier.padding(14.dp)) {
            BigButton("Оголосити рофл тривогу",{
                onCreate(current,selected.toSet(),delay.toLongOrNull() ?: 0L,
                    (endAfter.toLongOrNull() ?: 0L)*60L)
            },enabled=selected.isNotEmpty())
        }
    }
}
