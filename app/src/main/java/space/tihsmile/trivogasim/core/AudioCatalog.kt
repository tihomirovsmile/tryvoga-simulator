package space.tihsmile.trivogasim.core

import android.content.Context
import android.media.MediaPlayer
import android.media.AudioAttributes
import android.net.Uri
import space.tihsmile.trivogasim.data.AlertEvent

/** All 126 available MP3 files are in res/raw. Deprecated tracks are not chosen by default. */
object AudioCatalog {
    fun fileName(type: AlertKind, level: String, voice: String, language: String, ending: Boolean): String {
        val en = language == "en"
        val prefix = (if (en) "en_" else "") + (if (voice == "male") "male_" else "female_")
        if (ending) {
            val suffix = when (type) {
                AlertKind.ARTILLERY -> "artillery_off"
                AlertKind.FIGHTS -> if (en) "urban_fights_off" else if (voice == "male") "urban_fights_off" else "vulichnyboi_fights_off"
                AlertKind.CHEMICAL -> "chemical_off"
                AlertKind.RADIATION -> "nuclear_off"
                AlertKind.SPECIAL, AlertKind.TECHNO, AlertKind.INCREASED, AlertKind.ROFL -> "custom_off"
                else -> "air_off"
            }
            return prefix + suffix
        }
        val suffix = when (type) {
            AlertKind.AIR -> when (level) {
                "YELLOW" -> "air_yellow_on"
                "RED" -> "air_red_on"
                else -> "air_on"
            }
            AlertKind.ARTILLERY -> "artillery_on"
            AlertKind.FIGHTS -> "urban_fights_on"
            AlertKind.CHEMICAL -> "chemical_on"
            AlertKind.RADIATION -> "nuclear_on"
            AlertKind.INCREASED -> if (en) "rapid_on" else if (voice == "male") "rapid_on" else "pidvichenanebespeka_on"
            AlertKind.DRONE -> "drone_on"
            AlertKind.MISSILE -> "missile_on"
            AlertKind.BALLISTIC -> "ballistic_on"
            AlertKind.BOMB -> if (en) "gbu_on" else "kab_on"
            AlertKind.TECHNO, AlertKind.SPECIAL, AlertKind.ROFL -> "custom_on"
        }
        return prefix + suffix
    }

    fun resolveName(context: Context, event: AlertEvent, ending: Boolean): String {
        val preferred = fileName(AlertKind.from(event.type), event.dangerLevel,
            event.voice, event.language, ending)
        val kind = AlertKind.from(event.type)
        val basePrefix = (if (event.language == "en") "en_" else "") +
            (if (event.voice == "male") "male_" else "female_")
        val variants = mutableListOf(preferred)
        if (!ending) {
            if (kind == AlertKind.AIR && event.dangerLevel.isNotBlank()) variants += basePrefix + "air_on"
            if (kind == AlertKind.INCREASED) {
                if (event.language == "uk") variants += if (event.voice == "male")
                    "pidvichenyrivennebespeki_malevoice" else "female_pidvichenanebespeka_on"
                else variants += basePrefix + "rapid_on"
            }
            when (kind) {
                AlertKind.FIGHTS -> if (event.language == "uk" && event.voice == "male")
                    variants += "begin_vulichni_boi_malevoice"
                AlertKind.MISSILE -> if (event.language == "uk" && event.voice == "female")
                    variants += "female_raketa_on"
                AlertKind.BALLISTIC -> if (event.language == "uk" && event.voice == "male")
                    variants += "male_ballisticnotify_on"
                AlertKind.BOMB -> if (event.language == "uk") variants += basePrefix + "kab_on"
                else -> Unit
            }
        }
        val candidates = variants + listOf(basePrefix + (if (ending) "air_off" else "custom_on"),
            if (ending) "female_air_off" else "female_custom_on")
        return candidates.firstOrNull { id(context, it) != 0 } ?: "female_custom_on"
    }

    fun id(context: Context, name: String): Int =
        context.resources.getIdentifier(name, "raw", context.packageName)
}

class AudioPreview(private val context: Context) {
    private var player: MediaPlayer? = null

    fun play(event: AlertEvent, ending: Boolean, volume: Float) {
        stop()
        val resourceId = AudioCatalog.id(context, AudioCatalog.resolveName(context, event, ending))
        if (resourceId == 0) return
        val soundUri = Uri.parse("android.resource://${context.packageName}/$resourceId")
        val media = MediaPlayer()
        try {
            media.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            media.setDataSource(context, soundUri)
            media.prepare()
            media.setVolume(volume.coerceIn(0f, 1f), volume.coerceIn(0f, 1f))
            media.setOnCompletionListener { stop() }
            player = media
            media.start()
        } catch (_: Exception) { media.release(); player = null }
    }

    fun stop() {
        player?.let { try { if (it.isPlaying) it.stop() } catch (_: Exception) { } ; it.release() }
        player = null
    }
}
