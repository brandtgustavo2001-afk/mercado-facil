package br.com.mercadofacil.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.mercadofacil.database.*
import br.com.mercadofacil.domain.Money
import br.com.mercadofacil.viewmodel.MainViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MercadoScreen(vm: MainViewModel) {
    val lists by vm.lists.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val syncStatus by vm.app.sync.status.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var listEditor by remember { mutableStateOf<ShoppingList?>(null) }
    var itemEditor by remember { mutableStateOf<ShoppingItem?>(null) }
    var deleteAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var sharing by remember { mutableStateOf<ShoppingList?>(null) }
    var filter by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("categoria") }
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val dark = settings.theme == "dark" || settings.theme == "auto" && isSystemInDarkTheme()
    val colors = if (dark) darkColorScheme(primary = Color(0xFF69D5AC), background = Color(0xFF111B18), surface = Color(0xFF192822)) else lightColorScheme(primary = Color(0xFF087E58), secondary = Color(0xFF507A65), background = Color(0xFFF4F7F4), surface = Color.White, primaryContainer = Color(0xFFD3F3E4))
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }
    LaunchedEffect(Unit) { runCatching { br.com.mercadofacil.updater.Updater(vm.app).check()?.let { vm.message.value = "Versão ${it.version} disponível em Configurações" } } }
    LaunchedEffect(Unit) { vm.app.sync.watch() }
    val current = lists.find { it.id == selected }
    BackHandler(selected != null) { selected = null }
    MaterialTheme(colorScheme = colors) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = { TopAppBar(title = { Column { Text(current?.name ?: "Mercado Fácil", fontWeight = FontWeight.Bold); Text(if (current?.shared == true) syncStatus else "Compras organizadas. Vida mais leve.", style = MaterialTheme.typography.labelMedium) } }, navigationIcon = { if (selected != null) IconButton(onClick = { selected = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } }, actions = { if (current != null) IconButton(onClick = { sharing = current }) { Icon(Icons.Default.Share, "Compartilhar") } }) },
            bottomBar = { if (selected == null) NavigationBar {
                val labels = listOf("Início", "Listas", "Histórico", "Estatísticas", "Ajustes")
                val icons = listOf(Icons.Default.Home, Icons.Default.ShoppingCart, Icons.Default.History, Icons.Default.BarChart, Icons.Default.Settings)
                labels.forEachIndexed { i, name -> NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icons[i], name) }, label = { Text(name, maxLines = 1) }) }
            } },
            floatingActionButton = { if (current != null && current.status == "active") ExtendedFloatingActionButton(onClick = { itemEditor = ShoppingItem(listId = current.id, name = "") }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Adicionar produto") }) else if (selected == null && tab <= 1) ExtendedFloatingActionButton(onClick = { listEditor = ShoppingList(name = "") }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Nova lista") }) }
        ) { padding ->
            if (current != null) {
                val all = items.filter { it.listId == current.id }
                val visible = all.filter { it.name.contains(filter, true) }.let { if (sort == "categoria") it.sortedWith(compareBy({ it.purchased }, { it.category }, { it.name })) else it.sortedWith(compareBy({ it.purchased }, { it.name })) }
                LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { Totals(current, all) }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { listEditor = current }) { Text("Editar") }
                        OutlinedButton(onClick = { vm.run { vm.repository.duplicate(current) } }) { Text("Duplicar") }
                        OutlinedButton(onClick = { vm.save(current.copy(status = if (current.status == "active") "completed" else "active")) }) { Text(if (current.status == "active") "Concluir" else "Reabrir") }
                    } }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { vm.save(current.copy(status = "archived")); selected = null }) { Text("Arquivar") }
                        TextButton(onClick = { deleteAction = { vm.save(current.copy(deleted = true)); selected = null } }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
                    } }
                    item { OutlinedTextField(filter, { filter = it }, label = { Text("Pesquisar produto") }, modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) }) }
                    item { TextButton(onClick = { sort = if (sort == "categoria") "nome" else "categoria" }) { Text("Ordenar por $sort") } }
                    if (visible.isEmpty()) item { EmptyState("Sua lista começa aqui", "Adicione os produtos que você precisa comprar.") }
                    items(visible, key = { it.id }) { item ->
                        Card(Modifier.fillMaxWidth().clickable { itemEditor = item }, shape = RoundedCornerShape(20.dp)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(item.purchased, { vm.save(item.copy(purchased = it)) })
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, fontWeight = FontWeight.SemiBold)
                                    Text("${item.quantity.replace('.', ',')} ${item.unit} · ${item.category}", style = MaterialTheme.typography.bodySmall)
                                    if (item.purchasedBy != null && current.shared) Text("Comprado/alterado por ${item.purchasedBy.take(8)}", style = MaterialTheme.typography.labelSmall)
                                }
                                Text(Money.format(Money.line(item.quantity, item.actualCents ?: item.estimatedCents)), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else when (tab) {
                4 -> SettingsScreen(vm, Modifier.padding(padding))
                3 -> Statistics(lists, items, Modifier.padding(padding))
                else -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (tab == 0) {
                        item { Text("Olá! Vamos organizar\nsua próxima compra?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
                        item { Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale.forLanguageTag("pt-BR"))), color = MaterialTheme.colorScheme.secondary) }
                        item {
                            val active = lists.filter { it.status == "active" }.map { it.id }.toSet()
                            val activeItems = items.filter { it.listId in active }
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Column(Modifier.fillMaxWidth().padding(22.dp)) {
                                Text("PREVISÃO DAS LISTAS ATIVAS", style = MaterialTheme.typography.labelMedium)
                                Text(Money.format(activeItems.sumOf { Money.line(it.quantity, it.estimatedCents) }), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(12.dp)); Text("${active.size} listas · ${activeItems.count { !it.purchased }} pendentes · ${activeItems.count { it.purchased }} comprados")
                            } }
                        }
                        item { Text("Suas listas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    }
                    val shown = lists.filter { if (tab == 2) it.status != "active" else it.status == "active" }
                    if (shown.isEmpty()) item { EmptyState(if (tab == 2) "Nenhuma compra concluída" else "Tudo pronto para começar", if (tab == 2) "Suas listas concluídas e arquivadas aparecerão aqui." else "Toque em Nova lista e planeje sua compra.") }
                    items(shown, key = { it.id }) { list ->
                        val products = items.filter { it.listId == list.id }
                        Card(Modifier.fillMaxWidth().clickable { selected = list.id; filter = "" }, shape = RoundedCornerShape(22.dp)) {
                            Column(Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.ShoppingBag, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); Text(list.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); if (list.shared) Icon(Icons.Default.Group, "Compartilhada") }
                                Spacer(Modifier.height(12.dp))
                                Text("${products.count { it.purchased }}/${products.size} produtos · ${list.market.ifBlank { "Sem mercado definido" }}", style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(10.dp))
                                Text(Money.format(products.sumOf { Money.line(it.quantity, it.actualCents ?: it.estimatedCents) }), fontWeight = FontWeight.Bold)
                                LinearProgressIndicator(progress = { if (products.isEmpty()) 0f else products.count { it.purchased }.toFloat() / products.size }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                            }
                        }
                    }
                }
            }
        }
        listEditor?.let { list -> ListEditor(list, { listEditor = null }, { vm.save(it); listEditor = null; selected = it.id }) }
        itemEditor?.let { item -> ItemEditor(vm, item, { itemEditor = null }, { vm.save(it); itemEditor = null }, { deleteAction = { vm.save(item.copy(deleted = true)); itemEditor = null } }) }
        deleteAction?.let { action -> AlertDialog(onDismissRequest = { deleteAction = null }, title = { Text("Confirmar exclusão?") }, text = { Text("Esta ação removerá o registro da lista.") }, confirmButton = { TextButton(onClick = { action(); deleteAction = null }) { Text("Excluir") } }, dismissButton = { TextButton(onClick = { deleteAction = null }) { Text("Cancelar") } }) }
        sharing?.let { ShareDialog(vm, it, { sharing = null }) {
            val lines = items.filter { p -> p.listId == it.id }.joinToString("\n") { p -> "${if (p.purchased) "✓" else "□"} ${p.quantity} ${p.unit} ${p.name} — ${Money.format(Money.line(p.quantity, p.actualCents ?: p.estimatedCents))}" }
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "${it.name}\n$lines"), "Compartilhar lista"))
        } }
    }
}
@Composable fun EmptyState(title: String, text: String) { Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.ShoppingBasket, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(16.dp)); Text(title, fontWeight = FontWeight.Bold); Text(text, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium) } }
@Composable fun Totals(list: ShoppingList, items: List<ShoppingItem>) {
    val estimated = items.sumOf { Money.line(it.quantity, it.estimatedCents) }
    val bought = items.filter { it.purchased }.sumOf { Money.line(it.quantity, it.actualCents ?: it.estimatedCents) }
    val remaining = items.filter { !it.purchased }.sumOf { Money.line(it.quantity, it.estimatedCents) }
    Card { Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("SEU CARRINHO", style = MaterialTheme.typography.labelMedium)
        Text(Money.format(bought), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Estimado: ${Money.format(estimated)} · Restante: ${Money.format(remaining)}")
        Text("Projeção: ${Money.format(bought + remaining)}")
        if (list.budgetCents > 0) Text("Orçamento: ${Money.format(list.budgetCents)} · Saldo: ${Money.format(list.budgetCents - bought - remaining)}", color = if (bought + remaining > list.budgetCents) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        Text("${items.count { it.purchased }} de ${items.size} produtos comprados")
        LinearProgressIndicator(progress = { if (items.isEmpty()) 0f else items.count { it.purchased }.toFloat() / items.size }, modifier = Modifier.fillMaxWidth())
    } }
}
@Composable fun ListEditor(list: ShoppingList, dismiss: () -> Unit, save: (ShoppingList) -> Unit) {
    var name by remember { mutableStateOf(list.name) }; var description by remember { mutableStateOf(list.description) }; var market by remember { mutableStateOf(list.market) }; var budget by remember { mutableStateOf(Money.input(list.budgetCents)) }; var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (list.name.isEmpty()) "Nova lista" else "Editar lista") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Field("Nome", name, { name = it }); Field("Descrição", description, { description = it }); Field("Mercado", market, { market = it }); Field("Orçamento (R$)", budget, { budget = it }, true); error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { TextButton(onClick = { runCatching { require(name.isNotBlank()) { "Informe um nome" }; save(list.copy(name = name, description = description, market = market, budgetCents = Money.parse(budget))) }.onFailure { error = it.message } }) { Text("Salvar") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
}
@Composable fun Field(label: String, value: String, change: (String) -> Unit, decimal: Boolean = false) { OutlinedTextField(value, change, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Text)) }
@Composable fun Choice(label: String, value: String, options: List<String>, change: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box { OutlinedButton(onClick = { open = true }) { Text("$label: $value") }; DropdownMenu(open, { open = false }) { options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { change(option); open = false }) } } }
}
@Composable fun ItemEditor(vm: MainViewModel, item: ShoppingItem, dismiss: () -> Unit, save: (ShoppingItem) -> Unit, delete: () -> Unit) {
    val scanContext = LocalContext.current
    var name by remember { mutableStateOf(item.name) }; var quantity by remember { mutableStateOf(item.quantity.replace('.', ',')) }; var price by remember { mutableStateOf(Money.input(item.estimatedCents)) }; var actual by remember { mutableStateOf(item.actualCents?.let { Money.input(it) } ?: "") }; var unit by remember { mutableStateOf(item.unit) }; var category by remember { mutableStateOf(item.category) }; var brand by remember { mutableStateOf(item.brand) }; var notes by remember { mutableStateOf(item.notes) }; var favorite by remember { mutableStateOf(item.favorite) }; var error by remember { mutableStateOf<String?>(null) }
    val categories by vm.categories.collectAsStateWithLifecycle()
    val allItems by vm.items.collectAsStateWithLifecycle()
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (item.name.isBlank()) "Adicionar produto" else "Editar produto") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(scanContext).startScan()
                .addOnSuccessListener { barcode -> vm.run {
                    val code = barcode.rawValue ?: error("Código não reconhecido")
                    notes = "Código: $code"
                    try { val found = br.com.mercadofacil.network.ProductLookup.name(code); if (found != null) name = found else error = "Produto não encontrado. Preencha o nome manualmente." }
                    catch (e: Exception) { error = "Sem consulta disponível. Preencha o nome manualmente." }
                } }.addOnFailureListener { error = "Leitor indisponível. Cadastre o produto manualmente." }
        }) { Text("Ler código de barras") }
        Field("Produto", name, { name = it })
        if (item.name.isBlank() && name.length >= 2) allItems.filter { it.name.contains(name, true) }.distinctBy { it.name }.take(3).forEach { old -> TextButton(onClick = { name = old.name; price = Money.input(old.actualCents ?: old.estimatedCents); category = old.category; unit = old.unit }) { Text("${old.name} · último ${Money.format(old.actualCents ?: old.estimatedCents)}") } }
        Field("Quantidade", quantity, { quantity = it }, true); Choice("Unidade", unit, units) { unit = it }; Field("Preço previsto por unidade (R$)", price, { price = it }, true); Field("Preço real por unidade (opcional)", actual, { actual = it }, true)
        Choice("Categoria", category, categories.map { it.name }) { category = it }; Field("Marca", brand, { brand = it }); Field("Observações", notes, { notes = it })
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(favorite, { favorite = it }); Text("Favorito") }
        if (item.name.isNotBlank()) Row { TextButton(onClick = delete) { Text("Excluir") }; TextButton(onClick = { save(item.copy(id = java.util.UUID.randomUUID().toString(), name = "$name (cópia)", purchased = false, version = 0)) }) { Text("Duplicar") } }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { TextButton(onClick = { runCatching {
        require(name.isNotBlank()) { "Informe o produto" }
        save(item.copy(name = name, quantity = Money.quantity(quantity), estimatedCents = Money.parse(price), actualCents = actual.takeIf { it.isNotBlank() }?.let { Money.parse(it) }, unit = unit, category = category, brand = brand, notes = notes, favorite = favorite))
    }.onFailure { error = it.message } }) { Text("Salvar") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
}
