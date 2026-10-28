package br.com.mercadofacil

import br.com.mercadofacil.domain.SpokenShoppingItem
import br.com.mercadofacil.domain.parseSpokenShoppingItems
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceShoppingParserTest {
    @Test fun parsesMultipleProductsAndNumericQuantities() {
        assertEquals(
            listOf(
                SpokenShoppingItem("leites", "2"),
                SpokenShoppingItem("arroz", "1"),
                SpokenShoppingItem("refrigerantes", "3")
            ),
            parseSpokenShoppingItems("2 leites, 1 arroz e 3 refrigerantes")
        )
    }

    @Test fun supportsWrittenNumbersAndVoiceWithoutPunctuation() {
        assertEquals(
            listOf(
                SpokenShoppingItem("leites", "2"),
                SpokenShoppingItem("arroz", "1"),
                SpokenShoppingItem("refrigerantes", "3")
            ),
            parseSpokenShoppingItems("dois leites um arroz e três refrigerantes")
        )
    }

    @Test fun defaultsToOneWhenNoQuantityWasSaid() {
        assertEquals(
            listOf(SpokenShoppingItem("leite", "1"), SpokenShoppingItem("pão", "1")),
            parseSpokenShoppingItems("leite e pão")
        )
    }

    @Test fun supportsBrazilianDecimalAmountsAndKeepsPackageSizes() {
        assertEquals(
            listOf(
                SpokenShoppingItem("kg de banana", "2.5"),
                SpokenShoppingItem("Coca Cola 2 litros", "1")
            ),
            parseSpokenShoppingItems("2,5 kg de banana, 1 Coca Cola 2 litros")
        )
    }

    @Test fun ignoresBlankFragmentsAndRejectsOnlyUnusableSpeech() {
        assertEquals(emptyList<SpokenShoppingItem>(), parseSpokenShoppingItems(" , ; e "))
        assertEquals(listOf(SpokenShoppingItem("sal", "1")), parseSpokenShoppingItems("sal."))
    }

    @Test fun refusesInvalidAmountsRatherThanSavingWrongQuantities() {
        assertEquals(emptyList<SpokenShoppingItem>(), parseSpokenShoppingItems("0 leites"))
        assertEquals(emptyList<SpokenShoppingItem>(), parseSpokenShoppingItems("1000001 leites"))
    }
}
