package space.tihsmile.trivogasim.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("simulator_settings")

data class SimSettings(
    val onboarded: Boolean = false,
    val voice: String = "female",
    val language: String = "uk",
    val notifications: Boolean = true,
    val sound: Boolean = true,
    val vibration: Boolean = true,
    val alertVolume: Float = 1f,
    val endVolume: Float = 0.8f,
    val increasedVolume: Float = 1f,
    val showDonationBanner: Boolean = true
)

class Prefs(private val context: Context) {
    private object Keys {
        val onboarded = booleanPreferencesKey("onboarded")
        val voice = stringPreferencesKey("voice")
        val language = stringPreferencesKey("language")
        val notifications = booleanPreferencesKey("notifications")
        val sound = booleanPreferencesKey("sound")
        val vibration = booleanPreferencesKey("vibration")
        val alertVolume = floatPreferencesKey("alert_volume")
        val endVolume = floatPreferencesKey("end_volume")
        val increasedVolume = floatPreferencesKey("increased_volume")
        val donationBanner = booleanPreferencesKey("show_donation_banner")
    }

    val settings: Flow<SimSettings> = context.dataStore.data.map { it ->
        SimSettings(
            onboarded = it[Keys.onboarded] ?: false,
            voice = it[Keys.voice] ?: "female",
            language = it[Keys.language] ?: "uk",
            notifications = it[Keys.notifications] ?: true,
            sound = it[Keys.sound] ?: true,
            vibration = it[Keys.vibration] ?: true,
            alertVolume = it[Keys.alertVolume] ?: 1f,
            endVolume = it[Keys.endVolume] ?: 0.8f,
            increasedVolume = it[Keys.increasedVolume] ?: 1f,
            showDonationBanner = it[Keys.donationBanner] ?: true
        )
    }

    suspend fun current(): SimSettings = settings.first()
    suspend fun setOnboarded(value: Boolean) = context.dataStore.edit { it[Keys.onboarded] = value }.let { Unit }
    suspend fun setVoice(value: String) = context.dataStore.edit { it[Keys.voice] = value }.let { Unit }
    suspend fun setLanguage(value: String) = context.dataStore.edit { it[Keys.language] = value }.let { Unit }
    suspend fun setNotifications(value: Boolean) = context.dataStore.edit { it[Keys.notifications] = value }.let { Unit }
    suspend fun setSound(value: Boolean) = context.dataStore.edit { it[Keys.sound] = value }.let { Unit }
    suspend fun setVibration(value: Boolean) = context.dataStore.edit { it[Keys.vibration] = value }.let { Unit }
    suspend fun showDonationBanner(show: Boolean) = context.dataStore.edit { it[Keys.donationBanner] = show }.let { Unit }
    suspend fun setVolume(which: String, value: Float) {
        context.dataStore.edit {
            when (which) {
                "start" -> it[Keys.alertVolume] = value
                "end" -> it[Keys.endVolume] = value
                "increased" -> it[Keys.increasedVolume] = value
            }
        }
    }
}
