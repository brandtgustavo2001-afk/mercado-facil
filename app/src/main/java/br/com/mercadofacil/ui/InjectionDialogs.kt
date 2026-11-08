package br.com.mercadofacil.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.mercadofacil.database.InjectionRecord
import br.com.mercadofacil.database.InjectionSchedule
import br.com.mercadofacil.domain.formatInjectionDateTime
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@Composable fun InjectionScheduleDialog(
    initial: InjectionSchedule?,
    onDismiss: () -> Unit,
    onSave: (InjectionSchedule) -> Unit
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var medication by remember(initial?.id) { mutableStateOf(initial?.medication.orEmpty()) }
    var dose by remember(initial?.id) { mutableStateOf(initial?.dose.orEmpty()) }
    var route by remember(initial?.id) { mutableStateOf(initial?.route.orEmpty()) }
    var responsible by remember(initial?.id) { mutableStateOf(initial?.responsible.orEmpty()) }
    var interval by remember(initial?.id) { mutableStateOf(initial?.intervalValue?.toString() ?: "1") }
    var unit by remember(initial?.id) { mutableStateOf(initial?.intervalUnit ?: "DAYS") }
    var nextDueAt by remember(initial?.id) { mutableLongStateOf(initial?.nextDueAt ?: System.currentTimeMillis() + 24 * 60 * 60 * 1000L) }
    var reminder by remember(initial?.id) { mutableStateOf(initial?.reminderEnabled ?: true) }
    var notes by remember(initial?.id) { mutableStateOf(initial?.notes.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    val intervalValue = interval.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nova aplicação" else "Editar aplicação") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it.take(120); error = null }, label = { Text("Nome da rotina *") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(medication, { medication = it.take(120) }, label = { Text("Medicamento") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(dose, { dose = it.take(80) }, label = { Text("Dose, como prescrita") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(route, { route = it.take(120) }, label = { Text("Via ou observação da aplicação") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(responsible, { responsible = it.take(120) }, label = { Text("Profissional responsável (opcional)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(interval, { interval = it.filter(Char::isDigit).take(4); error = null },
                        label = { Text("Repetir a cada") }, modifier = Modifier.weight(1f), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = interval.isNotBlank() && (intervalValue == null || intervalValue !in 1..8760),
                        supportingText = if (interval.isNotBlank() && (intervalValue == null || intervalValue !in 1..8760)) {
                            { Text("Use um valor entre 1 e 8.760.") }
                        } else null)
                    Choice("Unidade", intervalUnitLabel(unit), listOf("horas", "dias", "semanas", "meses")) { selected ->
                        unit = when (selected) { "horas" -> "HOURS"; "semanas" -> "WEEKS"; "meses" -> "MONTHS"; else -> "DAYS" }
                    }
                }
                Text("Use o intervalo recebido na orientação do seu profissional de saúde.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                InjectionDateTimeField("Próxima aplicação", nextDueAt, { nextDueAt = it })
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Lembrete no celular", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("O Android pode atrasar avisos em segundo plano.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = reminder, onCheckedChange = { reminder = it })
                }
                OutlinedTextField(notes, { notes = it.take(2000) }, label = { Text("Observações") },
                    modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 5)
                if (reminder) Text("O lembrete depende do ajuste global de notificações e da permissão do Android.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) { error = "Informe um nome para esta rotina."; return@TextButton }
                val parsed = interval.toIntOrNull()
                if (parsed == null || parsed !in 1..8760) { error = "Use um intervalo entre 1 e 8.760."; return@TextButton }
                val result = (initial ?: InjectionSchedule(name = name.trim(), nextDueAt = nextDueAt)).copy(
                    name = name.trim(), medication = medication.trim(), dose = dose.trim(), route = route.trim(),
                    responsible = responsible.trim(), intervalValue = parsed, intervalUnit = unit,
                    nextDueAt = nextDueAt, reminderEnabled = reminder, notes = notes.trim()
                )
                onSave(result)
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable fun InjectionRecordDialog(
    schedule: InjectionSchedule,
    initial: InjectionRecord?,
    onDismiss: () -> Unit,
    onSave: (InjectionRecord) -> Unit
) {
    var administeredAt by remember(initial?.id, schedule.id) { mutableLongStateOf(initial?.administeredAt ?: System.currentTimeMillis()) }
    var dose by remember(initial?.id, schedule.id) { mutableStateOf(initial?.dose ?: schedule.dose) }
    var site by remember(initial?.id, schedule.id) { mutableStateOf(initial?.applicationSite.orEmpty()) }
    var notes by remember(initial?.id, schedule.id) { mutableStateOf(initial?.notes.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    val inFuture = administeredAt > System.currentTimeMillis()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Registrar aplicação" else "Editar registro") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 490.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(schedule.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                InjectionDateTimeField("Data e horário", administeredAt, { administeredAt = it })
                OutlinedTextField(dose, { dose = it.take(80) }, label = { Text("Dose aplicada") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(site, { site = it.take(120) }, label = { Text("Local da aplicação (opcional)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(notes, { notes = it.take(2000) }, label = { Text("Observações") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
                if (inFuture) Text("A data de uma aplicação registrada não pode estar no futuro.",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = !inFuture, onClick = {
                if (administeredAt > System.currentTimeMillis()) { error = "Escolha uma data e horário que já tenham ocorrido."; return@TextButton }
                val record = (initial ?: InjectionRecord(scheduleId = schedule.id,
                    scheduledForAt = schedule.nextDueAt, administeredAt = administeredAt)).copy(
                    scheduleId = schedule.id, administeredAt = administeredAt,
                    dose = dose.trim(), applicationSite = site.trim(), notes = notes.trim()
                )
                onSave(record)
            }) { Text(if (initial == null) "Salvar registro" else "Salvar alterações") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun InjectionDateTimeField(label: String, millis: Long, onChange: (Long) -> Unit) {
    var dateOpen by remember { mutableStateOf(false) }
    var timeOpen by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { dateOpen = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp)); Spacer(Modifier.width(7.dp))
                Text(formatInjectionDateTime(millis).substringBefore(" às "))
            }
            OutlinedButton(onClick = { timeOpen = true }) {
                Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp)); Spacer(Modifier.width(7.dp))
                Text(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")))
            }
        }
    }
    if (dateOpen) {
        val localDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
        val state = rememberDatePickerState(initialSelectedDateMillis = localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { dateOpen = false },
            confirmButton = { TextButton(onClick = {
                state.selectedDateMillis?.let { selected ->
                    val date = Instant.ofEpochMilli(selected).atZone(ZoneOffset.UTC).toLocalDate()
                    onChange(withInjectionDate(millis, date))
                }
                dateOpen = false
            }) { Text("Escolher") } },
            dismissButton = { TextButton(onClick = { dateOpen = false }) { Text("Cancelar") } }
        ) { DatePicker(state) }
    }
    if (timeOpen) {
        val localTime = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        val state = rememberTimePickerState(initialHour = localTime.hour, initialMinute = localTime.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { timeOpen = false },
            title = { Text("Escolher horário") },
            text = { TimePicker(state) },
            confirmButton = { TextButton(onClick = { onChange(withInjectionTime(millis, state.hour, state.minute)); timeOpen = false }) { Text("Escolher") } },
            dismissButton = { TextButton(onClick = { timeOpen = false }) { Text("Cancelar") } }
        )
    }
}

private fun intervalUnitLabel(unit: String): String = when (unit) {
    "HOURS" -> "horas"
    "WEEKS" -> "semanas"
    "MONTHS" -> "meses"
    else -> "dias"
}

private fun withInjectionDate(millis: Long, date: LocalDate): Long {
    val old = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return date.atTime(old.toLocalTime()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

private fun withInjectionTime(millis: Long, hour: Int, minute: Int): Long {
    val old = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return old.toLocalDate().atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
