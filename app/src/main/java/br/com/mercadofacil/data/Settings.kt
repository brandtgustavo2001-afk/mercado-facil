package br.com.mercadofacil.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("settings")
data class AppSettings(val theme: String = "auto", val repository: String = "", val supabaseUrl: String = "", val supabaseKey: String = "", val notifications: Boolean = true, val lastCheck: Long = 0, val intervalHours: Int = 12)
class Settings(private val context: Context) {
    val flow = context.store.data.map { p -> AppSettings(p[stringPreferencesKey("theme")] ?: "auto", p[stringPreferencesKey("repository")] ?: "", p[stringPreferencesKey("url")] ?: "", p[stringPreferencesKey("key")] ?: "", p[booleanPreferencesKey("notifications")] ?: true, p[longPreferencesKey("lastCheck")] ?: 0, p[intPreferencesKey("interval")] ?: 12) }
    suspend fun get() = flow.first()
    suspend fun save(value: AppSettings) { context.store.edit {
        it[stringPreferencesKey("theme")] = value.theme
        it[stringPreferencesKey("repository")] = value.repository.trim().removePrefix("https://github.com/").trimEnd('/')
        it[stringPreferencesKey("url")] = value.supabaseUrl.trim().trimEnd('/')
        it[stringPreferencesKey("key")] = value.supabaseKey.trim()
        it[booleanPreferencesKey("notifications")] = value.notifications
        it[intPreferencesKey("interval")] = value.intervalHours.coerceIn(1, 168)
    } }
    suspend fun checked() { context.store.edit { it[longPreferencesKey("lastCheck")] = System.currentTimeMillis() } }
}
