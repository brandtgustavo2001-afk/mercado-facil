package br.com.mercadofacil

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import br.com.mercadofacil.backup.Backup
import br.com.mercadofacil.database.*
import br.com.mercadofacil.repository.ShoppingRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
class RoomPersistenceTest {
    @Test fun listsAndDecimalPricesSurviveReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "test-${java.util.UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, MercadoDatabase::class.java, name).build()
        try {
            val repo = ShoppingRepository(db)
            val list = ShoppingList(name = "Compras")
            repo.save(list)
            repo.save(ShoppingItem(listId = list.id, name = "Arroz", quantity = "1.5", estimatedCents = 1234))
            db.close()
            db = Room.databaseBuilder(context, MercadoDatabase::class.java, name).build()
            assertEquals("Compras", db.dao().list(list.id)!!.name)
            assertEquals("1.5", db.dao().allItems().single().quantity)
            assertEquals(1234L, db.dao().allItems().single().estimatedCents)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun backupRestoreDoesNotDuplicateRecords() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, MercadoDatabase::class.java).build()
        try {
            val repo = ShoppingRepository(db)
            val list = ShoppingList(name = "Compras")
            repo.save(list); repo.save(ShoppingItem(listId = list.id, name = "Leite"))
            val backup = Backup(repo)
            val raw = backup.export()
            assertEquals(0, backup.restore(raw))
            assertEquals(1, repo.dao.allLists().size)
            assertEquals(1, repo.dao.allItems().size)
        } finally { db.close() }
    }
    @Test fun localWritesAndOperationsCommitTogetherInInsertionOrder() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, MercadoDatabase::class.java).build()
        try {
            val repo = ShoppingRepository(db)
            val list = ShoppingList(name = "Família", shared = true)
            repo.save(list); repo.save(ShoppingItem(listId = list.id, name = "Leite"))
            assertEquals(listOf("list", "item"), repo.dao.pending().map { it.entityType })
            assertEquals(1, repo.dao.allItems().size)
        } finally { db.close() }
    }
    @Test fun spokenBatchIsAtomicAndQueuesEverySharedItem() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, MercadoDatabase::class.java).build()
        try {
            val repo = ShoppingRepository(db)
            val list = ShoppingList(name = "Família", shared = true)
            repo.save(list)
            val items = listOf(
                ShoppingItem(listId = list.id, name = "Leites", quantity = "2", position = 0),
                ShoppingItem(listId = list.id, name = "Arroz", quantity = "1", position = 1),
                ShoppingItem(listId = list.id, name = "Refrigerantes", quantity = "3", position = 2)
            )

            repo.saveAll(items)

            assertEquals(items.map { it.name }, repo.dao.allItems().map { it.name })
            assertEquals(listOf("list", "item", "item", "item"), repo.dao.pending().map { it.entityType })
            assertEquals(items.map { it.id }, repo.dao.pending().filter { it.entityType == "item" }.map { it.entityId })

            try {
                repo.saveAll(listOf(
                    ShoppingItem(listId = list.id, name = "Manteiga"),
                    ShoppingItem(listId = list.id, name = "", quantity = "1")
                ))
                fail("Invalid batch must fail as a whole")
            } catch (_: IllegalArgumentException) { }
            assertEquals(items.size, repo.dao.allItems().size)
            assertEquals(items.size + 1, repo.dao.pending().size)
        } finally { db.close() }
    }
    @Test fun malformedBackupRollsBackAllImportedRecords() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, MercadoDatabase::class.java).build()
        try {
            val repo = ShoppingRepository(db)
            val list = ShoppingList(name = "Original")
            repo.save(list)
            val raw = org.json.JSONObject(Backup(repo).export())
            raw.getJSONArray("lists").getJSONObject(0).put("id", java.util.UUID.randomUUID().toString())
            raw.getJSONArray("items").put(org.json.JSONObject().put("id", "invalid-uuid"))
            try { Backup(repo).restore(raw.toString()); fail("Import should fail") } catch (_: IllegalArgumentException) { }
            assertEquals(1, repo.dao.allLists().size)
        } finally { db.close() }
    }
}

