package br.com.mercadofacil.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream

@Composable fun PhotoAttachment(data: String, change: ((String) -> Unit)?, enabled: Boolean) {
    var viewing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            val bytes = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, bytes)
            change?.invoke(Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP))
        }
    }
    Row {
        if (change != null) TextButton(enabled = enabled, onClick = {
            runCatching { camera.launch(null) }.onFailure { error = "Não foi possível abrir a câmera neste aparelho." }
        }) { Text(if (data.isEmpty()) "Tirar foto do item" else "Trocar foto") }
        if (data.isNotEmpty()) TextButton(onClick = { viewing = true }) { Text("Ver foto") }
    }
    if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
    if (viewing) Dialog(
        onDismissRequest = { viewing = false },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = Color.Black, contentColor = Color.White) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (change != null) TextButton(onClick = { change(""); viewing = false }) {
                        Text("Remover foto", color = Color.White)
                    }
                    TextButton(onClick = { viewing = false }) { Text("Fechar", color = Color.White) }
                }
                val bitmap = remember(data) { runCatching {
                    val bytes = Base64.decode(data, Base64.NO_WRAP)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }.getOrNull() }
                if (bitmap != null) Image(
                    bitmap.asImageBitmap(), "Foto de referência do produto",
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentScale = ContentScale.Fit
                ) else Text("Não foi possível carregar a foto.", Modifier.padding(24.dp))
            }
        }
    }
}
