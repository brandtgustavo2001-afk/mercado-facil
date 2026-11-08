package br.com.mercadofacil.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.mercadofacil.database.InjectionRecord
import br.com.mercadofacil.database.InjectionSchedule
import br.com.mercadofacil.domain.formatInjectionDateTime
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun InjectionCalendar(
    schedules: List<InjectionSchedule>,
    records: List<InjectionRecord>,
    month: YearMonth,
    selectedDate: LocalDate,
    onMonthChange: (YearMonth) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onRecord: (InjectionSchedule) -> Unit,
    onEditSchedule: (InjectionSchedule) -> Unit,
    onEditRecord: (InjectionSchedule, InjectionRecord) -> Unit
) {
    val locale = remember { Locale("pt", "BR") }
    val schedulesById = remember(schedules) { schedules.associateBy { it.id } }
    val dueByDate = remember(schedules, month) {
        schedules.filter { it.active }.filter { due ->
            val date = java.time.Instant.ofEpochMilli(due.nextDueAt).atZone(ZoneId.systemDefault()).toLocalDate()
            YearMonth.from(date) == month
        }.groupBy { due ->
            java.time.Instant.ofEpochMilli(due.nextDueAt).atZone(ZoneId.systemDefault()).toLocalDate()
        }
    }
    val recordsByDate = remember(records, schedules, month) {
        records.mapNotNull { record ->
            val schedule = schedulesById[record.scheduleId] ?: return@mapNotNull null
            val date = java.time.Instant.ofEpochMilli(record.administeredAt).atZone(ZoneId.systemDefault()).toLocalDate()
            if (YearMonth.from(date) == month) date to (schedule to record) else null
        }.groupBy({ it.first }, { it.second })
    }
    val daysInMonth = month.lengthOfMonth()
    val leadingBlanks = month.atDay(1).dayOfWeek.value - 1
    val cellCount = ((leadingBlanks + daysInMonth + 6) / 7) * 7
    val cells = (0 until cellCount).map { index ->
        (index - leadingBlanks + 1).takeIf { it in 1..daysInMonth }?.let(month::atDay)
    }
    val monthLabel = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    val selectedLabel = selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", locale))
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    val selectedDues = dueByDate[selectedDate].orEmpty()
    val selectedRecords = recordsByDate[selectedDate].orEmpty()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Calendário", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) {
                    Icon(Icons.Outlined.ChevronLeft, "Mês anterior")
                }
                Text(monthLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { onMonthChange(month.plusMonths(1)) }) {
                    Icon(Icons.Outlined.ChevronRight, "Próximo mês")
                }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("seg", "ter", "qua", "qui", "sex", "sáb", "dom").forEach { day ->
                    Text(day, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        if (date == null) {
                            Spacer(Modifier.weight(1f).height(44.dp))
                        } else {
                            val isSelected = date == selectedDate
                            val hasDue = !dueByDate[date].isNullOrEmpty()
                            val hasRecord = !recordsByDate[date].isNullOrEmpty()
                            val isToday = date == LocalDate.now()
                            val background = when {
                                isSelected -> MaterialTheme.colorScheme.primaryContainer
                                isToday -> MaterialTheme.colorScheme.secondaryContainer
                                else -> MaterialTheme.colorScheme.surface
                            }
                            val content = when {
                                isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                isToday -> MaterialTheme.colorScheme.onSecondaryContainer
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                            Column(
                                Modifier.weight(1f).padding(2.dp).height(44.dp)
                                    .background(background, CircleShape)
                                    .clickable { onDateChange(date) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(date.dayOfMonth.toString(), color = content,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal)
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.height(5.dp)) {
                                    if (hasDue) Box(Modifier.size(4.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                                    if (hasRecord) Box(Modifier.size(4.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape))
                                }
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                CalendarLegend(MaterialTheme.colorScheme.primary, "Prevista")
                CalendarLegend(MaterialTheme.colorScheme.tertiary, "Registrada")
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(selectedLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (selectedDues.isEmpty() && selectedRecords.isEmpty()) {
                Text("Nenhuma aplicação prevista ou registrada nesta data.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            selectedDues.forEach { schedule ->
                InjectionCalendarEvent(
                    title = schedule.name,
                    person = schedule.personName,
                    detail = "Prevista · ${formatInjectionDateTime(schedule.nextDueAt).substringAfter("às ")}" +
                        schedule.medication.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                    onAction = { onRecord(schedule) },
                    onEdit = { onEditSchedule(schedule) },
                    actionLabel = "Registrar"
                )
            }
            selectedRecords.forEach { (schedule, record) ->
                InjectionCalendarEvent(
                    title = schedule.name,
                    person = schedule.personName,
                    detail = "Registrada · ${formatInjectionDateTime(record.administeredAt)}" +
                        record.dose.takeIf { it.isNotBlank() }?.let { " · Dose $it" }.orEmpty(),
                    onAction = { onEditRecord(schedule, record) },
                    onEdit = { onEditSchedule(schedule) },
                    actionLabel = "Editar registro"
                )
            }
        }
    }
}

@Composable
private fun CalendarLegend(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InjectionCalendarEvent(
    title: String,
    person: String,
    detail: String,
    onAction: () -> Unit,
    onEdit: () -> Unit,
    actionLabel: String
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${person.ifBlank { "Pessoa não identificada" }} · $title", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "Editar rotina") }
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}
