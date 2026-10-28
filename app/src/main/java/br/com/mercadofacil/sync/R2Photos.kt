package br.com.mercadofacil.sync

import android.content.Context
import android.util.Base64
import br.com.mercadofacil.database.ShoppingItem
import br.com.mercadofacil.repository.ShoppingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.room.withTransaction
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import java.security.MessageDigest

class R2Photos(private val context: Context, private val repository: ShoppingRepository, private val client: OkHttpClient) {
    companion object { const val ENDPOINT = "https://mercado-facil-api.brandtgustavo2001.workers.dev" }
    private val cache = context.getSharedPreferences("r2-photo-cache", Context.MODE_PRIVATE)
    private fun hash(data: String) = MessageDigest.getInstance("SHA-256").digest(Base64.decode(data, Base64.NO_WRAP)).joinToString("") { "%02x".format(it) }
    suspend fun upload(item: ShoppingItem, token: String) = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url("$ENDPOINT/photos/${item.listId}/${item.id}")
            .header("Authorization", "Bearer $token")
        if (item.photoData.isEmpty()) builder.delete()
        else builder.put(Base64.decode(item.photoData, Base64.NO_WRAP).toRequestBody("image/jpeg".toMediaType()))
        client.newCall(builder.build()).execute().use { check(it.isSuccessful) { "Não foi possível enviar a foto para o Cloudflare." } }
        repository.db.withTransaction {
            repository.dao.item(item.id)?.let { current ->
                if (current.photoData == item.photoData) repository.dao.save(current.copy(photoDirty = false))
            }
        }
        cache.edit().putString(item.id, if (item.photoData.isEmpty()) "" else hash(item.photoData)).apply()
    }
    suspend fun receive(rows: JSONArray, token: String, canEdit: Boolean) {
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val local = repository.dao.item(row.getString("item_id")) ?: continue
            if (local.photoDirty) continue
            val key = row.optString("object_key")
            val remoteHash = row.optString("content_hash")
            val legacyVersion = "legacy:" + row.optString("updated_at")
            if (key.isEmpty()) {
                // Read through the authenticated proxy; the APK no longer downloads photo blobs from Postgres.
                if (!row.optBoolean("legacy_present")) {
                    if (local.photoData.isNotEmpty()) store(local, "")
                    cache.edit().putString(local.id, "").apply()
                } else {
                    val received = if (cache.getString(local.id, null) == legacyVersion && local.photoData.isNotEmpty()) local.photoData else download(local, token)
                    store(local, received)
                    if (received.isNotEmpty() && canEdit) upload(local.copy(photoData = received), token)
                    else cache.edit().putString(local.id, legacyVersion).apply()
                }
            } else if (cache.getString(local.id, null) != remoteHash || local.photoData.isEmpty()) {
                val received = download(local, token)
                store(local, received)
                cache.edit().putString(local.id, remoteHash).apply()
            }
        }
    }
    private suspend fun download(item: ShoppingItem, token: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$ENDPOINT/photos/${item.listId}/${item.id}")
            .header("Authorization", "Bearer $token").build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Não foi possível receber a foto do Cloudflare." }
            if (response.code == 204) "" else {
                val stream = requireNotNull(response.body).byteStream()
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val read = stream.read(buffer)
                    if (read == -1) break
                    check(output.size() + read <= 1500000) { "Foto acima do limite" }
                    output.write(buffer, 0, read)
                }
                val bytes = output.toByteArray()
                check(bytes.size <= 1500000) { "Foto acima do limite" }
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        }
    }
    private suspend fun store(item: ShoppingItem, data: String) = repository.db.withTransaction {
        repository.dao.item(item.id)?.let { current ->
            if (!current.photoDirty) repository.dao.save(current.copy(photoData = data))
        }
    }
}
