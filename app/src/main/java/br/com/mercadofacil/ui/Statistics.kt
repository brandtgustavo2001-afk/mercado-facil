package br.com.mercadofacil.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.mercadofacil.database.*
import br.com.mercadofacil.domain.Money
import java.time.*

@Composable fun Statistics(lists: List<ShoppingList>, items: List<ShoppingItem>, modifier: Modifier) {
    val months = lists.filter { it.status == "completed" }.map { Instant.ofEpochMilli(it.updatedAt).atZone(ZoneId.systemDefault()).toLocalDate().toString().take(7) }.distinct().sortedDescending()
    var month by remember { mutableStateOf("") }
    val selected = lists.filter { it.status == "completed" && (month.isEmpty() || Instant.ofEpochMilli(it.updatedAt).atZone(ZoneId.systemDefault()).toLocalDate().toString().startsWith(month)) }.map { it.id }.toSet()
    val purchased = items.filter { it.purchased && it.listId in selected }
    val categories = purchased.groupBy { it.category }.mapValues { (_, values) -> values.sumOf { Money.line(it.quantity, it.actualCents ?: it.estimatedCents) } }.entries.sortedByDescending { it.value }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Seu dinheiro,\nmais bem cuidado.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Choice("Período", month.ifEmpty { "Todos" }, listOf("Todos") + months) { month = if (it == "Todos") "" else it }
        Text(Money.format(purchased.sumOf { Money.line(it.quantity, it.actualCents ?: it.estimatedCents) }), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text("Gastos em compras concluídas")
        if (categories.isEmpty()) EmptyState("Ainda sem estatísticas", "Conclua uma compra para acompanhar os gastos.")
        categories.forEach { entry -> Column { Row { Text(entry.key, Modifier.weight(1f)); Text(Money.format(entry.value)) }; LinearProgressIndicator(progress = { if (categories.first().value == 0L) 0f else entry.value.toFloat() / categories.first().value }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) } }
        Text("Gastos mensais", style = MaterialTheme.typography.titleLarge)
        months.take(12).forEach { m ->
            val ids = lists.filter { it.status == "completed" && Instant.ofEpochMilli(it.updatedAt).atZone(ZoneId.systemDefault()).toLocalDate().toString().startsWith(m) }.map { it.id }.toSet()
            val total = items.filter { it.listId in ids && it.purchased }.sumOf { Money.line(it.quantity, it.actualCents ?: it.estimatedCents) }
            Row { Text(m, Modifier.weight(1f)); Text(Money.format(total)) }
        }
    }
}
