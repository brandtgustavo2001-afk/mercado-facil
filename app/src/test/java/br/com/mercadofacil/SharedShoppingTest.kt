package br.com.mercadofacil

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import br.com.mercadofacil.data.Settings
import br.com.mercadofacil.database.*
import br.com.mercadofacil.repository.ShoppingRepository
import br.com.mercadofacil.domain.quickShoppingItem
import br.com.mercadofacil.sync.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
class SharedShoppingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun database() = Room.inMemoryDatabaseBuilder(context, MercadoDatabase::class.java).build()
    private fun engine(repo: ShoppingRepository, server: Server, user: String) = SyncEngine(
        context, repo, Settings(context), OkHttpClient.Builder().addInterceptor(server).build(),
        object : SessionStorage {
            private var value: String? = JSONObject().put("access_token", user)
                .put("expires_at", Long.MAX_VALUE).put("user", JSONObject().put("email", "$user@example.test")).toString()
            override fun read() = value
            override fun save(value: String) { this.value = value }
            override fun clear() { value = null }
        }
    )

    @Test fun acceptedInvitationOpensSharedListInsteadOfPreviousLocalList() = runBlocking {
        val db = database()
        try {
            val repo = ShoppingRepository(db)
            val local = repo.ensureShoppingList()
            Settings(context).selectList(local.id)
            val shared = ShoppingList(name = "Família", shared = true)
            val server = Server()
            server.lists[shared.id] = row(shared.id, JSONObject().put("id", shared.id).put("name", shared.name).put("status", "active"))
            server.invitedList = shared.id
            engine(repo, server, "guest").join("a".repeat(64))
            assertEquals(shared.id, Settings(context).selectedListId())
            assertEquals("Família", repo.dao.list(shared.id)?.name)
            assertNotNull(repo.dao.list(local.id))
        } finally { db.close() }
    }

    @Test fun twoPhonesExchangeExistingProductsNewNescauDetailsSelectionAndDeletion() = runBlocking {
        val firstDb = database(); val secondDb = database()
        try {
            val first = ShoppingRepository(firstDb); val second = ShoppingRepository(secondDb)
            val server = Server()
            val owner = engine(first, server, "owner"); val guest = engine(second, server, "guest")
            val list = ShoppingList(name = "Compras da casa", description = "Sábado", market = "Mercado", budgetCents = 25000)
            first.save(list)
            first.save(ShoppingItem(listId = list.id, name = "Arroz"))
            first.share(list); owner.sync()
            server.invitedList = list.id
            guest.join("b".repeat(64))
            assertEquals("Arroz", second.dao.allItems().single().name)
            assertEquals("Sábado", second.dao.list(list.id)?.description)
            val nescau = quickShoppingItem(list.id, "Nescau", "2", 1).copy(unit = "pct",
                photoData = "dGVzdC1waG90bw==", brand = "Nestlé", notes = "400 g", estimatedCents = 1200, actualCents = 1100, category = "Mercearia")
            first.save(nescau); owner.sync(); guest.sync()
            val received = requireNotNull(second.dao.item(nescau.id))
            assertEquals("Nescau", received.name)
            assertEquals(nescau.photoData, received.photoData)
            assertFalse(received.photoDirty)
            assertEquals("2", received.quantity); assertEquals("pct", received.unit)
            assertEquals("Nestlé", received.brand); assertEquals("400 g", received.notes)
            assertEquals(1200L, received.estimatedCents); assertEquals(1100L, received.actualCents)
            second.save(received.copy(purchased = true)); guest.sync(); owner.sync()
            assertTrue(first.dao.item(nescau.id)!!.purchased)
            first.completePurchase(list.id); owner.sync(); guest.sync()
            assertTrue(second.dao.item(nescau.id)!!.deleted)
            assertEquals("Nescau", JSONArray(second.dao.purchases().first().single().itemsJson).getJSONObject(0).getString("name"))
            assertFalse(second.dao.allItems().first { it.name == "Arroz" }.deleted)
        } finally { firstDb.close(); secondDb.close() }
    }

    @Test fun failedUploadInAnotherListDoesNotBlockReceivingSharedProducts() = runBlocking {
        val db = database()
        try {
            val repo = ShoppingRepository(db); val server = Server()
            val broken = ShoppingList(name = "Acesso revogado", shared = true)
            repo.save(broken); server.deniedList = broken.id
            val incoming = ShoppingList(name = "Família", shared = true)
            server.lists[incoming.id] = row(incoming.id, JSONObject().put("name", incoming.name))
            val product = ShoppingItem(listId = incoming.id, name = "Nescau")
            server.items[product.id] = row(product.id, JSONObject().put("name", product.name).put("quantity", "1"))
                .put("list_id", incoming.id)
            runCatching { engine(repo, server, "guest").sync() }
            assertEquals("Nescau", repo.dao.item(product.id)?.name)
            assertEquals(1, repo.dao.totalQueued())
        } finally { db.close() }
    }

    @Test fun unresolvedConflictIsNotReportedAsSynchronized() = runBlocking {
        val db = database()
        try {
            val repo = ShoppingRepository(db); val server = Server()
            val list = ShoppingList(name = "Família", shared = true)
            repo.save(list)
            val op = repo.dao.pending().single()
            repo.dao.failed(op.operationId, "conflict", "{}")
            server.lists[list.id] = row(list.id, JSONObject().put("name", "Servidor"))
            val sync = engine(repo, server, "owner")
            sync.sync()
            assertNotEquals("Sincronizado", sync.status.value)
        } finally { db.close() }
    }

    @Test fun editAfterUploadAcknowledgementBeforePullKeepsCorrectFieldBase() = runBlocking {
        val db = database()
        try {
            val repo = ShoppingRepository(db)
            val list = ShoppingList(name = "Família", shared = true)
            repo.save(list)
            val product = ShoppingItem(listId = list.id, name = "Nescau")
            repo.save(product)
            // Upload acknowledged; the snapshot with its version has not arrived yet.
            repo.dao.pending().forEach { repo.dao.ack(it.operationId) }
            repo.save(product.copy(quantity = "2"))
            val operation = JSONObject(repo.dao.pending().single().payload)
            assertEquals(1, operation.getJSONObject("data").length())
            assertEquals("1", operation.getJSONObject("base").getString("quantity"))
        } finally { db.close() }
    }

    @Test fun fullInvitationMessageCanBePastedEvenWithUnrelatedPendingUploads() = runBlocking {
        val db = database()
        try {
            val repo = ShoppingRepository(db); val server = Server()
            val broken = ShoppingList(name = "Antiga", shared = true)
            repo.save(broken); server.deniedList = broken.id
            val shared = ShoppingList(name = "Família")
            server.lists[shared.id] = row(shared.id, JSONObject().put("name", shared.name))
            server.invitedList = shared.id
            engine(repo, server, "guest").join("Convite da família. Código: ${"c".repeat(64)}")
            assertEquals(shared.id, Settings(context).selectedListId())
            assertEquals(1, repo.dao.totalQueued())
        } finally { db.close() }
    }

    @Test fun offlineEditsAreRetriedWithoutDuplicateProducts() = runBlocking {
        val firstDb = database(); val secondDb = database()
        try {
            val first = ShoppingRepository(firstDb); val second = ShoppingRepository(secondDb)
            val server = Server()
            val owner = engine(first, server, "owner"); val guest = engine(second, server, "guest")
            val list = ShoppingList(name = "Família", shared = true)
            first.save(list); owner.sync()
            server.invitedList = list.id; guest.join("d".repeat(64))
            val nescau = ShoppingItem(listId = list.id, name = "Nescau")
            server.offline = true
            first.save(nescau); first.save(nescau.copy(quantity = "2"))
            assertTrue(runCatching { owner.sync() }.isFailure)
            assertEquals(2, first.dao.totalQueued())
            assertTrue(second.dao.allItems().isEmpty())
            server.offline = false
            owner.sync(); guest.sync(); owner.sync(); guest.sync()
            assertEquals(0, first.dao.totalQueued())
            assertEquals(1, second.dao.allItems().size)
            assertEquals("2", second.dao.item(nescau.id)?.quantity)
        } finally { firstDb.close(); secondDb.close() }
    }

    @Test fun incomingSnapshotDoesNotOverwriteAnEditQueuedWhileRequestWasInFlight() = runBlocking {
        val db = database()
        try {
            val repo = ShoppingRepository(db); val server = Server()
            val list = ShoppingList(name = "Família", shared = true)
            repo.save(list)
            val item = ShoppingItem(listId = list.id, name = "Nescau")
            repo.save(item)
            val sync = engine(repo, server, "owner")
            sync.sync()
            server.beforeItemsResponse = { runBlocking {
                repo.save(repo.dao.item(item.id)!!.copy(quantity = "3"))
            } }
            sync.sync()
            assertEquals("3", repo.dao.item(item.id)?.quantity)
            assertEquals(1, repo.dao.totalQueued())
            assertNotEquals("Sincronizado", sync.status.value)
            sync.sync()
            assertEquals(0, repo.dao.totalQueued())
            assertEquals("3", server.items[item.id]!!.getJSONObject("data").getString("quantity"))
        } finally { db.close() }
    }

    private fun row(id: String, data: JSONObject) = JSONObject().put("id", id).put("data", data)
        .put("version", 1).put("created_at", "2026-10-25T12:00:00Z").put("updated_at", "2026-10-25T12:00:00Z")

    // HTTP contract double: exercises real request serialization, Room and both sync engines.
    // Production SQL authorization and Realtime still require their separate integration checks.
    private inner class Server : Interceptor {
        val lists = linkedMapOf<String, JSONObject>()
        val items = linkedMapOf<String, JSONObject>()
        val purchases = linkedMapOf<String, JSONObject>()
        val photos = linkedMapOf<String, JSONObject>()
        var invitedList = ""
        var deniedList = ""
        var offline = false
        var beforeItemsResponse: (() -> Unit)? = null
        override fun intercept(chain: Interceptor.Chain): Response {
            if (offline) throw java.io.IOException("Offline")
            val request = chain.request()
            val path = request.url.encodedPath
            var code = 200
            val body = when {
                path.endsWith("/apply_operation") -> {
                    val buffer = okio.Buffer(); request.body!!.writeTo(buffer)
                    val op = JSONObject(buffer.readUtf8())
                    if (op.getString("target_list") == deniedList) {
                        code = 403; "{\"message\":\"Sem permissão de edição\"}"
                    } else {
                        val rows = if (op.getString("entity") == "list") lists else items
                        val id = op.getString("entity_id")
                        val patch = op.getJSONObject("patch")
                        val current = rows[id]
                        val data = current?.getJSONObject("data") ?: JSONObject()
                        patch.keys().forEach { data.put(it, patch.get(it)) }
                        rows[id] = row(id, data).put("list_id", op.getString("target_list"))
                            .put("version", (current?.optLong("version") ?: 0) + 1)
                        "{\"ok\":true}"
                    }
                }
                path.endsWith("/member_role") -> "\"owner\""
                path.endsWith("/save_purchase") -> {
                    val buffer = okio.Buffer(); request.body!!.writeTo(buffer)
                    val purchase = JSONObject(buffer.readUtf8()).getJSONObject("purchase")
                    purchases[purchase.getString("id")] = purchase
                    "{\"ok\":true}"
                }
                path.endsWith("/shared_purchases") -> JSONArray(purchases.values.filter { "eq.${it.getString("list_id")}" == request.url.queryParameter("list_id") }).toString()
                path.endsWith("/item_photos") -> {
                    if (request.method == "POST") {
                        val buffer = okio.Buffer(); request.body!!.writeTo(buffer)
                        val photo = JSONObject(buffer.readUtf8()); photos[photo.getString("item_id")] = photo
                        "{}"
                    } else JSONArray(photos.values.filter { "eq.${it.getString("list_id")}" == request.url.queryParameter("list_id") }).toString()
                }
                path.endsWith("/accept_invitation") -> JSONObject().put("list_id", invitedList).toString()
                path.endsWith("/shopping_lists") -> JSONArray(lists.values.toList()).toString()
                path.endsWith("/shopping_items") -> {
                    val snapshot = JSONArray(items.values.filter {
                        "eq.${it.getString("list_id")}" == request.url.queryParameter("list_id")
                    }).toString()
                    val callback = beforeItemsResponse; beforeItemsResponse = null
                    callback?.invoke()
                    snapshot
                }
                else -> error("Unexpected request: $path")
            }
            return Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("Test")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }
    }
}
