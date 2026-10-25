package br.com.mercadofacil.ui

import android.content.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.mercadofacil.database.ShoppingList
import br.com.mercadofacil.viewmodel.MainViewModel
import kotlinx.coroutines.CancellationException
import org.json.JSONArray

@Composable fun ShareDialog(
    vm: MainViewModel, list: ShoppingList, dismiss: () -> Unit,
    openAccount: () -> Unit, shareText: () -> Unit
) {
    var token by remember(list.id) { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var permission by remember { mutableStateOf("Pode editar") }
    var members by remember { mutableStateOf(JSONArray()) }
    var activity by remember { mutableStateOf(JSONArray()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val email by vm.app.sync.email.collectAsStateWithLifecycle()
    val status by vm.app.sync.status.collectAsStateWithLifecycle()
    val context = LocalContext.current
    fun perform(action: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        vm.run {
            try { action() }
            catch (e: Exception) { if (e is CancellationException) throw e; error = e.message ?: "Não foi possível concluir. Tente novamente." }
            finally { busy = false }
        }
    }
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text("Compartilhar lista") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(list.name, style = MaterialTheme.typography.titleMedium)
            Text("Todos usam a mesma lista: produtos adicionados, editados ou marcados aparecem nos outros celulares conectados.")
            if (email.isBlank()) {
                Text("Entre na sua conta para convidar alguém ou aceitar um convite. Cada pessoa pode usar sua própria conta.")
                Button(onClick = openAccount, modifier = Modifier.fillMaxWidth()) { Text("Entrar na minha conta") }
            } else {
                if (list.shared) Text(status, style = MaterialTheme.typography.bodySmall)
                Choice("Quem recebe o convite", permission, listOf("Pode editar", "Só visualizar")) { if (!busy) permission = it }
                Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = { perform {
                    val current = requireNotNull(vm.repository.dao.list(list.id))
                    if (!current.shared) vm.repository.share(current)
                    vm.app.sync.sync()
                    check(vm.repository.dao.queued(list.id) == 0) { "A lista ainda tem alterações pendentes. Resolva em Conta e ajustes antes de convidar." }
                    token = vm.app.sync.invite(list.id, if (permission == "Pode editar") "editor" else "viewer")
                } }) { Text(if (busy) "Aguarde…" else "Criar convite para esta lista") }
                if (token.isNotEmpty()) {
                    Text("Convite válido por 24 horas, para uma pessoa. Crie outro convite para cada participante.", style = MaterialTheme.typography.bodySmall)
                    TextButton(enabled = !busy, onClick = {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, "Vamos usar a mesma lista de compras: ${list.name}. No Mercado Fácil, abra o menu → Compartilhar lista e cole esta mensagem em Convite recebido. Código: $token"), "Enviar convite"))
                    }) { Text("Enviar convite") }
                    TextButton(onClick = { context.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("Convite Mercado Fácil", token)) }) { Text("Copiar código") }
                }
                HorizontalDivider()
                Text("Convite recebido", style = MaterialTheme.typography.titleSmall)
                Text("Cole o código ou a mensagem completa. A lista compartilhada será aberta aqui.", style = MaterialTheme.typography.bodySmall)
                Field("Código ou mensagem do convite", code, { code = it })
                OutlinedButton(enabled = !busy && code.isNotBlank(), modifier = Modifier.fillMaxWidth(), onClick = { perform {
                    vm.app.sync.join(code)
                    vm.message.value = "Lista compartilhada aberta. Os produtos serão atualizados para todos."
                    dismiss()
                } }) { Text("Abrir lista compartilhada") }
                if (list.shared) {
                    TextButton(enabled = !busy, onClick = { perform {
                        members = vm.app.sync.members(list.id); activity = vm.app.sync.activity(list.id)
                    } }) { Text("Ver participantes e atividade") }
                    for (i in 0 until members.length()) {
                        val member = members.getJSONObject(i)
                        Text("${member.getString("user_id").take(8)} · ${when(member.getString("role")) { "owner" -> "Responsável"; "editor" -> "Pode editar"; else -> "Só visualizar" }}")
                        if (member.getString("role") != "owner") TextButton(enabled = !busy, onClick = { perform {
                            vm.app.sync.removeMember(list.id, member.getString("user_id")); members = vm.app.sync.members(list.id)
                        } }) { Text("Remover acesso") }
                    }
                    for (i in 0 until activity.length()) {
                        val event = activity.getJSONObject(i)
                        Text("${event.getString("created_at")} · ${event.getString("action")}", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(enabled = !busy, onClick = { perform {
                        vm.app.sync.revoke(list.id); token = ""; vm.message.value = "Convites revogados"
                    } }) { Text("Cancelar convites ainda não usados") }
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            HorizontalDivider()
            Text("Enviar só uma cópia", style = MaterialTheme.typography.titleSmall)
            Text("A cópia em texto não recebe atualizações dos outros celulares.", style = MaterialTheme.typography.bodySmall)
            TextButton(enabled = !busy, onClick = shareText) { Text("Enviar cópia em texto") }
        }
    }, confirmButton = { TextButton(enabled = !busy, onClick = dismiss) { Text("Fechar") } })
}
