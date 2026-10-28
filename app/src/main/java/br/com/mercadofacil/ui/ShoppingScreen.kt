package br.com.mercadofacil.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import br.com.mercadofacil.BuildConfig
import br.com.mercadofacil.database.*
import br.com.mercadofacil.domain.Money
import br.com.mercadofacil.domain.quickShoppingItem
import br.com.mercadofacil.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MercadoScreen(vm: MainViewModel) {
    val lists by vm.lists.collectAsStateWithLifecycle()
    val products by vm.items.collectAsStateWithLifecycle()
    val purchases by vm.purchases.collectAsStateWithLifecycle()
    val selected by vm.selectedList.collectAsStateWithLifecycle()
    val config by vm.settings.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val current = lists.find { it.id == selected } ?: lists.firstOrNull { it.status == "active" }
    val currentProducts = products.filter { it.listId == current?.id }
    var draftPhoto by rememberSaveable { mutableStateOf("") }
    var draft by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableStateOf("1") }
    var inputError by remember { mutableStateOf<String?>(null) }
    var quantityError by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var paying by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var preferences by remember { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var chooseList by remember { mutableStateOf(false) }
    var newList by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<ShoppingItem?>(null) }
    var delete by remember { mutableStateOf<ShoppingItem?>(null) }
    var sharing by remember { mutableStateOf(false) }
    var checkUpdates by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val dark = config.theme == "dark" || config.theme == "auto" && isSystemInDarkTheme()
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }
    LaunchedEffect(vm, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.app.sync.watch() }
    }
    LaunchedEffect(selected) { tab = 0; draft = ""; draftPhoto = ""; quantity = "1"; inputError = null; quantityError = null }
    BackHandler(advanced) { advanced = false }
    ShoppingTheme(dark) {
        UpdatePrompt(vm, checkUpdates)
        if (advanced) Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = { TopAppBar(title = { Text("Ajustes") }, navigationIcon = { IconButton(onClick = { advanced = false }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar à lista") } }) }
        ) { padding -> SettingsScreen(vm, Modifier.padding(padding)) }
        else ShoppingContent(
            products = currentProducts, purchases = purchases,
            photoData = draftPhoto, onPhotoChange = { draftPhoto = it },
            selectedTab = tab, onTabChange = { tab = it }, draft = draft,
            onDraftChange = { draft = it; inputError = null },
            quantity = quantity, onQuantityChange = { quantity = it; quantityError = null },
            onAdd = {
                val list = current
                val name = draft.trim()
                if (name.length > 200) inputError = "Use até 200 caracteres para o produto."
                else if (list != null && name.isNotEmpty() && !adding) {
                    val position = (currentProducts.maxOfOrNull { it.position } ?: -1) + 1
                    val item = runCatching { quickShoppingItem(list.id, name, quantity, position).copy(photoData = draftPhoto) }
                        .onFailure { quantityError = it.message ?: "Quantidade inválida." }.getOrNull()
                    if (item != null) {
                        adding = true
                        vm.run {
                            try {
                                vm.repository.save(item)
                                if (list.status != "active") vm.save(list.copy(status = "active"))
                                draft = ""
                                draftPhoto = ""
                                quantity = "1"
                                vm.requestSync()
                            } finally { adding = false }
                        }
                    }
                }
            },
            onToggle = { vm.save(it.copy(purchased = !it.purchased)) },
            onComplete = {
                val list = current
                if (list != null && !paying) {
                    paying = true
                    vm.run {
                        try {
                            if (vm.repository.completePurchase(list.id) != null) {
                                vm.requestSync()
                                tab = 1
                            }
                        } finally { paying = false }
                    }
                }
            },
            onEdit = { edit = it }, onDelete = { delete = it },
            loading = current == null, adding = adding, paying = paying, error = inputError,
            quantityError = quantityError, snackbar = snackbar,
            actions = {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Menu da lista") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Compartilhar lista") }, onClick = { menu = false; sharing = true })
                        DropdownMenuItem(text = { Text("Outras listas") }, onClick = { menu = false; chooseList = true })
                        DropdownMenuItem(text = { Text("Conta e ajustes") }, onClick = { menu = false; advanced = true })
                        DropdownMenuItem(text = { Text("Ajustes rápidos") }, onClick = { menu = false; preferences = true })
                    }
                }
            }
        )
        if (preferences) AlertDialog(onDismissRequest = { preferences = false }, title = { Text("Configurações") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Choice("Tema", when (config.theme) { "light" -> "Claro"; "dark" -> "Escuro"; else -> "Automático" }, listOf("Automático", "Claro", "Escuro")) { choice ->
                    vm.run { vm.app.settings.save(config.copy(theme = when (choice) { "Claro" -> "light"; "Escuro" -> "dark"; else -> "auto" })) }
                }
                Text("Versão ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { preferences = false; checkUpdates++ }) { Text("Verificar atualizações") }
                TextButton(onClick = { preferences = false; advanced = true }) { Text("Conta, atualizações e backups") }
            }
        }, confirmButton = { TextButton(onClick = { preferences = false }) { Text("Fechar") } })
        if (chooseList) AlertDialog(onDismissRequest = { chooseList = false }, title = { Text("Suas listas") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                lists.forEach { list -> TextButton(onClick = { vm.run { vm.app.settings.selectList(list.id) }; chooseList = false }) { Text(list.name + if (list.shared) " · Compartilhada" else " · Neste celular") } }
                TextButton(onClick = { chooseList = false; newList = true }) { Text("Criar outra lista") }
            }
        }, confirmButton = { TextButton(onClick = { chooseList = false }) { Text("Fechar") } })
        if (newList) {
            var name by remember { mutableStateOf("") }
            AlertDialog(onDismissRequest = { newList = false }, title = { Text("Nome da lista") }, text = { Field("Nome", name, { name = it }) },
                confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { vm.run { val list = ShoppingList(name = name); vm.repository.save(list); vm.app.settings.selectList(list.id); newList = false } }) { Text("Criar") } },
                dismissButton = { TextButton(onClick = { newList = false }) { Text("Cancelar") } })
        }
        edit?.let { product -> SimpleProductEditor(product, { edit = null }) { vm.save(it); edit = null } }
        delete?.let { product -> AlertDialog(onDismissRequest = { delete = null }, title = { Text("Excluir ${product.name}?") }, text = { Text("O produto será removido da lista.") },
            confirmButton = { TextButton(onClick = { vm.save(product.copy(deleted = true)); delete = null }) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { delete = null }) { Text("Cancelar") } }) }
        if (sharing && current != null) ShareDialog(vm, current, { sharing = false }, { sharing = false; advanced = true }) {
            val text = current.name + "\n" + currentProducts.joinToString("\n") { "${if (it.purchased) "✓" else "□"} ${it.name}${if (it.quantity != "1") " (${it.quantity} ${it.unit})" else ""}" }
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Compartilhar lista"))
        }
    }
}

@Composable private fun SimpleProductEditor(product: ShoppingItem, dismiss: () -> Unit, save: (ShoppingItem) -> Unit) {
    var photo by remember { mutableStateOf(product.photoData) }
    var name by remember { mutableStateOf(product.name) }
    var quantity by remember { mutableStateOf(product.quantity.replace('.', ',')) }
    var unit by remember { mutableStateOf(product.unit) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Editar produto") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PhotoAttachment(photo, { photo = it }, true)
            Field("Produto", name, { name = it }); Field("Quantidade", quantity, { quantity = it }, true)
            Choice("Unidade", unit, units) { unit = it }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = { runCatching {
        require(name.isNotBlank() && name.length <= 200) { "Informe o nome do produto (até 200 caracteres)." }
        save(product.copy(name = name.trim(), quantity = Money.quantity(quantity), unit = unit, photoData = photo))
    }.onFailure { error = it.message } }) { Text("Salvar") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
}
