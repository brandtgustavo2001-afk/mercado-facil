package br.com.mercadofacil.domain

import br.com.mercadofacil.database.ShoppingItem

/** Também interpreta a quantidade ditada pelo microfone do teclado. */
fun quickShoppingItemFromInput(listId: String, name: String, quantityInput: String, position: Int): ShoppingItem {
    val parsed = parseSpokenShoppingItems(name).singleOrNull()
    val hasQuantity = parsed != null && parsed.name != name.trim()
    return quickShoppingItem(listId, if (hasQuantity) parsed!!.name else name,
        if (hasQuantity && Money.quantity(quantityInput) == "1") parsed!!.quantity else quantityInput, position)
}

/** Cria o produto da inclusão rápida com a mesma quantidade usada na edição e na sincronização. */
fun quickShoppingItem(listId: String, name: String, quantityInput: String, position: Int): ShoppingItem {
    val trimmed = name.trim()
    require(trimmed.isNotEmpty() && trimmed.length <= 200) { "Informe o nome do produto (até 200 caracteres)." }
    return ShoppingItem(listId = listId, name = normalizeProductName(trimmed), quantity = Money.quantity(quantityInput), position = position)
}
