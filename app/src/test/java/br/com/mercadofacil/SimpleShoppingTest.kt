package br.com.mercadofacil

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import br.com.mercadofacil.database.*
import br.com.mercadofacil.repository.ShoppingRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
class SimpleShoppingTest {
    private fun database() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), MercadoDatabase::class.java).build()

    @Test fun firstLaunchCreatesOnlyOneShoppingList() = runBlocking {
        val db = database()
        try {
            val repo = ShoppingRepository(db)
            val first = repo.ensureShoppingList()
            val second = repo.ensureShoppingList()
            assertEquals(first.id, second.id)
            assertEquals(1, repo.dao.allLists().size)
        } finally { db.close() }
    }
    @Test fun upgradeKeepsExistingListsAndProducts() = runBlocking {
        val db = database()
        try {
            val repo = ShoppingRepository(db)
            val existing = ShoppingList(name = "Compras da família", shared = true)
            repo.save(existing)
            repo.save(ShoppingItem(listId = existing.id, name = "Café", purchased = true, estimatedCents = 2390))
            assertEquals(existing.id, repo.ensureShoppingList().id)
            assertEquals(1, repo.dao.allLists().size)
            assertTrue(repo.dao.allItems().single().purchased)
            assertEquals(2390L, repo.dao.allItems().single().estimatedCents)
        } finally { db.close() }
    }
    @Test fun previouslySelectedListRemainsSelected() = runBlocking {
        val db = database()
        try {
            val repo = ShoppingRepository(db)
            val first = ShoppingList(name = "Casa", updatedAt = 1)
            val second = ShoppingList(name = "Semana", updatedAt = 2)
            repo.dao.save(first); repo.dao.save(second)
            assertEquals(first.id, repo.ensureShoppingList(first.id).id)
        } finally { db.close() }
    }
    @Test fun purchasedProductsMoveBelowPendingAndCanBeUnmarked() = runBlocking {
        val db = database()
        try {
            val repo = ShoppingRepository(db)
            val list = repo.ensureShoppingList()
            val rice = ShoppingItem(listId = list.id, name = "Arroz")
            val milk = ShoppingItem(listId = list.id, name = "Leite")
            repo.save(rice); repo.save(milk)
            repo.save(rice.copy(purchased = true))
            assertEquals(listOf(milk.id, rice.id), repo.dao.items().first().map { it.id })
            repo.save(rice.copy(purchased = false))
            assertEquals(listOf(rice.id, milk.id), repo.dao.items().first().map { it.id })
        } finally { db.close() }
    }
}
