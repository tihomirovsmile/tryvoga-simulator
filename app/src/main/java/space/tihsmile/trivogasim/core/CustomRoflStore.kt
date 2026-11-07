package space.tihsmile.trivogasim.core

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** User-created local scenarios. Media is copied into app-private storage and never uploaded. */
object CustomRoflStore {
    private const val PREFS = "custom_rofl_v1"
    private const val KEY = "scenarios"

    private const val HIDDEN = "hidden"
    @Synchronized private fun all(context: Context): List<RoflEntry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        return try {
            val json = JSONArray(raw)
            (0 until json.length()).map { i ->
                val o = json.getJSONObject(i)
                RoflEntry(o.getString("id"), o.getString("title"), o.optString("body"),
                    o.optString("audio"), o.optString("image"), true)
            }
        } catch (_: Exception) { emptyList() }
    }

    fun list(context: Context): List<RoflEntry> {
        val hidden = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(HIDDEN, emptySet()).orEmpty()
        return all(context).filterNot { it.id in hidden }
    }

    fun find(context: Context, id: String): RoflEntry? =
        RoflCatalog.byId(id) ?: all(context).firstOrNull { it.id == id }

    private fun save(context: Context, values: List<RoflEntry>) {
        val array = JSONArray()
        values.forEach { e -> array.put(JSONObject().put("id", e.id).put("title", e.title)
            .put("body", e.body).put("audio", e.audioFile).put("image", e.imageFile)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).commit()
    }

    private fun copyMedia(context: Context, uri: Uri?, folder: String, id: String, maxBytes: Long): String {
        if (uri == null) return ""
        val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
        val extension = if (folder == "audio") when {
            mime.contains("ogg") -> ".ogg"
            mime.contains("wav") -> ".wav"
            mime.contains("mp4") || mime.contains("m4a") -> ".m4a"
            else -> ".mp3"
        } else if (mime.contains("png")) ".png" else ".jpg"
        val name = id + extension
        val target = File(context.filesDir, "custom_rofl/$folder/$name")
        target.parentFile?.mkdirs()
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    val bytes = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(bytes)
                        if (n == -1) break
                        total += n
                        require(total <= maxBytes) { "Файл завеликий" }
                        output.write(bytes, 0, n)
                    }
                }
            } ?: error("Не вдалося прочитати вибраний файл")
            return name
        } catch (e: Exception) { target.delete(); throw e }
    }

    @Synchronized fun add(context: Context, title: String, body: String,
                          audio: Uri?, image: Uri?): RoflEntry {
        require(title.trim().length in 2..80) { "Назва має бути від 2 до 80 символів" }
        require(body.length <= 800) { "Опис завеликий" }
        val id = "custom_" + UUID.randomUUID().toString()
        val audioFile = copyMedia(context, audio, "audio", id, 25L * 1024 * 1024)
        val imageFile = copyMedia(context, image, "images", id, 12L * 1024 * 1024)
        val record = RoflEntry(id, title.trim(), body.trim(), audioFile, imageFile, true)
        save(context, all(context) + record)
        return record
    }

    @Synchronized fun delete(context: Context, id: String) {
        // Keep metadata and media available for pending and historical events.
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val hidden = prefs.getStringSet(HIDDEN, emptySet()).orEmpty().toMutableSet()
        hidden += id
        prefs.edit().putStringSet(HIDDEN, hidden).commit()
    }

    fun mediaPath(context: Context, type: String, name: String): File? {
        if (!Regex("[A-Za-z0-9_-]+\\.(mp3|ogg|wav|m4a|png|jpg)").matches(name)) return null
        val file = File(context.filesDir, "custom_rofl/$type/$name")
        return file.takeIf { it.isFile && it.canonicalPath.startsWith(context.filesDir.canonicalPath) }
    }
}
