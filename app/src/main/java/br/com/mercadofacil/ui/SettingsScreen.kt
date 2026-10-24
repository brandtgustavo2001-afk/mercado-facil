package br.com.mercadofacil.ui

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.mercadofacil.BuildConfig
import br.com.mercadofacil.backup.Backup
import br.com.mercadofacil.database.Category
import br.com.mercadofacil.updater.*
import br.com.mercadofacil.viewmodel.MainViewModel
import kotlinx.coroutines.*
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable fun SettingsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val config by vm.settings.collectAsStateWithLifecycle()
    val email by vm.app.sync.email.collectAsStateWithLifecycle()
    val conflicts by vm.conflicts.collectAsStateWithLifecycle()
    val queued by vm.queue.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    var repo by remember(config.repository) { mutableStateOf(config.repository) }
    var url by remember(config.supabaseUrl) { mutableStateOf(config.supabaseUrl) }
    var key by remember(config.supabaseKey) { mutableStateOf(config.supabaseKey) }
    var loginEmail by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var release by remember { mutableStateOf<Release?>(null) }; var checked by remember { mutableStateOf("") }; var checking by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }; var download by remember { mutableStateOf<Job?>(null) }; var apk by remember { mutableStateOf<File?>(null) }
    var importData by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val updater = remember { br.com.mercadofacil.updater.Updater(context.applicationContext) }
    val backup = remember { Backup(vm.repository) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (!granted) vm.message.value = "Notificações não autorizadas no Android" }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) vm.run { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(backup.export()) } }; vm.message.value = "Backup exportado" } }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> if (uri != null) vm.run { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(backup.csv()) } }; vm.message.value = "Relatório exportado" } }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.run {
        importData = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { input ->
            val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) { val count = input.read(buffer); if (count < 0) break; require(output.size() + count <= 10_000_000) { "Backup excede 10 MB" }; output.write(buffer, 0, count) }
            String(output.toByteArray(), Charsets.UTF_8)
        } }
    } }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Configurações", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Aparência", style = MaterialTheme.typography.titleMedium)
        Choice("Tema", config.theme, listOf("auto", "light", "dark")) { vm.run { vm.app.settings.save(config.copy(theme = it)) } }
        Row { Checkbox(config.notifications, { enabled -> vm.run { vm.app.settings.save(config.copy(notifications = enabled)) }; if (enabled && Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }); Text("Receber notificações", Modifier.padding(top = 12.dp)) }
        HorizontalDivider()
        Text("Dados e backups", style = MaterialTheme.typography.titleMedium)
        Button(onClick = { export.launch("mercado-facil-backup.json") }) { Text("Exportar backup") }
        OutlinedButton(onClick = { import.launch(arrayOf("application/json", "application/octet-stream")) }) { Text("Importar backup") }
        OutlinedButton(onClick = { csv.launch("mercado-facil-relatorio.csv") }) { Text("Exportar relatório CSV") }
        Text("A importação acrescenta registros ausentes. Dados existentes são preservados.", style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text("Categorias", style = MaterialTheme.typography.titleMedium)
        Field("Nova categoria", category, { category = it })
        TextButton(onClick = { if (category.isNotBlank()) vm.run { vm.repository.dao.category(Category(category.trim().take(80))); category = "" } }) { Text("Adicionar categoria") }
        categories.filter { it.name !in br.com.mercadofacil.database.defaultCategories }.forEach { c -> Row { Text(c.name, Modifier.weight(1f)); TextButton(onClick = { vm.run { vm.repository.dao.deleteCategory(c.name) } }) { Text("Remover") } } }
        HorizontalDivider()
        Text("Atualizações do aplicativo", style = MaterialTheme.typography.titleMedium)
        Text("Versão instalada: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        Text("Última verificação: ${if (config.lastCheck == 0L) "nunca" else DateFormat.getDateTimeInstance().format(Date(config.lastCheck))}")
        Field("GitHub: usuário/repositório", repo, { repo = it })
        OutlinedButton(onClick = { vm.run { require(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+").matches(repo.trim())) { "Use usuário/repositório" }; vm.app.settings.save(config.copy(repository = repo)); vm.message.value = "Repositório salvo" } }) { Text("Salvar repositório") }
        Button(enabled = !checking, onClick = { vm.run { checking = true; try { release = updater.check(true); checked = if (release == null) "Você está na versão mais recente" else "Versão ${release!!.version} disponível" } finally { checking = false } } }) { Text(if (checking) "Verificando…" else "Verificar atualizações") }
        if (checked.isNotEmpty()) Text(checked)
        release?.let { update ->
            Text("Novidades ${update.version}", fontWeight = FontWeight.Bold); Text(update.notes.ifBlank { "Sem notas nesta versão" })
            if (progress != null) { LinearProgressIndicator(progress = { progress ?: 0f }, modifier = Modifier.fillMaxWidth()); Text("${((progress ?: 0f) * 100).toInt()}%"); TextButton(onClick = { download?.cancel(); progress = null }) { Text("Cancelar download") } }
            else Button(onClick = { download = vm.run { progress = 0f; try { apk = updater.download(update) { progress = it }; vm.message.value = "APK validado. Toque em Instalar atualização." } finally { progress = null } } }) { Text("Baixar atualização") }
        }
        apk?.let { file -> Button(onClick = { runCatching { if (!updater.install(context as Activity, file)) vm.message.value = "Autorize esta origem e toque em Instalar novamente" }.onFailure { vm.message.value = it.message } }) { Text("Instalar atualização") } }
        HorizontalDivider()
        Text("Sincronização Supabase", style = MaterialTheme.typography.titleMedium)
        Field("URL HTTPS", url, { url = it }); Field("Chave pública (anon/publishable)", key, { key = it })
        OutlinedButton(onClick = { vm.run {
            require(email.isBlank() && queued == 0) { "Saia da conta e sincronize as pendências antes de trocar de backend" }
            val uri = java.net.URI(url); require(uri.scheme == "https" && uri.host != null)
            require(key.isNotBlank() && !key.startsWith("sb_secret_")) { "Use somente a chave pública" }
            vm.app.settings.save(config.copy(supabaseUrl = url, supabaseKey = key)); vm.message.value = "Supabase configurado"
        } }) { Text("Salvar conexão") }
        if (email.isBlank()) {
            Field("Seu nome", name, { name = it }); Field("E-mail", loginEmail, { loginEmail = it })
            OutlinedTextField(password, { password = it }, label = { Text("Senha") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.run { vm.message.value = vm.app.sync.authenticate(loginEmail, password, false, name); password = ""; vm.app.sync.sync() } }) { Text("Entrar") }
                OutlinedButton(onClick = { vm.run { vm.message.value = vm.app.sync.authenticate(loginEmail, password, true, name); password = "" } }) { Text("Criar conta") }
            }
            TextButton(onClick = { vm.run { vm.app.sync.recover(loginEmail); vm.message.value = "Confira o e-mail de recuperação" } }) { Text("Recuperar senha") }
        } else {
            Text("Conectado: $email · $queued alterações na fila")
            Button(onClick = { vm.run { vm.app.sync.sync(); vm.message.value = "Sincronização concluída" } }) { Text("Sincronizar agora") }
            TextButton(onClick = { vm.run { vm.app.sync.logout() } }) { Text("Sair da conta") }
        }
        conflicts.forEach { op -> Card { Column(Modifier.padding(16.dp)) {
            Text("Conflito de edição", fontWeight = FontWeight.Bold); Text("Registro ${op.entityId.take(8)}. Outra pessoa alterou o mesmo campo.")
            Text("Sua alteração: ${org.json.JSONObject(op.payload).getJSONObject("data")}", style = MaterialTheme.typography.bodySmall)
            Text("Servidor: ${op.error?.let { org.json.JSONObject(it).optJSONObject("current") }}", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { vm.run { vm.app.sync.resolve(op, true) } }) { Text("Manter minha alteração") }
            TextButton(onClick = { vm.run { vm.app.sync.resolve(op, false) } }) { Text("Usar versão do servidor") }
        } } }
        Text("Mercado Fácil · Código Kotlin e Jetpack Compose", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(32.dp))
    }
    importData?.let { raw -> AlertDialog(onDismissRequest = { importData = null }, title = { Text("Importar backup?") }, text = { Text("Serão acrescentados apenas registros ainda não existentes. Listas restauradas ficam locais.") }, confirmButton = { TextButton(onClick = { vm.run { val count = backup.restore(raw); importData = null; vm.message.value = "$count registros importados" } }) { Text("Importar") } }, dismissButton = { TextButton(onClick = { importData = null }) { Text("Cancelar") } }) }
}
