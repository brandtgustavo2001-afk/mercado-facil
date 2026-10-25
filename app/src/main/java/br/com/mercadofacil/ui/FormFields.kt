package br.com.mercadofacil.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

@Composable fun Field(label: String, value: String, change: (String) -> Unit, decimal: Boolean = false) {
    OutlinedTextField(value, change, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Text))
}
@Composable fun Choice(label: String, value: String, options: List<String>, change: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text("$label: $value") }
        DropdownMenu(open, { open = false }) { options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { change(option); open = false }) } }
    }
}
