package br.com.mercadofacil

import br.com.mercadofacil.domain.SpokenShoppingItem
import br.com.mercadofacil.domain.parseSpokenShoppingItems
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceShoppingParserTest {
    @Test fun normalizesCommonPluralsWithoutChangingQuantity() {
        assertEquals(
            listOf(SpokenShoppingItem("Tomate", "3"), SpokenShoppingItem("Arroz", "2"), SpokenShoppingItem("Pão", "4")),
            parseSpokenShoppingItems("três tomates, dois arrozes e quatro pães")
        )
    }

    @Test fun preservesBrandsAndPackageSizesAndCapitalizesAccentedNames() {
        assertEquals(
            listOf(SpokenShoppingItem("Lápis", "1"), SpokenShoppingItem("Coca Cola 2 litros", "1"), SpokenShoppingItem("Água", "2")),
            parseSpokenShoppingItems("lápis, Coca Cola 2 litros e duas águas")
        )
    }
    @Test fun parsesMultipleProductsAndNumericQuantities() {
        assertEquals(
            listOf(
                SpokenShoppingItem("Leite", "2"),
                SpokenShoppingItem("Arroz", "1"),
                SpokenShoppingItem("Refrigerante", "3")
            ),
            parseSpokenShoppingItems("2 leites, 1 arroz e 3 refrigerantes")
        )
    }

    @Test fun supportsWrittenNumbersAndVoiceWithoutPunctuation() {
        assertEquals(
            listOf(
                SpokenShoppingItem("Leite", "2"),
                SpokenShoppingItem("Arroz", "1"),
                SpokenShoppingItem("Refrigerante", "3")
            ),
            parseSpokenShoppingItems("dois leites um arroz e três refrigerantes")
        )
    }

    @Test fun defaultsToOneWhenNoQuantityWasSaid() {
        assertEquals(
            listOf(SpokenShoppingItem("Leite", "1"), SpokenShoppingItem("Pão", "1")),
            parseSpokenShoppingItems("leite e pão")
        )
    }

    @Test fun supportsBrazilianDecimalAmountsAndKeepsPackageSizes() {
        assertEquals(
            listOf(
                SpokenShoppingItem("Kg de banana", "2.5"),
                SpokenShoppingItem("Coca Cola 2 litros", "1")
            ),
            parseSpokenShoppingItems("2,5 kg de banana, 1 Coca Cola 2 litros")
        )
    }

    @Test fun ignoresBlankFragmentsAndRejectsOnlyUnusableSpeech() {
        assertEquals(emptyList<SpokenShoppingItem>(), parseSpokenShoppingItems(" , ; e "))
        assertEquals(listOf(SpokenShoppingItem("Sal", "1")), parseSpokenShoppingItems("sal."))
    }

    @Test fun refusesInvalidAmountsRatherThanSavingWrongQuantities() {
        assertEquals(emptyList<SpokenShoppingItem>(), parseSpokenShoppingItems("0 leites"))
        assertEquals(emptyList<SpokenShoppingItem>(), parseSpokenShoppingItems("1000001 leites"))
    }
}
