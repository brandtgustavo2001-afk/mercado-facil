package br.com.mercadofacil.ui

import android.net.Uri
import androidx.core.content.FileProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.*
import java.io.File
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var capturePath by rememberSaveable { mutableStateOf("") }
    var processing by remember { mutableStateOf(false) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val path = capturePath
        if (success && path.isNotEmpty()) scope.launch {
            processing = true
            try {
                val encoded = withContext(Dispatchers.IO) {
                    val source = File(path)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(path, bounds)
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2560) sample *= 2
                    val original = requireNotNull(BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }))
                    val scale = minOf(1f, 1920f / maxOf(original.width, original.height))
                    var bitmap = Bitmap.createScaledBitmap(original, (original.width * scale).toInt().coerceAtLeast(1), (original.height * scale).toInt().coerceAtLeast(1), true)
                    val orientation = android.media.ExifInterface(path).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)
                    val matrix = android.graphics.Matrix()
                    when (orientation) {
                        2 -> matrix.setScale(-1f, 1f)
                        3 -> matrix.setRotate(180f)
                        4 -> matrix.setScale(1f, -1f)
                        5 -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
                        6 -> matrix.setRotate(90f)
                        7 -> { matrix.setRotate(270f); matrix.postScale(-1f, 1f) }
                        8 -> matrix.setRotate(270f)
                    }
                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    var bytes: ByteArray
                    var quality = 92
                    do {
                        val output = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
                        bytes = output.toByteArray()
                        quality -= 8
                    } while (bytes.size > 1500000 && quality >= 60)
                    require(bytes.size <= 1500000) { "Foto muito grande. Tente fotografar mais de perto." }
                    source.delete()
                    Base64.encodeToString(bytes, Base64.NO_WRAP)
                }
                change?.invoke(encoded)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error = e.message ?: "Não foi possível salvar a foto."
            } finally { processing = false }
        } else if (path.isNotEmpty()) File(path).delete()
    }
    Row {
        if (change != null) TextButton(enabled = enabled && !processing, onClick = {
            runCatching {
                val directory = File(context.cacheDir, "captures").apply { mkdirs() }
                val file = File.createTempFile("item-", ".jpg", directory)
                capturePath = file.absolutePath
                camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.photos", file))
            }.onFailure { error = "Não foi possível abrir a câmera neste aparelho." }
        }) { Text(if (data.isEmpty()) "Tirar foto do item" else "Trocar foto") }
        if (data.isNotEmpty()) TextButton(onClick = { viewing = true }) { Text("Ver foto") }
    }
    if (processing) Text("Preparando foto…")
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
