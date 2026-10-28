package br.com.mercadofacil

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import br.com.mercadofacil.database.MercadoDatabase
import br.com.mercadofacil.database.MIGRATION_1_2
import br.com.mercadofacil.database.ShoppingItem
import br.com.mercadofacil.database.ShoppingList
import br.com.mercadofacil.backup.Backup
import br.com.mercadofacil.repository.ShoppingRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
class PurchaseHistoryTest {
    private fun database() = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(), MercadoDatabase::class.java
    ).build()

    @Test fun payingMovesOnlySelectedProductsToAnImmutableReceipt() = runBlocking {
        val db = database()
        try {
            val repo = ShoppingRepository(db)
            val list = ShoppingList(name = "Mercado")
            repo.save(list)
            val milk = ShoppingItem(listId = list.id, name = "Leite", quantity = "2", estimatedCents = 500, purchased = true)
            val rice = ShoppingItem(listId = list.id, name = "Arroz")
            repo.save(milk)
            repo.save(rice)

            val purchase = repo.completePurchase(list.id, 100)
            assertNotNull(purchase)
            assertEquals(1, purchase!!.itemCount)
            assertEquals(1000L, purchase.totalCents)
            assertEquals("Leite", JSONArray(purchase.itemsJson).getJSONObject(0).getString("name"))
            assertEquals(listOf(rice.id), repo.dao.items().first().map { it.id })
            assertTrue(repo.dao.item(milk.id)!!.deleted)
            assertEquals(1, repo.dao.purchases().first().size)

            assertNull(repo.completePurchase(list.id, 101))
            assertEquals(1, repo.dao.purchases().first().size)
            repo.save(milk.copy(name = "Leite integral", deleted = true))
            assertEquals("Leite", JSONArray(repo.dao.purchases().first().single().itemsJson).getJSONObject(0).getString("name"))
        } finally { db.close() }
    }

    @Test fun keepsExactlyTenNewestPurchasesGlobally() = runBlocking {
        val db = database()
        try {
            val repo = ShoppingRepository(db)
            val listA = ShoppingList(name = "Casa")
            val listB = ShoppingList(name = "Escritório")
            repo.save(listA)
            repo.save(listB)
            repeat(12) { i ->
                val list = if (i % 2 == 0) listA else listB
                repo.save(ShoppingItem(listId = list.id, name = "Produto $i", purchased = true))
                assertNotNull(repo.completePurchase(list.id, (i + 1).toLong()))
            }
            val purchases = repo.dao.purchases().first()
            assertEquals(10, purchases.size)
            assertEquals((12L downTo 3L).toList(), purchases.map { it.createdAt })
            assertEquals(12, repo.dao.allItems().size)
            assertTrue(repo.dao.allItems().all { it.deleted })
        } finally { db.close() }
    }

    @Test fun sharedPurchaseQueuesDeletionForOtherPhones() = runBlocking {
        val db = database()
        try {
            val repo = ShoppingRepository(db)
            val list = ShoppingList(name = "Família", shared = true)
            repo.save(list)
            val item = ShoppingItem(listId = list.id, name = "Café", purchased = true)
            repo.save(item)
            repo.completePurchase(list.id)
            val operations = repo.dao.pending().filter { it.entityType == "item" && it.entityId == item.id }
            assertTrue(operations.last().payload.contains("\"deleted\":true"))
        } finally { db.close() }
    }

    @Test fun migratesExistingVersionOneDatabaseWithoutLosingSelectedProducts() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val filename = "migration-${java.util.UUID.randomUUID()}.db"
        val oldSchema = listOf(
            "schemas/br.com.mercadofacil.database.MercadoDatabase/1.json",
            "app/schemas/br.com.mercadofacil.database.MercadoDatabase/1.json"
        ).map { java.io.File(it) }.first { it.exists() }
        val entities = JSONObject(oldSchema.readText()).getJSONObject("database").getJSONArray("entities")
        val legacy = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(filename), null)
        try {
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                legacy.execSQL(entity.getString("createSql").replace(36.toChar().toString() + "{TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: JSONArray()
                for (j in 0 until indices.length()) {
                    legacy.execSQL(indices.getJSONObject(j).getString("createSql").replace(36.toChar().toString() + "{TABLE_NAME}", table))
                }
            }
            val list = ContentValues().apply {
                put("id", "legacy-list"); put("name", "Lista antiga")
                put("description", ""); put("market", ""); put("budgetCents", 0)
                put("status", "active"); put("createdAt", 100L); put("updatedAt", 100L)
                put("shared", 0); put("version", 0); put("deleted", 0)
            }
            legacy.insertOrThrow("shopping_lists", null, list)
            val item = ContentValues().apply {
                put("id", "legacy-item"); put("listId", "legacy-list"); put("name", "Arroz")
                put("quantity", "2"); put("unit", "un"); put("estimatedCents", 150)
                put("category", "Outros"); put("brand", ""); put("notes", "")
                put("purchased", 1); put("favorite", 0); put("position", 0)
                put("updatedAt", 100L); put("version", 0); put("deleted", 0)
            }
            legacy.insertOrThrow("shopping_items", null, item)
            legacy.version = 1
        } finally { legacy.close() }

        val db = Room.databaseBuilder(context, MercadoDatabase::class.java, filename).addMigrations(MIGRATION_1_2, br.com.mercadofacil.database.MIGRATION_2_3).build()
        try {
            assertEquals("Lista antiga", db.dao().list("legacy-list")!!.name)
            assertTrue(db.dao().item("legacy-item")!!.purchased)
            assertTrue(db.dao().purchases().first().isEmpty())
            val purchase = ShoppingRepository(db).completePurchase("legacy-list", 200)
            assertNotNull(purchase)
            assertEquals("Arroz", JSONArray(purchase!!.itemsJson).getJSONObject(0).getString("name"))
        } finally { db.close(); context.deleteDatabase(filename) }
    }

    @Test fun backupPreservesCheckoutHistoryAndAcceptsOldBackups() = runBlocking {
        val source = database()
        val destination = database()
        try {
            val repo = ShoppingRepository(source)
            val list = ShoppingList(name = "Mercado")
            repo.save(list)
            repo.save(ShoppingItem(listId = list.id, name = "Ovos", purchased = true))
            repo.completePurchase(list.id, 42)
            val backup = Backup(repo).export()
            assertEquals(2, JSONObject(backup).getInt("version"))

            val restoredRepo = ShoppingRepository(destination)
            Backup(restoredRepo).restore(backup)
            Backup(restoredRepo).restore(backup)
            assertEquals(1, restoredRepo.dao.purchases().first().size)
            assertEquals("Ovos", JSONArray(restoredRepo.dao.purchases().first().single().itemsJson).getJSONObject(0).getString("name"))

            val legacy = JSONObject(backup).put("version", 1).apply { remove("purchases") }
            Backup(restoredRepo).restore(legacy.toString())
            assertEquals(1, restoredRepo.dao.purchases().first().size)
        } finally { source.close(); destination.close() }
    }
}
