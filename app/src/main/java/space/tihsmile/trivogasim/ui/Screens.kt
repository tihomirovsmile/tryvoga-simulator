package space.tihsmile.trivogasim.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import space.tihsmile.trivogasim.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.tihsmile.trivogasim.core.AlertKind
import space.tihsmile.trivogasim.data.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val Accent = Color(0xFFFFD600)
val Tile = Color(0xFF242424)
val Muted = Color(0xFF999999)
val Sky = Color(0xFF398FFA)
private val Ukrainian = Locale("uk", "UA")

@Composable
fun AlertIllustration(kind: AlertKind, modifier: Modifier = Modifier.size(49.dp)) {
    val name = kind.iconName
    val res = when (name) {
        "ic_alarm_air" -> R.drawable.ic_alarm_air
        "ic_alarm_artillery" -> R.drawable.ic_alarm_artillery
        "ic_alarm_urban" -> R.drawable.ic_alarm_urban
        "ic_alarm_chemical" -> R.drawable.ic_alarm_chemical
        "ic_alarm_nuclear" -> R.drawable.ic_alarm_nuclear
        else -> R.drawable.ic_alarm_custom
    }
    Image(painterResource(res), contentDescription = kind.label, modifier = modifier)
}


fun clock(t: Long?): String = if (t == null) "—" else SimpleDateFormat("dd.MM, HH:mm", Ukrainian).format(Date(t))
@Composable
fun SimBadge() {
    Text("СИМУЛЯЦІЯ", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        modifier = Modifier.border(1.dp, Accent.copy(alpha = .6f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp))
}

@Composable
fun Header(title: String, onBack: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Назад", tint = Accent)
            }
        }
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
            modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun DarkCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null,
             content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(17.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(Tile)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
fun BigButton(label: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier,
              secondary: Boolean = false) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (secondary) Tile else Accent,
            contentColor = if (secondary) Color.White else Color.Black,
            disabledContainerColor = Color.DarkGray,
            disabledContentColor = Muted)) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BottomNavigation(page: String, onRoute: (String) -> Unit) {
    val nav = listOf(Triple("alerts", "Тривоги", Icons.Default.NotificationsActive),
        Triple("map", "Мапа", Icons.Default.Map),
        Triple("useful", "Корисне", Icons.Default.Info),
        Triple("settings", "Налаштування", Icons.Default.Settings))
    Row(Modifier.fillMaxWidth().height(72.dp).background(Color(0xFF1B1B1B))
        .border(BorderStroke(.5.dp, Color(0xFF333333))), verticalAlignment = Alignment.CenterVertically) {
        nav.forEach { (key, text, icon) ->
            Column(Modifier.weight(1f).fillMaxHeight().clickable { onRoute(key) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Icon(icon, contentDescription = text, tint = if (page == key) Accent else Muted,
                    modifier = Modifier.size(25.dp))
                Spacer(Modifier.height(5.dp))
                Text(text, fontSize = 11.sp, color = if (page == key) Accent else Muted,
                    fontWeight = if (page == key) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
fun OnboardingScreen(onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(112.dp).background(Sky, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.NotificationsActive, contentDescription = null,
                tint = Color.White, modifier = Modifier.size(58.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("Тривога — Симулятор", color = Color.White, fontSize = 26.sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        SimBadge()
        Spacer(Modifier.height(20.dp))
        Text("Локальний симулятор. Усі повідомлення створюєте ви. Додаток не отримує реальних попереджень і не замінює офіційну «Повітряну тривогу».",
            textAlign = TextAlign.Center, color = Muted, fontSize = 15.sp, lineHeight = 23.sp)
        Spacer(Modifier.weight(1f))
        BigButton("Обрати регіон і розпочати", onStart)
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
fun AlertsScreen(active: List<AlertEvent>, all: List<AlertEvent>, subscriptions: List<Subscription>,
                 onChooseRegion: () -> Unit, onDetail: (String) -> Unit, onSend: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Header("Тривоги", trailing = { SimBadge() })
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (subscriptions.isEmpty()) "Регіони не обрано" else
                subscriptions.joinToString(" · ") { RegionCatalog.getName(it.regionId) },
                color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text("Змінити", modifier = Modifier.clickable(onClick = onChooseRegion).padding(8.dp),
                color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        if (active.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Image(painterResource(R.drawable.ic_ok_big), contentDescription = null,
                    modifier = Modifier.size(124.dp))
                Spacer(Modifier.height(21.dp))
                Text("Немає тривоги", color = Color.White, fontSize = 24.sp,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(9.dp))
                Text("Слідкуйте за подальшими повідомленнями", color = Muted,
                    fontSize = 14.sp, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text("Активні тривоги: ${active.size}", color = Muted, fontSize = 13.sp)
                }
                val provinceGroups = active.groupBy { e ->
                    val province = RegionCatalog.oblastId(e.regionId)
                    val followedProvince = subscriptions.any { s -> province != null &&
                        RegionCatalog.type(s.regionId) == "OBLAST" && RegionCatalog.matches(province,s.regionId) }
                    if (followedProvince && province != null) "${province}|${e.type}" else ""
                }
                provinceGroups.filterKeys { it.isNotBlank() }.forEach { (groupKey, groupEvents) ->
                    val regionId = groupKey.substringBefore('|')
                    val kind = AlertKind.from(groupEvents.first().type)
                    item(key = "partial_"+groupKey) {
                        DarkCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (kind == AlertKind.AIR) Image(painterResource(if (groupEvents.any { it.dangerLevel == "RED" })
                                    R.drawable.ic_alarm_big else R.drawable.ic_alarm_big_yellow),
                                    contentDescription = kind.label, modifier = Modifier.size(49.dp))
                                else AlertIllustration(kind)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    val total = groupEvents.any { it.regionId == regionId ||
                                        (RegionCatalog.type(it.regionId) == "OBLAST" && RegionCatalog.matches(it.regionId,regionId)) }
                                    Text("${if (total) "" else "Часткова "}${kind.label.lowercase(Ukrainian)}",
                                        color = Color.White, fontWeight = FontWeight.Bold)
                                    Text(RegionCatalog.locationCase(regionId), color = Muted)
                                }
                            }
                            groupEvents.distinctBy { it.regionId }.forEach { event ->
                                Text(event.regionName, color = Accent,
                                    modifier = Modifier.fillMaxWidth().clickable { onDetail(event.id) }
                                        .padding(vertical = 5.dp))
                            }
                        }
                    }
                }
                items(active.filter { e ->
                    subscriptions.none { s -> RegionCatalog.type(s.regionId) == "OBLAST" &&
                        RegionCatalog.oblastId(e.regionId)?.let { RegionCatalog.matches(it,s.regionId) } == true }
                }, key = { it.id }) { alert -> AlertCard(alert) { onDetail(alert.id) } }
            }
        }
        val scheduled = all.count { it.status == "SCHEDULED" }
        if (scheduled > 0) Text("Заплановані події: $scheduled", color = Muted,
            fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(9.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            TextButton(onClick = onSend, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Accent)
                Spacer(Modifier.width(5.dp))
                Text("Розсилка тривог", color = Accent)
            }
        }
    }
}

@Composable
fun AlertCard(alert: AlertEvent, onClick: () -> Unit) {
    val kind = AlertKind.from(alert.type)
    DarkCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (kind == AlertKind.AIR) Image(painterResource(if (alert.dangerLevel == "YELLOW")
                R.drawable.ic_alarm_big_yellow else R.drawable.ic_alarm_big),
                contentDescription = kind.label, modifier = Modifier.size(49.dp))
            else AlertIllustration(kind)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(alert.title.ifBlank { kind.label }, color = Color.White,
                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(alert.regionName, color = Muted, fontSize = 12.sp, maxLines = 2)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = "Відкрити", tint = Muted)
        }
        Text(alert.description.ifBlank { kind.advice }, color = Color.White, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (alert.dangerLevel.isNotEmpty()) {
                Text(if (alert.dangerLevel == "RED") "Червоний рівень" else "Жовтий рівень",
                    color = if (alert.dangerLevel == "RED") Color(0xFFFF6F66) else Accent, fontSize = 12.sp)
                Spacer(Modifier.width(9.dp))
            }
            Text("З ${clock(alert.startedAt)}", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
fun RegionScreen(selected: Set<String>, events: List<AlertEvent>, onToggle: (String) -> Unit,
                 onBack: () -> Unit, onDetail: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var browsed by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }
    val selectedRegions = selected.mapNotNull { RegionCatalog.byId[it] }
    val visible = if (query.trim().isNotBlank()) RegionCatalog.regions
        .filter { it.name.contains(query.trim(), true) }.take(150)
        else RegionCatalog.regions.filter { it.parentId == browsed }
    Column(Modifier.fillMaxSize()) {
        Header("Регіони (${selected.size}/5)", {
            if (browsed == null) onBack()
            else browsed = browsed?.let { RegionCatalog.byId[it]?.parentId }
        })
        OutlinedTextField(value = query, onValueChange = { query = it },
            label = { Text("Пошук області, району, громади, міста") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp), singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) })
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)) {
            if (selectedRegions.isNotEmpty()) {
                item { Text("Відстежувані регіони", color = Accent, fontWeight = FontWeight.Bold) }
                items(selectedRegions, key = { "selected_"+it.id }) { region ->
                    val affected = events.filter { it.status == "ACTIVE" &&
                        RegionCatalog.matches(it.regionId,region.id) }
                    DarkCard(onClick = { expanded = if (expanded == region.id) null else region.id }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (affected.isEmpty()) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null, tint = if (affected.isEmpty()) Sky else Color(0xFFFF725C))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(region.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text(if (affected.isEmpty()) "Тривог немає" else
                                    "${if (region.type == "OBLAST") "Часткова тривога" else "Активна тривога"} · ${affected.size} подій",
                                    color = Muted, fontSize = 12.sp)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Muted)
                        }
                        if (expanded == region.id) {
                            if (affected.isEmpty()) Text("Активних тривог немає", color = Muted)
                            affected.forEach { e ->
                                Text("${AlertKind.from(e.type).label} · ${e.regionName}", color = Accent,
                                    modifier = Modifier.fillMaxWidth().clickable { onDetail(e.id) }.padding(6.dp))
                            }
                            Text("Припинити відстеження", color = Accent,
                                modifier = Modifier.clickable { onToggle(region.id) }.padding(vertical = 8.dp))
                        }
                    }
                }
            }
            item {
                Text(if (browsed == null) "Області та міста" else "У ${RegionCatalog.getName(browsed!!)}",
                    color = Accent, fontWeight = FontWeight.Bold)
            }
            items(visible, key = { it.id }) { region ->
                DarkCard(onClick = {
                    if (region.type == "SETTLEMENT") onToggle(region.id)
                    else { browsed = region.id; query = "" }
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(region.name, color = Color.White, fontSize = 14.sp,
                                fontWeight = if (region.type == "OBLAST") FontWeight.Bold else FontWeight.Medium)
                            Text(when (region.type) { "OBLAST" -> "Область / місто"; "RAYON" -> "Район";
                                "HROMADA" -> "Територіальна громада"; else -> "Населений пункт" },
                                color = Muted, fontSize = 11.sp)
                        }
                        Checkbox(checked = region.id in selected, onCheckedChange = { onToggle(region.id) })
                        if (region.type != "SETTLEMENT")
                            Icon(Icons.Default.ChevronRight, tint = Accent, contentDescription = "Відкрити")
                    }
                }
            }
            item { Text("Довідник із локальних даних APK. Список населених пунктів поки неповний; дані про реальні тривоги не завантажуються.",
                color = Muted, fontSize = 11.sp) }
        }
    }
}

@Composable
fun UsefulScreen() {
    val ctx = LocalContext.current
    fun open(url: String) {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("Корисне", trailing = { SimBadge() })
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Text("ДОВІДКА", color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            DarkCard(onClick = { open("https://www.ukrainealarm.com/faq") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.ic_faq), contentDescription = null, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Питання та відповіді", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("Як працюють сповіщення, підписки, звуки", color = Muted, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Accent)
                }
            }
            DarkCard(onClick = { open("https://www.ukrainealarm.com/") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.ic_instructions), contentDescription = null, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Як користуватися", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("Офіційні інструкції та джерела", color = Muted, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Accent)
                }
            }
            Text("ОФІЦІЙНІ ДЖЕРЕЛА", color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            listOf(
                "ДСНС України" to "https://dsns.gov.ua/",
                "Офіційний застосунок «Повітряна тривога»" to "https://www.ukrainealarm.com/",
                "Рекомендації з цивільного захисту" to "https://www.dsns.gov.ua/",
                "Контакти розробників" to "mailto:support@stfalcon.com",
                "Політика конфіденційності офіційного застосунку" to "https://map.ukrainealarm.com/confidentiality"
            ).forEach { (label, link) ->
                DarkCard(onClick = { open(link) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, color = Color.White, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.OpenInNew, tint = Accent, contentDescription = "Відкрити")
                    }
                }
            }
            DarkCard {
                Text("Про застосунок", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Локальна версія для самостійно створених сповіщень. Справжніх загроз не отримує і не замінює офіційне оповіщення.",
                    color = Muted, fontSize = 13.sp)
                SimBadge()
            }
        }
    }
}

@Composable
fun SettingsScreen(settings: SimSettings, exact: Boolean, onRoute: (String) -> Unit,
                   onExactSettings: () -> Unit, onNotificationPermission: () -> Unit,
                   dndGranted: Boolean, onDndSettings: () -> Unit,
                   onVoice: (String) -> Unit, onLanguage: (String) -> Unit,
                   onSound: (Boolean) -> Unit, onNotifications: (Boolean) -> Unit,
                   onVibration: (Boolean) -> Unit, onVolume: (String, Float) -> Unit,
                   onTest: (AlertKind, Boolean) -> Unit, onStop: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("Налаштування", trailing = { SimBadge() })
        Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("СПОВІЩЕННЯ", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            DarkCard {
                SettingsToggle("Дозволити сповіщення", settings.notifications, onNotifications)
                SettingsToggle("Звуки", settings.sound, onSound)
                SettingsToggle("Вібрація", settings.vibration, onVibration)
                SmallAction("Дозволи Android") { onNotificationPermission() }
                Text("Точне планування: ${if (exact) "доступне" else "потрібен дозвіл"}", color = Muted)
                if (!exact) SmallAction("Дозволити точні будильники", onClick = onExactSettings)
                Text("Доступ до «Не турбувати»: ${if (dndGranted) "надано" else "не надано"}", color = Muted)
                SmallAction("Налаштування «Не турбувати»", onClick = onDndSettings)
            }
            Text("ЗВУК", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            DarkCard {
                Text("Голос", color = Color.White, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("Жіночий", settings.voice == "female") { onVoice("female") }
                    ChoiceChip("Чоловічий", settings.voice == "male") { onVoice("male") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("Українська", settings.language == "uk") { onLanguage("uk") }
                    ChoiceChip("English", settings.language == "en") { onLanguage("en") }
                }
                VolumeControl("Гучність початку", settings.alertVolume) { onVolume("start", it) }
                VolumeControl("Гучність відбою", settings.endVolume) { onVolume("end", it) }
                VolumeControl("Гучність загроз", settings.increasedVolume) { onVolume("increased", it) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallAction("Перевірити тривогу", Modifier.weight(1f)) { onTest(AlertKind.AIR, false) }
                    SmallAction("Перевірити відбій", Modifier.weight(1f)) { onTest(AlertKind.AIR, true) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallAction("Загроза", Modifier.weight(1f)) { onTest(AlertKind.INCREASED, false) }
                    SmallAction("Зупинити", Modifier.weight(1f), onStop)
                }
                Text("Звукова доріжка відтворюється окремо від каналу Android-сповіщень. Системна гучність медіа й обмеження режиму «Не турбувати» продовжують діяти.",
                    color = Muted, fontSize = 12.sp)
            }
            Text("СИМУЛЯЦІЯ", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            SettingsLink("Керування тривогами та загрозами", "Планування, повідомлення, відбій і журнал", "control", onRoute)
            DarkCard {
                Text("Тривога — Симулятор 0.4.0", color = Muted)
                Text("Дані про реальні загрози відсутні", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SettingsLink(title: String, subtitle: String, route: String, onClick: (String) -> Unit) {
    DarkCard(onClick = { onClick(route) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Muted, fontSize = 12.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Muted)
        }
    }
}

@Composable
fun ChoiceChip(label: String, checked: Boolean, onClick: () -> Unit) {
    FilterChip(selected = checked, onClick = onClick, label = { Text(label, fontSize = 12.sp) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent,
            selectedLabelColor = Color.Black, containerColor = Tile, labelColor = Color.White))
}

@Composable
private fun SettingsToggle(name: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = Color.White, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun VolumeControl(name: String, volume: Float, onChange: (Float) -> Unit) {
    Text("$name · ${(volume * 100).toInt()}%", color = Color.White, fontSize = 13.sp)
    Slider(value = volume.coerceIn(0f, 1f), onValueChange = onChange)
}

@Composable
private fun SmallAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(9.dp)) {
        Text(label, color = Accent, fontSize = 12.sp, maxLines = 2)
    }
}

@Composable
fun DelayField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) },
        modifier = modifier, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}

@Composable
fun EndScreen(active: List<AlertEvent>, onBack: () -> Unit, onEnd: (Set<String>) -> Unit) {
    val selected = remember { mutableStateListOf<String>() }
    Column(Modifier.fillMaxSize()) {
        Header("Оголосити відбій", onBack, trailing = { SimBadge() })
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Активних: ${active.size}", color = Muted, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                if (active.isNotEmpty() && active.all { it.id in selected }) selected.clear()
                else { selected.clear(); selected.addAll(active.map { it.id }) }
            }) { Text("Обрати всі", color = Accent) }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(active, key = { it.id }) { alert ->
                DarkCard(onClick = {
                    if (alert.id in selected) selected.remove(alert.id) else selected.add(alert.id)
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = alert.id in selected,
                            onCheckedChange = { if (it) { if (alert.id !in selected) selected.add(alert.id) } else selected.remove(alert.id) })
                        Column(Modifier.weight(1f)) {
                            Text(AlertKind.from(alert.type).label, color = Color.White,
                                fontWeight = FontWeight.SemiBold)
                            Text(alert.regionName, color = Muted, fontSize = 12.sp)
                            Text("З ${clock(alert.startedAt)}", color = Muted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            BigButton("Оголосити відбій (${selected.size})", { onEnd(selected.toSet()) }, selected.isNotEmpty())
        }
    }
}

@Composable
fun ScheduledScreen(events: List<AlertEvent>, onBack: () -> Unit, onCancel: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Header("Заплановані події", onBack)
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (events.isEmpty()) item { Text("Немає запланованих подій", color = Muted) }
            items(events, key = { it.id }) { event ->
                DarkCard {
                    Text(AlertKind.from(event.type).label, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(event.regionName, color = Muted)
                    Text("Запуск: ${clock(event.scheduledAt)}", color = Accent)
                    SmallAction("Скасувати") { onCancel(event.id) }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(events: List<AlertEvent>, history: List<HistoryRecord>, onBack: () -> Unit,
                  onDetail: (String) -> Unit, onClear: () -> Unit) {
    var filter by remember { mutableStateOf("ALL") }
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Header("Історія тривог", onBack)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ALL" to "Усі", "ACTIVE" to "Активні", "SCHEDULED" to "Очікують",
                "ENDED" to "Завершені", "CANCELLED" to "Скасовані").forEach { (key, title) ->
                ChoiceChip(title, filter == key) { filter = key }
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(events.filter { !AlertKind.from(it.type).isOneShot && (filter == "ALL" || filter == it.status) }, key = { it.id }) { event ->
                DarkCard(onClick = { onDetail(event.id) }) {
                    Text(AlertKind.from(event.type).label, color = Color.White,
                        fontWeight = FontWeight.SemiBold)
                    Text(event.regionName, color = Muted, fontSize = 12.sp)
                    Text(when (event.status) {
                        "ACTIVE" -> "Триває"
                        "ENDED" -> "Відбій оголошено"
                        "SCHEDULED" -> "Заплановано"
                        "CANCELLED" -> "Скасовано"
                        else -> "Повідомлення"
                    }, color = Accent, fontSize = 12.sp)
                    if (event.startedAt != null) Text("Початок: ${clock(event.startedAt)}", color = Muted, fontSize = 12.sp)
                    if (event.endedAt != null) Text("Відбій: ${clock(event.endedAt)}", color = Muted, fontSize = 12.sp)
                }
            }
            item { Text("Записи повітряних та інших тривог", color = Muted, fontSize = 12.sp) }
        }
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            BigButton("Очистити завершені події", { confirm = true }, secondary = true)
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text("Очистити історію?") },
        text = { Text("Буде видалено лише завершені та скасовані тривоги. Активні залишаться.") },
        confirmButton = { TextButton(onClick = { onClear(); confirm = false }) { Text("Очистити", color = Accent) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Скасувати") } })
}

@Composable
fun DetailScreen(event: AlertEvent?, onBack: () -> Unit, onEnd: () -> Unit) {
    if (event == null) {
        Column { Header("Подію не знайдено", onBack); Text("Запис було видалено", color = Muted) }
        return
    }
    val kind = AlertKind.from(event.type)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("Деталі тривоги", onBack, trailing = { SimBadge() })
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Spacer(Modifier.height(20.dp))
            AlertIllustration(kind, Modifier.size(108.dp))
            Text(event.title.ifBlank { kind.label }, color = Color.White, fontSize = 24.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(event.regionName, color = Muted, textAlign = TextAlign.Center)
            if (event.dangerLevel.isNotEmpty())
                Text(if (event.dangerLevel == "RED") "Червоний рівень" else "Жовтий рівень",
                    color = if (event.dangerLevel == "YELLOW") Accent else Color(kind.color), fontWeight = FontWeight.Bold)
            Text(when (event.status) {
                "ACTIVE" -> "Тривога активна"
                "DELIVERED" -> "Повідомлення надіслано"
                "ENDED" -> "Відбій тривоги"
                "CANCELLED" -> "Подію скасовано"
                else -> "Очікується запуск"
            }, color = if (event.status == "ACTIVE") Color(kind.color) else Sky)
            DarkCard {
                Text("Рекомендація", color = Accent, fontWeight = FontWeight.Bold)
                Text(kind.advice, color = Color.White, lineHeight = 22.sp)
                if (event.description.isNotBlank()) Text(event.description, color = Muted)
            }
            DarkCard {
                if (event.startedAt != null) Text("Початок: ${clock(event.startedAt)}", color = Color.White)
                if (event.endedAt != null) Text("Відбій: ${clock(event.endedAt)}", color = Sky)
            }
            Text("СИМУЛЯЦІЯ · Не містить реальних даних про загрози", color = Accent,
                textAlign = TextAlign.Center, fontSize = 12.sp)
            if (event.status == "ACTIVE" && kind.hasAllClear && !event.permanent)
                BigButton("Оголосити відбій цієї тривоги", onEnd)
            Spacer(Modifier.height(15.dp))
        }
    }
}
