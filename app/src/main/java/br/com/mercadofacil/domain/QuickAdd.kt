package br.com.mercadofacil.domain

import br.com.mercadofacil.database.ShoppingItem

/** Cria o produto da inclusão rápida com a mesma quantidade usada na edição e na sincronização. */
fun quickShoppingItem(listId: String, name: String, quantityInput: String, position: Int): ShoppingItem {
    val trimmed = name.trim()
    require(trimmed.isNotEmpty() && trimmed.length <= 200) { "Informe o nome do produto (até 200 caracteres)." }
    return ShoppingItem(listId = listId, name = trimmed, quantity = Money.quantity(quantityInput), position = position)
}
