package space.tihsmile.trivogasim.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.tihsmile.trivogasim.core.AlertKind
import space.tihsmile.trivogasim.data.Region
import space.tihsmile.trivogasim.data.RegionCatalog

@Composable
fun ControlScreen(onBack: () -> Unit, onRoute: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("Керування сповіщеннями", onBack, trailing = { SimBadge() })
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Text("ОГОЛОШЕННЯ", color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            ControlLink("Повітряна тривога", "Жовтий або червоний рівень · тільки райони", "compose-air", onRoute)
            ControlLink("Рофл тривоги", "6 готових подій і власні сценарії", "compose-rofl", onRoute)
            ControlLink("Інші тривоги", "Артобстріли, хімічна та радіаційна небезпека, вуличні бої", "compose-other", onRoute)
            ControlLink("Загрози та повідомлення", "БПЛА, ракети, балістика, КАБ, підвищена небезпека, своє повідомлення", "compose-threat", onRoute)
            Spacer(Modifier.height(10.dp))
            Text("КЕРУВАННЯ", color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            ControlLink("Підвищити / знизити рівень", "Зміна жовтого та червоного рівнів із повідомленням", "change-level", onRoute)
            ControlLink("Оголосити відбій", "Вибірковий або масовий", "end", onRoute)
            ControlLink("Заплановані", "Керування затримкою та скасування", "scheduled", onRoute)
            ControlLink("Журнал", "Історія звичайних тривог", "history", onRoute)
        }
    }
}

@Composable
private fun ControlLink(title: String, sub: String, route: String, onRoute: (String) -> Unit) {
    DarkCard(onClick = { onRoute(route) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(sub, color = Muted, fontSize = 12.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Accent)
        }
    }
}

private fun regionAncestors(r: Region): List<String> = RegionCatalog.lineage(r.id)

/** Air alert can cover an oblast, raion or settlement; other event types are point alerts. */
@Composable
fun ComposeAlertScreen(mode: String, onBack: () -> Unit,
                       onCreate: (Set<AlertKind>, Set<String>, String, Long, String, String, Long) -> Unit) {
    val isAir = mode == "air"
    val categories = when (mode) {
        "air" -> listOf(AlertKind.AIR)
        "other" -> listOf(AlertKind.ARTILLERY, AlertKind.FIGHTS, AlertKind.CHEMICAL, AlertKind.RADIATION)
        else -> listOf(AlertKind.INCREASED, AlertKind.DRONE, AlertKind.MISSILE, AlertKind.BALLISTIC,
            AlertKind.BOMB, AlertKind.TECHNO, AlertKind.SPECIAL)
    }
    val types = remember(mode) { mutableStateListOf(categories.first()) }
    val selected = remember(mode) { mutableStateListOf<String>() }
    var level by remember { mutableStateOf("RED") }
    var autoEndMinutes by remember { mutableStateOf("") }
    var territoryType by remember { mutableStateOf(if (isAir) "RAYON" else "SETTLEMENT") }
    var hrs by remember { mutableStateOf("0") }
    var mins by remember { mutableStateOf("0") }
    var secs by remember { mutableStateOf("5") }
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val delay = ((hrs.toLongOrNull() ?: 0L).coerceIn(0, 24 * 365) * 3600L) +
        ((mins.toLongOrNull() ?: 0L).coerceIn(0, 59) * 60) + (secs.toLongOrNull() ?: 0L).coerceIn(0, 59)
    val selectedType = if (isAir) "RAYON" else territoryType
    Column(Modifier.fillMaxSize()) {
        Header(when (mode) {
            "air" -> "Повітряна тривога"
            "other" -> "Інші тривоги"
            else -> "Загрози та повідомлення"
        }, onBack, trailing = { SimBadge() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 17.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!isAir) {
                Text(if (mode == "other") "Види тривог" else "Типи повідомлень", color = Accent,
                    fontWeight = FontWeight.Bold)
                categories.forEach { category ->
                    DarkCard(onClick = {
                        if (category in types) types.remove(category) else if (territoryType != "RAYON" || category != AlertKind.ARTILLERY) types.add(category)
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AlertIllustration(category, Modifier.size(37.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(category.label, color = Color.White, modifier = Modifier.weight(1f))
                            Checkbox(category in types, onCheckedChange = {
                                if (it) { if (category !in types && (territoryType != "RAYON" || category != AlertKind.ARTILLERY)) types.add(category) }
                                else types.remove(category)
                            })
                        }
                    }
                }
            }
            if (isAir) {
                Text("Рівень небезпеки", color = Accent, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("Жовтий", level == "YELLOW") { level = "YELLOW" }
                    ChoiceChip("Червоний", level == "RED") { level = "RED" }
                }
            }
            Text(if (isAir) "Райони України" else "Територія оголошення", color = Accent,
                fontWeight = FontWeight.Bold)
            if (!isAir && mode == "other") {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    ChoiceChip("Райони", territoryType == "RAYON") {
                        territoryType = "RAYON"; selected.clear()
                        types.remove(AlertKind.ARTILLERY)
                    }
                    ChoiceChip("Міста й села", territoryType == "SETTLEMENT") {
                        territoryType = "SETTLEMENT"; selected.clear()
                    }
                }
            }
            Text(when {
                isAir -> "Повітряна тривога оголошується лише в районах. Луганщина й Крим залишаються незмінними."
                territoryType == "RAYON" -> "Для районів доступні хімічна й радіаційна небезпека та вуличні бої. Артобстріл — лише для населеного пункту."
                else -> "Обирайте населені пункти. Результати згруповані за областями."
            }, color = Muted, fontSize = 12.sp)
            if (selected.isNotEmpty()) {
                Text("Обрано: ${selected.size}", color = Accent)
                selected.take(20).forEach { id ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(RegionCatalog.getName(id), color = Color.White, modifier = Modifier.weight(1f), fontSize = 12.sp)
                        TextButton({ selected.remove(id) }) { Text("×", color = Accent) }
                    }
                }
            }
            TerritoryPicker(selectedType, selected, omitPermanent = isAir)
            Text("Відкладена відправка", color = Accent, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DelayField("Год.", hrs, { hrs = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f))
                DelayField("Хв.", mins, { mins = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f))
                DelayField("Сек.", secs, { secs = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0L, 5L, 10L, 30L, 60L).forEach { d ->
                    ChoiceChip(if (d == 0L) "Зараз" else "${d} с", delay == d) {
                        hrs = "0"; mins = (d / 60).toString(); secs = (d % 60).toString()
                    }
                }
            }
            if (mode != "threat") {
                OutlinedTextField(autoEndMinutes, { autoEndMinutes = it.filter(Char::isDigit).take(6) },
                    label = { Text("Автоматичний відбій через N хв. (порожньо = вручну)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            if (AlertKind.SPECIAL in types) {
                OutlinedTextField(title, { title = it }, label = { Text("Заголовок повідомлення") },
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(message, { message = it }, label = { Text("Текст повідомлення") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2)
                Text("Рекомендація залишається стандартною.", color = Muted, fontSize = 12.sp)
            }
            Text("Планується подій: ${selected.size * types.size}", color = Muted, fontSize = 12.sp)
        }
        Box(Modifier.fillMaxWidth().padding(14.dp)) {
            BigButton(if (delay == 0L) "Оголосити" else "Запланувати", {
                onCreate(types.toSet(), selected.toSet(), level, delay, title, message,
                    ((autoEndMinutes.toLongOrNull() ?: 0L).coerceIn(0,525600))*60L)
            }, enabled = types.isNotEmpty() && selected.isNotEmpty())
        }
    }
}
