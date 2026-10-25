package br.com.mercadofacil.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.mercadofacil.R
import br.com.mercadofacil.database.PurchaseHistory
import br.com.mercadofacil.database.ShoppingItem
import br.com.mercadofacil.domain.Money
import org.json.JSONArray
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable fun ShoppingTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFFA5E3C4), onPrimary = Color(0xFF102D22),
        primaryContainer = Color(0xFF254C3B), onPrimaryContainer = Color(0xFFE4FAED),
        secondary = Color(0xFFF1C879), onSecondary = Color(0xFF382909),
        secondaryContainer = Color(0xFF514329), onSecondaryContainer = Color(0xFFFFE7B3),
        background = Color(0xFF101A16), surface = Color(0xFF1B2922), surfaceVariant = Color(0xFF26392E),
        onBackground = Color(0xFFF1F5EE), onSurface = Color(0xFFF1F5EE),
        onSurfaceVariant = Color(0xFFB9CABD), outline = Color(0xFF8FA69A), outlineVariant = Color(0xFF35473C)
    ) else lightColorScheme(
        primary = Color(0xFF176347), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE1F2E6), onPrimaryContainer = Color(0xFF174B36),
        secondary = Color(0xFFBE8D38), onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFFEDC8), onSecondaryContainer = Color(0xFF624313),
        background = Color(0xFFF7F8F4), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFEDF3EC),
        onBackground = Color(0xFF192C23), onSurface = Color(0xFF192C23),
        onSurfaceVariant = Color(0xFF63756A), outline = Color(0xFF899D8E), outlineVariant = Color(0xFFE4EAE2)
    )
    MaterialTheme(
        colorScheme = colors,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp)),
        typography = Typography(
            headlineSmall = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
            titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold),
            titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
            bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
            labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
            labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold),
            labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp)
        ),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ShoppingContent(
    listName: String,
    products: List<ShoppingItem>,
    purchases: List<PurchaseHistory>,
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    draft: String,
    onDraftChange: (String) -> Unit,
    onAdd: () -> Unit,
    onToggle: (ShoppingItem) -> Unit,
    onComplete: () -> Unit,
    onEdit: (ShoppingItem) -> Unit,
    onDelete: (ShoppingItem) -> Unit,
    loading: Boolean = false,
    adding: Boolean = false,
    paying: Boolean = false,
    error: String? = null,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    actions: @Composable RowScope.() -> Unit = {}
) {
    val pending = products.filter { !it.purchased }
    val selected = products.filter { it.purchased }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { Column { TopAppBar(
            title = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BrandBadge()
                Text("Mercado Fácil", style = MaterialTheme.typography.titleLarge, letterSpacing = (-0.6).sp)
            } },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
            TabRow(selectedTabIndex = selectedTab,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp).clip(RoundedCornerShape(16.dp)),
                containerColor = MaterialTheme.colorScheme.surface,
                divider = {}) {
                Tab(selected = selectedTab == 0, onClick = { onTabChange(0) },
                    text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Icon(Icons.Outlined.Checklist, null, Modifier.size(19.dp))
                        Text("Minha lista", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium)
                    } })
                Tab(selected = selectedTab == 1, onClick = { onTabChange(1) },
                    text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.ReceiptLong, null, Modifier.size(19.dp))
                        Text("Compras (${purchases.size})", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium)
                    } })
            }
        } },
        bottomBar = {
            if (selectedTab == 0) {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 5.dp,
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)) {
                Column(Modifier.navigationBarsPadding().imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (selected.isNotEmpty()) {
                        Column(Modifier.fillMaxWidth().widthIn(max = 640.dp)
                            .padding(start = 20.dp, end = 20.dp, top = 14.dp)) {
                            Button(onClick = onComplete, enabled = !paying && !loading,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                shape = RoundedCornerShape(15.dp)) {
                                if (paying) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                                Spacer(Modifier.width(10.dp))
                                Text("Comprados (${selected.size})", fontWeight = FontWeight.SemiBold)
                            }
                            Text("Confirme depois de passar pelo caixa.",
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(Modifier.fillMaxWidth().widthIn(max = 640.dp).padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = draft, onValueChange = onDraftChange,
                            placeholder = { Text("Adicionar produto...") },
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(15.dp),
                            singleLine = true, enabled = !loading && !adding,
                            isError = error != null,
                            supportingText = if (error != null) ({ Text(error) }) else null,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { if (draft.isNotBlank() && !adding) onAdd() })
                        )
                        FilledIconButton(
                            onClick = onAdd, enabled = !loading && !adding && draft.isNotBlank(),
                            modifier = Modifier.size(54.dp), shape = RoundedCornerShape(15.dp)
                        ) { if (adding) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.Add, "Adicionar produto", Modifier.size(26.dp)) }
                    }
                }
            }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            if (selectedTab == 1) PurchaseHistoryContent(purchases)
            else if (loading) CircularProgressIndicator(Modifier.padding(48.dp))
            else LazyColumn(
                Modifier.fillMaxSize().widthIn(max = 640.dp),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 28.dp)
            ) {
                if (listName != "Minha lista") item("list-name") {
                    Text(listName, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 2.dp, bottom = 14.dp))
                }
                if (pending.isNotEmpty() || selected.isEmpty()) item("pending-heading") { SectionTitle("Para comprar", pending.size) }
                if (products.isEmpty()) item("empty") { EmptyState("Sua lista começa aqui", "Adicione o primeiro produto no campo abaixo.") }
                items(pending, key = { it.id }) { product ->
                    ShoppingRow(product, { onToggle(product) }, { onEdit(product) }, { onDelete(product) }, Modifier.animateItem(placementSpec = tween(180)))
                }
                if (selected.isNotEmpty()) item("selected-heading") {
                    Spacer(Modifier.height(22.dp)); SectionTitle("Selecionados", selected.size)
                }
                items(selected, key = { it.id }) { product ->
                    ShoppingRow(product, { onToggle(product) }, { onEdit(product) }, { onDelete(product) }, Modifier.animateItem(placementSpec = tween(180)))
                }
            }
        }
    }
}

@Composable private fun PurchaseHistoryContent(history: List<PurchaseHistory>) {
    val dateFormat = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR")) }
    LazyColumn(Modifier.fillMaxSize().widthIn(max = 640.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item("history-header") {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.AutoMirrored.Outlined.ReceiptLong, null, Modifier.padding(13.dp).size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column {
                    Text("Suas compras", style = MaterialTheme.typography.titleLarge)
                    Text("As 10 compras mais recentes ficam aqui.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (history.isEmpty()) item("history-empty") {
            EmptyState("Nenhuma compra registrada", "Selecione produtos na lista e toque em Comprados depois de pagar.")
        }
        items(history, key = { it.id }) { purchase ->
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                            Icon(Icons.Outlined.Check, null, Modifier.padding(9.dp).size(18.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Column {
                            Text(Instant.ofEpochMilli(purchase.createdAt).atZone(ZoneId.systemDefault()).format(dateFormat),
                                style = MaterialTheme.typography.titleMedium)
                            Text("${purchase.listName} · ${purchase.itemCount} ${if (purchase.itemCount == 1) "item" else "itens"}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val products = remember(purchase.itemsJson) {
                        JSONArray(purchase.itemsJson).let { array -> (0 until array.length()).map { array.getJSONObject(it) } }
                    }
                    products.forEach { product ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(product.getString("name"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text("${product.getString("quantity").replace('.', ',')} ${product.getString("unit")}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (purchase.totalCents > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text("Total informado: ${Money.format(purchase.totalCents)}", style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable private fun BrandBadge() {
    Image(painter = painterResource(R.drawable.ic_brand_mark), contentDescription = null,
        modifier = Modifier.size(44.dp))
}

@Composable private fun SectionTitle(title: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, bottom = 13.dp).semantics { heading() }, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Surface(shape = RoundedCornerShape(9.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(count.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp))
        }
    }
}

@Composable private fun ShoppingRow(product: ShoppingItem, toggle: () -> Unit, edit: () -> Unit, delete: () -> Unit, modifier: Modifier) {
    var menu by remember { mutableStateOf(false) }
    Surface(modifier.fillMaxWidth().padding(bottom = 9.dp), shape = RoundedCornerShape(17.dp),
        color = if (product.purchased) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (product.purchased) MaterialTheme.colorScheme.primary.copy(alpha = 0.23f)
            else MaterialTheme.colorScheme.outlineVariant)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 66.dp).toggleable(value = product.purchased, role = Role.Checkbox, onValueChange = { toggle() })
                .padding(start = 15.dp, end = 5.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(if (product.purchased) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null,
                tint = if (product.purchased) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(end = 13.dp).size(26.dp))
            Column(Modifier.weight(1f)) {
                Text(product.name, style = MaterialTheme.typography.bodyLarge, fontWeight = if (product.purchased) FontWeight.Normal else FontWeight.Medium,
                    color = if (product.purchased) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (product.purchased) TextDecoration.LineThrough else TextDecoration.None)
                if (product.quantity != "1" || product.unit != "un") Text("${product.quantity.replace('.', ',')} ${product.unit}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreHoriz, "Opções de ${product.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Editar produto") }, onClick = { menu = false; edit() })
                    DropdownMenuItem(text = { Text("Excluir produto") }, onClick = { menu = false; delete() })
                }
            }
        }
    }
}

@Composable fun EmptyState(title: String, text: String) {
    Surface(Modifier.fillMaxWidth().padding(top = 4.dp), color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.Outlined.ShoppingBasket, null, Modifier.padding(16.dp).size(32.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
