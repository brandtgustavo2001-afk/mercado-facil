package br.com.mercadofacil.domain

/** Item reconhecido por voz, pronto para ser incluído pela mesma rotina da inclusão manual. */
data class SpokenShoppingItem(val name: String, val quantity: String)

private val spokenNumberWords = linkedMapOf(
    "um" to "1", "uma" to "1", "dois" to "2", "duas" to "2",
    "três" to "3", "tres" to "3", "quatro" to "4", "cinco" to "5",
    "seis" to "6", "sete" to "7", "oito" to "8", "nove" to "9",
    "dez" to "10", "onze" to "11", "doze" to "12", "treze" to "13",
    "quatorze" to "14", "catorze" to "14", "quinze" to "15",
    "dezesseis" to "16", "dezessete" to "17", "dezoito" to "18",
    "dezenove" to "19", "vinte" to "20"
)

private val quantityToken = "(?:\\d+(?:[.,]\\d{1,3})?|${spokenNumberWords.keys.joinToString("|")})"
private val leadingQuantity = Regex("^($quantityToken)\\s+(.+)$", RegexOption.IGNORE_CASE)
private val quantityOnly = Regex("^$quantityToken$", RegexOption.IGNORE_CASE)
private val punctuation = Regex("\\s*(?:[;\\n\\r]|,(?!\\d)|\\.(?!\\d))\\s*")
private val conjunction = Regex("\\s+e\\s+", RegexOption.IGNORE_CASE)
// '2 litros' faz parte do nome de uma embalagem; não significa um segundo produto.
private val implicitNextItem = Regex(
    "\\s+(?=$quantityToken\\s+(?!litros?\\b|ml\\b|gramas?\\b|quilogramas?\\b|quilos?\\b|kg\\b|g\\b))",
    RegexOption.IGNORE_CASE
)

/** Divide uma única fala em produtos. Retorna vazio quando houver quantidade inválida. */
fun parseSpokenShoppingItems(speech: String): List<SpokenShoppingItem> {
    val fragments = speech.trim().split(punctuation).flatMap { it.split(conjunction) }
        .flatMap { it.split(implicitNextItem) }.map { it.trim() }
        .filter { it.isNotBlank() && !it.equals("e", ignoreCase = true) }

    val result = mutableListOf<SpokenShoppingItem>()
    for (fragment in fragments) {
        val match = leadingQuantity.matchEntire(fragment)
        val name = (match?.groupValues?.get(2) ?: fragment).trim()
        if (quantityOnly.matches(name) || name.length !in 1..200) return emptyList()
        val rawQuantity = match?.groupValues?.get(1)?.lowercase()
        val quantity = rawQuantity?.let { spokenNumberWords[it] ?: it } ?: "1"
        val checkedQuantity = runCatching { Money.quantity(quantity) }.getOrNull() ?: return emptyList()
        result += SpokenShoppingItem(name, checkedQuantity)
    }
    return result
}
