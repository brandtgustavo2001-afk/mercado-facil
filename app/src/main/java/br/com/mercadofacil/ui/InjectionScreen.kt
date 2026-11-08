package br.com.mercadofacil.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.mercadofacil.database.InjectionRecord
import br.com.mercadofacil.database.InjectionSchedule
import br.com.mercadofacil.domain.formatInjectionDateTime
import br.com.mercadofacil.domain.injectionIntervalLabel
import br.com.mercadofacil.viewmodel.MainViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable fun InjectionScreen(vm: MainViewModel) {
    val schedules by vm.injectionSchedules.collectAsStateWithLifecycle()
    val records by vm.injectionRecords.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("ATIVAS") }
    var adding by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<InjectionSchedule?>(null) }
    var logging by remember { mutableStateOf<InjectionSchedule?>(null) }
    var editingRecord by remember { mutableStateOf<Pair<InjectionSchedule, InjectionRecord>?>(null) }
    var deleting by remember { mutableStateOf<InjectionSchedule?>(null) }
    var deletingRecord by remember { mutableStateOf<Pair<InjectionSchedule, InjectionRecord>?>(null) }

    val active = schedules.filter { it.active }
    val visible = when (filter) {
        "PAUSADAS" -> schedules.filterNot { it.active }
        "TODAS" -> schedules
        else -> active
    }
    val recordsBySchedule = records.groupBy { it.scheduleId }
    val now = System.currentTimeMillis()
    val dueSoon = active.count { it.nextDueAt in now..(now + 7 * 24 * 60 * 60 * 1000L) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().widthIn(max = 640.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ROTINA DE SAÚDE", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text("Injeções", style = MaterialTheme.typography.headlineSmall)
                Text("Organize as próximas datas e registre cada aplicação.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Nova aplicação")
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InjectionSummary("Ativas", active.size.toString(), Icons.Outlined.MedicalServices, Modifier.weight(1f))
                InjectionSummary("Nos próximos 7 dias", dueSoon.toString(), Icons.Outlined.Event, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ATIVAS" to "Ativas", "PAUSADAS" to "Pausadas", "TODAS" to "Todas").forEach { (key, label) ->
                    FilterChip(selected = filter == key, onClick = { filter = key }, label = { Text(label) })
                }
            }
        }
        if (visible.isEmpty()) item {
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (schedules.isEmpty()) "Nenhuma aplicação cadastrada" else "Nenhuma aplicação nesta lista",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (schedules.isEmpty()) "Cadastre uma rotina para acompanhar a próxima data e seu histórico."
                    else "Mude o filtro ou cadastre uma nova aplicação.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (schedules.isEmpty()) TextButton(onClick = { adding = true }) { Text("Cadastrar aplicação") }
                }
            }
        }
        items(visible, key = { it.id }) { schedule ->
            InjectionScheduleCard(
                schedule = schedule,
                records = recordsBySchedule[schedule.id].orEmpty().sortedByDescending { it.administeredAt },
                onRecord = { logging = schedule },
                onEdit = { editing = schedule },
                onPause = { vm.saveInjection(schedule.copy(active = !schedule.active)) },
                onDelete = { deleting = schedule },
                onEditRecord = { record -> editingRecord = schedule to record },
                onDeleteRecord = { record -> deletingRecord = schedule to record }
            )
        }
        item {
            Text("Os registros ficam neste aparelho e não entram na sincronização nem no backup geral. Confira as orientações recebidas do seu profissional de saúde.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp))
        }
    }

    if (adding || editing != null) InjectionScheduleDialog(
        initial = editing,
        onDismiss = { adding = false; editing = null },
        onSave = { vm.saveInjection(it); adding = false; editing = null }
    )
    logging?.let { schedule -> InjectionRecordDialog(
        schedule = schedule,
        initial = null,
        onDismiss = { logging = null },
        onSave = { vm.recordInjection(schedule, it); logging = null }
    ) }
    editingRecord?.let { (schedule, record) -> InjectionRecordDialog(
        schedule = schedule,
        initial = record,
        onDismiss = { editingRecord = null },
        onSave = { vm.updateInjectionRecord(schedule, it); editingRecord = null }
    ) }
    deleting?.let { schedule -> AlertDialog(
        onDismissRequest = { deleting = null },
        title = { Text("Excluir esta rotina?") },
        text = { Text("A rotina e o histórico de aplicações dela serão removidos deste aparelho.") },
        confirmButton = { TextButton(onClick = { vm.deleteInjection(schedule); deleting = null }) { Text("Excluir") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } }
    ) }
    deletingRecord?.let { (schedule, record) -> AlertDialog(
        onDismissRequest = { deletingRecord = null },
        title = { Text("Excluir este registro?") },
        text = { Text("O histórico será atualizado e a próxima data será recalculada se este for o registro mais recente.") },
        confirmButton = { TextButton(onClick = { vm.deleteInjectionRecord(schedule, record); deletingRecord = null }) { Text("Excluir") } },
        dismissButton = { TextButton(onClick = { deletingRecord = null }) { Text("Cancelar") } }
    ) }
}

@Composable private fun InjectionSummary(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Column {
                Text(value, style = MaterialTheme.typography.titleLarge)
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable private fun InjectionScheduleCard(
    schedule: InjectionSchedule,
    records: List<InjectionRecord>,
    onRecord: () -> Unit,
    onEdit: () -> Unit,
    onPause: () -> Unit,
    onDelete: () -> Unit,
    onEditRecord: (InjectionRecord) -> Unit,
    onDeleteRecord: (InjectionRecord) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showHistory by rememberSaveable(schedule.id) { mutableStateOf(false) }
    val due = dueStatus(schedule.nextDueAt)
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(schedule.name, style = MaterialTheme.typography.titleMedium)
                    if (schedule.medication.isNotBlank()) Text(schedule.medication, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(color = if (!schedule.active) MaterialTheme.colorScheme.surfaceVariant else due.color,
                    contentColor = if (!schedule.active) MaterialTheme.colorScheme.onSurfaceVariant else due.contentColor,
                    shape = RoundedCornerShape(50)) {
                    Text(if (schedule.active) due.label else "Pausada", Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, "Mais opções para ${schedule.name}") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Editar") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                            onClick = { menuOpen = false; onEdit() })
                        DropdownMenuItem(text = { Text(if (schedule.active) "Pausar rotina" else "Retomar rotina") },
                            leadingIcon = { Icon(if (schedule.active) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null) },
                            onClick = { menuOpen = false; onPause() })
                        DropdownMenuItem(text = { Text("Excluir rotina") }, leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) },
                            onClick = { menuOpen = false; onDelete() })
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(Icons.Outlined.Event, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                Column {
                    Text("Próxima aplicação", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatInjectionDateTime(schedule.nextDueAt), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(buildString {
                if (schedule.dose.isNotBlank()) append("Dose: ${schedule.dose}  ·  ")
                append(injectionIntervalLabel(schedule.intervalValue, schedule.intervalUnit))
                if (schedule.route.isNotBlank()) append("  ·  ${schedule.route}")
            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (schedule.responsible.isNotBlank()) Text("Profissional: ${schedule.responsible}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (schedule.notes.isNotBlank()) Text(schedule.notes, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onRecord, enabled = schedule.active, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)); Text("Registrar")
                }
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)); Text("Editar")
                }
            }
            if (records.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TextButton(onClick = { showHistory = !showHistory }, contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)) {
                    Icon(if (showHistory) Icons.Outlined.ExpandLess else Icons.Outlined.History, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (showHistory) "Ocultar histórico" else "Histórico (${records.size})")
                }
                if (showHistory) records.forEach { record ->
                    InjectionHistoryRow(record, onEdit = { onEditRecord(record) }, onDelete = { onDeleteRecord(record) })
                }
            } else Text("Nenhuma aplicação registrada ainda.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun InjectionHistoryRow(record: InjectionRecord, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(formatInjectionDateTime(record.administeredAt), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            val details = listOf(record.dose.takeIf { it.isNotBlank() }, record.applicationSite.takeIf { it.isNotBlank() })
                .filterNotNull().joinToString(" · ")
            if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (record.notes.isNotBlank()) Text(record.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "Editar registro") }
        IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, "Excluir registro", tint = MaterialTheme.colorScheme.error) }
    }
}

private data class DueStatus(val label: String, val color: androidx.compose.ui.graphics.Color, val contentColor: androidx.compose.ui.graphics.Color)

@Composable private fun dueStatus(dueAt: Long): DueStatus {
    val now = System.currentTimeMillis()
    val due = Instant.ofEpochMilli(dueAt).atZone(ZoneId.systemDefault())
    val today = LocalDate.now()
    val date = due.toLocalDate()
    val time = due.format(DateTimeFormatter.ofPattern("HH:mm", Locale("pt", "BR")))
    return when {
        dueAt < now -> DueStatus("Atrasada", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        date == today -> DueStatus("Hoje · $time", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        date == today.plusDays(1) -> DueStatus("Amanhã · $time", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        else -> {
            val days = ChronoUnit.DAYS.between(today, date)
            DueStatus("Em $days dias", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}
