package br.com.mercadofacil.sync

import android.content.Context
import androidx.room.withTransaction
import androidx.work.*
import br.com.mercadofacil.MercadoApp
import br.com.mercadofacil.data.*
import br.com.mercadofacil.database.*
import br.com.mercadofacil.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.*
import java.util.concurrent.TimeUnit

class SyncEngine(
    private val context: Context,
    private val repository: ShoppingRepository,
    private val settings: Settings,
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build(),
    private val sessionStore: SessionStorage = SessionStore(context)
) {
    private val mutex = Mutex()
    private val authMutex = Mutex()
    val status = MutableStateFlow("Somente neste aparelho")
    val email = MutableStateFlow(runCatching { sessionStore.read()?.let { JSONObject(it).optJSONObject("user")?.optString("email") } }.getOrNull() ?: "")
    private var socket: WebSocket? = null
    suspend fun configured(): Boolean = settings.get().let { it.supabaseUrl.startsWith("https://") && it.supabaseKey.isNotBlank() }
    private suspend fun request(path: String, body: JSONObject? = null, token: String? = null): String = withContext(Dispatchers.IO) {
        val config = settings.get()
        val uri = java.net.URI(config.supabaseUrl)
        require(uri.scheme == "https" && uri.host != null && uri.userInfo == null && uri.query == null) { "Configure uma URL HTTPS do Supabase" }
        require(config.supabaseKey.isNotBlank()) { "Configure a chave pública do Supabase" }
        val builder = Request.Builder().url("${config.supabaseUrl}$path").header("apikey", config.supabaseKey)
        if (token != null) builder.header("Authorization", "Bearer $token")
        if (body != null) builder.post(body.toString().toRequestBody("application/json".toMediaType()))
        client.newCall(builder.build()).execute().use {
            val text = requireNotNull(it.body).string()
            if (!it.isSuccessful) error(AuthFeedback.error(it.code, text))
            text
        }
    }
    suspend fun authenticate(email: String, password: String, signup: Boolean, name: String): String {
        require(AuthFeedback.validEmail(email) && password.length >= 8) { "Informe e-mail válido e senha de pelo menos 8 caracteres" }
        val normalizedEmail = email.trim().lowercase(java.util.Locale.ROOT)
        val body = JSONObject().put("email", normalizedEmail).put("password", password)
        if (signup) body.put("data", JSONObject().put("display_name", name))
        val response = request(if (signup) "/auth/v1/signup" else "/auth/v1/token?grant_type=password", body)
        val feedback = AuthFeedback.success(response, signup)
        val value = JSONObject(response)
        if (value.optString("access_token").isBlank()) return feedback
        sessionStore.save(value.toString())
        this.email.value = normalizedEmail
        br.com.mercadofacil.notifications.PushNotifications.schedule(context)
        return feedback
    }
    suspend fun recover(email: String) { request("/auth/v1/recover", JSONObject().put("email", email.trim())) }
    suspend fun token(): String = authMutex.withLock {
        var value = JSONObject(sessionStore.read() ?: error("Entre na sua conta para compartilhar"))
        if (value.optLong("expires_at") <= System.currentTimeMillis() / 1000 + 120) {
            value = JSONObject(request("/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", value.getString("refresh_token"))))
            sessionStore.save(value.toString())
        }
        value.getString("access_token")
    }
    suspend fun logout() {
        require(repository.dao.totalQueued() == 0) { "Sincronize as alterações pendentes e resolva conflitos antes de sair" }
        rpc("unregister_push", JSONObject().put("installation", br.com.mercadofacil.notifications.PushNotifications.installation(context)))
        br.com.mercadofacil.notifications.PushNotifications.preferences(context).edit().remove("registered_email").apply()
        runCatching { request("/auth/v1/logout", JSONObject(), token()) }
        sessionStore.clear(); email.value = ""; socket?.close(1000, "logout"); status.value = "Somente neste aparelho"
        repository.dao.allLists().filter { it.shared }.forEach { repository.dao.save(it.copy(deleted = true)) }
    }
    suspend fun rpc(name: String, body: JSONObject = JSONObject()): String = request("/rest/v1/rpc/$name", body, token())
    suspend fun sync() = mutex.withLock {
        if (!configured() || email.value.isBlank()) return@withLock
        val beforeItems = repository.dao.allItems().associateBy { it.id }
        val beforePurchases = repository.dao.purchases().first().map { it.id }.toSet()
        status.value = "Sincronizando…"
        try {
            var uploadFailure: Exception? = null
            val blockedLists = mutableSetOf<String>()
            for (op in repository.dao.pending()) {
                if (op.listId in blockedLists) continue
                if (repository.dao.conflicting(op.entityId) > 0) continue
                try {
                    if (op.entityType == "purchase") {
                        rpc("save_purchase", JSONObject().put("purchase", JSONObject(op.payload)))
                        repository.dao.ack(op.operationId)
                        continue
                    }
                    val payload = JSONObject(op.payload)
                    val result = JSONObject(rpc("apply_operation", JSONObject().put("op_id", op.operationId).put("entity", op.entityType).put("entity_id", op.entityId).put("target_list", op.listId).put("patch", payload.getJSONObject("data")).put("base", payload.getJSONObject("base"))))
                    if (result.optBoolean("conflict")) repository.dao.failed(op.operationId, "conflict", result.toString())
                    else {
                        check(result.optBoolean("ok")) { "O servidor não confirmou a alteração" }
                        repository.dao.ack(op.operationId)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    // Preserve operation order within this list; keep other lists flowing.
                    blockedLists.add(op.listId)
                    uploadFailure = e
                }
            }
            pull()
            syncPurchases()
            syncPhotos()
            notifySharedChanges(beforeItems, beforePurchases)
            uploadFailure?.let { throw it }
            status.value = if (repository.dao.totalQueued() == 0) "Sincronizado" else "Há alterações pendentes; confira Conta e ajustes"
        } catch (e: Exception) { if (e is CancellationException) throw e; status.value = "Não foi possível sincronizar: ${e.message ?: "verifique sua conexão"}"; throw e }
    }
    private suspend fun notifySharedChanges(before: Map<String, ShoppingItem>, oldPurchases: Set<String>) {
        val account = JSONObject(sessionStore.read() ?: return).optJSONObject("user")?.optString("id").orEmpty()
        if (account.isEmpty()) return
        if (br.com.mercadofacil.notifications.PushNotifications.preferences(context).getString("registered_email", "") == email.value) return
        val prefs = context.getSharedPreferences("change-notifications", Context.MODE_PRIVATE)
        val enabled = settings.get().notifications
        val items = repository.dao.allItems()
        val purchases = repository.dao.purchases().first()
        for (list in repository.dao.allLists().filter { it.shared && !it.deleted }) {
            val key = "$account:${list.id}"
            if (prefs.getBoolean(key, false) && enabled) {
                val added = items.filter { it.listId == list.id && !it.deleted && it.id !in before && it.purchasedBy != account }
                val bought = purchases.filter { it.listId == list.id && it.id !in oldPurchases }
                if (added.isNotEmpty()) br.com.mercadofacil.updater.notifyUser(context,
                    "Novos itens em ${list.name}", added.take(4).joinToString(", ") { it.name }, (key + "items").hashCode())
                if (bought.isNotEmpty()) br.com.mercadofacil.updater.notifyUser(context,
                    "Compra concluída em ${list.name}", "${bought.sumOf { it.itemCount }} itens comprados. Toque para ver o histórico.", (key + "purchases").hashCode())
                val changed = items.count { item -> item.listId == list.id && !item.deleted && item.purchasedBy != account &&
                    before[item.id]?.let { old -> !old.deleted && (old.name != item.name || old.quantity != item.quantity || old.purchased != item.purchased) } == true }
                if (changed > 0 && added.isEmpty() && bought.isEmpty()) br.com.mercadofacil.updater.notifyUser(context,
                    "Lista ${list.name} atualizada", "$changed itens foram alterados por outro participante.", (key + "changes").hashCode())
            }
            prefs.edit().putBoolean(key, true).apply()
        }
    }
    private suspend fun syncPurchases() {
        for (list in repository.dao.allLists().filter { it.shared && !it.deleted }) {
            val migration = context.getSharedPreferences("shared-purchase-migration", Context.MODE_PRIVATE)
            if (!migration.getBoolean(list.id, false)) {
                val role = rpc("member_role", JSONObject().put("target_list", list.id)).trim().trim('"')
                if (role == "owner" || role == "editor") {
                    for (purchase in repository.dao.purchases().first().filter { it.listId == list.id }) {
                        rpc("save_purchase", JSONObject().put("purchase", JSONObject().put("id", purchase.id)
                            .put("list_id", list.id).put("list_name", purchase.listName).put("created_at", purchase.createdAt)
                            .put("items", JSONArray(purchase.itemsJson)).put("total_cents", purchase.totalCents).put("item_count", purchase.itemCount)))
                    }
                }
                migration.edit().putBoolean(list.id, true).apply()
            }
            val rows = JSONArray(request("/rest/v1/shared_purchases?list_id=eq.${list.id}&select=*&order=created_at.desc,id.desc&limit=10", token = token()))
            repository.db.withTransaction {
                val ids = mutableListOf<String>()
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i); val id = row.getString("id"); ids.add(id)
                    repository.dao.save(PurchaseHistory(id, list.id, row.getString("list_name"), row.getLong("created_at"),
                        row.getJSONArray("items").toString(), row.getLong("total_cents"), row.getInt("item_count")))
                }
                ids.addAll(repository.dao.pending().filter { it.entityType == "purchase" && it.listId == list.id }.map { it.entityId })
                repository.dao.reconcilePurchases(list.id, ids)
            }
        }
    }
    private suspend fun syncPhotos() {
        val photos = R2Photos(context, repository, client)
        val shared = repository.dao.allLists().filter { it.shared && !it.deleted }.map { it.id }.toSet()
        for (item in repository.dao.allItems().filter { it.listId in shared && it.photoDirty }) {
            if (repository.dao.queued(item.id) == 0) photos.upload(item, token())
        }
        for (listId in shared) {
            val canEdit = rpc("member_role", JSONObject().put("target_list", listId)).trim().trim('"') in listOf("owner", "editor")
            var offset = 0
            do {
                val rows = JSONArray(request("/rest/v1/item_photos?list_id=eq.$listId&select=item_id,object_key,content_hash,legacy_present,updated_at&order=item_id&limit=100&offset=$offset", token = token()))
                photos.receive(rows, token(), canEdit)
                offset += rows.length()
            } while (rows.length() == 100)
        }
    }
    private suspend fun pull() {
        var offset = 0
        val accessible = mutableSetOf<String>()
        do {
            val rows = JSONArray(request("/rest/v1/shopping_lists?select=*&order=id&limit=100&offset=$offset", token = token()))
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i); val id = row.getString("id"); accessible.add(id)
                val d = row.getJSONObject("data")
                repository.db.withTransaction {
                    if (repository.dao.queued(id) == 0) repository.dao.save(ShoppingList(id = id, name = d.getString("name"), description = d.optString("description"), market = d.optString("market"), budgetCents = d.optLong("budget_cents"), status = d.optString("status", "active"), createdAt = java.time.Instant.parse(row.getString("created_at")).toEpochMilli(), updatedAt = java.time.Instant.parse(row.getString("updated_at")).toEpochMilli(), shared = true, version = row.getLong("version"), deleted = d.optBoolean("deleted")))
                }
                var itemOffset = 0
                do {
                    val items = JSONArray(request("/rest/v1/shopping_items?list_id=eq.$id&select=*&order=id&limit=200&offset=$itemOffset", token = token()))
                    for (j in 0 until items.length()) {
                        val itemRow = items.getJSONObject(j); val itemId = itemRow.getString("id")
                        repository.db.withTransaction {
                            if (repository.dao.queued(itemId) == 0) {
                                val item = itemRow.getJSONObject("data")
                                val local = repository.dao.item(itemId)
                                repository.dao.save(ShoppingItem(id = itemId, listId = id, name = item.getString("name"), quantity = item.getString("quantity"), unit = item.optString("unit", "un"), estimatedCents = item.optLong("estimated_cents"), actualCents = if (item.isNull("actual_cents")) null else item.getLong("actual_cents"), category = item.optString("category", "Outros"), brand = item.optString("brand"), notes = item.optString("notes"), purchased = item.optBoolean("purchased"), purchasedBy = itemRow.optString("updated_by"), version = itemRow.getLong("version"), deleted = item.optBoolean("deleted"), photoData = local?.photoData ?: "", photoDirty = local?.photoDirty ?: false, position = local?.position ?: 0, favorite = local?.favorite ?: false))
                            }
                        }
                    }
                    itemOffset += items.length()
                } while (items.length() == 200)
            }
            offset += rows.length()
        } while (rows.length() == 100)
        // Cached lists are hidden when membership has been revoked on the server.
        repository.db.withTransaction {
            repository.dao.allLists().filter { it.shared && it.id !in accessible && repository.dao.queued(it.id) == 0 }.forEach { repository.dao.save(it.copy(deleted = true)) }
        }
    }
    suspend fun invite(listId: String, role: String): String = JSONObject(rpc("create_invitation", JSONObject().put("target_list", listId).put("member_role", role))).getString("token")
    suspend fun join(code: String) {
        val received = code.trim()
        // Also accept the complete invitation text pasted from a messaging app.
        val invitation = Regex("(?i)(?<![a-z0-9])[a-f0-9]{64}(?![a-z0-9])").find(received)?.value
            ?: throw IllegalArgumentException("Cole o código ou a mensagem do convite recebido")
        val id = JSONObject(rpc("accept_invitation", JSONObject().put("invitation_token", invitation))).getString("list_id")
        java.util.UUID.fromString(id)
        // Acceptance must not be blocked by an unrelated local upload failure.
        settings.selectList(id)
        mutex.withLock { pull() }
        require(repository.dao.list(id)?.let { it.shared && !it.deleted } == true) { "Lista aceita; aguarde a conexão para carregar os produtos" }
    }
    suspend fun members(id: String) = JSONArray(request("/rest/v1/list_members?list_id=eq.$id&select=user_id,role", token = token()))
    suspend fun removeMember(id: String, userId: String) { rpc("remove_member", JSONObject().put("target_list", id).put("target_user", userId)) }
    suspend fun activity(id: String) = JSONArray(request("/rest/v1/shopping_activity?list_id=eq.$id&select=*&order=created_at.desc&limit=50", token = token()))
    suspend fun revoke(id: String) { rpc("revoke_invitations", JSONObject().put("target_list", id)) }
    suspend fun resolve(op: SyncOperation, keepMine: Boolean) {
        repository.db.withTransaction {
            repository.dao.ack(op.operationId)
            if (keepMine) {
                val original = JSONObject(op.payload)
                val server = JSONObject(requireNotNull(op.error)).getJSONObject("current")
                val base = JSONObject(); original.getJSONObject("data").keys().forEach { base.put(it, server.opt(it) ?: JSONObject.NULL) }
                repository.dao.enqueue(op.copy(operationId = java.util.UUID.randomUUID().toString(), payload = original.put("base", base).toString(), status = "pending", error = null))
            }
        }
        sync()
    }
    suspend fun watch() = coroutineScope {
        var delayMs = 1000L
        while (isActive) {
            try {
                if (email.value.isBlank() || !configured()) { delay(5000); continue }
                try { sync() } catch (e: Exception) { if (e is CancellationException) throw e }
                val config = settings.get()
                val access = token()
                val closed = CompletableDeferred<Unit>()
                socket = client.newWebSocket(Request.Builder().url(config.supabaseUrl.replaceFirst("https://", "wss://") + "/realtime/v1/websocket?apikey=${config.supabaseKey}&vsn=1.0.0").build(), object : WebSocketListener() {
                    override fun onOpen(ws: WebSocket, response: Response) {
                        ws.send(JSONObject().put("topic", "realtime:mercado").put("event", "phx_join").put("ref", "1").put("payload", JSONObject().put("access_token", access).put("config", JSONObject().put("postgres_changes", JSONArray().put(JSONObject().put("event", "*").put("schema", "public").put("table", "shopping_items")).put(JSONObject().put("event", "*").put("schema", "public").put("table", "shopping_lists")).put(JSONObject().put("event", "*").put("schema", "public").put("table", "list_members"))))).toString())
                    }
                    override fun onMessage(ws: WebSocket, text: String) {
                        val msg = runCatching { JSONObject(text) }.getOrNull() ?: return
                        if (msg.optString("event") == "postgres_changes") launch { runCatching { sync() } }
                        if (msg.optString("event") == "phx_reply" && msg.optString("ref") == "1") {
                            if (msg.optJSONObject("payload")?.optString("status") == "ok") {
                                // Fetch changes made between the initial pull and subscription.
                                launch { runCatching { sync() } }
                            } else closed.complete(Unit)
                        }
                        if (msg.optString("event") in listOf("phx_error", "phx_close")) closed.complete(Unit)
                    }
                    override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) { closed.complete(Unit) }
                    override fun onClosed(ws: WebSocket, code: Int, reason: String) { closed.complete(Unit) }
                })
                delayMs = 1000
                // Refresh subscriptions periodically so JWT expiration and missed events recover.
                withTimeoutOrNull(600_000) {
                    var ref = 2
                    var polls = 0
                    while (!closed.isCompleted && isActive) {
                        delay(5000)
                        if (++polls % 5 == 0) socket?.send(JSONObject().put("topic", "phoenix").put("event", "heartbeat").put("payload", JSONObject()).put("ref", "${ref++}").toString())
                        try { sync() } catch (e: Exception) { if (e is CancellationException) throw e }
                    }
                }
            } catch (e: Exception) { if (e is CancellationException) throw e }
            finally { socket?.close(1000, "reconnect"); socket = null }
            delay(delayMs); delayMs = (delayMs * 2).coerceAtMost(60000)
        }
    }
}
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try { (applicationContext as MercadoApp).sync.sync(); Result.success() }
        catch (e: Exception) { if (e is CancellationException) throw e; Result.retry() }
}
