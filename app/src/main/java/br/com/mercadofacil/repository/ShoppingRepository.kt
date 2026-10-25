package br.com.mercadofacil.repository

import androidx.room.withTransaction
import br.com.mercadofacil.database.*
import br.com.mercadofacil.domain.Money
import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID

class ShoppingRepository(val db: MercadoDatabase) {
    val dao = db.dao()
    suspend fun initialize() { defaultCategories.forEach { dao.category(Category(it)) } }
    suspend fun ensureShoppingList(preferredId: String? = null): ShoppingList = db.withTransaction {
        val available = dao.allLists().filter { !it.deleted }
        available.firstOrNull { it.id == preferredId }
            ?: available.filter { it.status == "active" }.maxByOrNull { it.updatedAt }
            ?: ShoppingList(name = "Minha lista").also { dao.save(it) }
    }
    suspend fun save(list: ShoppingList) = db.withTransaction {
        require(list.name.isNotBlank() && list.name.length <= 120)
        require(list.budgetCents >= 0)
        val next = list.copy(name = list.name.trim(), updatedAt = System.currentTimeMillis())
        val before = dao.list(next.id)
        dao.save(next)
        if (next.shared) dao.enqueue(SyncOperation(entityId = next.id, listId = next.id, entityType = "list", payload = mutation(next.json(), before?.takeIf { it.shared }?.json()), baseVersion = next.version))
    }
    suspend fun save(item: ShoppingItem) = db.withTransaction {
        require(item.name.isNotBlank() && item.name.length <= 200)
        Money.quantity(item.quantity)
        require(item.estimatedCents >= 0 && (item.actualCents ?: 0) >= 0)
        Money.line(item.quantity, item.actualCents ?: item.estimatedCents)
        val next = item.copy(name = item.name.trim(), updatedAt = System.currentTimeMillis())
        val before = dao.item(next.id)
        dao.save(next)
        val list = requireNotNull(dao.list(item.listId))
        dao.save(list.copy(updatedAt = next.updatedAt))
        if (list.shared) dao.enqueue(SyncOperation(entityId = next.id, listId = next.listId, entityType = "item", payload = mutation(next.json(), before?.takeIf { it.version > 0 || dao.queued(it.id) > 0 }?.json()), baseVersion = next.version))
    }
    /** A checkout is atomic: only selected products move into its permanent snapshot. */
    suspend fun completePurchase(listId: String, purchasedAt: Long = System.currentTimeMillis()): PurchaseHistory? = db.withTransaction {
        val list = requireNotNull(dao.list(listId)) { "Lista não encontrada" }
        val selected = dao.selectedItems(listId)
        if (selected.isEmpty()) return@withTransaction null

        val snapshots = JSONArray()
        selected.forEach { item ->
            snapshots.put(JSONObject()
                .put("name", item.name).put("quantity", item.quantity).put("unit", item.unit)
                .put("estimatedCents", item.estimatedCents).put("actualCents", item.actualCents ?: JSONObject.NULL))
        }
        val purchase = PurchaseHistory(
            listId = listId, listName = list.name, createdAt = purchasedAt,
            itemsJson = snapshots.toString(), itemCount = selected.size,
            totalCents = selected.sumOf { Money.line(it.quantity, it.actualCents ?: it.estimatedCents) }
        )
        dao.save(purchase)
        selected.forEach { item ->
            val removed = item.copy(deleted = true, updatedAt = purchasedAt)
            dao.save(removed)
            if (list.shared) dao.enqueue(SyncOperation(
                entityId = item.id, listId = listId, entityType = "item",
                payload = mutation(removed.json(), item.takeIf { it.version > 0 || dao.queued(it.id) > 0 }?.json()),
                baseVersion = item.version
            ))
        }
        dao.save(list.copy(updatedAt = purchasedAt))
        dao.trimPurchaseHistory()
        purchase
    }
    suspend fun duplicate(list: ShoppingList) = db.withTransaction {
        val next = ShoppingList(name = "${list.name} (cópia)", description = list.description, market = list.market, budgetCents = list.budgetCents)
        dao.save(next)
        dao.allItems().filter { it.listId == list.id && !it.deleted }.forEach {
            dao.save(it.copy(id = UUID.randomUUID().toString(), listId = next.id, purchased = false, actualCents = null, version = 0, purchasedBy = null))
        }
    }
    suspend fun share(list: ShoppingList) = db.withTransaction {
        require(!list.shared)
        save(list.copy(shared = true))
        dao.allItems().filter { it.listId == list.id && !it.deleted }.forEach { save(it) }
    }
}
fun mutation(after: JSONObject, before: JSONObject?): String {
    val patch = JSONObject()
    val base = JSONObject()
    after.keys().forEach { key ->
        if (before == null || after.opt(key)?.toString() != before.opt(key)?.toString()) {
            patch.put(key, after.get(key))
            if (before?.has(key) == true) base.put(key, before.get(key))
        }
    }
    return JSONObject().put("data", patch).put("base", base).toString()
}
fun ShoppingList.json() = JSONObject().put("id", id).put("name", name).put("description", description).put("market", market).put("budget_cents", budgetCents).put("status", status).put("deleted", deleted)
fun ShoppingItem.json() = JSONObject().put("id", id).put("list_id", listId).put("name", name).put("quantity", quantity).put("unit", unit).put("estimated_cents", estimatedCents).put("actual_cents", actualCents ?: JSONObject.NULL).put("category", category).put("brand", brand).put("notes", notes).put("purchased", purchased).put("deleted", deleted)
