package space.tihsmile.trivogasim.ui

import android.graphics.BitmapFactory
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()
    var created by remember { mutableStateOf(CustomRoflStore.list(context)) }
    var current by remember { mutableStateOf(RoflCatalog.entries.first().id) }
    var delayText by remember { mutableStateOf("0") }
    var endAfter by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var audioUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var imageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var error by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    val audioPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> audioUri = uri }
    val imagePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> imageUri = uri }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Header("Рофл тривоги", onBack, trailing = { SimBadge() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("ШІСТЬ ГОТОВИХ СЦЕНАРІЇВ", color = Accent, fontWeight = FontWeight.Bold)
            (RoflCatalog.entries + created).forEach { entry ->
                DarkCard(onClick = { current = entry.id }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val bitmap = remember(entry.id, entry.imageFile) {
                            try {
                                val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
                                if (entry.custom) {
                                    CustomRoflStore.mediaPath(context, "images", entry.imageFile)?.let { f ->
                                        BitmapFactory.decodeFile(f.absolutePath, opts)?.asImageBitmap()
                                    }
                                } else context.assets.open("alerts/rofl/images/${entry.imageFile}").use { stream ->
                                    BitmapFactory.decodeStream(stream, null, opts)?.asImageBitmap()
                                }
                            } catch (_: Exception) { null }
                        }
                        if (bitmap != null) androidx.compose.foundation.Image(bitmap, null, Modifier.size(52.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(entry.body, color = Muted, fontSize = 11.sp, maxLines = 2)
                        }
                        RadioButton(current == entry.id, onClick = { current = entry.id })
                    }
                    if (entry.custom) TextButton(onClick = {
                        CustomRoflStore.delete(context, entry.id)
                        created = CustomRoflStore.list(context)
                        if (current == entry.id) current = RoflCatalog.entries.first().id
                    }) { Text("Видалити шаблон", color = Accent) }
                }
            }
            Text("СТВОРИТИ ВЛАСНИЙ СЦЕНАРІЙ", color = Accent, fontWeight = FontWeight.Bold)
            OutlinedTextField(title, { title = it.take(80) }, label = { Text("Назва") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(body, { body = it.take(800) }, label = { Text("Опис / текст повідомлення") },
                modifier = Modifier.fillMaxWidth(), minLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { audioPicker.launch(arrayOf("audio/*")) }, modifier = Modifier.weight(1f)) {
                    Text(if (audioUri == null) "Обрати звук" else "Звук обрано")
                }
                Button(onClick = { imagePicker.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) {
                    Text(if (imageUri == null) "Обрати зображення" else "Зображення обрано")
                }
            }
            Text("Файли зберігаються лише на цьому пристрої. Аудіо та картинка необов'язкові.",
                color = Muted, fontSize = 11.sp)
            BigButton(if (creating) "Збереження…" else "Зберегти свій сценарій", {
                creating = true
                scope.launch {
                    try {
                        val added = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            CustomRoflStore.add(context, title, body, audioUri, imageUri)
                        }
                        created = CustomRoflStore.list(context)
                        current = added.id; title = ""; body = ""
                        audioUri = null; imageUri = null; error = "Сценарій додано"
                    } catch (e: Exception) { error = e.message ?: "Не вдалося додати" }
                    finally { creating = false }
                }
            }, enabled = !creating && title.trim().length >= 2)
            if (error.isNotBlank()) Text(error, color = Accent, fontSize = 12.sp)
            Text("НАСЕЛЕНІ ПУНКТИ", color = Accent, fontWeight = FontWeight.Bold)
            if (selected.isNotEmpty()) Text("Обрано: ${selected.size}", color = Accent)
            TerritoryPicker("SETTLEMENT", selected, label = "Пошук населеного пункту")
            OutlinedTextField(delayText, { delayText = it.filter(Char::isDigit).take(8) },
                label = { Text("Затримка в секундах") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(endAfter, { endAfter = it.filter(Char::isDigit).take(6) },
                label = { Text("Автовідбій, хвилини (необов'язково)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        Box(Modifier.padding(14.dp)) {
            BigButton("Оголосити обраний сценарій", {
                onCreate(current, selected.toSet(), delayText.toLongOrNull() ?: 0L,
                    (endAfter.toLongOrNull() ?: 0L) * 60L)
            }, enabled = selected.isNotEmpty())
        }
    }
}
