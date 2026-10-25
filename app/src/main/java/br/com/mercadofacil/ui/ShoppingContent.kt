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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.mercadofacil.R
import br.com.mercadofacil.database.ShoppingItem

@Composable fun ShoppingTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFF97D8B9), onPrimary = Color(0xFF103A2C),
        secondary = Color(0xFFF6C96E), onSecondary = Color(0xFF3F2A08),
        background = Color(0xFF101A16), surface = Color(0xFF192821), surfaceVariant = Color(0xFF24382D),
        onBackground = Color(0xFFF1F5EE), onSurface = Color(0xFFF1F5EE),
        onSurfaceVariant = Color(0xFFB3C7BA), outline = Color(0xFF91A69A), outlineVariant = Color(0xFF32483C)
    ) else lightColorScheme(
        primary = Color(0xFF165A43), onPrimary = Color(0xFFFFFFFF),
        secondary = Color(0xFFE2A945), onSecondary = Color(0xFF382505),
        background = Color(0xFFF6F8F3), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFEDF3EA),
        onBackground = Color(0xFF1D3026), onSurface = Color(0xFF1D3026),
        onSurfaceVariant = Color(0xFF52695A), outline = Color(0xFF6E8475), outlineVariant = Color(0xFFDEE9DC)
    )
    MaterialTheme(colorScheme = colors, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ShoppingContent(
    listName: String,
    products: List<ShoppingItem>,
    draft: String,
    onDraftChange: (String) -> Unit,
    onAdd: () -> Unit,
    onToggle: (ShoppingItem) -> Unit,
    onEdit: (ShoppingItem) -> Unit,
    onDelete: (ShoppingItem) -> Unit,
    loading: Boolean = false,
    adding: Boolean = false,
    error: String? = null,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    actions: @Composable RowScope.() -> Unit = {}
) {
    val pending = products.filter { !it.purchased }
    val purchased = products.filter { it.purchased }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(
            title = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BrandBadge()
                Column {
                    Text("Mercado Fácil", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
                    Text("Sua lista, sem complicação", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        ) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
                Column(Modifier.navigationBarsPadding().imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth().widthIn(max = 640.dp).padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 16.dp),
                        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = draft, onValueChange = onDraftChange,
                            label = { Text("O que precisa comprar?") },
                            placeholder = { Text("Ex.: arroz, leite, café") },
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp),
                            singleLine = true, enabled = !loading && !adding,
                            isError = error != null,
                            supportingText = if (error != null) ({ Text(error) }) else null,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { if (draft.isNotBlank() && !adding) onAdd() })
                        )
                        FilledIconButton(
                            onClick = onAdd, enabled = !loading && !adding && draft.isNotBlank(),
                            modifier = Modifier.padding(top = 8.dp).size(56.dp), shape = RoundedCornerShape(16.dp)
                        ) { if (adding) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.Add, "Adicionar produto", Modifier.size(26.dp)) }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            if (loading) CircularProgressIndicator(Modifier.padding(48.dp))
            else LazyColumn(
                Modifier.fillMaxSize().widthIn(max = 640.dp),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 28.dp)
            ) {
                if (listName != "Minha lista") item("list-name") {
                    Text(listName, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 20.dp, start = 4.dp))
                }
                if (pending.isNotEmpty() || purchased.isEmpty()) item("pending-heading") { SectionTitle("Para comprar", pending.size) }
                if (products.isEmpty()) item("empty") { EmptyState("Sua lista começa aqui", "Adicione o primeiro produto no campo abaixo.") }
                if (pending.isEmpty() && purchased.isNotEmpty()) item("complete") {
                    Row(Modifier.padding(vertical = 24.dp, horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        Column { Text("Tudo comprado!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold); Text("Pode adicionar mais produtos quando precisar.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                items(pending, key = { it.id }) { product ->
                    ShoppingRow(product, { onToggle(product) }, { onEdit(product) }, { onDelete(product) }, Modifier.animateItem(placementSpec = tween(180)))
                }
                if (purchased.isNotEmpty()) item("purchased-heading") {
                    Spacer(Modifier.height(22.dp)); SectionTitle("Comprados", purchased.size)
                }
                items(purchased, key = { it.id }) { product ->
                    ShoppingRow(product, { onToggle(product) }, { onEdit(product) }, { onDelete(product) }, Modifier.animateItem(placementSpec = tween(180)))
                }
            }
        }
    }
}

@Composable private fun BrandBadge() {
    Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFF165A43)), contentAlignment = Alignment.Center) {
        Image(painter = painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(66.dp))
    }
}

@Composable private fun SectionTitle(title: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 12.dp).semantics { heading() }, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(count.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
}

@Composable private fun ShoppingRow(product: ShoppingItem, toggle: () -> Unit, edit: () -> Unit, delete: () -> Unit, modifier: Modifier) {
    var menu by remember { mutableStateOf(false) }
    Surface(modifier.fillMaxWidth().padding(bottom = 10.dp), shape = RoundedCornerShape(18.dp),
        color = if (product.purchased) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 74.dp).toggleable(value = product.purchased, role = Role.Checkbox, onValueChange = { toggle() })
                .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(if (product.purchased) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null,
                tint = if (product.purchased) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(end = 14.dp).size(28.dp))
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
    Surface(Modifier.fillMaxWidth().padding(top = 14.dp), color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 42.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BrandBadge()
            Spacer(Modifier.height(20.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
