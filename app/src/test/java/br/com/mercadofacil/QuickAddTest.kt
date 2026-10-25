package br.com.mercadofacil

import br.com.mercadofacil.domain.quickShoppingItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class QuickAddTest {
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
}
