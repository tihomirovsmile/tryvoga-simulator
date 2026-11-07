package space.tihsmile.trivogasim.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import space.tihsmile.trivogasim.data.Region
import space.tihsmile.trivogasim.data.RegionCatalog

/** Search is debounced and executed on Default; the list only lays out visible rows. */
@Composable
fun TerritoryPicker(type: String, selected: SnapshotStateList<String>,
                    omitPermanent: Boolean = false, label: String = "Пошук області, району або населеного пункту") {
    var query by remember(type) { mutableStateOf("") }
    var provinceId by remember(type) { mutableStateOf<String?>(null) }
    var matches by remember { mutableStateOf(emptyList<Region>()) }
    val version by RegionCatalog.generation.collectAsState()
    val provinces = remember(version) { RegionCatalog.children(null)
        .filter { it.type == "OBLAST" && (!omitPermanent || !RegionCatalog.isPermanentTerritory(it.id)) } }
    LaunchedEffect(type, query, provinceId, version) {
        if (query.isNotBlank()) delay(220)
        matches = withContext(Dispatchers.Default) {
            val result = when {
                type == "OBLAST" -> RegionCatalog.search(query, "OBLAST", 120).ifEmpty { if (query.isBlank()) provinces else emptyList() }
                query.isNotBlank() -> RegionCatalog.search(query, type, 180, provinceId)
                provinceId != null -> RegionCatalog.byOblast(type, provinceId!!, 180)
                else -> emptyList()
            }
            result.filter { !omitPermanent || !RegionCatalog.isPermanentTerritory(it.id) }
        }
    }
    val selectedSet = selected.toSet()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text(label) }, singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) })
        if (provinceId != null && type != "OBLAST") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { provinceId = null }) { Text("Усі області", color = Accent) }
                Text(RegionCatalog.getName(provinceId!!), color = Color.White, fontSize = 12.sp)
            }
        }
        LazyColumn(Modifier.fillMaxWidth().height(305.dp),
            contentPadding = PaddingValues(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (type != "OBLAST" && provinceId == null && query.isBlank()) {
                items(provinces, key = { "province_${it.id}" }) { p ->
                    DarkCard(onClick = { provinceId = p.id }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.name, color = Color.White, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Text(RegionCatalog.countByOblast(type, p.id).toString(), color = Accent)
                        }
                    }
                }
            } else {
                if (matches.isEmpty()) item { Text("Нічого не знайдено. Спробуйте іншу назву.", color = Muted) }
                var group = ""
                matches.forEach { r ->
                    val current = RegionCatalog.oblastFor(r.id)?.let(RegionCatalog::getName) ?: "Інші території"
                    if (group != current && type != "OBLAST") {
                        group = current
                        item(key = "group_${r.id}") {
                            Text(current, color = Accent, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 7.dp, bottom = 3.dp), fontSize = 12.sp)
                        }
                    }
                    item(key = r.id) {
                        Row(Modifier.fillMaxWidth().clickable {
                            if (r.id in selected) selected.remove(r.id) else selected.add(r.id)
                        }.padding(vertical = 1.dp, horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(r.id in selectedSet, onCheckedChange = {
                                if (it && r.id !in selected) selected.add(r.id)
                                if (!it) selected.remove(r.id)
                            })
                            Text(r.name, color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
                if (matches.size == 180) item {
                    Text("Показано перші 180 збігів — уточніть назву для решти.", color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
}
