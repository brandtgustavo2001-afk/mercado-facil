package br.com.mercadofacil.ui

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.mercadofacil.BuildConfig
import br.com.mercadofacil.backup.Backup
import br.com.mercadofacil.database.Category
import br.com.mercadofacil.sync.AuthFeedback
import br.com.mercadofacil.updater.Release
import br.com.mercadofacil.updater.Updater
import br.com.mercadofacil.viewmodel.MainViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable fun SettingsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val config by vm.settings.collectAsStateWithLifecycle()
    val email by vm.app.sync.email.collectAsStateWithLifecycle()
    val conflicts by vm.conflicts.collectAsStateWithLifecycle()
    val queued by vm.queue.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()

    var loginEmail by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var signup by rememberSaveable { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var authBusy by remember { mutableStateOf(false) }
    var authFeedback by remember { mutableStateOf("") }
    var authError by remember { mutableStateOf(false) }

    var repo by remember(config.repository) { mutableStateOf(config.repository) }
    var url by remember(config.supabaseUrl) { mutableStateOf(config.supabaseUrl) }
    var key by remember(config.supabaseKey) { mutableStateOf(config.supabaseKey) }
    var category by remember { mutableStateOf("") }
    var reminderHours by rememberSaveable { mutableStateOf("24") }
    var release by remember { mutableStateOf<Release?>(null) }
    var checked by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var download by remember { mutableStateOf<Job?>(null) }
    var apk by remember { mutableStateOf<File?>(null) }
    var importData by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val updater = remember { Updater(context.applicationContext) }
    val backup = remember { Backup(vm.repository) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) vm.message.value = "Notificações não autorizadas no Android."
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.run {
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(backup.export()) } }
            vm.message.value = "Backup salvo com sucesso."
        }
    }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.run {
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(backup.csv()) } }
            vm.message.value = "Relatório exportado."
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.run {
            importData = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)!!.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= 10_000_000) { "Backup excede 10 MB." }
                        output.write(buffer, 0, count)
                    }
                    String(output.toByteArray(), Charsets.UTF_8)
                }
            }
        }
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("PERSONALIZE SUA EXPERIÊNCIA", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text("Conta e ajustes", style = MaterialTheme.typography.headlineSmall)
        Text("Gerencie sua conta e suas preferências.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp))

        SettingsPanel("Conta e sincronização", "Suas listas em todos os celulares.",
            icon = Icons.Outlined.CloudSync, initiallyExpanded = true) {
            if (email.isNotBlank()) {
                Text("Conta conectada", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold)
                Text(email, style = MaterialTheme.typography.bodyMedium)
                Text(if (queued == 0) "Suas alterações estão salvas." else "$queued alterações aguardando envio.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { vm.run { vm.app.sync.sync(); vm.message.value = "Sincronização concluída." } },
                    modifier = Modifier.fillMaxWidth()) { Text("Sincronizar agora") }
                TextButton(onClick = { vm.run { vm.app.sync.logout() } }) { Text("Sair da conta") }
            } else {
                Text("Entre para acessar listas compartilhadas em outros celulares. A lista local funciona sem conta.",
                    style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (signup) OutlinedButton(onClick = { signup = false; authFeedback = "" }, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(13.dp)) { Text("Entrar") }
                    else Button(onClick = { signup = false; authFeedback = "" }, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(13.dp)) { Text("Entrar") }
                    if (signup) Button(onClick = {}, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(13.dp)) { Text("Criar conta") }
                    else OutlinedButton(onClick = { signup = true; authFeedback = "" }, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(13.dp)) { Text("Criar conta") }
                }
                if (signup) {
                    Text("Crie sua conta com e-mail e senha.", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(name, { name = it }, label = { Text("Seu nome") }, singleLine = true,
                        enabled = !authBusy, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(loginEmail, { loginEmail = it.trim(); authFeedback = "" },
                    label = { Text("E-mail") }, placeholder = { Text("voce@exemplo.com") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true, enabled = !authBusy, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it; authFeedback = "" }, label = { Text("Senha") },
                    supportingText = if (signup) ({ Text("Pelo menos 8 caracteres.") }) else null,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                if (showPassword) "Ocultar senha" else "Mostrar senha")
                        }
                    },
                    singleLine = true, enabled = !authBusy, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                if (signup) OutlinedTextField(confirmation, { confirmation = it; authFeedback = "" },
                    label = { Text("Confirmar senha") }, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true, enabled = !authBusy, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())

                if (authFeedback.isNotBlank()) Surface(color = if (authError) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp)) {
                    Text(authFeedback, Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodyMedium,
                        color = if (authError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Button(enabled = !authBusy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(14.dp), onClick = {
                    val validation = AuthFeedback.validate(name, loginEmail, password, confirmation, signup)
                    if (validation != null) { authFeedback = validation; authError = true }
                    else {
                        authBusy = true
                        vm.run {
                            try {
                                val result = vm.app.sync.authenticate(loginEmail, password, signup, name.trim())
                                authFeedback = result; authError = false
                                password = ""; confirmation = ""
                                if (vm.app.sync.email.value.isNotBlank()) vm.requestSync()
                                else if (signup) signup = false
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                authError = true
                                authFeedback = AuthFeedback.failure(e)
                            } finally { authBusy = false }
                        }
                    }
                }) {
                    if (authBusy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(if (signup) "Cadastrar minha conta" else "Entrar na minha conta")
                }
                TextButton(enabled = !authBusy, onClick = {
                    if (!AuthFeedback.validEmail(loginEmail)) { authError = true; authFeedback = "Digite seu e-mail para recuperar a senha." }
                    else vm.run {
                        try {
                            vm.app.sync.recover(loginEmail)
                            authError = false
                            authFeedback = "Se houver uma conta com este e-mail, você receberá instruções para recuperar sua senha."
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            authError = true; authFeedback = AuthFeedback.failure(e)
                        }
                    }
                }) { Text("Esqueci minha senha") }
                Text("Importante: a conta do aplicativo usa e-mail e senha próprios. Entrar no painel do Supabase pelo GitHub não cria uma conta no aplicativo.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (config.supabaseUrl.isBlank() || config.supabaseKey.isBlank()) Text(
                    "A conexão do aplicativo precisa ser configurada em Opções avançadas.", color = MaterialTheme.colorScheme.error)
            }
        }

        SettingsPanel("Atualizações do app", "Versão instalada e novidades.", icon = Icons.Outlined.SystemUpdateAlt) {
            Text("Versão instalada: ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.SemiBold)
            Text("Última verificação: ${if (config.lastCheck == 0L) "nunca" else DateFormat.getDateTimeInstance().format(Date(config.lastCheck))}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(enabled = !checking, onClick = { vm.run {
                checking = true
                try {
                    release = updater.check(true)
                    checked = if (release == null) "Seu aplicativo está atualizado." else "Atualização ${release!!.version} disponível."
                } finally { checking = false }
            } }, modifier = Modifier.fillMaxWidth()) { Text(if (checking) "Verificando…" else "Verificar atualizações") }
            if (checked.isNotBlank()) Text(checked)
            release?.let { update ->
                Text("Novidades da versão ${update.version}", fontWeight = FontWeight.Bold)
                Text(update.notes.ifBlank { "Sem descrição das novidades." })
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress ?: 0f }, modifier = Modifier.fillMaxWidth())
                    Text("Baixando: ${((progress ?: 0f) * 100).toInt()}%")
                    TextButton(onClick = { download?.cancel(); progress = null }) { Text("Cancelar download") }
                } else Button(onClick = { download = vm.run {
                    progress = 0f
                    try { apk = updater.download(update) { progress = it }; vm.message.value = "Atualização baixada. Toque em Instalar." }
                    finally { progress = null }
                } }) { Text("Baixar atualização") }
            }
            apk?.let { file -> Button(onClick = {
                runCatching { if (!updater.install(context as Activity, file)) vm.message.value = "Autorize esta origem e toque em Instalar novamente." }
                    .onFailure { vm.message.value = it.message }
            }) { Text("Instalar atualização") } }
        }

        SettingsPanel("Aparência e lembretes", "Tema e avisos de compras.", icon = Icons.Outlined.Palette) {
            Choice("Tema do aplicativo", when (config.theme) { "light" -> "Claro"; "dark" -> "Escuro"; else -> "Automático" },
                listOf("Automático", "Claro", "Escuro")) { selected ->
                vm.run { vm.app.settings.save(config.copy(theme = when (selected) { "Claro" -> "light"; "Escuro" -> "dark"; else -> "auto" })) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Receber notificações", Modifier.weight(1f))
                Switch(config.notifications, { enabled ->
                    vm.run { vm.app.settings.save(config.copy(notifications = enabled)) }
                    if (enabled && Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                })
            }
            OutlinedTextField(reminderHours, { reminderHours = it }, label = { Text("Lembrar em quantas horas?") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val hours = reminderHours.toLongOrNull()
                    if (hours == null || hours < 1 || hours > 8760) vm.message.value = "Informe um número entre 1 e 8760 horas."
                    else vm.run {
                        br.com.mercadofacil.notifications.ReminderWorker.schedule(context, hours)
                        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        vm.message.value = "Lembrete agendado."
                    }
                }) { Text("Agendar") }
                TextButton(onClick = { br.com.mercadofacil.notifications.ReminderWorker.cancel(context); vm.message.value = "Lembrete cancelado." }) {
                    Text("Cancelar lembrete")
                }
            }
        }

        SettingsPanel("Meus dados e backups", "Guarde suas listas com segurança.", icon = Icons.Outlined.FolderCopy) {
            Text("O backup inclui suas listas e as últimas 10 compras.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { export.launch("mercado-facil-backup.json") }, modifier = Modifier.fillMaxWidth()) { Text("Salvar backup") }
            OutlinedButton(onClick = { import.launch(arrayOf("application/json", "application/octet-stream")) },
                modifier = Modifier.fillMaxWidth()) { Text("Restaurar backup") }
            OutlinedButton(onClick = { csv.launch("mercado-facil-relatorio.csv") },
                modifier = Modifier.fillMaxWidth()) { Text("Exportar relatório de produtos") }
            Text("Restaurar um backup acrescenta registros ausentes sem substituir os existentes.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SettingsPanel("Opções avançadas", "Servidor, repositório e categorias.", icon = Icons.Outlined.Tune) {
            Text("Conexão Supabase", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("O Mercado Fácil já vem configurado. Altere estes campos somente se usar outro projeto.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(url, { url = it }, label = { Text("URL do Supabase") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(key, { key = it }, label = { Text("Chave pública") }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { vm.run {
                require(email.isBlank() && queued == 0) { "Saia da conta e sincronize as pendências antes de mudar o servidor." }
                val uri = java.net.URI(url.trim())
                require(uri.scheme == "https" && uri.host != null) { "Informe uma URL HTTPS válida." }
                require(key.isNotBlank() && !key.trim().startsWith("sb_secret_")) { "Use somente a chave pública." }
                vm.app.settings.save(config.copy(supabaseUrl = url, supabaseKey = key))
                vm.message.value = "Conexão salva."
            } }) { Text("Salvar conexão") }
            HorizontalDivider()
            Text("Fonte de atualizações", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(repo, { repo = it }, label = { Text("GitHub: usuário/repositório") }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { vm.run {
                require(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+").matches(repo.trim())) { "Use usuário/repositório." }
                vm.app.settings.save(config.copy(repository = repo)); vm.message.value = "Repositório salvo."
            } }) { Text("Salvar repositório") }
            HorizontalDivider()
            Text("Categorias personalizadas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(category, { category = it }, label = { Text("Nome da categoria") }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = {
                if (category.isNotBlank()) vm.run { vm.repository.dao.category(Category(category.trim().take(80))); category = "" }
            }) { Text("Adicionar categoria") }
            categories.filter { it.name !in br.com.mercadofacil.database.defaultCategories }.forEach { item ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name, Modifier.weight(1f))
                    TextButton(onClick = { vm.run { vm.repository.dao.deleteCategory(item.name) } }) { Text("Remover") }
                }
            }
            if (conflicts.isNotEmpty()) {
                HorizontalDivider()
                Text("Conflitos de sincronização", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                conflicts.forEach { conflict ->
                    Text("Uma alteração em ${conflict.entityId.take(8)} foi editada em dois aparelhos.")
                    OutlinedButton(onClick = { vm.run { vm.app.sync.resolve(conflict, true) } }) { Text("Manter minha alteração") }
                    TextButton(onClick = { vm.run { vm.app.sync.resolve(conflict, false) } }) { Text("Usar alteração do outro celular") }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    importData?.let { raw ->
        AlertDialog(onDismissRequest = { importData = null }, title = { Text("Restaurar backup?") },
            text = { Text("Serão acrescentados apenas os registros que ainda não existem neste aparelho.") },
            confirmButton = { TextButton(onClick = { vm.run {
                val count = backup.restore(raw); importData = null; vm.message.value = "$count registros importados."
            } }) { Text("Restaurar") } },
            dismissButton = { TextButton(onClick = { importData = null }) { Text("Cancelar") } })
    }
}

@Composable private fun SettingsPanel(
    title: String,
    subtitle: String,
    icon: ImageVector,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(21.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(13.dp)) {
                    Icon(icon, null, modifier = Modifier.padding(11.dp).size(22.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    if (expanded) "Recolher $title" else "Abrir $title",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
            }
        }
    }
}
