package br.com.mercadofacil.backup

import androidx.room.withTransaction
import br.com.mercadofacil.database.*
import br.com.mercadofacil.domain.Money
import br.com.mercadofacil.repository.*
import org.json.*

class Backup(private val repository: ShoppingRepository) {
    suspend fun export(): String = repository.db.withTransaction {
        JSONObject().put("format", "mercado-facil").put("version", 1)
            .put("lists", JSONArray().also { out -> repository.dao.allLists().filter { !it.deleted }.forEach { out.put(it.json().put("created_at", it.createdAt)) } })
            .put("items", JSONArray().also { out -> repository.dao.allItems().filter { !it.deleted }.forEach { out.put(it.json().put("favorite", it.favorite)) } }).toString(2)
    }
    suspend fun restore(raw: String): Int {
        require(raw.length <= 10_000_000) { "Backup excede 10 MB" }
        val json = JSONObject(raw)
        require(json.getString("format") == "mercado-facil" && json.getInt("version") == 1) { "Formato não suportado" }
        val lists = json.getJSONArray("lists"); val items = json.getJSONArray("items")
        require(lists.length() <= 10000 && items.length() <= 100000) { "Backup excede os limites" }
        var count = 0
        repository.db.withTransaction {
            for (i in 0 until lists.length()) {
                val d = lists.getJSONObject(i); val id = d.getString("id"); java.util.UUID.fromString(id)
                if (repository.dao.list(id) == null) {
                    repository.save(ShoppingList(id = id, name = d.getString("name"), description = d.optString("description"), market = d.optString("market"), budgetCents = d.getLong("budget_cents"), status = d.getString("status"), createdAt = d.optLong("created_at", System.currentTimeMillis())))
                    count++
                }
            }
            for (i in 0 until items.length()) {
                val d = items.getJSONObject(i); val id = d.getString("id"); java.util.UUID.fromString(id)
                if (repository.dao.item(id) == null) {
                    val list = requireNotNull(repository.dao.list(d.getString("list_id"))) { "Produto sem lista" }
                    // Restoring never writes into a live shared list.
                    require(!list.shared) { "O backup contém produtos de uma lista compartilhada já existente" }
                    repository.save(ShoppingItem(id = id, listId = list.id, name = d.getString("name"), quantity = Money.quantity(d.getString("quantity")), unit = d.getString("unit"), estimatedCents = d.getLong("estimated_cents"), actualCents = if (d.isNull("actual_cents")) null else d.getLong("actual_cents"), category = d.getString("category"), brand = d.optString("brand"), notes = d.optString("notes"), purchased = d.getBoolean("purchased"), favorite = d.optBoolean("favorite")))
                    count++
                }
            }
        }
        return count
    }
    suspend fun csv(): String {
        fun quote(value: String) = "\"${value.replace("\"", "\"\"")}\""
        val lists = repository.dao.allLists().associateBy { it.id }
        return "lista;produto;quantidade;unidade;preco_centavos;comprado\r\n" + repository.dao.allItems().filter { !it.deleted }.joinToString("\r\n") { listOf(lists[it.listId]?.name ?: "", it.name, it.quantity, it.unit, (it.actualCents ?: it.estimatedCents).toString(), it.purchased.toString()).joinToString(";", transform = ::quote) }
    }
}
