package br.com.mercadofacil.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import br.com.mercadofacil.BuildConfig
import br.com.mercadofacil.updater.Release
import br.com.mercadofacil.updater.Updater
import br.com.mercadofacil.viewmodel.MainViewModel
import kotlinx.coroutines.Job
import java.io.File

@Composable fun UpdatePrompt(vm: MainViewModel, checkRequest: Int = 0) {
    val context = LocalContext.current
    val updater = remember { Updater(context.applicationContext) }
    var release by remember { mutableStateOf<Release?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var download by remember { mutableStateOf<Job?>(null) }
    var ready by remember { mutableStateOf<File?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (context.packageManager.canRequestPackageInstalls()) ready?.let { file -> vm.run { updater.install(context as Activity, file) } }
        else vm.message.value = "Autorize a instalação desta origem para concluir a atualização."
    }
    fun install(file: File) {
        if (context.packageManager.canRequestPackageInstalls()) vm.run { updater.install(context as Activity, file) }
        else permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (release == null && ready == null) vm.run { release = updater.check() }
    }
    LaunchedEffect(checkRequest) {
        if (checkRequest > 0) vm.run {
            release = updater.check(true)
            if (release == null) vm.message.value = "Você está na versão mais recente."
        }
    }
    release?.let { update ->
        AlertDialog(
            onDismissRequest = { if (progress == null) release = null },
            title = { Text("Nova atualização disponível") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Versão instalada: ${BuildConfig.VERSION_NAME}\nNova versão: ${update.version}")
                Text(update.notes.ifBlank { "Melhorias e correções do Mercado Fácil." })
                progress?.let { value -> LinearProgressIndicator(progress = { value }, modifier = Modifier.fillMaxWidth()); Text("Baixando: ${(value * 100).toInt()}%") }
                if (ready != null) Text("APK verificado. Confirme a instalação no Android para atualizar.")
            } },
            confirmButton = {
                if (progress == null) TextButton(onClick = {
                    val file = ready
                    if (file != null) install(file)
                    else download = vm.run {
                        progress = 0f
                        try { ready = updater.download(update) { progress = it }; install(requireNotNull(ready)) }
                        finally { progress = null }
                    }
                }) { Text(if (ready == null) "Atualizar agora" else "Instalar atualização") }
            },
            dismissButton = { TextButton(onClick = {
                download?.cancel(); progress = null; release = null
            }) { Text(if (progress == null) "Mais tarde" else "Cancelar download") } }
        )
    }
}
