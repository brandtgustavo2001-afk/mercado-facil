package br.com.mercadofacil.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("settings")
data class AppSettings(val theme: String = "auto", val repository: String = br.com.mercadofacil.BuildConfig.UPDATE_REPOSITORY, val supabaseUrl: String = br.com.mercadofacil.BuildConfig.SUPABASE_URL, val supabaseKey: String = br.com.mercadofacil.BuildConfig.SUPABASE_PUBLIC_KEY, val notifications: Boolean = true, val lastCheck: Long = 0, val intervalHours: Int = 12)
class Settings(private val context: Context) {
    val selectedList = context.store.data.map { it[stringPreferencesKey("selectedList")] }
    suspend fun selectedListId() = selectedList.first()
    suspend fun selectList(id: String) { context.store.edit { it[stringPreferencesKey("selectedList")] = id } }
    val flow = context.store.data.map { p -> AppSettings(p[stringPreferencesKey("theme")] ?: "auto", p[stringPreferencesKey("repository")] ?: br.com.mercadofacil.BuildConfig.UPDATE_REPOSITORY, p[stringPreferencesKey("url")]?.takeIf { it.isNotBlank() } ?: br.com.mercadofacil.BuildConfig.SUPABASE_URL, p[stringPreferencesKey("key")]?.takeIf { it.isNotBlank() } ?: br.com.mercadofacil.BuildConfig.SUPABASE_PUBLIC_KEY, p[booleanPreferencesKey("notifications")] ?: true, p[longPreferencesKey("lastCheck")] ?: 0, p[intPreferencesKey("interval")] ?: 12) }
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
    suspend fun cachedRelease(repository: String): String? {
        val prefs = context.store.data.first()
        return prefs[stringPreferencesKey("cachedRelease")].takeIf { prefs[stringPreferencesKey("cachedReleaseRepo")] == repository }
    }
    suspend fun cacheRelease(repository: String, body: String?) { context.store.edit {
        it[stringPreferencesKey("cachedReleaseRepo")] = repository
        if (body == null) it.remove(stringPreferencesKey("cachedRelease")) else it[stringPreferencesKey("cachedRelease")] = body
    } }
}
