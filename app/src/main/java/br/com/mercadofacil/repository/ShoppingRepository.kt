package br.com.mercadofacil.repository

import androidx.room.withTransaction
import br.com.mercadofacil.database.*
import br.com.mercadofacil.domain.Money
import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID
import kotlinx.coroutines.flow.first

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
        val before = dao.list(list.id)
        val role = when {
            !list.shared -> null
            before?.shared != true -> "owner"
            else -> list.memberRole ?: before.memberRole
        }
        val next = list.copy(name = list.name.trim(), updatedAt = System.currentTimeMillis(), memberRole = role)
        dao.save(next)
        if (next.shared) dao.enqueue(SyncOperation(entityId = next.id, listId = next.id, entityType = "list", payload = mutation(next.json(), before?.takeIf { it.shared }?.json()), baseVersion = next.version))
    }
    suspend fun save(item: ShoppingItem) = db.withTransaction {
        require(item.name.isNotBlank() && item.name.length <= 200)
        Money.quantity(item.quantity)
        require(item.estimatedCents >= 0 && (item.actualCents ?: 0) >= 0)
        Money.line(item.quantity, item.actualCents ?: item.estimatedCents)
        val before = dao.item(item.id)
        val next = item.copy(name = item.name.trim(), updatedAt = System.currentTimeMillis(),
            photoDirty = item.photoDirty || before?.photoData != item.photoData && (before != null || item.photoData.isNotEmpty()))
        dao.save(next)
        val list = requireNotNull(dao.list(item.listId))
        dao.save(list.copy(updatedAt = next.updatedAt))
        if (list.shared) dao.enqueue(SyncOperation(entityId = next.id, listId = next.listId, entityType = "item", payload = mutation(next.json(), before?.json()), baseVersion = next.version))
    }
    suspend fun saveInjection(schedule: InjectionSchedule): InjectionSchedule = db.withTransaction {
        require(schedule.name.isNotBlank() && schedule.name.length <= 120)
        require(schedule.personName.length <= 120 && schedule.medication.length <= 120 && schedule.dose.length <= 80)
        require(schedule.route.length <= 120 && schedule.responsible.length <= 120 && schedule.notes.length <= 2000)
        require(schedule.intervalValue in 1..8760 && schedule.intervalUnit in setOf("HOURS", "DAYS", "WEEKS", "MONTHS"))
        val before = dao.injectionSchedule(schedule.id)
        val now = System.currentTimeMillis()
        val target = schedule.sharedListId ?: automaticInjectionList()?.id
        val next = schedule.copy(name = schedule.name.trim(), personName = schedule.personName.trim(), updatedAt = now, sharedListId = target)

        if (before?.sharedListId != null && before.sharedListId != next.sharedListId) {
            val history = dao.injectionRecordsFor(before.id)
            val removed = before.copy(active = false, deleted = true, updatedAt = now)
            dao.saveInjection(removed)
            enqueueInjectionSchedule(removed, before, requireNotNull(before.sharedListId))
            history.forEach { record ->
                val tombstone = record.copy(deleted = true, updatedAt = now)
                dao.saveInjectionRecord(tombstone)
            }
            val replacement = next.copy(id = UUID.randomUUID().toString(), version = 0, deleted = false)
            dao.saveInjection(replacement)
            val replacements = history.map { it.copy(
                id = UUID.randomUUID().toString(), scheduleId = replacement.id, version = 0, deleted = false,
                updatedAt = now
            ) }
            replacements.forEach { dao.saveInjectionRecord(it) }
            replacement.sharedListId?.let { listId ->
                enqueueInjectionSchedule(replacement, null, listId)
                replacements.forEach { enqueueInjectionRecord(it, null, listId) }
            }
            return@withTransaction replacement
        }

        dao.saveInjection(next)
        next.sharedListId?.let { listId ->
            val sameSharedListBefore = before?.takeIf { it.sharedListId == listId && !it.deleted }
            enqueueInjectionSchedule(next, sameSharedListBefore, listId)
            // A private routine can have history before the owner chooses to share it.
            if (before?.sharedListId == null && before != null) {
                dao.injectionRecordsFor(next.id).forEach { enqueueInjectionRecord(it, null, listId) }
            }
        }
        next
    }

    private suspend fun automaticInjectionList() = dao.allLists()
        .filter { it.shared && !it.deleted && it.memberRole in listOf("owner", "editor") }
        .sortedWith(compareBy<ShoppingList> { it.createdAt }.thenBy { it.id }).firstOrNull()

    /** Include existing local routines and their history when shopping collaboration becomes available. */
    suspend fun sharePrivateInjections() = db.withTransaction {
        val target = automaticInjectionList() ?: return@withTransaction
        dao.injectionSchedules().first().filter { !it.deleted && it.sharedListId == null }.forEach {
            saveInjection(it.copy(sharedListId = target.id))
        }
    }

    suspend fun deleteInjection(schedule: InjectionSchedule) = db.withTransaction {
        val current = dao.injectionSchedule(schedule.id) ?: return@withTransaction
        if (current.sharedListId == null) {
            dao.deleteInjectionSchedule(current.id)
        } else {
            val tombstone = current.copy(active = false, deleted = true, updatedAt = System.currentTimeMillis())
            dao.saveInjection(tombstone)
            dao.injectionRecordsFor(current.id).forEach { dao.saveInjectionRecord(it.copy(deleted = true, updatedAt = tombstone.updatedAt)) }
            enqueueInjectionSchedule(tombstone, current, current.sharedListId)
        }
    }

    /** Changes only this installation's notification preference, never a shared payload. */
    suspend fun setInjectionReminder(id: String, enabled: Boolean): InjectionSchedule = db.withTransaction {
        val current = requireNotNull(dao.injectionSchedule(id)) { "Rotina não encontrada" }
        val updated = current.copy(reminderEnabled = enabled)
        dao.saveInjection(updated)
        updated
    }

    suspend fun saveInjectionAndRecord(schedule: InjectionSchedule, record: InjectionRecord) = db.withTransaction {
        require(record.scheduleId == schedule.id)
        saveInjection(schedule)
        saveInjectionRecord(record)
    }

    suspend fun saveInjectionRecord(record: InjectionRecord) = db.withTransaction {
        val schedule = requireNotNull(dao.injectionSchedule(record.scheduleId)) { "Rotina não encontrada" }
        require(!schedule.deleted)
        val before = dao.injectionRecord(record.id)
        val next = record.copy(updatedAt = System.currentTimeMillis())
        dao.saveInjectionRecord(next)
        schedule.sharedListId?.let { enqueueInjectionRecord(next, before, it) }
    }

    suspend fun saveInjectionAndDeleteRecord(schedule: InjectionSchedule, record: InjectionRecord) = db.withTransaction {
        require(record.scheduleId == schedule.id)
        val saved = saveInjection(schedule)
        val current = requireNotNull(dao.injectionRecord(record.id)) { "Este registro não está mais disponível." }
        if (saved.sharedListId == null) dao.deleteInjectionRecord(record.id)
        else {
            val tombstone = current.copy(deleted = true, updatedAt = System.currentTimeMillis())
            dao.saveInjectionRecord(tombstone)
            enqueueInjectionRecord(tombstone, current, saved.sharedListId)
        }
    }

    private suspend fun enqueueInjectionSchedule(after: InjectionSchedule, before: InjectionSchedule?, listId: String) {
        val payload = mutation(after.syncJson(), before?.syncJson())
        if (JSONObject(payload).getJSONObject("data").length() == 0) return
        dao.enqueue(SyncOperation(entityId = after.id, listId = listId, entityType = "injection_schedule",
            payload = payload, baseVersion = before?.version ?: 0))
    }

    private suspend fun enqueueInjectionRecord(after: InjectionRecord, before: InjectionRecord?, listId: String) {
        val payload = mutation(after.syncJson(), before?.syncJson())
        if (JSONObject(payload).getJSONObject("data").length() == 0) return
        dao.enqueue(SyncOperation(entityId = after.id, listId = listId, entityType = "injection_record",
            payload = payload, baseVersion = before?.version ?: 0))
    }
    /** Adds all recognized products or none, including their pending shared-list operations. */
    suspend fun saveAll(items: List<ShoppingItem>) = db.withTransaction {
        items.forEach { save(it) }
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
        if (list.shared) dao.enqueue(SyncOperation(entityId = purchase.id, listId = listId, entityType = "purchase",
            payload = JSONObject().put("id", purchase.id).put("list_id", listId).put("list_name", purchase.listName)
                .put("created_at", purchase.createdAt).put("items", JSONArray(purchase.itemsJson))
                .put("total_cents", purchase.totalCents).put("item_count", purchase.itemCount).toString(), baseVersion = 0))
        selected.forEach { item ->
            // Drop the local image immediately and queue a remote R2 deletion for shared items.
            val removed = item.copy(deleted = true, photoData = "", photoDirty = list.shared, updatedAt = purchasedAt)
            dao.save(removed)
            if (list.shared) dao.enqueue(SyncOperation(
                entityId = item.id, listId = listId, entityType = "item",
                payload = mutation(removed.json(), item.json()),
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
        dao.allItems().filter { it.listId == list.id && it.photoData.isNotEmpty() }.forEach { dao.save(it.copy(photoDirty = true)) }
        // Existing local products are new remote entities when collaboration starts.
        dao.allItems().filter { it.listId == list.id && !it.deleted }.forEach {
            dao.enqueue(SyncOperation(entityId = it.id, listId = list.id, entityType = "item",
                payload = mutation(it.json(), null), baseVersion = it.version))
        }
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
fun InjectionSchedule.syncJson() = JSONObject()
    .put("id", id).put("name", name).put("person_name", personName).put("medication", medication)
    .put("dose", dose).put("route", route).put("interval_value", intervalValue).put("interval_unit", intervalUnit)
    .put("next_due_at", nextDueAt).put("responsible", responsible).put("notes", notes)
    .put("active", active).put("deleted", deleted)
fun InjectionRecord.syncJson() = JSONObject()
    .put("id", id).put("schedule_id", scheduleId).put("scheduled_for_at", scheduledForAt)
    .put("administered_at", administeredAt).put("dose", dose).put("application_site", applicationSite)
    .put("notes", notes).put("deleted", deleted)
