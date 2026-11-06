package space.tihsmile.trivogasim

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import space.tihsmile.trivogasim.core.*
import space.tihsmile.trivogasim.data.*
import space.tihsmile.trivogasim.ui.*

class SimulatorViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val repo = AlertRepository(application)
    val events = repo.alerts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val subs = repo.subscriptions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val history = repo.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = repo.prefs.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SimSettings())
    val selectedEvent = MutableStateFlow<String?>(null)
    val statusMessage = MutableStateFlow("")

    init {
        viewModelScope.launch(Dispatchers.IO) { repo.seedRegions(); repo.restore() }
        viewModelScope.launch(Dispatchers.IO) { RegionCatalog.updateFull(application) }
    }

    fun open(id: String?) { selectedEvent.value = id }
    fun message(text: String) { statusMessage.value = text }

    fun subscribe(id: String) = launchTask {
        val ok = repo.toggleSubscription(id)
        if (!ok) message("Можна обрати не більше п'яти регіонів")
    }

    fun create(kinds: Set<AlertKind>, regions: Set<String>, level: String, delay: Long,
               title: String, description: String, autoEndSeconds: Long = 0L,
               roflId: String = "") = launchTask {
        if (kinds.isEmpty() || regions.isEmpty()) { message("Оберіть тип та регіон") ; return@launchTask }
        repo.create(kinds, regions, level, delay, title, description, autoEndSeconds, roflId)
        message("Створено ${kinds.size * regions.size} подій")
    }

    fun changeLevel(ids: Set<String>, level: String, delay: Long) = launchTask {
        repo.changeLevel(ids, level, delay)
        message(if (delay>0) "Зміну рівня заплановано" else "Рівень небезпеки змінено")
    }
    fun retryCatalog() = launchTask {
        val ok = RegionCatalog.updateFull(getApplication<android.app.Application>())
        message(if (ok) "Повний каталог КАТОТТГ завантажено" else "Завантаження не вдалося")
    }
    fun end(ids: Set<String>) = launchTask { repo.end(ids); message("Оголошено відбій: ${ids.size}") }
    fun cancel(id: String) = launchTask { repo.cancel(id); message("Заплановану подію скасовано") }
    fun refreshSummary() = launchTask { repo.updateSummary() }
    fun clearCompleted() = launchTask { repo.clearCompleted(); message("Завершені події очищено") }
    fun onboardingDone() = launchTask { repo.prefs.setOnboarded(true) }
    fun setVoice(voice: String) = launchTask { repo.prefs.setVoice(voice) }
    fun setLanguage(language: String) = launchTask { repo.prefs.setLanguage(language) }
    fun setSound(value: Boolean) = launchTask { repo.prefs.setSound(value) }
    fun setNotifications(value: Boolean) = launchTask { repo.prefs.setNotifications(value) }
    fun setVibration(value: Boolean) = launchTask { repo.prefs.setVibration(value) }
    fun setVolume(which: String, value: Float) = launchTask { repo.prefs.setVolume(which, value) }

    private fun launchTask(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try { block() } catch (e: Exception) { message("Помилка: ${e.localizedMessage ?: "невідома"}") }
        }
    }
}

class MainActivity : ComponentActivity() {
    private val vm: SimulatorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RegionCatalog.initialize(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        intent?.getStringExtra("open_alert_id")?.let { vm.open(it) }
        setContent { SimulatorApp(vm, ::openExactSettings, ::openDndSettings) }
    }

    override fun onStart() {
        super.onStart()
        vm.refreshSummary()
    }

    override fun onDestroy() {
        if (isFinishing) SimNotifications(this).dismissSummary()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("open_alert_id")?.let { vm.open(it) }
    }

    private fun openDndSettings() {
        try { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
        catch (_: Exception) { }
    }

    private fun openExactSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarm = getSystemService(ALARM_SERVICE) as AlarmManager
            if (!alarm.canScheduleExactAlarms()) {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:$packageName")))
                } catch (_: Exception) { }
            }
        }
    }
}

@Composable
fun SimulatorApp(vm: SimulatorViewModel, onExactAlarmSettings: () -> Unit, onDndSettings: () -> Unit) {
    val context = LocalContext.current
    val events by vm.events.collectAsStateWithLifecycle()
    val subs by vm.subs.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val pendingOpen by vm.selectedEvent.collectAsStateWithLifecycle()
    val status by vm.statusMessage.collectAsStateWithLifecycle()
    var page by rememberSaveableMutableString("alerts")
    var currentDetail by rememberSaveableMutableString("")
    val preview = remember { AudioPreview(context) }
    DisposableEffect(preview) { onDispose { preview.stop() } }
    LaunchedEffect(pendingOpen) {
        if (pendingOpen != null) { currentDetail = pendingOpen!!; page = "detail"; vm.open(null) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()) { _ -> }
    val alarmManager = remember { context.getSystemService(android.content.Context.ALARM_SERVICE) as AlarmManager }
    val notificationManager = remember { context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager }
    val exact = remember(page) { Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms() }
    val regionVersion by RegionCatalog.generation.collectAsState()
    val activeAlerts = events.filter { it.status == "ACTIVE" && !it.permanent }
    val shownAlerts = activeAlerts.filter { alert ->
        subs.any { RegionCatalog.matches(alert.regionId, it.regionId) }
    }
    val screensWithBottom = setOf("alerts", "map", "useful", "settings")

    val googleFamily = remember {
        androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Typeface(
            android.graphics.Typeface.create("google-sans", android.graphics.Typeface.NORMAL)))
    }
    val defaults = Typography()
    val fontTheme = defaults.copy(
        displayLarge = defaults.displayLarge.copy(fontFamily = googleFamily),
        displayMedium = defaults.displayMedium.copy(fontFamily = googleFamily),
        displaySmall = defaults.displaySmall.copy(fontFamily = googleFamily),
        headlineLarge = defaults.headlineLarge.copy(fontFamily = googleFamily),
        headlineMedium = defaults.headlineMedium.copy(fontFamily = googleFamily),
        headlineSmall = defaults.headlineSmall.copy(fontFamily = googleFamily),
        titleLarge = defaults.titleLarge.copy(fontFamily = googleFamily),
        titleMedium = defaults.titleMedium.copy(fontFamily = googleFamily),
        titleSmall = defaults.titleSmall.copy(fontFamily = googleFamily),
        bodyLarge = defaults.bodyLarge.copy(fontFamily = googleFamily),
        bodyMedium = defaults.bodyMedium.copy(fontFamily = googleFamily),
        bodySmall = defaults.bodySmall.copy(fontFamily = googleFamily),
        labelLarge = defaults.labelLarge.copy(fontFamily = googleFamily),
        labelMedium = defaults.labelMedium.copy(fontFamily = googleFamily),
        labelSmall = defaults.labelSmall.copy(fontFamily = googleFamily)
    )
    androidx.compose.material3.MaterialTheme(typography = fontTheme, colorScheme = darkColorScheme(
        primary = Accent, background = Color.Black, surface = Tile, onSurface = Color.White)) {
        Scaffold(containerColor = Color.Black,
            modifier = Modifier.fillMaxSize().systemBarsPadding(),
            bottomBar = {
                if (page in screensWithBottom) BottomNavigation(page) { page = it }
            }
        ) { inner ->
            Box(Modifier.fillMaxSize().padding(inner)) {
                when {
                    !settings.onboarded -> OnboardingScreen {
                        vm.onboardingDone()
                        page = "regions"
                        if (Build.VERSION.SDK_INT >= 33) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    page == "alerts" -> AlertsScreen(shownAlerts, events, subs,
                        onChooseRegion = { page = "regions" },
                        onDetail = { currentDetail = it; page = "detail" },
                        onSend = { page = "control" })
                    page == "regions" -> key(regionVersion) { RegionScreen(subs.map { it.regionId }.toSet(), events,
                        onToggle = { vm.subscribe(it) }, onBack = { page = "alerts" },
                        onDetail = { currentDetail = it; page = "detail" }) }
                    page == "map" -> MapScreen(events)
                    page == "useful" -> UsefulScreen()
                    page == "settings" -> SettingsScreen(settings, exact,
                        dndGranted = notificationManager.isNotificationPolicyAccessGranted,
                        onDndSettings = onDndSettings,
                        onRoute = { page = it },
                        onExactSettings = onExactAlarmSettings,
                        onNotificationPermission = {
                            if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        onVoice = vm::setVoice,
                        onLanguage = vm::setLanguage,
                        onSound = vm::setSound,
                        onNotifications = vm::setNotifications,
                        onVibration = vm::setVibration,
                        onVolume = vm::setVolume,
                        onTest = { kind, ending ->
                            val sample = AlertEvent(id = "preview", regionId = "level1_15",
                                regionName = "Полтавська обл.", type = kind.name,
                                notificationId = 0, voice = settings.voice, language = settings.language,
                                dangerLevel = if (kind == AlertKind.AIR) "RED" else "")
                            preview.play(sample, ending,
                                if (ending) settings.endVolume else if (kind == AlertKind.INCREASED) settings.increasedVolume else settings.alertVolume)
                        }, onStop = preview::stop)
                    page == "control" -> ControlScreen(onBack = { page = "settings" }, onRoute = { page = it })
                    page == "compose-map" -> MapComposerScreen(events,
                        onBack = { page = "control" },
                        onCreate = { kinds, regions, level, delay, title, description, autoEnd ->
                            vm.create(kinds,regions,level,delay,title,description,autoEnd)
                            page = "alerts"
                        })
                    page == "compose-rofl" -> RoflComposerScreen(
                        onBack = { page = "control" },
                        onCreate = { roflId, regions, delay, autoEnd ->
                            vm.create(setOf(AlertKind.ROFL),regions,"",delay,"","",autoEnd,roflId)
                            page = "alerts"
                        })
                    page == "change-level" -> LevelChangeScreen(events,
                        onBack = { page = "control" }, onChange = { ids, level, delay ->
                            vm.changeLevel(ids,level,delay);page="alerts"
                        })
                    page.startsWith("compose-") -> ComposeAlertScreen(
                        mode = when (page) { "compose-air" -> "air"; "compose-other" -> "other"; else -> "threat" },
                        onBack = { page = "control" },
                        onCreate = { kinds, regions, level, delay, title, description, autoEnd ->
                            vm.create(kinds, regions, level, delay, title, description, autoEnd)
                            page = "alerts"
                        })
                    page == "end" -> EndScreen(activeAlerts,
                        onBack = { page = "settings" }, onEnd = { vm.end(it); page = "alerts" })
                    page == "scheduled" -> ScheduledScreen(events.filter { it.status == "SCHEDULED" },
                        onBack = { page = "settings" }, onCancel = vm::cancel)
                    page == "history" -> HistoryScreen(events, history,
                        onBack = { page = "settings" }, onDetail = { currentDetail = it; page = "detail" },
                        onClear = vm::clearCompleted)
                    page == "detail" -> DetailScreen(events.find { it.id == currentDetail },
                        onBack = { page = "alerts" }, onEnd = { vm.end(setOf(currentDetail)); page = "alerts" })
                    else -> AlertsScreen(shownAlerts, events, subs,
                        onChooseRegion = { page = "regions" },
                        onDetail = { currentDetail = it; page = "detail" },
                        onSend = { page = "control" })
                }
                if (status.isNotEmpty()) {
                    LaunchedEffect(status) {
                        kotlinx.coroutines.delay(3_500)
                        if (vm.statusMessage.value == status) vm.message("")
                    }
                    if (page in screensWithBottom) {
                        androidx.compose.material3.Snackbar(
                            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
                                .padding(bottom = 6.dp, start = 12.dp, end = 12.dp),
                            containerColor = Color(0xFF303030), contentColor = Color.White
                        ) { Text(status) }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberSaveableMutableString(initial: String): MutableState<String> =
    androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initial) }
