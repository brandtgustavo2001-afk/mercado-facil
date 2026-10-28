package br.com.mercadofacil

import br.com.mercadofacil.domain.quickShoppingItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class QuickAddTest {
    @Test fun separatesQuantityDictatedThroughKeyboard() {
        val item = br.com.mercadofacil.domain.quickShoppingItemFromInput("list", "dois arroz", "1", 0)
        assertEquals("arroz", item.name)
        assertEquals("2", item.quantity)
    }
    @Test fun addsProductWithTypedBrazilianQuantity() {
        val item = quickShoppingItem("shared-list", "  Nescau  ", "2,5", 3)
        assertEquals("shared-list", item.listId)
        assertEquals("Nescau", item.name)
        assertEquals("2.5", item.quantity)
        assertEquals("un", item.unit)
        assertEquals(3, item.position)
    }

    @Test fun rejectsInvalidQuantityInsteadOfSavingDefaultOne() {
        assertThrows(IllegalArgumentException::class.java) {
            quickShoppingItem("list", "Leite", "0", 0)
        }
    }

    @Test fun rejectsBlankProductName() {
        assertThrows(IllegalArgumentException::class.java) {
            quickShoppingItem("list", "  ", "2", 0)
        }
    }

    @Test fun acceptsLastQuickOptionAndCustomizedQuantityBeyondTen() {
        assertEquals("10", quickShoppingItem("list", "Maçã", "10", 0).quantity)
        assertEquals("12.75", quickShoppingItem("list", "Farinha", "12,75", 1).quantity)
    }

    @Test fun invalidCustomizedQuantityIsNotSaved() {
        assertThrows(IllegalArgumentException::class.java) {
            quickShoppingItem("list", "Farinha", "abc", 1)
        }
    }
}
