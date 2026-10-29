package br.com.mercadofacil.domain

import java.util.Locale

// Only known product nouns: removing a final 's' would damage names such as lápis.
private val productSingulars = mapOf(
    "tomates" to "tomate", "arrozes" to "arroz", "leites" to "leite",
    "refrigerantes" to "refrigerante", "pães" to "pão", "paes" to "pão",
    "feijões" to "feijão", "feijoes" to "feijão", "ovos" to "ovo",
    "bananas" to "banana", "maçãs" to "maçã", "macas" to "maçã",
    "batatas" to "batata", "cebolas" to "cebola", "cenouras" to "cenoura",
    "laranjas" to "laranja", "limões" to "limão", "limoes" to "limão",
    "mamões" to "mamão", "mamoes" to "mamão", "melões" to "melão",
    "melancias" to "melancia", "abacates" to "abacate", "abacaxis" to "abacaxi",
    "morangos" to "morango", "mangas" to "manga", "peras" to "pera",
    "pepinos" to "pepino", "pimentões" to "pimentão", "abobrinhas" to "abobrinha",
    "berinjelas" to "berinjela", "beterrabas" to "beterraba", "alhos" to "alho",
    "iogurtes" to "iogurte", "queijos" to "queijo", "manteigas" to "manteiga",
    "biscoitos" to "biscoito", "bolachas" to "bolacha", "macarrões" to "macarrão",
    "farinhas" to "farinha", "açúcares" to "açúcar", "cafés" to "café",
    "sucos" to "suco", "águas" to "água", "aguas" to "água", "cervejas" to "cerveja",
    "carnes" to "carne", "frangos" to "frango", "peixes" to "peixe",
    "salsichas" to "salsicha", "linguiças" to "linguiça", "miojos" to "miojo",
    "sabões" to "sabão", "saboes" to "sabão", "sabonetes" to "sabonete",
    "detergentes" to "detergente", "amaciantes" to "amaciante",
    "esponjas" to "esponja", "shampoos" to "shampoo", "fraldas" to "fralda"
)

/** Normalize the product noun, retaining brand spelling, descriptions and package sizes. */
fun normalizeProductName(input: String): String {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return trimmed
    val noun = trimmed.takeWhile { !it.isWhitespace() }
    val singular = productSingulars[noun.lowercase(Locale.ROOT)] ?: noun
    return (singular + trimmed.drop(noun.length)).replaceFirstChar { it.titlecase(Locale.forLanguageTag("pt-BR")) }
}
