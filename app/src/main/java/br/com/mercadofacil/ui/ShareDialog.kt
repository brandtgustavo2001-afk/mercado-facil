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
import br.com.mercadofacil.database.ShoppingList
import br.com.mercadofacil.viewmodel.MainViewModel
import org.json.JSONArray

@Composable fun ShareDialog(vm: MainViewModel, list: ShoppingList, dismiss: () -> Unit, shareText: () -> Unit) {
    var token by remember { mutableStateOf("") }; var code by remember { mutableStateOf("") }; var role by remember { mutableStateOf("editor") }; var members by remember { mutableStateOf(JSONArray()) }; var activity by remember { mutableStateOf(JSONArray()) }
    val context = LocalContext.current
    AlertDialog(onDismissRequest = dismiss, title = { Text("Compartilhar lista") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = shareText) { Text("Compartilhar texto") }
        Text("Para colaborar em tempo real, configure o Supabase e entre na sua conta em Ajustes.")
        if (!list.shared) OutlinedButton(onClick = { vm.run { require(vm.app.sync.configured()) { "Configure o Supabase primeiro" }; vm.app.sync.token(); vm.repository.share(list); vm.app.sync.sync(); vm.message.value = "Lista compartilhada ativada"; dismiss() } }) { Text("Ativar colaboração") }
        else {
            Choice("Permissão do convite", role, listOf("editor", "viewer")) { role = it }
            Button(onClick = { vm.run { token = vm.app.sync.invite(list.id, role) } }) { Text("Gerar convite (24h, um uso)") }
            if (token.isNotEmpty()) {
                Text(token)
                TextButton(onClick = { context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Convite Mercado Fácil", token)) }) { Text("Copiar código") }
                TextButton(onClick = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Entre na lista ${list.name} no Mercado Fácil com o código: $token"), "Enviar convite")) }) { Text("Enviar convite") }
            }
            TextButton(onClick = { vm.run { vm.app.sync.revoke(list.id); token = ""; vm.message.value = "Convites revogados" } }) { Text("Revogar convites") }
            TextButton(onClick = { vm.run { members = vm.app.sync.members(list.id); activity = vm.app.sync.activity(list.id) } }) { Text("Ver participantes e atividade") }
            for (i in 0 until members.length()) {
                val member = members.getJSONObject(i)
                Text("${member.getString("user_id").take(8)} · ${member.getString("role")}")
                if (member.getString("role") != "owner") TextButton(onClick = { vm.run { vm.app.sync.removeMember(list.id, member.getString("user_id")); members = vm.app.sync.members(list.id) } }) { Text("Remover acesso") }
            }
            for (i in 0 until activity.length()) { val event = activity.getJSONObject(i); Text("${event.getString("created_at")} · ${event.getString("action")}", style = MaterialTheme.typography.bodySmall) }
        }
        Field("Código de convite recebido", code, { code = it })
        OutlinedButton(onClick = { vm.run { vm.app.sync.join(code); vm.message.value = "Convite aceito"; dismiss() } }) { Text("Entrar em uma lista") }
    } }, confirmButton = { TextButton(onClick = dismiss) { Text("Fechar") } })
}
