package br.com.mercadofacil.repository

import androidx.room.withTransaction
import br.com.mercadofacil.database.*
import br.com.mercadofacil.domain.Money
import org.json.JSONObject
import java.util.UUID

class ShoppingRepository(val db: MercadoDatabase) {
    val dao = db.dao()
    suspend fun initialize() { defaultCategories.forEach { dao.category(Category(it)) } }
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
