package br.com.mercadofacil

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.mercadofacil.database.*
import br.com.mercadofacil.repository.ShoppingRepository
import br.com.mercadofacil.backup.Backup
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistenceTest {
    @Test fun persistedListSurvivesReopenAndBackupIsIdempotent() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-${java.util.UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, MercadoDatabase::class.java, name).build()
        try {
            var repo = ShoppingRepository(db)
            val list = ShoppingList(name = "Compras")
            repo.save(list); repo.save(ShoppingItem(listId = list.id, name = "Arroz", quantity = "1.5", estimatedCents = 1234))
            val backup = Backup(repo).export()
            db.close()
            db = Room.databaseBuilder(context, MercadoDatabase::class.java, name).build()
            repo = ShoppingRepository(db)
            assertEquals("Compras", repo.dao.list(list.id)!!.name)
            assertEquals(0, Backup(repo).restore(backup))
            assertEquals(1, repo.dao.allItems().size)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun sharedWriteAndQueueAreAtomic() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, MercadoDatabase::class.java).build()
        try {
            val repo = ShoppingRepository(db)
            val list = ShoppingList(name = "Família", shared = true)
            repo.save(list)
            repo.save(ShoppingItem(listId = list.id, name = "Leite"))
            assertEquals(2, repo.dao.pending().size)
            assertEquals("list", repo.dao.pending().first().entityType)
        } finally { db.close() }
    }
}
